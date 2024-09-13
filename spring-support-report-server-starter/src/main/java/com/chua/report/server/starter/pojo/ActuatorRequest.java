package com.chua.report.server.starter.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * actuator请求
 * @author CH
 * @since 2024/9/13
 */
@Data
@Schema(title = "actuator请求")
public class ActuatorRequest {


    /**
     * 请求地址
     */
    private String url;

    /**
     * 请求方式
     */
    private String method;

    /**
     * 请求参数
     */
    private String body;
}
