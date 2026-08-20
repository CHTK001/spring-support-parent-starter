package com.chua.starter.device;

import com.chua.starter.device.support.configuration.DeviceAutoConfiguration;
import com.chua.starter.device.support.controller.DeviceController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 设备管理后端测试启动类。
 * <p>仅用于本地自检，启动后访问 {@code http://localhost:19091/api/device/page?pageNum=1&pageSize=20} 验证。</p>
 *
 * @since 4.0.0.42
 */
@Slf4j
@SpringBootApplication(scanBasePackageClasses = {
        DeviceController.class,
        DeviceAutoConfiguration.class
})
public class DeviceTestApplication {

    /**
     * 测试入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(DeviceTestApplication.class, args);
        log.info("设备管理后端启动成功 — 端口: {}", ctx.getEnvironment().getProperty("server.port"));
    }
}
