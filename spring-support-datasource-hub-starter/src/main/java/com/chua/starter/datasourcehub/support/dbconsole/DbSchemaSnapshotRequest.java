package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.List;
import java.util.Map;

/**
 * 表结构版本快照 — 创建/列出/回滚/删除的请求参数
 *
 * @param datasourceId 数据源 ID（走 @SysLog 唯一能定位的表）
 * @param database     数据库名（多库数据源的目标库；可空=当前库）
 * @param tableName    表名（从路径上取，不必再传）
 * @param name          snapshots 名称（不传则后端生成「保存于 2026-10-30 15:30」）
 * @param creator      创建人快照（不传则从请求头取当前登录人，记 SYSTEM）
 * @param remarks      备注说明
 * @author CH
 * @since 4.0.0.51
 */
public record DbSchemaSnapshotRequest(
        Long datasourceId,
        String database,
        String tableName,
        String name,
        String creator,
        String remarks
) {
    /**
     * 按请求参数组装：路径上的表名 / 表名/库名合并在 body 或 query 里任写一处。
     *
     * @param tableName    路径表名
     * @param body         请求体（可空）
     * @param datasourceId query datasourceId（可空）
     * @param database     query database（可空）
     * @return 请求 DTO
     */
    public static DbSchemaSnapshotRequest of(String tableName, Map<String, Object> body,
                                             Long datasourceId, String database) {
        return new DbSchemaSnapshotRequest(
                datasourceId, database, tableName,
                optionalString(body, "name"),
                optionalString(body, "creator"),
                optionalString(body, "remarks")
        );
    }

    private static String optionalString(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key)) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v).strip();
    }

    /**
     * 列表查询参数（分页）：datasourceId + 表名 + 名称模糊 + 页码。
     *
     * @param datasourceId 数据源 ID
     * @param database     库名（多库数据源的目标库，可空=只读当前库的快照）
     * @param tableName    表名（模糊搜索可空）
     * @param name         快照名称（模糊搜索可空）
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     */
    public record ListRequest(
            Long datasourceId,
            String database,
            String tableName,
            String name,
            Integer page,
            Integer size
    ) {
        public static ListRequest of(Long datasourceId, String database, String tableName, String name, Integer page, Integer size) {
            return new ListRequest(datasourceId, database, tableName, name, page, size);
        }
    }
}
