package com.chua.starter.datasourcehub.support.dbconsole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表结构版本快照 — 详情 VO
 *
 * <p>给回滚确认弹窗/DDL 预览窗用：完整的建表 DDL 文本 + 快照时的列/索引/外键定义。
 * 列与索引的原始 JSON 原文也保留在 {@code columns} / {@code indexes} / {@code foreignKeys}
 * 里，方便前端直接渲染并与拍快照时的版本比对。</p>
 *
 * @param id              快照 ID
 * @param database        数据库名
 * @param tableName       表名
 * @param name            快照名称/标签
 * @param creator         创建人
 * @param createTime      创建时间（拍快照时刻）
 * @param ddl             建表 DDL 全文（SHOW CREATE TABLE 取得的原文）
 * @param columns         快照时的列定义
 * @param indexes         快照时的索引定义
 * @param foreignKeys     快照时的外键定义
 * @param options         快照时的表选项（存储引擎/字符集/排序规则/注释）
 * @author CH
 * @since 4.0.0.51
 */
public record DbSnapshotDetailVO(
        Long id,
        String database,
        String tableName,
        String name,
        String creator,
        LocalDateTime createTime,
        String ddl,
        List<Map<String, Object>> columns,
        List<Map<String, Object>> indexes,
        List<Map<String, Object>> foreignKeys,
        Map<String, Object> options
) {
}
