package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectFlowSaveDTO;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.vo.CollectFlowVO;

import java.util.List;

/**
 * 采集流程服务。
 *
 * <p>流程定义与调度状态同表存储，DSL 只存 JSON 文本。并行线程数以流程字段为准，
 * 读写时同步到 DSL 的 {@code threadCount}，避免出现两处不同的线程数。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFlowService extends IService<CollectFlow> {

    /**
     * 分页查询流程。
     *
     * @param page      分页对象
     * @param condition 查询条件
     * @return 流程分页，列表不含 DSL 正文
     */
    IPage<CollectFlowVO> pageFlows(IPage<CollectFlow> page, CollectFlow condition);

    /**
     * 精简列表，用于下拉框与首页卡片。
     *
     * @param keyword 名称或编码模糊词
     * @return 流程列表
     */
    List<CollectFlowVO> listFlows(String keyword);

    /**
     * 流程详情，含 DSL。
     *
     * @param flowId 流程ID
     * @return 流程出参
     */
    CollectFlowVO detail(Integer flowId);

    /**
     * 新增流程。
     *
     * @param dto 保存入参
     * @return 流程出参
     */
    CollectFlowVO addFlow(CollectFlowSaveDTO dto);

    /**
     * 修改流程。
     *
     * @param dto 保存入参
     * @return 流程出参
     */
    CollectFlowVO editFlow(CollectFlowSaveDTO dto);

    /**
     * 启停调度，启用时校验 cron 并同步调度台账。
     *
     * @param flowId 流程ID
     * @param status 0 停用 1 启用
     * @return 是否成功
     */
    CollectFlowVO changeStatus(Integer flowId, Integer status);

    /**
     * 复制流程，副本为停用状态。
     *
     * @param flowId 流程ID
     * @return 副本出参
     */
    CollectFlowVO copyFlow(Integer flowId);

    /**
     * 逻辑删除流程，运行中拒绝。
     *
     * @param flowId 流程ID
     * @return 是否成功
     */
    boolean deleteFlow(Integer flowId);

    /**
     * 取流程，不存在直接拒绝。
     *
     * @param flowId 流程ID
     * @return 流程实体
     */
    CollectFlow requireFlow(Integer flowId);

    /**
     * 读取并校验流程 DSL。
     *
     * @param flow 流程实体
     * @return DSL 对象
     */
    CollectDslDTO readDsl(CollectFlow flow);

    /**
     * 校验 DSL 并返回规整后的 JSON 文本。
     *
     * @param dsl DSL 对象
     * @return JSON 文本
     * @throws IllegalArgumentException 校验失败
     */
    String normalizeDsl(CollectDslDTO dsl);

    /**
     * 记一次开始执行，只写最近开始时间与累计次数，不碰调度台账。
     *
     * @param flowId 流程ID
     */
    void markStarted(Integer flowId);

    /**
     * 累加一次执行产出的条数。
     *
     * <p>在任务收尾时调用：列表页直接读 {@code collect_flow_record_count}，
     * 不再临时对 {@code collect_task} 求和（任务表会一直长，聚合会越来越慢）。</p>
     *
     * @param flowId 流程ID，空或非正数（草稿运行）时忽略
     * @param delta  本次产出条数，0 或负数时忽略
     */
    void addRecords(Integer flowId, long delta);

    /**
     * 调度台账里已登记的采集流程数，读不到台账时为 0。
     *
     * @return 登记数
     */
    int scheduledJobCount();
}
