package com.chua.starter.project.pojo.email;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 邮件请求
 * @author CH
 * @since 2024/9/6
 */
@Data
@Schema(description = "邮件请求")
public class EmailRequest {

    /**
     * 接收人
     */
    @Schema(description = "接收人")
    private String[] target;

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private String sysEmailTemplateId;

    /**
     * 邮件参数
     */
    @Schema(description = "邮件参数")
    private Map<String, Object> params;
}
