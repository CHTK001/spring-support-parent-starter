package com.chua.starter.datasourcehub.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaSnapshotRequest;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaSnapshotVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSnapshotDetailVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaRollbackResultVO;

/**
 * 表结构版本控制（DDL 快照 + 回滚）。
 *
 * <p>核心语义：拍快照 = 把表在拍摄时刻的「建表 DDL + 列 + 索引 + 外键 + 表选项
 * （JSON 文本）」落盘到 {@code ms_schema_snapshot}；回滚 = 删掉当前表
 * （多表倒序，MySQL 先关 FOREIGN_KEY_CHECKS），再把 DDL 原样执行一遍。
 * 回滚只影响被回滚的表，不会碰同库的其他表。</p>
 *
 * @author CH
 * @since 4.0.0.51
 * @see com.chua.starter.datasourcehub.support.controller.DbConsoleController#snapshot*
 */
public interface DbSchemaSnapshotService {

    /**
     * 给表拍结构快照。
     *
     * <p>从库中取出表的完整定义（SHOW CREATE TABLE/失败则 fall back 到 JDBC
     * 元数据拼装）后落盘，返回快照 ID。name 为空时后端生成（如「保存于 2026-10-30 15:30」）。</p>
     *
     * @param request 拍快照参数
     * @return 快照 ID（可直接走 RETURN 键查详情）
     */
    ReturnResult<Long> takeSnapshot(DbSchemaSnapshotRequest request);

    /**
     * 列出快照（分页、按时间倒序）。
     *
     * @param request 列表参数
     * @return 分页 VO
     */
    ReturnResult<Page<DbSchemaSnapshotVO>> listSnapshots(DbSchemaSnapshotRequest.ListRequest request);

    /**
     * 获取快照详情：完整 DDL 文本 + 列/索引/外键 JSON 原文。
     *
     * @param snapshotId 快照 ID
     * @return 详情 VO
     */
    ReturnResult<DbSnapshotDetailVO> getSnapshotDetail(Long snapshotId);

    /**
     * 删除快照（逻辑删除）。
     *
     * @param snapshotId 快照 ID
     * @return 是否成功
     */
    ReturnResult<Boolean> deleteSnapshot(Long snapshotId);

    /**
     * 回滚到指定快照：删当前表 → CREATE TABLE DDL → 重建索引/外键（SHOW CREATE TABLE
     * 来源的 DDL 已含这些约束，无需重建）。
     *
     * @param snapshotId 快照 ID
     * @return 回滚结果（总语句数/成功数/失败数/耗时/失败原因）
     */
    ReturnResult<DbSchemaRollbackResultVO> rollbackSnapshot(Long snapshotId);
}
