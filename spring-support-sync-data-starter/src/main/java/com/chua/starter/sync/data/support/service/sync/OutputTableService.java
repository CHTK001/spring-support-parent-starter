package com.chua.starter.sync.data.support.service.sync;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.sync.ColumnDefinition;

import java.util.List;

/**
 * 输出节点表管理服务
 * <p>
 * 用于管理输出节点（数据库类型）的自动建表功能
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
public interface OutputTableService {

    /**
     * 检查目标表是否存在
     *
     * @param nodeConfig 节点配置JSON
     * @param tableName  表名
     * @return 是否存在
     */
    ReturnResult<Boolean> checkTableExists(String nodeConfig, String tableName);

    /**
     * 创建目标表
     *
     * @param nodeConfig 节点配置JSON（包含数据库连接信息）
     * @param tableName  表名
     * @param columns    列定义列表
     * @return 创建结果
     */
    ReturnResult<Boolean> createTable(String nodeConfig, String tableName, List<ColumnDefinition> columns);

    /**
     * 获取表结构
     *
     * @param nodeConfig 节点配置JSON
     * @param tableName  表名
     * @return 列定义列表
     */
    ReturnResult<List<ColumnDefinition>> getTableStructure(String nodeConfig, String tableName);

    /**
     * 删除表
     *
     * @param nodeConfig 节点配置JSON
     * @param tableName  表名
     * @return 删除结果
     */
    ReturnResult<Boolean> dropTable(String nodeConfig, String tableName);

    /**
     * 预览建表SQL
     *
     * @param tableName  表名
     * @param columns    列定义列表
     * @param dbType     数据库类型（mysql/postgresql/oracle等）
     * @return DDL语句
     */
    ReturnResult<String> previewCreateTableSql(String tableName, List<ColumnDefinition> columns, String dbType);

    /**
     * 同步表结构
     * <p>
     * 根据列定义修改现有表结构（添加缺失列）
     * </p>
     *
     * @param nodeConfig 节点配置JSON
     * @param tableName  表名
     * @param columns    列定义列表
     * @return 同步结果
     */
    ReturnResult<Boolean> syncTableStructure(String nodeConfig, String tableName, List<ColumnDefinition> columns);
}
