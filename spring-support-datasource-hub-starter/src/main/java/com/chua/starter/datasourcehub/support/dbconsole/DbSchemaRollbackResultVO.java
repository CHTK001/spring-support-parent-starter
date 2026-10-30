package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.List;

/**
 * 表结构版本快照 — 回滚结果
 *
 * <p>回滚不是"一刀切"：脚本按语句执行（MySQL 关外键开关、删表、原样建表、重建索引/外键），
 * 谁报错谁进 {@code errors}，其余照常提交。调用方凭这份报告判断是「全部从新开始」
 * 还是「部分失败」。</p>
 *
 * @param snapshotId 快照 ID
 * @param database 表所在的库（拍快照时测到的目标库）
 * @param tableName 表名
 * @param total 执行的 SQL 语句总条数
 * @param ok 成功条数
 * @param fail 失败条数（出错的句子在 {@code errors} 里）
 * @param elapsedMs 端到端耗时（毫秒）
 * @param errors 失败原因（语句标题 -> 原因）
 * @author CH
 * @since 4.0.0.51
 */
public record DbSchemaRollbackResultVO(
        Long snapshotId,
        String database,
        String tableName,
        int total,
        int ok,
        int fail,
        long elapsedMs,
        List<String> errors
) {

    /**
     * 是否全部成功。
     *
     * @return true=没有任何一条语句失败
     */
    public boolean allOk() {
        return fail == 0;
    }

    /**
     * 回滚摘要（UI 提示条用，不加完整 SQL 正文避免打满屏幕）
     *
     * @return 摘要
     */
    public String summary() {
        return String.format("回滚到 %s 完成：%d 条语句，成功 %d，失败 %d，耗时 %dms",
                tableName, total, ok, fail, elapsedMs);
    }
}
