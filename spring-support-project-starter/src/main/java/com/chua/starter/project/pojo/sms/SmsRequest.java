package com.chua.starter.project.pojo.sms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 短信请求
 * @author CH
 * @since 2024/9/6
 */
@Data
@Schema(description = "短信请求")
public class SmsRequest {

    /**
     * 接收人
     */
    @Schema(description = "接收人")
    private String[] target;

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private String sysSmsTemplateId;

    /**
     * 短信参数
     */
    @Schema(description = "短信参数")
    private Map<String, Object> params;
}
