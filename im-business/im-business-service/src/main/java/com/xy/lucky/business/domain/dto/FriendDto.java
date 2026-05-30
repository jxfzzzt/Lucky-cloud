package com.xy.lucky.business.domain.dto;

import com.xy.lucky.business.domain.dto.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 好友 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "好友对象")
public class FriendDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.user_id.required}", groups = {ValidationGroups.Query.class, ValidationGroups.Update.class, ValidationGroups.Delete.class})
    @Size(max = 64, message = "{validation.user_id.size}")
    @Schema(description = "自己的ID")
    private String fromId;

    @NotBlank(message = "{validation.friend_id.required}", groups = {ValidationGroups.Query.class, ValidationGroups.Update.class, ValidationGroups.Delete.class})
    @Size(max = 64, message = "{validation.friend_id.size}")
    @Schema(description = "好友ID")
    private String toId;

    @Size(max = 50, message = "{validation.keyword.size}")
    @Schema(description = "搜索关键词")
    private String keyword;

    @Size(max = 50, message = "{validation.friend_remark.size}")
    @Schema(description = "好友备注")
    private String remark;
}
