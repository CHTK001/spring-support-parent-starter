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
     * 收口僵尸任务：把开始时间早于给定水位、仍处于运行中的任务判为失败。
     *
     * <p>用于启动对账。进程被 kill / OOM / 断电时收尾的释放逻辑不会执行，任务行会永远
     * 停在运行中，并发名额也会被永久占住。判据只取任务自身的开始时间与单任务超时上限，
     * 不依赖执行器标识，因此多节点部署下也不会误伤其它节点真正在跑的任务——超过上限还在
     * 跑的任务本来就不该存在。</p>
     *
     * @param cutoff   判死水位，早于该时刻开始的任务收口
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
