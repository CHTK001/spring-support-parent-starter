package com.chua.starter.monitor.entity;

import lombok.Data;

/**
 * 命令请求
 *
 * @author CH
 * @since 2023/09/08
 */
@Data
public class CommandRequest {

    String dataType;
    String data;
    String applicationName;
    String applicationProfile;
}
