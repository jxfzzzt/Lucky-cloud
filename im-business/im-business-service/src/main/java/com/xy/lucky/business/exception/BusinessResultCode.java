package com.xy.lucky.business.exception;

import com.xy.lucky.general.response.domain.IResult;
import com.xy.lucky.general.response.service.I18nService;
import lombok.Getter;

/**
 * im-business 模块业务专属错误码
 *
 * <p>错误码段位约定：
 * <ul>
 *   <li>40xx：群组（Group）相关</li>
 *   <li>41xx：好友 / 关系（Friend / Relationship）相关</li>
 *   <li>42xx：表情包（Sticker）相关</li>
 *   <li>43xx：会话（Chat）相关</li>
 *   <li>44xx：分布式锁 / 并发相关</li>
 *   <li>45xx：用户（User）相关</li>
 * </ul>
 * <p>对应文案存放于 {@code i18n/messages-business_*.properties}，运行时由
 * {@link I18nService} 根据当前 {@link java.util.Locale} 解析。</p>
 */
@Getter
public enum BusinessResultCode implements IResult {

    // ========== Group: 4000-4099 ==========
    GROUP_USER_NOT_IN_GROUP(4001, "group.user_not_in_group"),
    GROUP_OWNER_CANNOT_QUIT(4002, "group.owner_cannot_quit"),
    GROUP_INVITE_TYPE_INVALID(4003, "group.invite_type_invalid"),
    GROUP_NOT_FOUND(4004, "group.not_found"),
    GROUP_UPDATE_FAILED(4005, "group.update_failed"),
    GROUP_MEMBER_UPDATE_FAILED(4006, "group.member_update_failed"),
    GROUP_NEED_AT_LEAST_ONE_INVITEE(4007, "group.need_at_least_one_invitee"),
    GROUP_MEMBER_CREATE_FAILED(4008, "group.member_create_failed"),
    GROUP_INFO_CREATE_FAILED(4009, "group.info_create_failed"),
    GROUP_INVITER_NOT_MEMBER(4010, "group.inviter_not_member"),
    GROUP_SAVE_INVITE_FAILED(4011, "group.save_invite_failed"),
    GROUP_JOIN_FAILED(4012, "group.join_failed"),
    GROUP_OPERATOR_NOT_IN_GROUP(4013, "group.operator_not_in_group"),
    GROUP_NO_PERMISSION(4014, "group.no_permission"),
    GROUP_TARGET_NOT_IN_GROUP(4015, "group.target_not_in_group"),
    GROUP_ADMIN_CANNOT_REMOVE_OWNER_OR_ADMIN(4016, "group.admin_cannot_remove_owner_or_admin"),
    GROUP_CANNOT_REMOVE_SELF(4017, "group.cannot_remove_self"),
    GROUP_REMOVE_MEMBER_FAILED(4018, "group.remove_member_failed"),
    GROUP_ONLY_OWNER_SET_ADMIN(4019, "group.only_owner_set_admin"),
    GROUP_CANNOT_CHANGE_OWNER_ROLE(4020, "group.cannot_change_owner_role"),
    GROUP_SET_ADMIN_FAILED(4021, "group.set_admin_failed"),
    GROUP_ONLY_OWNER_TRANSFER(4022, "group.only_owner_transfer"),
    GROUP_CANNOT_TRANSFER_TO_SELF(4023, "group.cannot_transfer_to_self"),
    GROUP_UPDATE_OLD_OWNER_FAILED(4024, "group.update_old_owner_failed"),
    GROUP_SET_NEW_OWNER_FAILED(4025, "group.set_new_owner_failed"),
    GROUP_INVALID_JOIN_MODE(4026, "group.invalid_join_mode"),
    GROUP_SET_JOIN_MODE_FAILED(4027, "group.set_join_mode_failed"),
    GROUP_CANNOT_MUTE_OWNER_OR_ADMIN(4028, "group.cannot_mute_owner_or_admin"),
    GROUP_MUTE_UPDATE_FAILED(4029, "group.mute_update_failed"),
    GROUP_MUTE_ALL_FAILED(4030, "group.mute_all_failed"),
    GROUP_ONLY_OWNER_DISMISS(4031, "group.only_owner_dismiss"),
    GROUP_ANNOUNCEMENT_FAILED(4032, "group.announcement_failed"),

    // ========== Friend / Relationship: 4100-4199 ==========
    FRIEND_USER_NOT_FOUND(4101, "friend.user_not_found"),
    FRIEND_REQUEST_NOT_FOUND(4102, "friend.request_not_found"),
    FRIEND_FRIENDSHIP_NOT_FOUND(4103, "friend.friendship_not_found"),
    FRIEND_REMARK_UPDATE_FAILED(4104, "friend.remark_update_failed"),

    // ========== Sticker: 4200-4299 ==========
    STICKER_PACK_NOT_FOUND(4201, "sticker.pack_not_found"),
    STICKER_ITEM_NOT_FOUND(4202, "sticker.item_not_found"),

    // ========== Chat: 4300-4399 ==========
    CHAT_UNSUPPORTED_TYPE(4301, "chat.unsupported_type"),
    CHAT_CREATE_FAILED(4302, "chat.create_failed"),

    // ========== Lock / 并发: 4400-4499 ==========
    LOCK_BUSY_RETRY(4401, "common.busy_retry"),
    LOCK_OPERATION_INTERRUPTED(4402, "common.operation_interrupted"),

    // ========== User: 4500-4599 ==========
    USER_CREATE_FAILED(4501, "user.create_failed"),
    USER_UPDATE_FAILED(4502, "user.update_failed"),
    USER_DELETE_FAILED(4503, "user.delete_failed"),
    ;

    /** 业务错误码 */
    private final int code;

    /** i18n 消息键 */
    private final String messageKey;

    BusinessResultCode(int code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    /**
     * 每次调用都通过 {@link I18nService} 解析当前 Locale 的文案，
     * 不缓存结果以保证不同请求 / 不同 Accept-Language 的隔离。
     */
    @Override
    public String getMessage() {
        return I18nService.getMessage(messageKey);
    }
}
