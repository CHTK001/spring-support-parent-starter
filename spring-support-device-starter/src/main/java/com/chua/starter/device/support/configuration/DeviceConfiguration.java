package com.chua.starter.device.support.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * scan
 * @author CH
 */
@ComponentScan("com.chua.starter.device.support")
@MapperScan("com.chua.starter.device.support.mapper")
public class DeviceConfiguration {
}
