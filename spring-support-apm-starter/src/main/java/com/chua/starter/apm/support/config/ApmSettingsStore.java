package com.chua.starter.apm.support.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 「APM 设置」的落盘存储（{@code .properties} 文件）。
 *
 * <p>为什么是文件而不是数据库：{@code spring-support-apm-starter} 可以被任何宿主引入
 * （控制台、独立观测台、甚至单体服务的独立模式），不能假设宿主有数据源；
 * 一个 UTF-8 的 properties 文件既零依赖，也能被运维直接在机器上查看与手改。</p>
 *
 * <p>为什么只存「允许热改」的键：{@code mode}/{@code path}/{@code port} 决定 Servlet 与端口的
 * 注册方式，进程起来后再写库/写文件都不会生效。把它们存下来只会制造
 * 「文件里是新值、进程里是旧值」的假象，所以这里由上层过滤，存储层不做判断。</p>
 *
 * <p>读写一律整文件替换（先写临时文件再 move），避免进程被杀时留下半截文件。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmSettingsStore {

    /**
     * 默认文件位置（相对进程工作目录）
     */
    public static final String DEFAULT_FILE = "config/apm-settings.properties";

    /**
     * 落盘位置
     */
    private final Path file;

    /**
     * 构造存储
     *
     * @param fileName 文件路径（相对工作目录或绝对路径）；空则用 {@link #DEFAULT_FILE}
     */
    public ApmSettingsStore(String fileName) {
        String name = fileName == null || fileName.isBlank() ? DEFAULT_FILE : fileName.trim();
        this.file = Paths.get(name).toAbsolutePath().normalize();
    }

    /**
     * 落盘位置（绝对路径，供界面展示「保存在哪」）
     *
     * @return 路径
     */
    public Path file() {
        return file;
    }

    /**
     * 读取已保存的设置。
     *
     * <p>文件不存在、读失败、内容非法都只返回空表并打一条日志：设置文件是「锦上添花」，
     * 读不到不能让 APM 模块起不来。</p>
     *
     * @return 键值（保持文件里的顺序）
     */
    public synchronized Map<String, String> load() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[APM] 读取设置文件失败({}): {}", file, e.getMessage());
            return values;
        }
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    /**
     * 覆盖写入设置（空表等价于清空）。
     *
     * @param values 键值
     * @return 是否写入成功
     */
    public synchronized boolean save(Map<String, String> values) {
        Properties properties = new Properties();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null) {
                    properties.setProperty(key, value);
                }
            });
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                properties.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8),
                        "APM 运行期设置（由 APM 设置页写入；重启后自动套用）");
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warn("[APM] 写入设置文件失败({}): {}", file, e.getMessage());
            return false;
        }
    }
}
