package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsExecJob;
import com.chua.starter.server.support.entity.MsExecLog;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 批量执行服务
 * <p>
 * 提供多主机批量命令执行、文件分发与 cron 计划任务管理。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ExecService {

    /**
     * 批量执行命令（同步等待全部主机完成）
     *
     * @param hostIds        目标主机 标识 集合
     * @param command        命令内容
     * @param timeoutSeconds 单机命令超时（秒），空取默认值
     * @param jobId          关联的计划任务 标识（可为空，非空时日志自动绑定 作业标识）
     * @return 每台主机的执行日志
     */
    ReturnResult<List<MsExecLog>> runCommand(List<Long> hostIds, String command, Integer timeoutSeconds, Long jobId);

    /**
     * 文件分发：上传到每台主机的指定目录
     *
     * @param hostIds   目标主机 标识 集合
     * @param directory 目标目录
     * @param file      分发文件
     * @return 每台主机的分发日志
     */
    ReturnResult<List<MsExecLog>> distributeFile(List<Long> hostIds, String directory, MultipartFile file);

    /**
     * 新增计划任务（自动预计算下次运行时间）
     *
     * @param job 任务
     * @return 结果
     */
    ReturnResult<MsExecJob> createJob(MsExecJob job);

    /**
     * 更新计划任务（重算下次运行时间）
     *
     * @param id  主键
     * @param job 任务
     * @return 结果
     */
    ReturnResult<MsExecJob> updateJob(Long id, MsExecJob job);

    /**
     * 删除计划任务
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> deleteJob(Long id);

    /**
     * 任务列表（含启停状态与运行时间）
     *
     * @return 任务列表
     */
    ReturnResult<List<MsExecJob>> listJobs();

    /**
     * 查询单个任务
     *
     * @param id 主键
     * @return 任务（不存在时为错误结果）
     */
    ReturnResult<MsExecJob> getJob(Long id);

    /**
     * 立即触发一次计划任务（支持指定目标主机）
     *
     * @param id             主键
     * @param hostIds        本次目标主机 标识 集合，为空时用任务自身配置的主机
     * @param timeoutSeconds 单机超时（秒），为空时用任务配置
     * @return 执行日志列表
     */
    ReturnResult<List<MsExecLog>> runJobNow(Long id, List<Long> hostIds, Integer timeoutSeconds);

    /**
     * 调度触发计划任务（供 CronTrigger 调度器回调）
     * <p>执行命令并回写 上次/下次 运行时间；任务不存在/已停用/非定时类型时跳过。</p>
     *
     * @param id 主键
     */
    void runScheduledJob(Long id);

    /**
     * 查询执行日志
     *
     * @param type   日志类型 命令 / 文件 / 作业，可为空
     * @param hostId 主机过滤，可为空
     * @param jobId  计划任务过滤，可为空
     * @return 日志列表（最新在前，上限 200）
     */
    ReturnResult<List<MsExecLog>> listLogs(String type, Long hostId, Long jobId);
}
