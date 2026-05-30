package com.xy.lucky.connect.netty;

import com.xy.lucky.connect.config.LogConstant;
import com.xy.lucky.connect.config.properties.RabbitMQProperties;
import com.xy.lucky.connect.mq.RabbitTemplate;
import com.xy.lucky.connect.utils.JacksonUtil;
import com.xy.lucky.connect.utils.MessageUtils;
import com.xy.lucky.core.constants.IMConstant;
import com.xy.lucky.core.enums.IMessageType;
import com.xy.lucky.core.model.IMessageWrap;
import com.xy.lucky.core.utils.StringUtils;
import com.xy.lucky.spring.annotations.core.Autowired;
import com.xy.lucky.spring.annotations.core.Component;
import com.xy.lucky.spring.annotations.core.Value;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端上行业务消息处理器
 * <p>
 * 负责把客户端消息标准化后投递到 MQ，供后端业务服务异步消费。
 */
@Slf4j(topic = LogConstant.Message)
@Component
@ChannelHandler.Sharable
public class ClientInboundMessageHandler extends SimpleChannelInboundHandler<IMessageWrap<Object>> {

    private static final AttributeKey<String> USER_ID_ATTR = AttributeKey.valueOf(IMConstant.IM_USER);
    private static final AttributeKey<String> DEVICE_TYPE_ATTR = AttributeKey.valueOf(IMConstant.IM_DEVICE_TYPE);

    private static final EnumSet<IMessageType> UPSTREAM_TYPES = EnumSet.of(
            IMessageType.SINGLE_MESSAGE,
            IMessageType.GROUP_MESSAGE,
            IMessageType.VIDEO_MESSAGE,
            IMessageType.GROUP_OPERATION,
            IMessageType.MESSAGE_OPERATION
    );

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    @Value("${brokerId:}")
    private String brokerId;

    @Value("${netty.config.clientUpstreamRoutingKey:}")
    private String clientUpstreamRoutingKey;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessageWrap<Object> message) {
        if (message == null || message.getCode() == null) {
            log.warn("收到空消息或非法 code，忽略上行");
            return;
        }

        IMessageType messageType = IMessageType.getByCode(message.getCode());
        if (messageType == null || !UPSTREAM_TYPES.contains(messageType)) {
            ctx.fireChannelRead(message);
            return;
        }

        String userId = ctx.channel().attr(USER_ID_ATTR).get();
        if (!StringUtils.hasText(userId)) {
            MessageUtils.sendError(ctx, IMessageType.NOT_LOGIN.getCode(), "未登录或登录已失效");
            ctx.close();
            return;
        }

        enrichMessage(ctx, message, userId);

        String payload = JacksonUtil.toJSONString(message);
        if (!StringUtils.hasText(payload)) {
            log.warn("客户端上行消息序列化为空，type={}, userId={}", messageType, userId);
            return;
        }

        String routingKey = resolveRoutingKey();
        rabbitTemplate.sendToBroker(routingKey, payload);
        log.debug("客户端消息已投递到 MQ: type={}, userId={}, routingKey={}, requestId={}",
                messageType, userId, routingKey, message.getRequestId());
    }

    private void enrichMessage(ChannelHandlerContext ctx, IMessageWrap<Object> message, String userId) {
        if (!StringUtils.hasText(message.getRequestId())) {
            message.setRequestId(UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null || message.getTimestamp() <= 0L) {
            message.setTimestamp(System.currentTimeMillis());
        }
        if (!StringUtils.hasText(message.getDeviceType())) {
            message.setDeviceType(ctx.channel().attr(DEVICE_TYPE_ATTR).get());
        }
        if (!StringUtils.hasText(message.getClientIp())) {
            message.setClientIp(resolveClientIp(ctx));
        }

        Map<String, String> metadata = message.getMetadata();
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
            message.setMetadata(metadata);
        }
        metadata.putIfAbsent("fromUserId", userId);
        if (StringUtils.hasText(brokerId)) {
            metadata.putIfAbsent("brokerId", brokerId);
        }

        // 客户端 token 不向业务队列透传，避免日志/链路泄漏。
        message.setToken(null);
    }

    private String resolveRoutingKey() {
        if (StringUtils.hasText(clientUpstreamRoutingKey)) {
            return clientUpstreamRoutingKey;
        }
        String prefix = rabbitMQProperties.getRoutingKeyPrefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = "IM-";
        }
        return prefix + "MESSAGE";
    }

    private String resolveClientIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getAddress() != null
                    ? socketAddress.getAddress().getHostAddress()
                    : socketAddress.getHostString();
        }
        return null;
    }
}
