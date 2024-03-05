package com.chua.starter.monitor.server.pojo;


import lombok.Data;

/**
 * ip实例
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/05
 */
@Data
public class IpInstance {


    private String ip;

    private String city;

    private Long count;
}
