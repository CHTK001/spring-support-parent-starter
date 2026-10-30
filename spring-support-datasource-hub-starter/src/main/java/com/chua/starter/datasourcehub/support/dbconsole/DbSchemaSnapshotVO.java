package com.chua.starter.datasourcehub.support.dbconsole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表结构版本快照 — 列表 VO
 *
 * <p>给「版本管理」列表页用：只暴露元数据与 DDL 摘要，正文由 {@code ddl} 单独读回。
 * 列表不展示全文：DDL 超过 160 个字符时截断并补 "…"。</p>
 *
 * @param id             快照 ID
 * @param database       数据库名
 * @param tableName      表名
 * @param name           快照名称/标签
 * @param creator        创建人
 * @param createTime     创建时间（拍快照时刻）
 * @param ddlPreview     DDL 摘要（截断到 160 字符）
 * @param columns        快照时的列数
 * @param indexes        快照时的索引数
 * @param foreignKeys     snapshots 时的外键数
 * @author CH
 * @since 4.0.0.51
 */
public record DbSchemaSnapshotVO(
        Long id,
        String database,
        String tableName,
        String name,
        String creator,
        LocalDateTime createTime,
        String ddlPreview,
        Long columns,
        Long indexes,
        Long foreignKeys
) {
    /**
     * 把数据库快照行转成列表 VO。
     *
     * @param snapshot    数据库快照行
     * @param ddl         快照的建表 DDL 全文
     * @param columns     列数
     * @param indexes     索引数
     * @param foreignKeys 外键数
     * @return 列表 VO
     */
    public static DbSchemaSnapshotVO of(com.chua.starter.datasourcehub.support.entity.MsSchemaSnapshot snapshot,
                                        String ddl,
                                        Long columns,
                                        Long indexes,
                                        Long foreignKeys) {
        return new DbSchemaSnapshotVO(
                snapshot.getId(),
                snapshot.getDatabase(),
                snapshot.getTableName(),
                snapshot.getName(),
                snapshot.getCreator(),
                snapshot.getCreateTime(),
                ddl == null ? "" : (ddl.length() > 160 ? ddl.substring(0, 160) + " ..." : ddl),
                columns,
                indexes,
                foreignKeys
        );
    }
}
