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
 * 群成员 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "群成员")
public class GroupMemberDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.group_id.required}", groups = {
            ValidationGroups.Query.class, ValidationGroups.Update.class,
            ValidationGroups.KickMember.class, ValidationGroups.SetAdmin.class,
            ValidationGroups.TransferOwner.class, ValidationGroups.MuteMember.class
    })
    @Size(max = 64, message = "{validation.group_id.size}")
    @Schema(description = "群组ID")
    private String groupId;

    @NotBlank(message = "{validation.operator_id.required}", groups = {
            ValidationGroups.KickMember.class, ValidationGroups.SetAdmin.class,
            ValidationGroups.TransferOwner.class, ValidationGroups.MuteMember.class
    })
    @Size(max = 64, message = "{validation.operator_id.size}")
    @Schema(description = "操作者用户ID（群主/管理员）")
    private String userId;

    @NotBlank(message = "{validation.target_user_id.required}", groups = {
            ValidationGroups.KickMember.class, ValidationGroups.SetAdmin.class,
            ValidationGroups.TransferOwner.class, ValidationGroups.MuteMember.class
    })
    @Size(max = 64, message = "{validation.target_user_id.size}")
    @Schema(description = "目标用户ID（被操作的成员）")
    private String targetUserId;

    @NotNull(message = "{validation.role.required}", groups = {ValidationGroups.SetAdmin.class})
    @Schema(description = "成员角色: 0-群主, 1-管理员, 2-普通成员, 3-禁言成员")
    private Integer role;

    @NotNull(message = "{validation.mute.required}", groups = {ValidationGroups.MuteMember.class})
    @Schema(description = "禁言状态: 0-禁言, 1-正常")
    private Integer mute;

    @Schema(description = "禁言时长（秒），0表示永久禁言")
    private Long muteDuration;

    @Size(max = 50, message = "{validation.member_alias.size}")
    @Schema(description = "群内昵称")
    private String alias;

    @Size(max = 200, message = "{validation.member_remark.size}")
    @Schema(description = "群备注")
    private String remark;
}
