package com.chua.starter.server.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerProjectOpLog;

/**
 * 项目操作日志服务。
 *
 * <p>所有项目操作都从这里落库：异步任务在触发时记 RUNNING、结束时回填
 * SUCCESS/FAILED 与原因；同步操作（增删改、启用/禁用）直接记终态。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface ProjectOpLogService {

    /**
     * 记录一次异步操作的开始。
     *
     * @param projectId   项目标识
     * @param projectName 项目名称
     * @param releaseId   发布标识
     * @param operation   操作类型
     * @param taskId      任务标识
     */
    void recordStart(Long projectId, String projectName, Long releaseId, String operation, String taskId);

    /**
     * 回填异步操作的终态。
     *
     * @param taskId  任务标识
     * @param success 是否成功
     * @param message 结果说明或失败原因
     */
    void recordFinish(String taskId, boolean success, String message);

    /**
     * 直接记录一次同步操作。
     *
     * @param projectId   项目标识
     * @param projectName 项目名称
     * @param releaseId   发布标识
     * @param operation   操作类型
     * @param success     是否成功
     * @param message     结果说明或失败原因
     */
    void recordOne(Long projectId, String projectName, Long releaseId,
                   String operation, boolean success, String message);

    /**
     * 分页查询操作日志。
     *
     * @param projectId 项目标识（可空）
     * @param releaseId 发布标识（可空）
     * @param operation 操作类型（可空）
     * @param status    状态（可空）
     * @param keyword   项目名关键字（可空）
     * @param page      页码（从 1 开始）
     * @param pageSize  每页条数
     * @return 分页结果
     */
    ReturnResult<IPage<MsServerProjectOpLog>> page(Long projectId, Long releaseId, String operation,
                                                   String status, String keyword, int page, int pageSize);
}
