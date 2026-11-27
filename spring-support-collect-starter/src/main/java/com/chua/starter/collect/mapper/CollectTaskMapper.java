package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采集任务 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectTaskMapper extends BaseMapper<CollectTask> {

    /**
     * 批量取本页流程的最近五次运行，避免全局任务截断及逐流程查询。
     *
     * @param flowIds 本页非空流程 ID 集合
     * @return 每个流程最多五条，按时间和 ID 倒序
     */
    java.util.List<CollectTask> recentForFlows(@Param("flowIds") java.util.List<Integer> flowIds);

    /**
     * 累加任务计量：节点执行次数、抓取次数、记录数各列独立自增。
     *
     * @param taskId      任务ID
     * @param nodeCount   本次新增节点执行次数
     * @param requestCount 本次新增抓取次数
     * @param recordCount 本次新增记录数
     * @return 影响行数
     */
    int accumulate(@Param("taskId") Long taskId,
                   @Param("nodeCount") int nodeCount,
                   @Param("requestCount") int requestCount,
                   @Param("recordCount") int recordCount);

    /**
     * 收口僵尸任务：把执行期限已过、仍处于运行中的任务判为失败。
     *
     * <p>用于启动对账。进程被 kill / OOM / 断电时收尾的释放逻辑不会执行，任务行会永远
     * 停在运行中，并发名额也会被永久占住。期限由本次生效的 DSL/全局超时加收尾宽限固化，
     * 不受后续编辑或另一实例的配置影响。历史任务期限为空时不自动回收。</p>
     *
     * @param cutoff   判死水位，期限早于该时刻的任务收口
     * @param status   收口后写入的状态
     * @param error    收口原因，写入错误列
     * @param endTime  收口时刻
     * @return 收口的任务条数
     */
    int reapStaleRunning(@Param("cutoff") java.time.LocalDateTime cutoff,
                         @Param("status") String status,
                         @Param("error") String error,
                         @Param("endTime") java.time.LocalDateTime endTime);
}
