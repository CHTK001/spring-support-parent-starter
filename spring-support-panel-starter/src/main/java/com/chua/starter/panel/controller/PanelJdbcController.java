package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelAiSqlRequest;
import com.chua.starter.panel.model.PanelAiMockDataRequest;
import com.chua.starter.panel.model.PanelDatabaseDocumentView;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelBackupRecord;
import com.chua.starter.panel.model.PanelBackupSchedule;
import com.chua.starter.panel.model.PanelBackupStartRequest;
import com.chua.starter.panel.model.PanelBinlogEvent;
import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionDescriptor;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.model.PanelJdbcAccountSaveRequest;
import com.chua.starter.panel.model.PanelJdbcAccountView;
import com.chua.starter.panel.model.PanelJdbcCommentRequest;
import com.chua.starter.panel.model.PanelJdbcPrivilegeRequest;
import com.chua.starter.panel.model.PanelMysqlProcessKillRequest;
import com.chua.starter.panel.model.PanelMysqlProcessKillView;
import com.chua.starter.panel.model.PanelRemarkRequest;
import com.chua.starter.panel.model.PanelRemarkView;
import com.chua.starter.panel.model.PanelSqlExecuteRequest;
import com.chua.starter.panel.model.PanelSqlTemplateRequest;
import com.chua.starter.panel.model.PanelTableActionRequest;
import com.chua.starter.panel.model.PanelTableDataRequest;
import com.chua.starter.panel.model.PanelTableDataView;
import com.chua.starter.panel.model.PanelTableMutationView;
import com.chua.starter.panel.model.PanelTableSaveRequest;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.PanelAiService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.service.PanelDocumentService;
import com.chua.starter.panel.service.PanelRemarkService;
import com.chua.starter.panel.service.PanelSqlTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * JDBC 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/jdbc")
@Tag(name = "Panel JDBC", description = "Panel JDBC 数据库管理接口")
public class PanelJdbcController {

    private final PanelConnectionService panelConnectionService;
    private final JdbcPanelService jdbcPanelService;
    private final PanelDocumentService panelDocumentService;
    private final PanelAiService panelAiService;
    private final PanelRemarkService panelRemarkService;
    private final PanelSqlTemplateService panelSqlTemplateService;

    /**
     * 打开数据库连接。
     *
     * @param definition 连接定义
     * @return 连接句柄
     */
    @Operation(summary = "打开连接")
    @PostMapping("/connection/open")
    public ReturnResult<PanelConnectionHandle> openConnection(@Parameter(description = "连接定义") @RequestBody PanelConnectionDefinition definition) {
        return ReturnResult.ok(panelConnectionService.open(definition));
    }

    /**
     * 查询缓存中的连接列表。
     *
     * @return 缓存连接列表
     */
    @Operation(summary = "查询缓存连接")
    @GetMapping("/connection/cache")
    public ReturnResult<List<PanelConnectionDescriptor>> cachedConnections() {
        return ReturnResult.ok(panelConnectionService.listCachedConnections());
    }

    /**
     * 关闭并移除连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/connection/{connectionId}")
    public ReturnResult<Boolean> closeConnection(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        panelConnectionService.evict(connectionId);
        return ReturnResult.ok(Boolean.TRUE);
    }

    /**
     * 查询目录树结构。
     *
     * @param connectionId 连接标识
     * @return 目录树
     */
    @Operation(summary = "查询目录树")
    @GetMapping("/{connectionId}/catalog")
    public ReturnResult<List<JdbcCatalogNode>> catalogTree(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.listCatalogTree(connectionId));
    }

    /**
     * 按关键字搜索对象。
     *
     * @param connectionId 连接标识
     * @param keyword 关键字
     * @return 搜索结果
     */
    @Operation(summary = "搜索目录对象")
    @GetMapping("/{connectionId}/search")
    public ReturnResult<List<JdbcCatalogNode>> search(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "关键字") @RequestParam String keyword) {
        return ReturnResult.ok(jdbcPanelService.search(connectionId, keyword));
    }

    /**
     * 查询数据表结构。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 表结构
     */
    @Operation(summary = "查询表结构")
    @GetMapping("/{connectionId}/structure")
    public ReturnResult<JdbcTableStructure> tableStructure(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "catalog 名称") @RequestParam(required = false) String catalog,
            @Parameter(description = "schema 名称") @RequestParam(required = false) String schema,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return ReturnResult.ok(jdbcPanelService.tableStructure(connectionId, catalog, schema, tableName));
    }

    /**
     * 查询表数据。
     *
     * @param connectionId 连接标识
     * @param request 查询参数
     * @return 表数据结果
     */
    @Operation(summary = "查询表数据")
    @PostMapping("/{connectionId}/table/data")
    public ReturnResult<PanelTableDataView> tableData(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "查询参数") @RequestBody PanelTableDataRequest request) {
        return ReturnResult.ok(jdbcPanelService.tableData(connectionId, request));
    }

    /**
     * 保存表数据变更。
     *
     * @param connectionId 连接标识
     * @param request 保存参数
     * @return 变更结果
     */
    @Operation(summary = "保存表数据")
    @PostMapping("/{connectionId}/table/save")
    public ReturnResult<PanelTableMutationView> saveTableData(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "保存参数") @RequestBody PanelTableSaveRequest request) {
        return ReturnResult.ok(jdbcPanelService.saveTableData(connectionId, request));
    }

    /**
     * 按动作生成并执行表操作 SQL。
     *
     * @param connectionId 连接标识
     * @param request 动作参数
     * @return 执行结果
     */
    @Operation(summary = "执行表动作")
    @PostMapping("/{connectionId}/table/action")
    public ReturnResult<PanelTableMutationView> tableAction(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "动作参数") @RequestBody PanelTableActionRequest request) {
        PanelSqlTemplateRequest templateRequest = new PanelSqlTemplateRequest();
        templateRequest.setPanelConnectionId(connectionId);
        templateRequest.setPanelCatalogName(request.getPanelCatalogName());
        templateRequest.setPanelSchemaName(request.getPanelSchemaName());
        templateRequest.setPanelTableName(request.getPanelTableName());
        templateRequest.setPanelActionType(request.getPanelActionType());
        templateRequest.setPanelBackupTableName(request.getPanelBackupTableName());
        String sql = panelSqlTemplateService.generateTemplate(connectionId, templateRequest);
        JdbcQueryResult result = jdbcPanelService.execute(connectionId, sql);
        return ReturnResult.ok(PanelTableMutationView.builder()
                .panelAffectedRows(result.getAffectedRows())
                .panelElapsedMillis(result.getElapsedMillis())
                .panelMessage("已执行 " + request.getPanelActionType() + " 操作")
                .build());
    }

    /**
     * 查询连接元数据。
     *
     * @param connectionId 连接标识
     * @return 连接元数据
     */
    @Operation(summary = "查询连接元数据")
    @GetMapping("/{connectionId}/metadata")
    public ReturnResult<JdbcConnectionMetadata> metadata(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.metadata(connectionId));
    }

    /**
     * 查询 MySQL 进程列表。
     *
     * @param connectionId 连接标识
     * @return 进程列表
     */
    @Operation(summary = "查询 MySQL 进程")
    @GetMapping("/{connectionId}/process")
    public ReturnResult<JdbcQueryResult> mysqlProcessList(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.mysqlProcessList(connectionId));
    }

    /**
     * 关闭 MySQL 进程。
     *
     * @param connectionId 连接标识
     * @param request 关闭请求
     * @return 关闭结果
     */
    @Operation(summary = "关闭 MySQL 进程")
    @PostMapping("/{connectionId}/process/kill")
    public ReturnResult<PanelMysqlProcessKillView> killMysqlProcess(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "关闭请求") @RequestBody PanelMysqlProcessKillRequest request) {
        return ReturnResult.ok(jdbcPanelService.killMysqlProcess(connectionId, request));
    }

    /**
     * 查询数据库账号列表。
     *
     * @param connectionId 连接标识
     * @return 账号列表
     */
    @Operation(summary = "查询账号列表")
    @GetMapping("/{connectionId}/account")
    public ReturnResult<List<PanelJdbcAccountView>> accounts(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.accounts(connectionId));
    }

    /**
     * 创建数据库账号。
     *
     * @param connectionId 连接标识
     * @param request 创建参数
     * @return 创建后的账号信息
     */
    @Operation(summary = "创建账号")
    @PostMapping("/{connectionId}/account")
    public ReturnResult<PanelJdbcAccountView> createAccount(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "账号创建参数") @RequestBody PanelJdbcAccountSaveRequest request) {
        return ReturnResult.ok(jdbcPanelService.createAccount(connectionId, request));
    }

    /**
     * 更新数据库账号。
     *
     * @param connectionId 连接标识
     * @param request 更新参数
     * @return 更新后的账号信息
     */
    @Operation(summary = "更新账号")
    @PostMapping("/{connectionId}/account/update")
    public ReturnResult<PanelJdbcAccountView> updateAccount(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "账号更新参数") @RequestBody PanelJdbcAccountSaveRequest request) {
        return ReturnResult.ok(jdbcPanelService.updateAccount(connectionId, request));
    }

    /**
     * 删除数据库账号。
     *
     * @param connectionId 连接标识
     * @param accountName 账号名称
     * @param host 主机
     * @return 删除结果
     */
    @Operation(summary = "删除账号")
    @DeleteMapping("/{connectionId}/account")
    public ReturnResult<Boolean> deleteAccount(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "账号名称") @RequestParam String accountName,
            @Parameter(description = "主机") @RequestParam(required = false) String host) {
        return ReturnResult.ok(jdbcPanelService.deleteAccount(connectionId, accountName, host));
    }

    /**
     * 授予账号权限。
     *
     * @param connectionId 连接标识
     * @param request 授权参数
     * @return 账号信息
     */
    @Operation(summary = "授予权限")
    @PostMapping("/{connectionId}/account/grant")
    public ReturnResult<PanelJdbcAccountView> grantAccount(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "授权参数") @RequestBody PanelJdbcPrivilegeRequest request) {
        return ReturnResult.ok(jdbcPanelService.grantAccount(connectionId, request));
    }

    /**
     * 回收账号权限。
     *
     * @param connectionId 连接标识
     * @param request 回收参数
     * @return 账号信息
     */
    @Operation(summary = "回收权限")
    @PostMapping("/{connectionId}/account/revoke")
    public ReturnResult<PanelJdbcAccountView> revokeAccount(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "回收参数") @RequestBody PanelJdbcPrivilegeRequest request) {
        return ReturnResult.ok(jdbcPanelService.revokeAccount(connectionId, request));
    }

    /**
     * 更新表注释。
     *
     * @param connectionId 连接标识
     * @param request 注释参数
     * @return 变更结果
     */
    @Operation(summary = "更新表注释")
    @PostMapping("/{connectionId}/comment/table")
    public ReturnResult<PanelTableMutationView> updateTableComment(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "注释参数") @RequestBody PanelJdbcCommentRequest request) {
        return ReturnResult.ok(jdbcPanelService.updateTableComment(
                connectionId,
                request.getPanelCatalogName(),
                request.getPanelSchemaName(),
                request.getPanelTableName(),
                request.getPanelCommentContent()));
    }

    /**
     * 更新列注释。
     *
     * @param connectionId 连接标识
     * @param request 注释参数
     * @return 变更结果
     */
    @Operation(summary = "更新列注释")
    @PostMapping("/{connectionId}/comment/column")
    public ReturnResult<PanelTableMutationView> updateColumnComment(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "注释参数") @RequestBody PanelJdbcCommentRequest request) {
        return ReturnResult.ok(jdbcPanelService.updateColumnComment(
                connectionId,
                request.getPanelCatalogName(),
                request.getPanelSchemaName(),
                request.getPanelTableName(),
                request.getPanelColumnName(),
                request.getPanelCommentContent()));
    }

    /**
     * 查询连接备注。
     *
     * @param connectionId 连接标识
     * @return 备注列表
     */
    @Operation(summary = "查询备注")
    @GetMapping("/{connectionId}/remark")
    public ReturnResult<List<PanelRemarkView>> remarks(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(panelRemarkService.listByConnectionId(connectionId));
    }

    /**
     * 保存连接备注。
     *
     * @param connectionId 连接标识
     * @param request 备注参数
     * @return 备注信息
     */
    @Operation(summary = "保存备注")
    @PostMapping("/{connectionId}/remark")
    public ReturnResult<PanelRemarkView> saveRemark(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "备注参数") @RequestBody PanelRemarkRequest request) {
        request.setPanelConnectionId(connectionId);
        return ReturnResult.ok(panelRemarkService.saveRemark(request));
    }

    /**
     * 生成 SQL 模板。
     *
     * @param connectionId 连接标识
     * @param request 模板参数
     * @return 模板 SQL
     */
    @Operation(summary = "生成 SQL 模板")
    @PostMapping("/{connectionId}/sql/template")
    public ReturnResult<String> sqlTemplate(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "模板参数") @RequestBody PanelSqlTemplateRequest request) {
        request.setPanelConnectionId(connectionId);
        return ReturnResult.ok(panelSqlTemplateService.generateTemplate(connectionId, request));
    }

    /**
     * 查询连接能力快照。
     *
     * @param connectionId 连接标识
     * @return 能力快照
     */
    @Operation(summary = "查询连接能力")
    @GetMapping("/{connectionId}/capabilities")
    public ReturnResult<PanelCapabilitySnapshot> capabilities(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.capabilities(connectionId));
    }

    /**
     * 生成数据表文档。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 表文档
     */
    @Operation(summary = "生成表文档")
    @GetMapping("/{connectionId}/document")
    public ReturnResult<String> tableDocument(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "catalog 名称") @RequestParam(required = false) String catalog,
            @Parameter(description = "schema 名称") @RequestParam(required = false) String schema,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return ReturnResult.ok(panelDocumentService.buildJdbcTableDocument(connectionId, catalog, schema, tableName));
    }

    /**
     * 生成数据库文档。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @return 数据库文档
     */
    @Operation(summary = "生成数据库文档")
    @GetMapping("/{connectionId}/database/document")
    public ReturnResult<PanelDatabaseDocumentView> databaseDocument(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "catalog 名称") @RequestParam(required = false) String catalog) {
        return ReturnResult.ok(panelDocumentService.buildJdbcDatabaseDocument(connectionId, catalog));
    }

    /**
     * AI 解释表结构。
     *
     * @param connectionId 连接标识
     * @param catalog catalog 名称
     * @param schema schema 名称
     * @param tableName 表名
     * @return 解释文本
     */
    @Operation(summary = "AI 解释表结构")
    @GetMapping("/{connectionId}/ai/structure")
    public ReturnResult<String> explainStructure(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "catalog 名称") @RequestParam(required = false) String catalog,
            @Parameter(description = "schema 名称") @RequestParam(required = false) String schema,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return ReturnResult.ok(panelAiService.explainJdbcStructure(connectionId, catalog, schema, tableName));
    }

    /**
     * AI 解释 SQL 语句。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @return 解释文本
     */
    @Operation(summary = "AI 解释 SQL")
    @PostMapping("/{connectionId}/ai/sql")
    public ReturnResult<String> explainSql(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "SQL 语句") @RequestBody String sql) {
        return ReturnResult.ok(panelAiService.explainSql(connectionId, sql));
    }

    /**
     * AI 生成 SQL。
     *
     * @param connectionId 连接标识
     * @param request 生成参数
     * @return SQL 文本
     */
    @Operation(summary = "AI 生成 SQL")
    @PostMapping("/{connectionId}/ai/sql/generate")
    public ReturnResult<String> generateSql(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "生成参数") @RequestBody PanelAiSqlRequest request) {
        return ReturnResult.ok(panelAiService.generateSql(connectionId, request));
    }

    /**
     * AI 生成模拟数据。
     *
     * @param connectionId 连接标识
     * @param request 生成参数
     * @return 模拟数据
     */
    @Operation(summary = "AI 生成模拟数据")
    @PostMapping("/{connectionId}/ai/mock-data")
    public ReturnResult<List<Map<String, Object>>> generateMockData(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "生成参数") @RequestBody PanelAiMockDataRequest request) {
        return ReturnResult.ok(panelAiService.generateMockData(connectionId, request));
    }

    /**
     * 执行 SQL 语句。
     *
     * @param connectionId 连接标识
     * @param request 执行参数
     * @return 执行结果
     */
    @Operation(summary = "执行 SQL")
    @PostMapping("/{connectionId}/execute")
    public ReturnResult<JdbcQueryResult> execute(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "执行参数") @RequestBody PanelSqlExecuteRequest request) {
        return ReturnResult.ok(jdbcPanelService.execute(connectionId, request.getSql(),
                request.getPageNum(), request.getPageSize()));
    }

    /**
     * 获取 SQL 执行计划。
     *
     * @param connectionId 连接标识
     * @param sql SQL 语句
     * @return 执行计划结果
     */
    @Operation(summary = "获取执行计划")
    @PostMapping("/{connectionId}/explain")
    public ReturnResult<JdbcQueryResult> explain(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "SQL 语句") @RequestBody String sql) {
        return ReturnResult.ok(jdbcPanelService.explain(connectionId, sql));
    }

    // ── 备份管理 ──────────────────────────────────────────

    @Operation(summary = "开启定时备份")
    @PostMapping("/{connectionId}/backup/start")
    public ReturnResult<PanelBackupSchedule> startBackup(
            @PathVariable String connectionId,
            @RequestBody PanelBackupStartRequest request) {
        return ReturnResult.ok(jdbcPanelService.startBackupSchedule(
                connectionId, request.getCronExpression(), request.getBackupPath(), request.getKeepCount()));
    }

    @Operation(summary = "停止定时备份")
    @PostMapping("/{connectionId}/backup/stop")
    public ReturnResult<Void> stopBackup(@PathVariable String connectionId) {
        jdbcPanelService.stopBackupSchedule(connectionId);
        return ReturnResult.ok(null);
    }

    @Operation(summary = "获取备份状态")
    @GetMapping("/{connectionId}/backup/status")
    public ReturnResult<PanelBackupSchedule> backupStatus(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.getBackupStatus(connectionId));
    }

    @Operation(summary = "立即执行备份")
    @PostMapping("/{connectionId}/backup/execute")
    public ReturnResult<PanelBackupRecord> executeBackup(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.executeBackup(connectionId));
    }

    @Operation(summary = "列出备份文件")
    @GetMapping("/{connectionId}/backup/list")
    public ReturnResult<List<PanelBackupRecord>> listBackups(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.listBackups(connectionId));
    }

    @Operation(summary = "删除备份文件")
    @DeleteMapping("/{connectionId}/backup/{backupId}")
    public ReturnResult<Boolean> deleteBackup(
            @PathVariable String connectionId,
            @PathVariable String backupId) {
        return ReturnResult.ok(jdbcPanelService.deleteBackup(connectionId, backupId));
    }

    @Operation(summary = "下载备份文件")
    @GetMapping("/{connectionId}/backup/{backupId}/download")
    public void downloadBackup(
            @PathVariable String connectionId,
            @PathVariable String backupId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.io.File file = jdbcPanelService.getBackupFile(connectionId, backupId);
        if (file == null) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"message\":\"备份文件不存在\"}");
            return;
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        response.setContentLengthLong(file.length());
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                response.getOutputStream().write(buf, 0, len);
            }
        }
    }

    // ── Binlog 增量监听 ──

    @PostMapping("/{connectionId}/binlog/start")
    public ReturnResult<Void> startBinlog(@PathVariable String connectionId) {
        jdbcPanelService.startBinlogMonitor(connectionId);
        return ReturnResult.ok(null, "Binlog监听已启动");
    }

    @PostMapping("/{connectionId}/binlog/stop")
    public ReturnResult<Void> stopBinlog(@PathVariable String connectionId) {
        jdbcPanelService.stopBinlogMonitor(connectionId);
        return ReturnResult.ok(null, "Binlog监听已停止");
    }

    @GetMapping("/{connectionId}/binlog/status")
    public ReturnResult<Map<String, Object>> binlogStatus(@PathVariable String connectionId) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", jdbcPanelService.isBinlogRunning(connectionId));
        return ReturnResult.ok(status);
    }

    @GetMapping("/{connectionId}/binlog/events")
    public ReturnResult<List<PanelBinlogEvent>> binlogEvents(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "100") int limit) {
        return ReturnResult.ok(jdbcPanelService.getBinlogEvents(connectionId, limit));
    }
}
