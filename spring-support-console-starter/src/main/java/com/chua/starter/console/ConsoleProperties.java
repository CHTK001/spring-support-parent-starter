package com.chua.starter.console;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理控制台配置属性。
 *
 * <p>控制台提供 FTL 管理页面（鉴权）临时调整 {@code @ApiFeature}/{@code @ApiGray} 与通用键值
 * 运行设置；调整写入 sqlite 持久化；启动时注解指纹比对，不一致则重置覆盖库（见测试报告/模块说明）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Data
@ConfigurationProperties(prefix = ConsoleProperties.PRE)
public class ConsoleProperties {

    public static final String PRE = "console"; // PRE

    /**
     * 是否启用控制台（默认启用——模块按需引入）
     */
    private boolean enable = true;

    /**
     * sqlite 持久化文件路径（相对运行目录，默认 控制台.sqlite）
     */
    private String sqlitePath = "console.sqlite";

    /**
     * 页面访问所需权限码（页面方法标注 {@code @Permission(permissionCode)}）
     */
    private String permissionCode = "console:manage";

    /**
     * 键值设置前缀过滤（为空表示管理全部键值）
     */
    private String kvPrefix = "";
}
