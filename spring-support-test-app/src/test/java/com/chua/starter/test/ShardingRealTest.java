package com.chua.starter.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数据源分片真实测试：两个 H2 分片库经 ShardingSphere v5 集成，orders 表按 id MOD 分片。
 *
 * <p>依赖门控：分片引擎依赖 {@code utils-support-shardingv5-starter}（datasource starter 的
 * 可选依赖，未随传递装配；{@code @ConditionalOnClass(ShardingV5Conversion)} 退避）。依赖就绪时
 * 本测试自动执行，未装配时以明确原因跳过（见测试报告"条件/预期/结果"）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest
class ShardingRealTest {

    @Autowired(required = false)
    @Qualifier("shardingDataSource")
    private DataSource shardingDataSource;

    @BeforeEach
    void assumeShardingAvailable() {
        Assumptions.assumeTrue(shardingDataSource != null,
                "分片集成依赖 utils-support-shardingv5-starter（未装配，shardingDataSource Bean 不存在），跳过");
    }

    @BeforeEach
    void seed() throws Exception {
        // 两个分片库同构：orders 表各含不同 id 数据
        for (String url : new String[]{"jdbc:h2:mem:shard0;DB_CLOSE_DELAY=-1", "jdbc:h2:mem:shard1;DB_CLOSE_DELAY=-1"}) {
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                 Statement statement = connection.createStatement()) {
                statement.execute("drop table if exists orders");
                statement.execute("create table orders(id bigint primary key, name varchar(50))");
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:shard0;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("insert into orders(id, name) values (1, 'order-1'), (3, 'order-3')");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:shard1;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("insert into orders(id, name) values (2, 'order-2'), (4, 'order-4')");
        }
    }

    /** 分片聚合：count 跨两个分片库聚合 */
    @Test
    void shardedAggregateQuery() {
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        Integer count = shardedJdbc.queryForObject("select count(*) from orders", Integer.class);
        assertEquals(4, count, "分片聚合应统计两个分片库的全部数据");
    }

    /** 分片查询：按分片键查询可命中对应分片 */
    @Test
    void shardedPointQuery() {
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        List<String> names = shardedJdbc.queryForList("select name from orders where id = 2", String.class);
        assertEquals(1, names.size(), "按分片键查询应命中分片返回对应行");
        assertEquals("order-2", names.get(0), "分片路由应返回正确数据");
    }
}
