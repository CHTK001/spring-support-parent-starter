package com.chua.starter.datapool.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datapool.support.constants.DatapoolConstants;
import com.chua.starter.datapool.support.model.dto.BlockQueryDTO;
import com.chua.starter.datapool.support.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大屏区块受限 SQL 查询接口。
 *
 * <p>自定义大屏的 sql 区块通过本接口取数：只允许只读查询（SELECT/WITH/SHOW/EXPLAIN），
 * 单次最多返回 {@value #MAX_ROWS} 行，超出截断并以 {@code truncated=true} 标记。
 * 与可写事务控制台（DbConsoleController）完全分离，本接口不提供任何写能力。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "数据源中心", description = "数据源台账与连通性")
@RestController
@RequestMapping(DatapoolConstants.API_PREFIX)
@RequiredArgsConstructor
public class DatasourceBlockQueryController {

    /**
     * 单次查询最大返回行数。
     */
    private static final int MAX_ROWS = 200;

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 大屏区块受限查询。
     *
     * <p>按次新建连接、用完即关，不占用连接缓存；SQL 只读白名单校验不通过、
     * 数据源不存在或执行失败时，经全局异常处理返回具体错误信息。</p>
     *
     * @param request 数据源 ID 与只读 SQL
     * @return columns/rows/rowCount/truncated/elapsedMs
     */
    @Operation(summary = "大屏区块受限查询", description = "只允许 SELECT/WITH/SHOW/EXPLAIN，最多返回 " + MAX_ROWS + " 行")
    @PostMapping("/block-query")
    public ReturnResult<Map<String, Object>> blockQuery(@RequestBody BlockQueryDTO request) {
        String sql = safeSql(request == null ? null : request.sql());
        Long datasourceId = request == null ? null : request.datasourceId();
        long start = System.currentTimeMillis();
        try (Connection conn = datasourceService.newConnection(datasourceId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < MAX_ROWS) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(serialize(rs.getObject(i)));
                }
                rows.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("truncated", rows.size() >= MAX_ROWS);
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            throw new IllegalStateException("大屏查询执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 结果值序列化：时间类型转字符串、二进制只给长度占位，避免 JSON 序列化歧义。
     *
     * @param value JDBC 原始值
     * @return 可安全 JSON 序列化的值
     */
    private static Object serialize(Object value) {
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (value instanceof byte[] b) {
            return "<binary " + b.length + "B>";
        }
        return value;
    }

    /**
     * SQL 白名单校验：仅允许只读查询，去尾分号。
     *
     * @param sql 原始 SQL
     * @return 规整后的 SQL
     */
    private static String safeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("select") || lower.startsWith("with") || lower.startsWith("show")
                || lower.startsWith("explain")) {
            return trimmed;
        }
        throw new IllegalArgumentException("大屏查询仅允许 SELECT/WITH/SHOW/EXPLAIN 语句");
    }
}
