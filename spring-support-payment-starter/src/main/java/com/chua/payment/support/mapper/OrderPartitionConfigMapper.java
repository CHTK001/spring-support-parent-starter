package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.OrderPartitionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 订单分表配置与分表操作预处理器。
 *
 * <p>自身继承 {@link BaseMapper}，对应实体 {@link OrderPartitionConfig} 与分表规则表 {@code pay_partition_config}。
 * 此外提供一组动态分表维护语句：源表与目标表名均由白名单与元数据校验后通过
 * {@code ${}} 拼接，SQL 定义在 {@code resources/mapper/OrderPartitionConfigMapper.xml} 中。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface OrderPartitionConfigMapper extends BaseMapper<OrderPartitionConfig> {

    /**
     * 将源表截止时间前的未迁移数据复制到目标分表。
     *
     * <p>对应 {@code pay_order} 等源表 → 目标分表（如 {@code pay_order_202603}）的数据搬运，
     * 复制范围为 {@code timeColumn < cutoff} 且主键尚未出现在目标分表中的记录。</p>
     *
     * @param sourceTable 经白名单与元数据校验的源表名
     * @param targetTable 经配置规则生成的只读目标表名
     * @param timeColumn 经白名单与元数据校验的时间列名
     * @param idColumn   经白名单与元数据校验的主键列名
     * @param cutoff     迁移截止时间
     * @return 迁移行数
     */
    int migrateToPartition(@Param("sourceTable") String sourceTable,
                           @Param("targetTable") String targetTable,
                           @Param("timeColumn") String timeColumn,
                           @Param("idColumn") String idColumn,
                           @Param("cutoff") LocalDateTime cutoff);

    /**
     * 删除源表中已迁移的旧数据。
     *
     * <p>在 {@link #migrateToPartition} 成功之后调用，清理源表中 {@code timeColumn < cutoff} 的历史记录。</p>
     *
     * @param sourceTable 经白名单与元数据校验的源表名
     * @param timeColumn  经白名单与元数据校验的时间列名
     * @param cutoff      迁移截止时间
     * @return 删除行数
     */
    int deleteMigratedRows(@Param("sourceTable") String sourceTable,
                           @Param("timeColumn") String timeColumn,
                           @Param("cutoff") LocalDateTime cutoff);

    /**
     * 统计目标分表中的历史数据。
     *
     * <p>用于迁移前后的一致性校验，确认目标分表已完整承接源表的历史数据。</p>
     *
     * @param targetTable 经配置规则生成的目标表名
     * @param timeColumn  经白名单与元数据校验的时间列名
     * @param cutoff      迁移截止时间
     * @return 历史数据行数
     */
    int countPartitionRows(@Param("targetTable") String targetTable,
                           @Param("timeColumn") String timeColumn,
                           @Param("cutoff") LocalDateTime cutoff);

    /**
     * 按源表结构创建目标分表。
     *
     * <p>对应 {@code CREATE TABLE IF NOT EXISTS ${targetTable} LIKE ${sourceTable}}，
     * 用于快速创建与源表结构一致的新分表。</p>
     *
     * @param sourceTable 经白名单与元数据校验的源表名
     * @param targetTable 经配置规则生成的目标表名
     */
    void createPartitionLike(@Param("sourceTable") String sourceTable,
                             @Param("targetTable") String targetTable);

    /**
     * 通过空结果集创建目标分表，兼容不支持 CREATE TABLE LIKE 的数据库。
     *
     * <p>对应 {@code CREATE TABLE IF NOT EXISTS ${targetTable} AS SELECT * FROM ${sourceTable} WHERE 1 = 0}，
     * 作为 {@link #createPartitionLike} 的降级方案。</p>
     *
     * @param sourceTable 经白名单与元数据校验的源表名
     * @param targetTable 经配置规则生成的目标表名
     */
    void createPartitionFromEmptyResult(@Param("sourceTable") String sourceTable,
                                        @Param("targetTable") String targetTable);
}
