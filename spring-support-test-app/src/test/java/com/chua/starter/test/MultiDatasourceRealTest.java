package com.chua.starter.test;

import com.chua.starter.test.mapper.DsRegionMapper;
import com.chua.starter.test.service.DsSwitchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 多数据源（@DS）与数据源合并真实测试：两个独立 H2 内存库经 MultiDataSource 合并为
 * 一个可路由数据源，{@code @DS} 在 Service 级与 mapper 级均正确路由。
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
class MultiDatasourceRealTest {

    @Autowired
    private DsSwitchService dsSwitchService;
    @Autowired
    private DsRegionMapper dsRegionMapper;

    @BeforeEach
    void seed() throws Exception {
        seed("jdbc:h2:mem:ds1;DB_CLOSE_DELAY=-1", "ds1-data");
        seed("jdbc:h2:mem:ds2;DB_CLOSE_DELAY=-1", "ds2-data");
        // 主数据源（spring.datasource，即 master/默认目标）
        seed("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL", "master-data");
    }

    private static void seed(String url, String data) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists region");
            statement.execute("create table region(data varchar(20))");
            statement.execute("insert into region values('" + data + "')");
        }
    }

    /** Service 级 @DS：方法标注 ds1/ds2 -> 方法内查询路由到对应数据源 */
    @Test
    void serviceLevelDsRouting() {
        assertEquals("ds1-data", dsSwitchService.regionFromDs1(), "Service 级 @DS(\"ds1\") 应路由到 ds1");
        assertEquals("ds2-data", dsSwitchService.regionFromDs2(), "Service 级 @DS(\"ds2\") 应路由到 ds2");
    }

    /** mapper 级 @DS：直接标注在数据访问层方法上，同样正确切换 */
    @Test
    void mapperLevelDsRouting() {
        assertEquals("ds1-data", dsRegionMapper.queryRegionDs1(), "mapper 级 @DS(\"ds1\") 应路由到 ds1");
        assertEquals("ds2-data", dsRegionMapper.queryRegionDs2(), "mapper 级 @DS(\"ds2\") 应路由到 ds2");
    }

    /** 默认路由（无 @DS）：DynamicDataSource 缺省取 master（主数据源 = spring.datasource） */
    @Test
    void defaultRouting() {
        assertEquals("master-data", dsSwitchService.defaultRegion(),
                "无 @DS 应使用主数据源（master = spring.datasource）");
    }
}
