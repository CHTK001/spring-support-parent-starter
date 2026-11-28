package com.chua.starter.datapool.support.datasync.loader;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datapool.support.model.DatasourceJdbcInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * PostgreSQL CSV 快速导入实现（{@code COPY ... FROM STDIN}）。
 *
 * <p>用 pgjdbc 的 {@code CopyManager#copyIn} 把规范 CSV 以 {@code CSV} 格式流式灌入目标表：
 * PG 的 COPY 是服务端原生批量装载，比逐行 insert 快一个数量级，且事务内一致。</p>
 *
 * <p><b>驱动归部署侧</b>：本模块不声明、不携带 PostgreSQL 驱动制品，pgjdbc 专属类型只按类名
 * 反射取得；驱动缺失时抛出可读原因，只影响该 PostgreSQL 目标的快速导入，不影响其它方言。</p>
 *
 * <p>规范 CSV 是「首行表头 + 逗号分隔 + 双引号转义」，与 COPY 的
 * {@code FORMAT csv, HEADER true} 直接对应，无需额外转换。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Spi("postgresql")
public class PostgresqlCsvLoader implements JdbcCsvLoader {

    /**
     * pgjdbc 物理连接类型（CopyManager 构造器入参）
     */
    private static final String BASE_CONNECTION = "org.postgresql.core.BaseConnection";

    /**
     * pgjdbc COPY 管理器
     */
    private static final String COPY_MANAGER = "org.postgresql.copy.CopyManager";

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    @Override
    public long load(DatasourceJdbcInfo target, String targetTable, List<String> columns, Path csvFile, String encoding)
            throws Exception {
        Class<?> baseConnectionType = driverType(BASE_CONNECTION);
        Class<?> copyManagerType = driverType(COPY_MANAGER);
        Constructor<?> copyManagerConstructor = copyManagerType.getConstructor(baseConnectionType);
        Method copyIn = copyManagerType.getMethod("copyIn", String.class, Reader.class);

        Properties props = new Properties();
        props.setProperty("user", target.username() == null ? "" : target.username());
        props.setProperty("password", target.password() == null ? "" : target.password());
        String columnList = String.join(", ", columns);
        String copySql = "COPY " + targetTable + " (" + columnList + ") FROM STDIN WITH (FORMAT csv, HEADER true)";
        log.info("[bulk-import] PostgreSQL COPY 直灌表 {}，文件 {}，列 {} 个", targetTable, csvFile, columns.size());
        try (Connection connection = DriverManager.getConnection(target.url(), props)) {
            Object copyManager = copyManagerConstructor.newInstance(baseConnectionType.cast(connection));
            try (Reader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
                try {
                    return ((Number) copyIn.invoke(copyManager, copySql, reader)).longValue();
                } catch (InvocationTargetException e) {
                    throw unwrap(e);
                }
            }
        }
    }

    /**
     * 按类名取 pgjdbc 类型。
     *
     * @param className pgjdbc 类名
     * @return 驱动类
     * @throws ClassNotFoundException 运行时没有 PostgreSQL 驱动
     */
    private static Class<?> driverType(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException(
                    "目标库为 PostgreSQL，但运行时缺少 PostgreSQL 驱动（由部署方提供 org.postgresql:postgresql）: "
                            + className, e);
        }
    }

    /**
     * 把反射包装异常还原成被调用方原始异常，保持失败原因可读。
     *
     * @param e 反射包装异常
     * @return 原始异常
     */
    private static Exception unwrap(InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        return cause instanceof Exception exception ? exception : e;
    }
}
