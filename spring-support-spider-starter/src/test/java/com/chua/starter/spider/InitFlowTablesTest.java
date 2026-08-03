package com.chua.starter.spider;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * 用 JDBC 直接执行 spider 数据库建表脚本（V1.2 流程编排表）。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class InitFlowTablesTest {

    /**
     * 数据库 JDBC URL
     */
    private static final String URL =
            "jdbc:mysql://172.16.0.40:3306/spider?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&useUnicode=true&allowPublicKeyRetrieval=true";

    /**
     * 数据库账号
     */
    private static final String USER = "root";

    /**
     * 数据库密码
     */
    private static final String PASSWORD = "root@";

    /**
     * V1.2 流程建表脚本路径
     */
    private static final String SQL_FILE = "D:/ch/project/spring-support-parent-starter/spring-support-spider-starter/target/classes/db/V1.2__init_flow.sql";

    @Test
    public void runSql() throws Exception {
        List<String> lines = Files.readAllLines(Path.of(SQL_FILE));
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            // 跳过纯注释行 / 空行
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        // 按 ";" 切分语句
        String[] statements = sb.toString().split(";\\s*\\n");
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            int ok = 0;
            for (String sql : statements) {
                String s = sql.trim();
                if (s.isEmpty()) {
                    continue;
                }
                stmt.execute(s);
                ok++;
                System.out.println("[OK] " + s.lines().findFirst().orElse(""));
            }
            System.out.println("==== 共执行 " + ok + " 条语句 ====");
        }
    }
}
