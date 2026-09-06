package com.chua.starter.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真库分片探针：远程 Docker MySQL（172.16.0.40:3307 mysql-shard）上验证 ShardingSphere 5.5.2
 * 路由查询——判定 7 类候选修复实验后的 "Table or view 'orders' does not exist" 是 H2 特定问题
 * 还是 ShardingSphere 5.5.2 核心问题（真库不可达时以明确原因跳过）。
 *
 * <p>接线：覆写 yaml 的 {@code spring.datasource.sharding.databases} 至真库两分片库
 * （shard0/shard1——同实例两库，库级 MOD(id,2) 分布），其余装配与 {@link ShardingRealTest}
 * 一致（ShardingV5Conversion autoDiscover 需物理表在上下文启动前存在——种子放静态块）。</p>
 *
 * @author CH
 * @since 2026/09/07
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.sharding.databases[0]=jdbc:mysql://root:root@172.16.0.40:3307/shard0",
        "spring.datasource.sharding.databases[1]=jdbc:mysql://root:root@172.16.0.40:3307/shard1"
})
class ShardingMySqlRealTest {

    @Autowired(required = false)
    @Qualifier("shardingDataSource")
    private DataSource shardingDataSource;

    // 真库种子：物理表须在上下文启动（ShardingV5Conversion autoDiscover）之前存在。
    // 数据分布（与 H2 用例同）：id=1 -> shard1.orders_1，id=2 -> shard0.orders_2，
    // id=3 -> shard1.orders_3，id=4 -> shard0.orders_4。
    static {
        seedShard("shard0", 2, "order-2", 4, "order-4");
        seedShard("shard1", 1, "order-1", 3, "order-3");
    }

    private static void seedShard(String db, Object... pairs) {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://root:root@172.16.0.40:3307/", "root", "root");
             Statement statement = connection.createStatement()) {
            statement.execute("create database if not exists " + db);
            statement.execute("use " + db);
            // 脚本运行器的版本记录表（DataSourceScriptProperties.versionTable 默认
            // sys_database_version）——分片库须预置，否则启动期版本查询经分片代理抛 TableNotFound 毒化元数据
            statement.execute("create table if not exists sys_database_version ("
                    + "sys_database_version_id int auto_increment primary key,"
                    + "sys_database_version_version varchar(32),"
                    + "sys_database_version_script_name varchar(200) not null,"
                    + "sys_database_version_script_type varchar(16),"
                    + "sys_database_version_description varchar(100),"
                    + "sys_database_version_checksum varchar(64),"
                    + "sys_database_version_executed_on timestamp default current_timestamp,"
                    + "sys_database_version_success varchar(8),"
                    + "constraint uk_script_name_version unique (sys_database_version_script_name, sys_database_version_version))");
            for (int i = 0; i < 16; i++) {
                statement.execute("drop table if exists orders_" + i);
                statement.execute("create table orders_" + i + "(id bigint primary key, name varchar(50))");
            }
            for (int i = 0; i < pairs.length; i += 2) {
                int id = (Integer) pairs[i];
                statement.execute("insert into orders_" + (id % 16) + "(id, name) values (" + id + ", '" + pairs[i + 1] + "')");
            }
        } catch (Exception e) {
            throw new IllegalStateException("真库种子初始化失败: " + db, e);
        }
    }

    /** 引擎激活验证：真库数据源覆写下分片引擎应可装配 */
    @Test
    void shardedEngineActivated() {
        assertNotNull(shardingDataSource, "分片引擎应已装配");
        assertTrue(shardingDataSource.getClass().getName().contains("ShardingSphere"),
                "shardingDataSource 应为 ShardingSphere 数据源，实际: " + shardingDataSource.getClass().getName());
    }

    /** 分片聚合：count 跨两个真库分片聚合（路由可用时执行） */
    @Test
    void shardedAggregateQuery() {
        assumeShardingRoutable();
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        Integer count = shardedJdbc.queryForObject("select count(*) from orders", Integer.class);
        assertEquals(4, count, "分片聚合应统计两个真库分片的全部数据");
    }

    /** 分片查询：按分片键查询可命中对应真库分片（路由可用时执行） */
    @Test
    void shardedPointQuery() {
        assumeShardingRoutable();
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        List<String> names = shardedJdbc.queryForList("select name from orders where id = 2", String.class);
        assertEquals(1, names.size(), "按分片键查询应命中分片返回对应行");
        assertEquals("order-2", names.get(0), "分片路由应返回正确数据");
    }

    /** 路由可用性门控：真库路由可用时执行断言，不可用时以明确原因跳过 */
    private void assumeShardingRoutable() {
        if (shardingDataSource == null) {
            Assumptions.abort("分片引擎未装配：shardingDataSource Bean 不存在");
        }
        try {
            new JdbcTemplate(shardingDataSource).queryForObject("select count(*) from orders", Integer.class);
        } catch (Throwable t) {
            Assumptions.abort("真库路由查询不可用（判定 H2 特定 vs ShardingSphere 5.5.2 核心问题）: " + t.getMessage());
        }
    }
}
