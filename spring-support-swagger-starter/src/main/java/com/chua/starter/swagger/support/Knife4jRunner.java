package com.chua.starter.swagger.support;

import org.springframework.boot.CommandLineRunner;

/**
 * 刀4j日志
 * @author CH
 * @deprecated 启动横幅已由 {@code ServiceInfoBannerListener} 统一渲染, 此类保留仅供兼容, 不输出日志。
 * @since 4.0.0
 */
@Deprecated
public class Knife4jRunner implements CommandLineRunner {

    private final org.springframework.core.env.Environment environment; // 环境

    /**
     * 刀4jrunner。
     * @param environment 环境
     */
    public Knife4jRunner(org.springframework.core.env.Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
 // 启动横幅已由 服务信息banner监听器 统一输出, 此处不再重复打印
    }
}
