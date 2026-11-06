package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.vo.CollectQualityVO;
import com.chua.starter.collect.vo.CollectStatsVO;
import com.chua.starter.collect.vo.CollectTaskLogVO;
import com.chua.starter.collect.vo.CollectTaskVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集任务查询服务。
 *
 * <p>任务行的写入由执行服务负责，本服务只做查询、日志读取与清理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectTaskService extends IService<CollectTask> {

    /**
     * 任务分页。
     *
     * @param page      分页对象
     * @param condition 查询条件
     * @return 任务分页
     */
    IPage<CollectTaskVO> pageTasks(IPage<CollectTask> page, CollectTask condition);

    /**
     * 任务详情。
     *
     * @param taskId 任务ID
     * @return 任务出参
     */
    CollectTaskVO detail(Long taskId);

    /**
     * 任务的节点执行日志，按记录顺序返回。
     *
     * @param taskId 任务ID
     * @return 日志列表
     */
    List<CollectTaskLogVO> logs(Long taskId);

    /**
     * 按节点汇总的最新状态，供设计器给节点上色。
     *
     * @param taskId 任务ID
     * @return 每个节点一条最新状态
     */
    List<CollectTaskLogVO> nodeStates(Long taskId);

    /**
     * 删除任务及其节点日志，运行中的任务拒绝。
     *
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean deleteTask(Long taskId);

    /**
     * 清理历史任务。
     *
     * @param flowId 流程ID，空表示全部流程
     * @param before 删除该时间之前结束的任务
     * @return 删除的任务数
     */
    int cleanTasks(Integer flowId, LocalDateTime before);

    /**
     * 清理 N 天前的历史任务。
     *
     * @param flowId 流程ID，空表示全部流程
     * @param days   天数
     * @return 删除的任务数
     */
    default int cleanTasks(Integer flowId, int days) {
        return cleanTasks(flowId, LocalDateTime.now().minusDays(Math.max(days, 0)));
    }

    /**
     * 任务质量报告：从节点日志聚合质量指标。
     *
     * @param taskId 任务ID
     * @return 质量报告
     */
    CollectQualityVO quality(Long taskId);

    /**
     * 流程执行历史统计。
     *
     * @param flowId 流程ID
     * @param days   统计天数
     * @return 统计结果
     */
    CollectStatsVO stats(Integer flowId, int days);

    /**
     * 运行中任务的实时节点状态（供前端轮询）。
     *
     * @param taskId 任务ID
     * @return 最新节点状态列表
     */
    List<CollectTaskLogVO> liveNodes(Long taskId);
}
