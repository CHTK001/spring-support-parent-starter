package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.model.SyncColumnVO;
import com.chua.starter.monitor.support.model.SyncPreviewVO;
import com.chua.starter.monitor.support.model.SyncTaskRunLogVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.chua.starter.monitor.support.model.dto.SyncPreviewDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;

import java.util.List;

/**
 * 数据同步任务服务：任务的增删改查、字段映射预览、立即执行与定时启停。
 *
 * <p>数据面复用 datasync 引擎（JDBC 源读取 + 字段映射转换 + Sink 契约），
 * 调度由 Spring 定时器驱动，理由见实现类注释。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface SyncTaskService {

    /**
     * 列出全部同步任务。
     *
     * @return 任务列表（按主键升序）
     */
    ReturnResult<List<MsSyncTask>> list();

    /**
     * 查询单个同步任务。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    ReturnResult<MsSyncTask> get(Long id);

    /**
     * 新建同步任务。
     *
     * @param request 任务入参
     * @return 新建后的任务
     */
    ReturnResult<MsSyncTask> create(SyncTaskDTO request);

    /**
     * 修改同步任务。
     *
     * @param id      任务 ID
     * @param request 任务入参
     * @return 修改后的任务
     */
    ReturnResult<MsSyncTask> update(Long id, SyncTaskDTO request);

    /**
     * 删除同步任务，并取消其定时。
     *
     * @param id 任务 ID
     * @return 是否删除成功
     */
    ReturnResult<Boolean> delete(Long id);

    /**
     * 立即执行一次同步。
     *
     * <p>同步阻塞到写入结束，返回真实读取/写入行数与错误信息；执行结果同时回写到任务行。</p>
     *
     * @param id 任务 ID
     * @return 本次执行结果
     */
    ReturnResult<SyncTaskRunVO> runOnce(Long id);

    /**
     * 查询任务的执行调试日志（过程日志：连源、SQL、列解析、映射、逐文件读写、错误堆栈）。
     *
     * <p>数据来自服务端内存缓冲：进行中的执行在最前（可轮询实时追加），其后是已完成的
     * 最近若干次执行（新在前）；后端重启即清空。</p>
     *
     * @param id 任务 ID
     * @return 日志列表；无记录时为空列表
     */
    ReturnResult<List<SyncTaskRunLogVO>> runLogs(Long id);

    /**
     * 启用或停用任务的定时调度。
     *
     * @param id      任务 ID
     * @param enabled true 启用、false 停用
     * @return 是否更新成功
     */
    ReturnResult<Boolean> setEnabled(Long id, boolean enabled);

    /**
     * 预览字段映射：读取源列与目标列，给出最终映射与需要人工确认的提示。
     *
     * @param request 预览入参
     * @return 预览结果
     */
    ReturnResult<SyncPreviewVO> preview(SyncPreviewDTO request);

    /**
     * 校验源 SQL 能否真正读出列（不做目标侧检查）。
     *
     * <p><b>为什么单独一个方法</b>：向导第①步点「下一步」时目标表还没填，
     * 但源 SQL 是否可用已经可以判定。早先源表不存在要到第②步点预览才暴露，
     * 用户会先配完目标表、映射，再被一句「预览失败」打回第①步，白走一整套。
     * 这里只读源列，与 {@link #preview} 的源侧分支同一实现（{@code loadSourceColumns}），
     * 因此「下一步放行」与「预览能配」永远不会给出相反结论。</p>
     *
     * @param request 源侧入参（只用 sourceType / sourceDatasourceId / sourceSql / sourcePath / sourceOptions）
     * @return 源列；源不可用时返回错误文案（成功即代表可进入下一步）
     */
    ReturnResult<List<SyncColumnVO>> validateSource(SyncPreviewDTO request);
}