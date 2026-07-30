package com.chua.starter.panel.service;

import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelJdbcAccountSaveRequest;
import com.chua.starter.panel.model.PanelJdbcAccountView;
import com.chua.starter.panel.model.PanelJdbcPrivilegeRequest;
import com.chua.starter.panel.model.PanelTableMutationView;
import com.chua.starter.panel.model.PanelBackupRecord;
import com.chua.starter.panel.model.PanelBackupSchedule;
import com.chua.starter.panel.model.PanelBinlogEvent;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelTableSaveRequest;
import com.chua.starter.panel.model.PanelTableDataRequest;
import com.chua.starter.panel.model.PanelTableDataView;
import com.chua.starter.panel.model.PanelMysqlProcessKillRequest;
import com.chua.starter.panel.model.PanelMysqlProcessKillView;

import java.util.List;

/**
 * JDBC 面板服务。
 *
 * @author CH
 */
public interface JdbcPanelService {

    /**
     * 查询目录树。
     *
     * @param connectionId 连接标识
     * @return 目录树节点列表
     */
    List<JdbcCatalogNode> listCatalogTree(String connectionId);

    /**
     * 按关键字搜索数据库对象。
     *
     * @param connectionId 连接标识
     * @param keyword 关键字
     * @return 搜索结果
     */
    List<JdbcCatalogNode> search(String connectionId, String keyword);

    /**
     * 查询数据表结构。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 表结构
     */
    JdbcTableStructure tableStructure(String connectionId, String catalog, String schema, String tableName);

    /**
     * 查询表数据。
     *
     * @param connectionId 连接标识
     * @param request 查询参数
     * @return 表数据视图
     */
    PanelTableDataView tableData(String connectionId, PanelTableDataRequest request);

    /**
     * 保存表数据变更。
     *
     * @param connectionId 连接标识
     * @param request 保存参数
     * @return 变更结果
     */
    PanelTableMutationView saveTableData(String connectionId, PanelTableSaveRequest request);

    /**
     * 执行 SQL。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @return 执行结果
     */
    JdbcQueryResult execute(String connectionId, String sql);

    /**
     * 执行 SQL（支持分页）。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 执行结果
     */
    JdbcQueryResult execute(String connectionId, String sql, Integer pageNum, Integer pageSize);

    /**
     * 获取执行计划。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @return 执行计划结果
     */
    JdbcQueryResult explain(String connectionId, String sql);

    /**
     * 查询账号列表（当前实现通常依赖具体数据库能力）。
     *
     * @param connectionId 连接标识
     * @return 账号列表
     */
    List<PanelJdbcAccountView> accounts(String connectionId);

    /**
     * 创建账号。
     *
     * @param connectionId 连接标识
     * @param request 创建参数
     * @return 账号信息
     */
    PanelJdbcAccountView createAccount(String connectionId, PanelJdbcAccountSaveRequest request);

    /**
     * 更新账号。
     *
     * @param connectionId 连接标识
     * @param request 更新参数
     * @return 账号信息
     */
    PanelJdbcAccountView updateAccount(String connectionId, PanelJdbcAccountSaveRequest request);

    /**
     * 删除账号。
     *
     * @param connectionId 连接标识
     * @param accountName 账号名称
     * @param host 主机
     * @return 是否成功
     */
    boolean deleteAccount(String connectionId, String accountName, String host);

    /**
     * 授权账号。
     *
     * @param connectionId 连接标识
     * @param request 授权参数
     * @return 账号信息
     */
    PanelJdbcAccountView grantAccount(String connectionId, PanelJdbcPrivilegeRequest request);

    /**
     * 回收权限。
     *
     * @param connectionId 连接标识
     * @param request 回收参数
     * @return 账号信息
     */
    PanelJdbcAccountView revokeAccount(String connectionId, PanelJdbcPrivilegeRequest request);

    /**
     * 更新表注释。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @param comment 注释内容
     * @return 变更结果
     */
    PanelTableMutationView updateTableComment(String connectionId, String catalog, String schema, String tableName, String comment);

    /**
     * 更新列注释。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @param columnName 列名
     * @param comment 注释内容
     * @return 变更结果
     */
    PanelTableMutationView updateColumnComment(String connectionId, String catalog, String schema, String tableName, String columnName, String comment);

    /**
     * 查询连接元信息。
     *
     * @param connectionId 连接标识
     * @return 元信息
     */
    JdbcConnectionMetadata metadata(String connectionId);

    /**
     * 查询 MySQL 进程列表（SHOW FULL PROCESSLIST）。
     *
     * @param connectionId 连接标识
     * @return 进程结果集
     */
    JdbcQueryResult mysqlProcessList(String connectionId);

    /**
     * 关闭 MySQL 进程（KILL）。
     *
     * @param connectionId 连接标识
     * @param request 关闭参数
     * @return 关闭结果
     */
    PanelMysqlProcessKillView killMysqlProcess(String connectionId, PanelMysqlProcessKillRequest request);

    /**
     * 查询连接能力快照。
     *
     * @param connectionId 连接标识
     * @return 能力快照
     */
    PanelCapabilitySnapshot capabilities(String connectionId);

    // ── 备份管理 ──────────────────────────────────────────

    /**
     * 开启定时备份。
     *
     * @param connectionId 连接标识
     * @param cronExpression cron 表达式
     * @param backupPath 备份文件保存路径（null 时使用默认路径）
     * @param keepCount 保留份数（null 时默认 5）
     * @return 备份调度配置
     */
    PanelBackupSchedule startBackupSchedule(String connectionId, String cronExpression, String backupPath, Integer keepCount);

    /**
     * 停止定时备份。
     *
     * @param connectionId 连接标识
     */
    void stopBackupSchedule(String connectionId);

    /**
     * 获取定时备份状态。
     *
     * @param connectionId 连接标识
     * @return 备份调度配置（未启动时返回 enabled=false 的对象）
     */
    PanelBackupSchedule getBackupStatus(String connectionId);

    /**
     * 立即执行一次备份。
     *
     * @param connectionId 连接标识
     * @return 备份记录
     */
    PanelBackupRecord executeBackup(String connectionId);

    /**
     * 列出所有备份文件。
     *
     * @param connectionId 连接标识
     * @return 备份记录列表
     */
    List<PanelBackupRecord> listBackups(String connectionId);

    /**
     * 删除备份文件。
     *
     * @param connectionId 连接标识
     * @param backupId 备份 ID（即文件名）
     * @return 是否成功
     */
    boolean deleteBackup(String connectionId, String backupId);

    /**
     * 获取备份文件的绝对路径。
     *
     * @param connectionId 连接标识
     * @param backupId 备份文件名
     * @return 文件绝对路径，若不存在返回 null
     */
    java.io.File getBackupFile(String connectionId, String backupId);

    // ── Binlog 增量监听 ──

    /**
     * 启动 Binlog 增量监听
     */
    void startBinlogMonitor(String connectionId);

    /**
     * 停止 Binlog 增量监听
     */
    void stopBinlogMonitor(String connectionId);

    /**
     * 查询 Binlog 监听状态
     */
    boolean isBinlogRunning(String connectionId);

    /**
     * 获取最近的 Binlog 事件
     */
    List<PanelBinlogEvent> getBinlogEvents(String connectionId, int limit);
}
