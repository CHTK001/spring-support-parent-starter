package com.chua.starter.oauth.server.support.third.paramter;

import lombok.Data;

/**
 * 基础参数
 *
 * @author CH
 */
@Data
public class BaseParameter {

    private String servicecode;
    private String time;
    private String sign;
    private String datatype = "json";
}
