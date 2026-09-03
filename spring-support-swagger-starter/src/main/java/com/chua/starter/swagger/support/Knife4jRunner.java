package com.chua.starter.swagger.support;

import org.springframework.boot.CommandLineRunner;

/**
 * Knife4j日志
 * @author CH
 * @deprecated 启动横幅已由 {@code ServiceInfoBannerListener} 统一渲染, 此类保留仅供兼容, 不输出日志。
 */
@Deprecated
public class Knife4jRunner implements CommandLineRunner {

    private final org.springframework.core.env.Environment environment;

    public Knife4jRunner(org.springframework.core.env.Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        // 启动横幅已由 ServiceInfoBannerListener 统一输出, 此处不再重复打印
    }
}
