package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsSyncTask;
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
}