package com.xy.lucky.business.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.codec.multipart.FilePart;

/**
 * 表情条目请求/响应对象
 */
@Data
@Schema(name = "StickerVo", description = "表情条目：包含名称、所属包、对象存储信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StickerVo {

    @Schema(description = "所属表情包ID")
    @NotBlank(message = "{validation.sticker.pack_id.required}")
    @Size(max = 64, message = "{validation.sticker.pack_id.size}")
    private String packId;

    @Schema(description = "表情名称", example = "smile")
    @NotBlank(message = "{validation.sticker.name.required}")
    @Size(max = 128, message = "{validation.sticker.name.size}")
    private String name;

    @Schema(description = "标签（逗号分隔）", example = "happy,funny")
    @Size(max = 256, message = "{validation.sticker.tags.size}")
    private String tags;

    @Schema(description = "表情ID")
    private String stickerId;

    @Schema(description = "下载URL（预签名）")
    private String url;

    @Schema(description = "顺序")
    private Integer sort;

    @Schema(description = "上传文件")
    private FilePart file;
}

