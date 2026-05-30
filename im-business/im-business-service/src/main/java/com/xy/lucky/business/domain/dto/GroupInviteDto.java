package com.xy.lucky.business.domain.dto;

import com.xy.lucky.business.domain.dto.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 群邀请 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "群聊邀请")
public class GroupInviteDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 64, message = "{validation.invite_group_id.size}")
    @Schema(description = "群聊ID")
    private String groupId;

    @NotBlank(message = "{validation.user_id.required}")
    @Size(max = 64, message = "{validation.user_id.size}")
    @Schema(description = "用户ID")
    private String userId;

    @NotEmpty(message = "{validation.member_ids.required}", groups = {ValidationGroups.Create.class})
    @Schema(description = "被邀请用户ID列表")
    private List<String> memberIds;

    @NotNull(message = "{validation.invite_type.required}")
    @Schema(description = "邀请类型")
    private Integer type;

    @Size(max = 500, message = "{validation.invite_message.size}")
    @Schema(description = "邀请信息")
    private String message;

    @Size(max = 50, message = "{validation.group_name.size}")
    @Schema(description = "群名称")
    private String groupName;

    @Size(max = 64, message = "{validation.inviter_id.size}")
    @Schema(description = "邀请人ID")
    private String inviterId;

    @Size(max = 64, message = "{validation.request_id.size}")
    @Schema(description = "邀请请求ID")
    private String requestId;

    @Size(max = 64, message = "{validation.verifier_id.size}")
    @Schema(description = "验证者用户ID（群主或管理员）")
    private String verifierId;

    @Schema(description = "邀请来源")
    private Integer addSource;

    @NotNull(message = "{validation.approve_status.required}", groups = {ValidationGroups.Approve.class})
    @Schema(description = "被邀请人状态 (0: 待处理, 1: 同意, 2: 拒绝)")
    private Integer approveStatus;

    @Schema(description = "群主或管理员验证 (0: 待处理, 1: 同意, 2: 拒绝)")
    private Integer verifierStatus;
}
