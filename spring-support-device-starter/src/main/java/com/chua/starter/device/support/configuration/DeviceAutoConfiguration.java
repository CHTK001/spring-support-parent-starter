package com.chua.starter.device.support.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.device.spi.DeviceProvider;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
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
@Slf4j
@AutoConfiguration
@ComponentScan({
        "com.chua.starter.device.support.controller",
        "com.chua.starter.device.support.service",
        "com.chua.starter.device.support.scheduler",
        "com.chua.starter.device.support.util",
        "com.chua.starter.device.provider"
})
@MapperScan("com.chua.starter.device.support.mapper")
@EnableScheduling
public class DeviceAutoConfiguration {

    /**
     * 设备 Provider 列表（SPI 自动加载）
     *
     * <p>通过 {@link ServiceLoader} 加载 {@code META-INF/services/com.chua.starter.device.spi.DeviceProvider}
     * 中配置的所有厂商实现，业务方添加新厂商只需新增 Provider 实现并配置 SPI 文件即可。</p>
     *
     * @return Provider 列表
     */
    @Bean
    public List<DeviceProvider> deviceProviders() {
        return ServiceLoader.load(DeviceProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .peek(provider -> log.info("[Device] SPI 加载 Provider: vendor={}", provider.getVendor()))
                .collect(Collectors.toList());
    }

    /**
     * 注册设备模块服务信息。
     *
     * <p>向全局 {@link com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry}
     * 贡献本模块的端点 URL 与开关信息，启动后由 ServiceInfo Starter 统一渲染。</p>
     *
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor deviceServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("设备", "设备管理", "/api/device/**", "设备增删改查、离线阈值、Excel 导出");
            registry.registerEndpoint("设备", "通道管理", "/api/device/channel/**", "通道的增删改查、启用/禁用");
            registry.registerEndpoint("设备", "视频", "/api/device/video/**", "视频预览、回放");
            registry.registerEndpoint("设备", "云台", "/api/device/ptz/**", "云台控制、预置位");
            registry.registerEndpoint("设备", "门禁", "/api/device/access/**", "远程开门、刷卡记录");
            registry.registerEndpoint("设备", "事件", "/api/device/event/**", "事件订阅与查询");
            registry.registerFlag("device", "设备", true, "设备管理模块 (海康/大华/ISUP/萤石)");
        };
    }
}
