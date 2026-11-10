package com.chua.starter.device.support.configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.device.provider.sdk.DevicePlatformSdkProvider;
import com.chua.starter.device.provider.sdk.DevicePlatformSdks;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.support.security.DeviceWebhookDefaults;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 设备模块自动配置类
 * <p>负责组件扫描与 Mapper 扫描，并启用定时调度。</p>
 * <p>设备厂商 Provider 由 {@link DeviceProvider} 子类通过 {@code @Component} 自动注册为 Spring Bean，</p>
 * <p>业务方添加新厂商只需新增 Provider 实现即可，SPI 文件不再使用。</p>
 *
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ComponentScan({
        "com.chua.starter.device.support.controller",
        "com.chua.starter.device.support.service",
        "com.chua.starter.device.support.scheduler",
        "com.chua.starter.device.support.util",
        "com.chua.starter.device.support.security",
        "com.chua.starter.device.support.realtime",
        "com.chua.starter.device.support.callback",
        "com.chua.starter.device.provider"
})
@MapperScan("com.chua.starter.device.support.mapper")
@EnableScheduling
/**
 * deviceauto配置类。
 *
 * @author CH
 * @since 4.0.0
 */
public class DeviceAutoConfiguration {

    /**
     * 注入所有设备 提供者 Bean（@组件 自动注册），并登记到 common-starter 的
     * {@link ServiceProvider} SPI 注册表，供其它模块按 vendor 取用。
     *
     * @param providers Spring 容器中所有 device提供者 实现
     * @return Provider 列表
     */
    @Bean
    public List<DeviceProvider> deviceProviders(List<DeviceProvider> providers) {
        ServiceProvider<DeviceProvider> spi = ServiceProvider.of(DeviceProvider.class);
        providers.forEach(p -> {
            spi.register(p.getVendor(), p);
            log.info("[Device] 注册 Provider: vendor={}（已登记到 ServiceProvider SPI）", p.getVendor());
        });
        return providers;
    }

    /**
     * 登记外部模块提供的平台 SDK 扩展（SPI）。
     *
     * <p>平台 SDK 门面走 common-starter 的 {@link ServiceProvider} 扩展点：
     * 外部模块只要提供一个 {@link DevicePlatformSdkProvider} Bean（即一个
     * 「厂商 + 平台」实现），本方法会自动把它登记到平台 SDK 工厂，
     * 业务侧与控制器无需任何改动即可调用新平台的全部接口。</p>
     *
     * @param sdkProviders Spring 容器中的平台 SDK 扩展
     * @return 平台 SDK 扩展列表
     */
    @Bean
    public List<DevicePlatformSdkProvider> devicePlatformSdkProviders(
            List<DevicePlatformSdkProvider> sdkProviders) {
        sdkProviders.forEach(DevicePlatformSdks::register);
        return sdkProviders;
    }

    /**
     * 分页插件兜底。
     *
     * <p>设备模块的 XML 分页 SQL 依赖 MyBatis-Plus 的 {@link PaginationInnerInterceptor}；
     * 宿主若已通过 spring-support-mybatis-starter 注册同名 Bean，本方法自动退避，
     * 不改变既有装配。模块独立运行（仅引入 device-starter）时也能正确返回 total。</p>
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor deviceMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 不指定 DbType，由连接自动识别，兼容 MySQL / H2(MySQL 模式)
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        log.info("[Device] 未发现宿主分页插件，已注册设备模块分页兜底");
        return interceptor;
    }

    /**
     * 注册设备模块服务信息。
     * @return device服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor deviceServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("设备", "设备管理", "/api/device/**", "设备增删改查、离线阈值、Excel 导出");
            registry.registerEndpoint("设备", "通道管理", "/api/device/channel/**", "通道的增删改查、启用/禁用");
            registry.registerEndpoint("设备", "视频", "/api/device/video/**", "视频预览、回放、直播开启/停止");
            registry.registerEndpoint("设备", "生命周期回调", "/api/device/callbacks/**", "回调实现列表与事件手工重放");
            registry.registerEndpoint("设备", "webhook 入站", DeviceWebhookDefaults.WEBHOOK_PREFIX + "/**",
                    "门禁记录/告警事件推送入库（appKey 鉴权，设备模块默认白名单）");
            registry.registerEndpoint("设备", "云台", "/api/device/ptz/**", "云台控制、预置位");
            registry.registerEndpoint("设备", "门禁", "/api/device/access/**", "远程开门、刷卡记录");
            registry.registerEndpoint("设备", "门禁记录", "/api/device/access-records/**", "门禁记录抽取落库与查询");
            registry.registerEndpoint("设备", "事件", "/api/device/event/**", "事件订阅与查询");
            registry.registerEndpoint("设备", "目录同步", "/api/device/sync/**", "厂商设备目录拉取与写回");
            registry.registerEndpoint("设备", "定时任务", "/api/device/tasks/**", "时间轮任务状态与间隔配置");
            registry.registerFlag("device", "设备", true, "设备管理模块（海康云耀/云眸/安防平台/大华云睿/乐橙/SDK 直连）");
        };
    }
}
