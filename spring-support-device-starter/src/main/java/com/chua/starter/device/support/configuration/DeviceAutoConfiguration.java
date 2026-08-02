package com.chua.starter.device.support.configuration;

import com.chua.starter.device.spi.DeviceProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * 设备模块自动配置类
 * <p>负责 SPI Provider 加载、组件扫描与 Mapper 扫描，并启用定时调度。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@ComponentScan("com.chua.starter.device.support")
@MapperScan("com.chua.starter.device.support.mapper")
@EnableScheduling
public class DeviceAutoConfiguration {

    /**
     * 设备 Provider 列表（SPI 自动加载）
     *
     * @return Provider 列表
     */
    @Bean
    public List<DeviceProvider> deviceProviders() {
        return ServiceLoader.load(DeviceProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());
    }
}