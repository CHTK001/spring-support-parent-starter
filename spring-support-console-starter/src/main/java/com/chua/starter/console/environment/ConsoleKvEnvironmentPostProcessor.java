package com.chua.starter.console.environment;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 键值环境注入：启动时（Spring 装配前）读取 SQLite {@code console_kv} 表，
 * 以 {@code MapPropertySource("console-kv")} 注入 Environment——业务代码可经
 * {@code @Value("${key}")} / {@code environment.getProperty("key")} 读取"运行设置"。
 *
 * <p>镜像 {@code DatabaseSettingEnvironmentPostProcessor} 模式（spring.factories 注册）；
 * 库/表不存在或不可读时静默跳过（首次启动）；键值优先级最高（addFirst——页面临时调整优先生效）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class ConsoleKvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.parseBoolean(environment.getProperty("console.enable", "true"))) {
            return;
        }
        String path = environment.getProperty("console.sqlite-path", "console.sqlite");
        Map<String, Object> kv = new LinkedHashMap<>();
        try {
            ReflectUtils.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT k, v FROM console_kv")) {
                while (rs.next()) {
                    kv.put(rs.getString("k"), rs.getString("v"));
                }
            }
        } catch (Exception e) {
            log.debug("[Console] 键值环境注入跳过（库/表不存在或不可读）: {}", e.getMessage());
            return;
        }
        if (!kv.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("console-kv", kv));
            log.info("[Console] 键值环境注入完成: {} 项", kv.size());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
