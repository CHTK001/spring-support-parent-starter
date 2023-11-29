package com.chua.starter.unified.server.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 执行器查询
 *
 * @author CH
 */
@Data
@AllArgsConstructor
public class ActuatorQuery {

    private String host;
    private String port;

    private String contextPath;

    private String endpointsUrl;
}
