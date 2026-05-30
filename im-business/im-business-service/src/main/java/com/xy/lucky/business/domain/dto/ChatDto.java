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
 * 会话 DTO
 *
 * @author xy
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话对象")
public class ChatDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 64, message = "{validation.chat_id.size}")
    @Schema(description = "会话ID")
    private String chatId;

    @NotNull(message = "{validation.chat_type.required}", groups = {ValidationGroups.Create.class})
    @Schema(description = "会话类型 (1: 单聊, 2: 群聊)")
    private Integer chatType;

    @NotBlank(message = "{validation.from_id.required}", groups = {ValidationGroups.Create.class, ValidationGroups.Query.class})
    @Size(max = 64, message = "{validation.from_id.size}")
    @Schema(description = "发送人ID")
    private String fromId;

    @NotBlank(message = "{validation.to_id.required}", groups = {ValidationGroups.Create.class})
    @Size(max = 64, message = "{validation.to_id.size}")
    @Schema(description = "接收人ID")
    private String toId;

    @Schema(description = "是否屏蔽 (0: 否, 1: 是)")
    private Integer isMute;

    @Schema(description = "是否置顶 (0: 否, 1: 是)")
    private Integer isTop;

    @Schema(description = "消息序列号（用于增量查询）")
    private Long sequence;
}
