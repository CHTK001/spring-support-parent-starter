package com.chua.starter.pay.support.configuration;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * 支付配置
 * @author CH
 * @since 2024/12/27
 */
@Slf4j

@MapperScan("com.chua.starter.pay.support.mapper")
@ComponentScan({
        "com.chua.starter.pay.support.service",
})
public class PayConfiguration {

}
