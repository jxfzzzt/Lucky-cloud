package com.xy.lucky.business.domain.dto;

import com.xy.lucky.business.domain.dto.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 好友请求 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "好友请求对象")
public class FriendRequestDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.friend_request_id.required}", groups = {ValidationGroups.Approve.class})
    @Size(max = 64, message = "{validation.friend_request_id.size}")
    @Schema(description = "好友请求ID")
    private String id;

    @NotBlank(message = "{validation.requester_id.required}", groups = {ValidationGroups.Create.class})
    @Size(max = 64, message = "{validation.requester_id.size}")
    @Schema(description = "请求者ID")
    private String fromId;

    @NotBlank(message = "{validation.target_user_id.required}", groups = {ValidationGroups.Create.class})
    @Size(max = 64, message = "{validation.target_user_id.size}")
    @Schema(description = "目标用户ID")
    private String toId;

    @Size(max = 50, message = "{validation.friend_remark.size}")
    @Schema(description = "好友备注")
    private String remark;

    @Size(max = 500, message = "{validation.friend_request_message.size}")
    @Schema(description = "验证消息")
    private String message;

    @NotNull(message = "{validation.approve_status.required}", groups = {ValidationGroups.Approve.class})
    @Schema(description = "审批状态 (0: 待处理, 1: 同意, 2: 拒绝)")
    private Integer approveStatus;
}
