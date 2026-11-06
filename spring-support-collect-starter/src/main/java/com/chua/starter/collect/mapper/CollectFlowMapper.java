package com.chua.starter.collect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.collect.entity.CollectFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 流程定义 Mapper。
 *
 * <p>到点查询与抢占已经交给 job-starter 的 {@code sys_job} 台账，这里只保留本表必须自己记的账。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface CollectFlowMapper extends BaseMapper<CollectFlow> {

    /**
     * 记一次开始执行，只写最近开始时间与累计次数。
     *
     * @param flowId 流程ID
     * @param begin  开始时间
     * @return 影响行数
     */
    int markStarted(@Param("flowId") Integer flowId, @Param("begin") LocalDateTime begin);

    /**
     * 累加采集条数。
     *
     * <p>任务收尾时调用。用增量更新而不是「读-改-写」，是为了并发收尾时互不覆盖；
     * 累计值也只是个展示口径，允许短暂落后于任务表。</p>
     *
     * @param flowId 流程ID
     * @param delta  本次新增条数（负数会被调用方挡掉）
     * @return 影响行数
     */
    int addRecordCount(@Param("flowId") Integer flowId, @Param("delta") long delta);

    /**
     * 原子占用一个跨节点并发名额。
     *
     * @param flowId 流程ID
     * @param limit  并发上限
     * @return 成功占用返回 1，达到上限返回 0
     */
    int tryAcquireRunning(@Param("flowId") Integer flowId, @Param("limit") int limit);

    /**
     * 释放一个跨节点并发名额。
     *
     * @param flowId 流程ID
     * @return 影响行数
     */
    int releaseRunning(@Param("flowId") Integer flowId);

    /**
     * 读取当前跨节点运行中数量。
     *
     * @param flowId 流程ID
     * @return 运行中数量，未知流程返回 0
     */
    int currentRunning(@Param("flowId") Integer flowId);

    /**
     * 按任务表重算全部流程的并发计数。
     *
     * <p>启动对账用。计数器只在任务收尾里减一，进程被强杀就永久偏大，导致该流程再也
     * 起不来。这里以任务表为唯一事实来源重算，重复执行幂等。</p>
     *
     * @return 被重算的流程数
     */
    int resetRunningFromTasks();
}
