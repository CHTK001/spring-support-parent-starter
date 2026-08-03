package com.chua.starter.spider;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简单的 MySQL 连接测试，验证 172.16.0.40 的 root/root@ 凭据。
 */
public class MysqlConnectionTest {

    @Test
    public void testConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://172.16.0.40:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (Connection conn = DriverManager.getConnection(url, "root", "root@");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            assertTrue(conn.isValid(5));
            System.out.println("[OK] connected to MySQL as root");
            System.out.println("--- databases ---");
            while (rs.next()) {
                String name = rs.getString(1);
                System.out.println("  - " + name);
            }
            System.out.println("-----------------");
        }
    }
}
