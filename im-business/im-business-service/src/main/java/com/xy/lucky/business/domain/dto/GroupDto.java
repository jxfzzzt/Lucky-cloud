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
 * 群组 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "群聊对象")
public class GroupDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.group_id.required}", groups = {
            ValidationGroups.Query.class, ValidationGroups.Update.class,
            ValidationGroups.Delete.class, ValidationGroups.SetJoinMode.class,
            ValidationGroups.MuteAll.class, ValidationGroups.DismissGroup.class
    })
    @Size(max = 15, message = "{validation.group_id.size}")
    @Schema(description = "群聊ID")
    private String groupId;

    @NotBlank(message = "{validation.user_id.required}", groups = {
            ValidationGroups.Delete.class, ValidationGroups.SetJoinMode.class,
            ValidationGroups.MuteAll.class, ValidationGroups.DismissGroup.class
    })
    @Size(max = 15, message = "{validation.user_id.size}")
    @Schema(description = "操作者用户ID")
    private String userId;

    @Size(max = 50, message = "{validation.group_name.size}")
    @Schema(description = "群名称")
    private String groupName;

    @Size(max = 500, message = "{validation.group_avatar.size}")
    @Schema(description = "群头像")
    private String avatar;

    @Size(max = 500, message = "{validation.group_introduction.size}")
    @Schema(description = "群简介")
    private String introduction;

    @Size(max = 500, message = "{validation.group_notification.size}")
    @Schema(description = "群公告")
    private String notification;

    @NotNull(message = "{validation.apply_join_type.required}", groups = {ValidationGroups.SetJoinMode.class})
    @Schema(description = "加入方式: 0-禁止申请, 1-需要审批, 2-自由加入")
    private Integer applyJoinType;

    @NotNull(message = "{validation.mute_all.required}", groups = {ValidationGroups.MuteAll.class})
    @Schema(description = "全员禁言: 0-禁言, 1-正常")
    private Integer muteAll;

    @Schema(description = "群类型: 0-公开群, 1-私有群")
    private Integer groupType;

    @Schema(description = "群状态: 0-解散, 1-正常")
    private Integer status;
}
