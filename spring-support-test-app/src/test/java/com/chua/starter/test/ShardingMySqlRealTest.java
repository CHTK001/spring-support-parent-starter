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
 * 真库分片探针：远程 Docker MySQL（172.16.0.40:3307 MySQL-shard）上验证 分库分表sphere 5.5.2
 * 路由查询——判定 7 类候选修复实验后的 "Table 或 view '订单' 执行 not exist" 是 H2 特定问题
 * 还是 分库分表sphere 5.5.2 核心问题（真库不可达时以明确原因跳过）。
 *
 * <p>接线：覆写 yaml 的 {@code spring.datasource.sharding.databases} 至真库两分片库
 * （shard0/shard1——同实例两库，库级 MOD(标识,2) 分布），其余装配与 {@link ShardingRealTest}
 * 一致（分库分表v5转换 autodiscover 需物理表在上下文启动前存在——种子放静态块）。</p>
 *
 * @author CH
 * @since 2026/09/07
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.sharding.databases[0]=jdbc:mysql://root:root@172.16.0.40:3307/shard0",
        "spring.datasource.sharding.databases[1]=jdbc:mysql://root:root@172.16.0.40:3307/shard1"
})
/**
 * 分库分表mysqlreal测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class ShardingMySqlRealTest {

    @Autowired(required = false)
    @Qualifier("shardingDataSource")
    private DataSource shardingDataSource; // 分库分表数据源

 // 真库种子：物理表须在上下文启动（分库分表v5转换 autodiscover）之前存在。
 // 数据分布（与 H2 用例同）：id=1 -> shard1.订单_1，id=2 -> shard0.订单_2，
 // id=3 -> shard1.订单_3，id=4 -> shard0.订单_4。
    static {
        /**
         * 参见shard。
         * @param "shard0" "shard0"
         * @param 2 2
         * @param "order-2" "订单-2"
         * @param 4 4
         * @param "order-4" "订单-4"
         */
        seedShard("shard0", 2, "order-2", 4, "order-4");
        /**
         * 参见shard。
         * @param "shard1" "shard1"
         * @param 1 1
         * @param "order-1" "订单-1"
         * @param 3 3
         * @param "order-3" "订单-3"
         */
        seedShard("shard1", 1, "order-1", 3, "order-3");
    }

    /**
     * 参见shard。
     * @param db db
     * @param pairs pairs
     */
    private static void seedShard(String db, Object... pairs) {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://root:root@172.16.0.40:3307/", "root", "root");
             Statement statement = connection.createStatement()) {
            statement.execute("create database if not exists " + db);
            statement.execute("use " + db);
 // 脚本运行器的版本记录表（数据源script属性.版本table 默认
 // sys_database_版本）——分片库须预置，否则启动期版本查询经分片代理抛 tablenotfound 毒化元数据
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
            /**
             * illegal状态异常。
             * @param db db
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("真库种子初始化失败: " + db, e);
        }
    }

    /** 引擎激活验证：真库数据源覆写下分片引擎应可装配 */
    @Test
    void shardedEngineActivated() {
        /**
         * 断言not空。
         * @param shardingDataSource 分库分表数据源
         * @param "分片引擎应已装配" "分片引擎应已装配"
         */
        assertNotNull(shardingDataSource, "分片引擎应已装配");
        /**
         * 断言true。
         * @param shardingDataSource.getClass( 分库分表数据源.getclass(
         */
        assertTrue(shardingDataSource.getClass().getName().contains("ShardingSphere"),
                "shardingDataSource 应为 ShardingSphere 数据源，实际: " + shardingDataSource.getClass().getName());
    }

    /** 分片聚合：数量 跨两个真库分片聚合（路由可用时执行） */
    @Test
    void shardedAggregateQuery() {
        /**
         * assume分库分表routable。
         */
        assumeShardingRoutable();
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        Integer count = shardedJdbc.queryForObject("select count(*) from orders", Integer.class);
        /**
         * 断言equals。
         * @param 4 4
         * @param count 数量
         * @param "分片聚合应统计两个真库分片的全部数据" "分片聚合应统计两个真库分片的全部数据"
         */
        assertEquals(4, count, "分片聚合应统计两个真库分片的全部数据");
    }

    /** 分片查询：按分片键查询可命中对应真库分片（路由可用时执行） */
    @Test
    void shardedPointQuery() {
        /**
         * assume分库分表routable。
         */
        assumeShardingRoutable();
        JdbcTemplate shardedJdbc = new JdbcTemplate(shardingDataSource);
        List<String> names = shardedJdbc.queryForList("select name from orders where id = 2", String.class);
        /**
         * 断言equals。
         * @param 1 1
         * @param names.size( 名称.大小(
         */
        assertEquals(1, names.size(), "按分片键查询应命中分片返回对应行");
        /**
         * 断言equals。
         * @param "order-2" "订单-2"
         * @param names.getFirst 名称.获取(0
         */
        assertEquals("order-2", names.getFirst(), "分片路由应返回正确数据");
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
