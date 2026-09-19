package com.chua.starter.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多数据源（@DS）与数据源合并**接口级**真实测试：经 mockmvc 走 HTTP 接口
 * （{@code /api/ds/**}）驱动 服务/映射器 级 {@code @DS} 路由与默认路由。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
@AutoConfigureMockMvc
class MultiDatasourceRealTest {

    @Autowired
    private MockMvc mockMvc; // mockmvc

    @BeforeEach
    /**
     * 参见。
     */
    void seed() throws Exception {
        /**
         * 参见。
         * @param "jdbc:h2:mem:ds1;DB_CLOSE_DELAY=-1" "JDBC:h2:mem:ds1;db关闭延迟=1"
         * @param "ds1-data" "ds1-数据"
         */
        seed("jdbc:h2:mem:ds1;DB_CLOSE_DELAY=-1", "ds1-data");
        /**
         * 参见。
         * @param "jdbc:h2:mem:ds2;DB_CLOSE_DELAY=-1" "JDBC:h2:mem:ds2;db关闭延迟=1"
         * @param "ds2-data" "ds2-数据"
         */
        seed("jdbc:h2:mem:ds2;DB_CLOSE_DELAY=-1", "ds2-data");
        // 主数据源（spring.datasource，即 master/默认目标）
        /**
         * 参见。
         * @param "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL" "JDBC:h2:mem:测试;db关闭延迟=1;mode=MySQL"
         * @param "master-data" "master-数据"
         */
        seed("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL", "master-data");
    }

    /**
     * 参见。
     * @param url url
     * @param data 数据
     */
    private static void seed(String url, String data) throws Exception {
        /**
         * 尝试。
         * @param "" ""
         */
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists region");
            statement.execute("create table region(data varchar(20))");
            statement.execute("insert into region values('" + data + "')");
        }
    }

    /**
     * 接口级：获取 /api/ds/region/ds1、/ds2 -> 服务 级 @DS 路由到对应数据源
    */
    @Test
    void serviceLevelDsRouting() throws Exception {
        mockMvc.perform(get("/api/ds/region/ds1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ds1-data"));
        mockMvc.perform(get("/api/ds/region/ds2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ds2-data"));
    }

    /**
     * 接口级：获取 /api/ds/region/映射器/ds1、/ds2 -> 映射器 级 @DS 路由
    */
    @Test
    void mapperLevelDsRouting() throws Exception {
        mockMvc.perform(get("/api/ds/region/mapper/ds1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ds1-data"));
        mockMvc.perform(get("/api/ds/region/mapper/ds2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ds2-data"));
    }

    /**
     * 接口级：获取 /api/ds/region（无 @DS）-> 主数据源（master = spring.datasource）
    */
    @Test
    void defaultRouting() throws Exception {
        mockMvc.perform(get("/api/ds/region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("master-data"));
    }
}
