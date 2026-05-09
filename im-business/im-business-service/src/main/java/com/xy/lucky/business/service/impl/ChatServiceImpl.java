package com.xy.lucky.business.service.impl;

import com.xy.lucky.business.common.LockExecutor;
import com.xy.lucky.business.domain.dto.ChatDto;
import com.xy.lucky.business.domain.mapper.ChatBeanMapper;
import com.xy.lucky.business.domain.vo.ChatVo;
import com.xy.lucky.business.exception.ChatException;
import com.xy.lucky.business.service.ChatService;
import com.xy.lucky.core.enums.IMStatus;
import com.xy.lucky.core.enums.IMessageReadStatus;
import com.xy.lucky.core.enums.IMessageType;
import com.xy.lucky.domain.po.ImChatPo;
import com.xy.lucky.domain.po.ImGroupPo;
import com.xy.lucky.domain.po.ImGroupMessagePo;
import com.xy.lucky.domain.po.ImGroupMessageStatusPo;
import com.xy.lucky.domain.po.ImSingleMessagePo;
import com.xy.lucky.domain.po.ImUserDataPo;
import com.xy.lucky.rpc.api.database.chat.ImChatDubboService;
import com.xy.lucky.rpc.api.database.group.ImGroupDubboService;
import com.xy.lucky.rpc.api.database.message.ImGroupMessageDubboService;
import com.xy.lucky.rpc.api.database.message.ImSingleMessageDubboService;
import com.xy.lucky.rpc.api.database.user.ImUserDataDubboService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 *
 * @author xy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /**
     * 分布式锁前缀
     */
    private static final String LOCK_PREFIX = "lock:chat:";
    private final LockExecutor lockExecutor;
    @DubboReference
    private ImChatDubboService chatDubboService;
    @DubboReference
    private ImUserDataDubboService userDataDubboService;
    @DubboReference
    private ImGroupDubboService groupDubboService;
    @DubboReference
    private ImSingleMessageDubboService singleMessageDubboService;
    @DubboReference
    private ImGroupMessageDubboService groupMessageDubboService;

    private final ChatBeanMapper chatBeanMapper;

    /**
     * 标记消息已读
     *
     * @param dto 会话信息（已在 Controller 层校验）
     */
    @Override
    public void read(ChatDto dto) {
        IMessageType type = IMessageType.getByCode(dto.getChatType());
        if (type == null) {
            throw new ChatException("不支持的消息类型");
        }
        switch (type) {
            case SINGLE_MESSAGE -> markSingleMessageRead(dto);
            case GROUP_MESSAGE -> markGroupMessageRead(dto);
            default -> throw new ChatException("不支持的消息类型");
        }
    }

    /**
     * 创建会话
     *
     * @param dto 会话信息（已在 Controller 层校验）
     * @return 会话详情
     */
    @Override
    public ChatVo create(ChatDto dto) {
        String lockKey = LOCK_PREFIX + "create:" + dto.getFromId() + ":" + dto.getToId() + ":" + dto.getChatType();
        return lockExecutor.execute(lockKey, () -> {
            ImChatPo chatPo = Optional.ofNullable(chatDubboService.queryOne(dto.getFromId(), dto.getToId(), dto.getChatType()))
                    .orElseGet(() -> createNewChat(dto));
            return enrichChatVo(chatPo, dto.getChatType());
        });
    }

    /**
     * 查询单个会话
     *
     * @param ownerId 所有者ID（已在 Controller 层校验）
     * @param toId    目标ID（已在 Controller 层校验）
     * @return 会话详情
     */
    @Override
    public ChatVo one(String ownerId, String toId) {
        return Optional.ofNullable(chatDubboService.queryOne(ownerId, toId, null))
                .map(this::buildChatVo)
                .orElseGet(ChatVo::new);
    }

    /**
     * 查询会话列表
     *
     * @param dto 查询条件（已在 Controller 层校验）
     * @return 会话列表
     */
    @Override
    public List<ChatVo> list(ChatDto dto) {
        List<ImChatPo> chatList = chatDubboService.queryList(dto.getFromId(), dto.getSequence());
        if (CollectionUtils.isEmpty(chatList)) {
            return Collections.emptyList();
        }
        ChatBuildContext context = buildContext(chatList, dto.getFromId());
        return chatList.stream()
                .map(chatPo -> buildChatVo(chatPo, context))
                .toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 创建新会话
     */
    private ImChatPo createNewChat(ChatDto dto) {
        ImChatPo chatPo = new ImChatPo()
                .setChatId(UUID.randomUUID().toString())
                .setOwnerId(dto.getFromId())
                .setToId(dto.getToId())
                .setChatType(dto.getChatType())
                .setIsMute(IMStatus.NO.getCode())
                .setIsTop(IMStatus.NO.getCode());

        if (!chatDubboService.creat(chatPo)) {
            throw new ChatException("创建会话失败");
        }

        log.info("创建会话成功: chatId={}, from={}, to={}", chatPo.getChatId(), dto.getFromId(), dto.getToId());
        return chatPo;
    }

    /**
     * 构建会话 VO（根据类型选择策略）
     */
    private ChatVo buildChatVo(ImChatPo chatPo) {
        return buildChatVo(chatPo, null);
    }

    /**
     * 构建会话 VO（支持批量预加载上下文，降低 Dubbo N+1 调用）
     */
    private ChatVo buildChatVo(ImChatPo chatPo, ChatBuildContext context) {
        return Optional.ofNullable(chatPo)
                .map(po -> Optional.ofNullable(IMessageType.getByCode(po.getChatType()))
                        .map(type -> switch (type) {
                            case SINGLE_MESSAGE -> buildSingleChatVo(po, context);
                            case GROUP_MESSAGE -> buildGroupChatVo(po, context);
                            default -> new ChatVo();
                        })
                        .orElseGet(ChatVo::new))
                .orElseGet(ChatVo::new);
    }

    /**
     * 构建单聊会话 VO
     */
    private ChatVo buildSingleChatVo(ImChatPo chatPo, ChatBuildContext context) {
        ChatVo vo = chatBeanMapper.toChatVo(chatPo);
        String ownerId = vo.getOwnerId();
        String toId = vo.getToId();

        // 获取最后一条消息
        ImSingleMessagePo lastMsg = singleMessageDubboService.queryLast(ownerId, toId);
        Optional.ofNullable(lastMsg)
                .map(ImSingleMessagePo::getMessageId)
                .ifPresentOrElse(messageId -> {
                    vo.setMessage(lastMsg.getMessageBody());
                    vo.setMessageContentType(lastMsg.getMessageContentType());
                    vo.setMessageTime(lastMsg.getMessageTime());
                }, () -> vo.setMessageTime(0L));

        // 获取未读数
        Integer unread = singleMessageDubboService.queryReadStatus(toId, ownerId, IMessageReadStatus.UNREAD.getCode());
        vo.setUnread(Optional.ofNullable(unread).orElse(0));

        // 获取用户信息
        String targetUserId = ownerId.equals(toId) ? ownerId : toId;
        ImUserDataPo userDataPo = Optional.ofNullable(context)
                .map(ChatBuildContext::users)
                .map(users -> users.get(targetUserId))
                .orElse(null);

        Optional.ofNullable(userDataPo != null ? userDataPo : userDataDubboService.queryOne(targetUserId))
                .ifPresent(user -> {
                    vo.setId(user.getUserId());
                    vo.setName(user.getName());
                    vo.setAvatar(user.getAvatar());
                });

        return vo;
    }

    /**
     * 构建群聊会话 VO
     */
    private ChatVo buildGroupChatVo(ImChatPo chatPo, ChatBuildContext context) {
        ChatVo vo = chatBeanMapper.toChatVo(chatPo);
        String ownerId = vo.getOwnerId();
        String groupId = vo.getToId();

        // 获取最后一条消息
        ImGroupMessagePo lastMsg = groupMessageDubboService.queryLast(groupId, ownerId);
        Optional.ofNullable(lastMsg)
                .map(ImGroupMessagePo::getMessageId)
                .ifPresentOrElse(messageId -> {
                    vo.setMessage(lastMsg.getMessageBody());
                    vo.setMessageTime(lastMsg.getMessageTime());
                }, () -> vo.setMessageTime(0L));

        // 获取未读数
        Integer unread = groupMessageDubboService.queryReadStatus(groupId, ownerId, IMessageReadStatus.UNREAD.getCode());
        vo.setUnread(Optional.ofNullable(unread).orElse(0));

        // 获取群信息
        ImGroupPo groupPo = Optional.ofNullable(context)
                .map(ChatBuildContext::groups)
                .map(groups -> groups.get(groupId))
                .orElse(null);
        Optional.ofNullable(groupPo != null ? groupPo : groupDubboService.queryOne(groupId))
                .ifPresent(group -> {
                    vo.setId(group.getGroupId());
                    vo.setName(group.getGroupName());
                    vo.setAvatar(group.getAvatar());
                });

        return vo;
    }

    /**
     * 构建会话列表上下文，避免在 list 场景对用户/群信息进行 N+1 查询
     */
    private ChatBuildContext buildContext(List<ImChatPo> chatList, String ownerId) {
        Set<String> userIds = new HashSet<>();
        Set<String> groupIds = new HashSet<>();

        for (ImChatPo chatPo : chatList) {
            IMessageType type = IMessageType.getByCode(chatPo.getChatType());
            if (type == null) {
                continue;
            }
            if (type == IMessageType.SINGLE_MESSAGE) {
                String targetUserId = ownerId.equals(chatPo.getToId()) ? ownerId : chatPo.getToId();
                if (targetUserId != null) {
                    userIds.add(targetUserId);
                }
                continue;
            }
            if (type == IMessageType.GROUP_MESSAGE && chatPo.getToId() != null) {
                groupIds.add(chatPo.getToId());
            }
        }

        Map<String, ImUserDataPo> userMap = Collections.emptyMap();
        if (!CollectionUtils.isEmpty(userIds)) {
            userMap = Optional.ofNullable(userDataDubboService.queryListByIds(new ArrayList<>(userIds)))
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .collect(Collectors.toMap(ImUserDataPo::getUserId, Function.identity(), (a, b) -> a));
        }

        Map<String, ImGroupPo> groupMap = Collections.emptyMap();
        if (!CollectionUtils.isEmpty(groupIds)) {
            groupMap = Optional.ofNullable(groupDubboService.queryListByIds(new ArrayList<>(groupIds)))
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .collect(Collectors.toMap(ImGroupPo::getGroupId, Function.identity(), (a, b) -> a));
        }
        return new ChatBuildContext(userMap, groupMap);
    }

    private record ChatBuildContext(Map<String, ImUserDataPo> users, Map<String, ImGroupPo> groups) {
    }

    /**
     * 丰富会话 VO（用于创建会话后返回）
     */
    private ChatVo enrichChatVo(ImChatPo chatPo, Integer chatType) {
        ChatVo vo = chatBeanMapper.toChatVo(chatPo);

        if (IMessageType.SINGLE_MESSAGE.getCode().equals(chatType)) {
            Optional.ofNullable(userDataDubboService.queryOne(vo.getToId()))
                    .ifPresent(user -> {
                        vo.setId(user.getUserId());
                        vo.setName(user.getName());
                        vo.setAvatar(user.getAvatar());
                    });
        } else if (IMessageType.GROUP_MESSAGE.getCode().equals(chatType)) {
            Optional.ofNullable(groupDubboService.queryOne(vo.getToId()))
                    .ifPresent(group -> {
                        vo.setId(group.getGroupId());
                        vo.setName(group.getGroupName());
                        vo.setAvatar(group.getAvatar());
                    });
        }

        return vo;
    }

    /**
     * 标记单聊消息已读
     */
    private void markSingleMessageRead(ChatDto dto) {
        ImSingleMessagePo updatePo = new ImSingleMessagePo()
                .setReadStatus(IMessageReadStatus.ALREADY_READ.getCode())
                .setFromId(dto.getFromId())
                .setToId(dto.getToId());
        singleMessageDubboService.modifyReadStatus(updatePo);
    }

    /**
     * 标记群聊消息已读
     */
    private void markGroupMessageRead(ChatDto dto) {
        ImGroupMessageStatusPo updatePo = new ImGroupMessageStatusPo()
                .setReadStatus(IMessageReadStatus.ALREADY_READ.getCode())
                .setGroupId(dto.getToId())
                .setToId(dto.getFromId());
        groupMessageDubboService.modifyReadStatus(updatePo);
    }
}
