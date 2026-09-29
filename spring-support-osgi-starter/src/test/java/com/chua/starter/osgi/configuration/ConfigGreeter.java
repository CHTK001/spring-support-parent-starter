package com.chua.starter.osgi.configuration;

/**
 * Spring 层测试用 SPI 接口。
 *
 * <p><b>刻意做成独立顶层类</b>：源码编译产物需要 import 它，
 * 嵌套接口的二进制名带 {@code $}，用点号书写时 javac 会按包路径解析并报
 * 「无法访问」——该报错会掩盖真正要验证的装配行为。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface ConfigGreeter {

    /**
     * 打招呼。
     *
     * @param name 名字
     * @return 问候语
     */
    String greet(String name);
}
