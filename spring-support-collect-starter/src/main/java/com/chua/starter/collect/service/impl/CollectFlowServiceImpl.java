// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectFlowSaveDTO;
import com.chua.starter.collect.dto.CollectNodeDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.CollectExecutorRegistry;
import com.chua.starter.collect.engine.executor.CollectProcessExecutor;
import com.chua.starter.collect.engine.model.CollectGraph;
import com.chua.starter.collect.engine.spi.CollectFlowProvider;
import com.chua.starter.collect.engine.spi.CollectFlowRef;
import com.chua.starter.collect.entity.CollectCategory;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.mapper.CollectCategoryMapper;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.schedule.CollectFlowScheduleKeys;
import com.chua.starter.collect.schedule.CollectFlowSchedulePort;
import com.chua.starter.collect.schedule.CollectFlowScheduleSpec;
import com.chua.starter.collect.schedule.CollectFlowScheduleState;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.collect.service.CollectFlowService;
import com.chua.starter.collect.service.CollectFlowVersionService;
import com.chua.starter.collect.support.CollectCronSupport;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectTaskStatus;
import com.chua.starter.collect.vo.CollectFlowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采集流程服务实现。
 *
 * <p>调度不再由本模块轮询：每个流程在调度台账（sys_job）里有一行自己的 cron
 * （键 {@code collect-flow-<流程ID>}），启停/改 cron/删除都会同步那一行，到点由
 * job-starter 直接派发。与调度器的交互全部收口在 {@link CollectFlowSchedulePort}：
 * 部署没有 job-starter 时该端口退化为空实现，流程仍可设计、手动执行、看结果，
 * 只是没有定时触发。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectFlowServiceImpl extends ServiceImpl<CollectFlowMapper, CollectFlow>
        implements CollectFlowService, CollectFlowProvider, ApplicationRunner {

    /**
     * 未分类的分类ID。
     */
    private static final int UNCATEGORIZED = 0;

    /**
     * 名称与编码的最大长度，与建表列宽一致。
     */
    private static final int MAX_TEXT_LENGTH = 128;

    /**
     * 节点执行器注册表，校验 DSL 时提供已注册类型。
     */
    private final CollectExecutorRegistry registry;

    /**
     * 执行引擎，用于判断流程是否正在运行。
     */
    private final CollectEngine engine;

    /**
     * 任务表访问，删除前的运行检查与统计。
     */
    private final CollectTaskMapper taskMapper;

    /**
     * 分类表访问，出参回填分类名称。
     */
    private final CollectCategoryMapper categoryMapper;

    /**
     * 平台配置，线程数缺省值。
     */
    private final CollectProperties properties;

    /**
     * 排期端口：每个流程一行调度台账。
     *
     * <p>有 job-starter 时落到 sys_job，没有时为空实现（定时不可用），采集本体不受影响。</p>
     */
    private final CollectFlowSchedulePort schedulePort;

    /**
     * 执行服务，调度派发时才取，避免与执行服务在启动期互相依赖。
     */
    private final ObjectProvider<CollectExecutionService> executionServiceProvider;

    /**
     * 流程版本服务，保存/编辑流程后自动落版本快照。
     */
    private final CollectFlowVersionService versionService;

    @Override
    public IPage<CollectFlowVO> pageFlows(IPage<CollectFlow> page, CollectFlow condition) {
        Map<Integer, String> categories = categoryNames();
        Map<String, CollectFlowScheduleState> runtimes = jobRuntimes();
        LambdaQueryWrapper<CollectFlow> wrapper = alive(condition)
                .select(CollectFlow.class, field -> !"collect_flow_dsl".equals(field.getColumn()));
        IPage<CollectFlow> entityPage = page(page, wrapper);
        return entityPage.convert(flow -> toVo(flow, categories,
                runtimes.get(CollectFlowScheduleKeys.jobKeyOf(flow.getCollectFlowId()))));
    }

    @Override
    public List<CollectFlowVO> listFlows(String keyword) {
        Map<Integer, String> categories = categoryNames();
        Map<String, CollectFlowScheduleState> runtimes = jobRuntimes();
        String text = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<CollectFlow> wrapper = Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowIsDeleted, 0)
                .and(!text.isEmpty(), query -> query
                        .like(CollectFlow::getCollectFlowName, text)
                        .or()
                        .like(CollectFlow::getCollectFlowCode, text))
                .orderByDesc(CollectFlow::getCollectFlowId);
        List<CollectFlowVO> list = new ArrayList<>();
        for (CollectFlow flow : list(wrapper.select(CollectFlow.class,
                field -> !"collect_flow_dsl".equals(field.getColumn())))) {
            list.add(toVo(flow, categories, runtimes.get(CollectFlowScheduleKeys.jobKeyOf(flow.getCollectFlowId()))));
        }
        return list;
    }

    @Override
    public CollectFlowVO detail(Integer flowId) {
        CollectFlow flow = requireFlow(flowId);
        CollectFlowVO vo = toVo(flow, categoryNames(),
                schedulePort.runtime(CollectFlowScheduleKeys.jobKeyOf(flowId)));
        CollectDslDTO dsl = readDsl(flow);
        CollectFlowVO full = new CollectFlowVO(
                vo.collectFlowId(),
                vo.collectFlowName(),
                vo.collectFlowCategoryId(),
                vo.collectFlowCategoryName(),
                vo.collectFlowCode(),
                vo.collectFlowCron(),
                vo.collectFlowStatus(),
                vo.collectFlowConcurrency(),
                vo.collectFlowThreadCount(),
                vo.collectFlowRemark(),
                vo.collectFlowLastExecTime(),
                vo.collectFlowNextExecTime(),
                vo.collectFlowExecCount(),
                vo.collectFlowRecordCount(),
                dsl.nodes() == null ? 0 : dsl.nodes().size(),
                dsl.edges() == null ? 0 : dsl.edges().size(),
                vo.running(),
                vo.scheduled(),
                dsl,
                vo.createName(),
                vo.createTime(),
                vo.updateTime());
        return full;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectFlowVO addFlow(CollectFlowSaveDTO dto) {
        CollectFlow flow = new CollectFlow();
        apply(flow, dto);
        flow.setCollectFlowStatus(0);
        flow.setCollectFlowExecCount(0);
        flow.setCollectFlowIsDeleted(0);
        save(flow);
        ensureJob(flow);
        saveVersionSnapshot(flow);
        return detail(flow.getCollectFlowId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectFlowVO editFlow(CollectFlowSaveDTO dto) {
        CollectFlow exists = requireFlow(dto.collectFlowId());
        CollectFlow flow = new CollectFlow();
        flow.setCollectFlowId(exists.getCollectFlowId());
        apply(flow, dto);
        updateById(flow);
        ensureJob(requireFlow(exists.getCollectFlowId()));
        saveVersionSnapshot(flow);
        return detail(exists.getCollectFlowId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectFlowVO changeStatus(Integer flowId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("调度状态只能是 0 停用或 1 启用");
        }
        CollectFlow exists = requireFlow(flowId);
        boolean enabled = status == 1;
        if (enabled) {
            requireScheduling();
            requireSchedulable(exists);
        }
        CollectFlow flow = new CollectFlow();
        flow.setCollectFlowId(exists.getCollectFlowId());
        flow.setCollectFlowStatus(status);
        updateById(flow);
        String jobKey = CollectFlowScheduleKeys.jobKeyOf(exists.getCollectFlowId());
        if (!schedulePort.changeStatus(jobKey, enabled)) {
            // 台账行缺失（历史流程或被人从任务中心删掉）时按当前配置补登记
            ensureJob(requireFlow(exists.getCollectFlowId()));
            schedulePort.changeStatus(jobKey, enabled);
        }
        return detail(exists.getCollectFlowId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectFlowVO copyFlow(Integer flowId) {
        CollectFlow exists = requireFlow(flowId);
        CollectFlow copy = new CollectFlow();
        copy.setCollectFlowName(shorten(exists.getCollectFlowName() + "-副本", MAX_TEXT_LENGTH));
        copy.setCollectFlowCode(newCode());
        copy.setCollectFlowCategoryId(exists.getCollectFlowCategoryId());
        copy.setCollectFlowDsl(exists.getCollectFlowDsl());
        copy.setCollectFlowCron(exists.getCollectFlowCron());
        copy.setCollectFlowConcurrency(exists.getCollectFlowConcurrency());
        copy.setCollectFlowThreadCount(exists.getCollectFlowThreadCount());
        copy.setCollectFlowRemark(exists.getCollectFlowRemark());
        copy.setCollectFlowStatus(0);
        copy.setCollectFlowExecCount(0);
        copy.setCollectFlowIsDeleted(0);
        save(copy);
        ensureJob(copy);
        return detail(copy.getCollectFlowId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFlow(Integer flowId) {
        CollectFlow exists = requireFlow(flowId);
        if (engine.isFlowRunning(flowId) || runningTasks(flowId) > 0) {
            throw new IllegalArgumentException("流程正在执行，请先停止任务再删除");
        }
        CollectFlow flow = new CollectFlow();
        flow.setCollectFlowId(exists.getCollectFlowId());
        flow.setCollectFlowStatus(0);
        flow.setCollectFlowIsDeleted(1);
        // 编码列上有唯一索引，逻辑删除后必须让位，否则同名流程再也建不起来
        flow.setCollectFlowCode(shorten(exists.getCollectFlowCode() + "#d" + exists.getCollectFlowId(), 64));
        boolean changed = updateById(flow);
        releaseJob(exists.getCollectFlowId());
        return changed;
    }

    @Override
    public CollectFlow requireFlow(Integer flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("流程ID不能为空");
        }
        CollectFlow flow = getById(flowId);
        if (flow == null || (flow.getCollectFlowIsDeleted() != null && flow.getCollectFlowIsDeleted() == 1)) {
            throw new IllegalArgumentException("流程不存在或已删除: " + flowId);
        }
        return flow;
    }

    @Override
    public CollectDslDTO readDsl(CollectFlow flow) {
        String dsl = flow.getCollectFlowDsl();
        if (dsl == null || dsl.isBlank()) {
            throw new IllegalArgumentException("流程还没有设计流程图");
        }
        CollectDslDTO parsed = CollectJson.parse(dsl, CollectDslDTO.class);
        if (parsed == null) {
            throw new IllegalArgumentException("流程图解析失败");
        }
        return withFlowMeta(parsed, flow);
    }

    /**
     * 用流程表的名称与线程数覆盖 DSL 中的冗余字段。
     *
     * <p>DSL 与流程表都存名称/线程数，展示与执行一律以流程表为准，避免两处不一致。</p>
     *
     * @param parsed 解析后的 DSL
     * @param flow   流程行
     * @return 覆盖冗余字段后的 DSL
     */
    private CollectDslDTO withFlowMeta(CollectDslDTO parsed, CollectFlow flow) {
        String name = parsed.name() == null || parsed.name().isBlank()
                ? flow.getCollectFlowName() : parsed.name();
        // 运行超时是 DSL 级配置：展示与执行都以 DSL 为准（流程表没有该列），原样透传。
        return new CollectDslDTO(name, threadCountOf(flow), parsed.timeoutMs(), parsed.headers(), parsed.cookies(),
                parsed.variables(), parsed.nodes(), parsed.edges());
    }

    @Override
    public String normalizeDsl(CollectDslDTO dsl) {
        if (dsl == null || dsl.nodes() == null || dsl.nodes().isEmpty()) {
            throw new IllegalArgumentException("流程图没有任何节点");
        }
        try {
            CollectGraph.build(dsl, registry.shapes());
        } catch (CollectGraph.CollectGraphException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        return CollectJson.toJson(dsl);
    }

    @Override
    public void markStarted(Integer flowId) {
        if (flowId == null) {
            return;
        }
        baseMapper.markStarted(flowId, LocalDateTime.now());
    }

    @Override
    public void addRecords(Integer flowId, long delta) {
        // 草稿运行（flowId 为负数）与 0 产出都不记账：前者没有流程行，后者写进去也没意义
        if (flowId == null || flowId <= 0 || delta <= 0) {
            return;
        }
        baseMapper.addRecordCount(flowId, delta);
    }

    @Override
    public int scheduledJobCount() {
        return jobRuntimes().size();
    }

    @Override
    public CollectFlowRef load(Integer flowId) {
        if (flowId == null) {
            return null;
        }
        CollectFlow flow = getById(flowId);
        if (flow == null || (flow.getCollectFlowIsDeleted() != null && flow.getCollectFlowIsDeleted() == 1)) {
            return null;
        }
        String dsl = flow.getCollectFlowDsl();
        CollectDslDTO parsed = dsl == null || dsl.isBlank() ? null : CollectJson.parse(dsl, CollectDslDTO.class);
        if (parsed != null) {
            parsed = withFlowMeta(parsed, flow);
        }
        return new CollectFlowRef(flow.getCollectFlowId(), flow.getCollectFlowName(), parsed);
    }

    /**
     * 保存流程版本快照。
     *
     * @param flow 已写入 DSL 的流程实体
     */
    private void saveVersionSnapshot(CollectFlow flow) {
        if (flow == null || flow.getCollectFlowId() == null || flow.getCollectFlowDsl() == null
                || flow.getCollectFlowDsl().isBlank()) {
            return;
        }
        versionService.saveVersion(flow.getCollectFlowId(), flow.getCollectFlowDsl(), null);
    }

    /**
     * 把保存入参写入实体并校验。
     *
     * @param flow 实体
     * @param dto  入参
     */
    private void apply(CollectFlow flow, CollectFlowSaveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("保存内容不能为空");
        }
        flow.setCollectFlowName(requireText(dto.collectFlowName(), "流程名称", MAX_TEXT_LENGTH));
        flow.setCollectFlowCategoryId(dto.collectFlowCategoryId() == null
                ? UNCATEGORIZED : dto.collectFlowCategoryId());
        String code = dto.collectFlowCode() == null ? "" : dto.collectFlowCode().trim();
        if (code.isEmpty()) {
            code = newCode();
        }
        flow.setCollectFlowCode(requireText(code, "流程编码", 64));
        checkCodeUnique(flow);
        String cron = dto.collectFlowCron() == null ? "" : dto.collectFlowCron().trim();
        CollectCronSupport.validate(cron);
        flow.setCollectFlowCron(cron);
        flow.setCollectFlowConcurrency(requirePositive(dto.collectFlowConcurrency(), 1, 32, "并发任务数"));
        flow.setCollectFlowThreadCount(requirePositive(dto.collectFlowThreadCount(),
                properties.getDefaultThreadCount(), 64, "并行线程数"));
        flow.setCollectFlowRemark(dto.collectFlowRemark());
        flow.setCollectFlowDsl(normalizeDsl(dto.dsl()));
        checkSubflows(flow.getCollectFlowId(), flow.getCollectFlowName(), dto.dsl());
    }

    /**
     * 校验子流程引用：目标必须存在、不能调用自己，引用链也不能绕回自己。
     *
     * <p>运行期还有一道按调用栈的拦截，这里提前在保存期报错，避免定时任务半夜失败。</p>
     *
     * @param selfId   当前流程ID，新增时为空
     * @param selfName 当前流程名称
     * @param dsl      待保存的流程图
     */
    private void checkSubflows(Integer selfId, String selfName, CollectDslDTO dsl) {
        List<Integer> refs = referencedFlows(dsl);
        if (refs.isEmpty()) {
            return;
        }
        if (selfId != null && refs.contains(selfId)) {
            throw new IllegalArgumentException("子流程不能调用自身: " + selfName);
        }
        LinkedHashSet<Integer> stack = new LinkedHashSet<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        if (selfId != null) {
            stack.add(selfId);
            names.put(selfId, selfName);
        }
        walkSubflows(refs, stack, names);
    }

    /**
     * 递归展开子流程引用链，边展开边校验存在性与成环。
     *
     * @param refs  待展开的流程ID
     * @param stack 当前引用链，用于识别成环
     * @param names 流程ID到名称，用于拼出错文案
     */
    private void walkSubflows(List<Integer> refs, LinkedHashSet<Integer> stack, Map<Integer, String> names) {
        for (Integer ref : refs) {
            CollectFlow flow = getById(ref);
            if (flow == null || (flow.getCollectFlowIsDeleted() != null && flow.getCollectFlowIsDeleted() == 1)) {
                throw new IllegalArgumentException("子流程不存在或已删除: " + chain(stack, names) + " → 流程 " + ref);
            }
            names.putIfAbsent(ref, flow.getCollectFlowName());
            if (!stack.add(ref)) {
                throw new IllegalArgumentException("子流程引用成环: " + chain(stack, names) + " → " + names.get(ref));
            }
            walkSubflows(referencedFlows(dslOf(flow)), stack, names);
            stack.remove(ref);
        }
    }

    /**
     * 取流程图正文，缺失或解析不出来当作没有图。
     *
     * @param flow 流程实体
     * @return DSL，可能为 null
     */
    private CollectDslDTO dslOf(CollectFlow flow) {
        String dsl = flow.getCollectFlowDsl();
        return dsl == null || dsl.isBlank() ? null : CollectJson.parse(dsl, CollectDslDTO.class);
    }

    /**
     * 收集图里所有子流程节点引用的流程ID。
     *
     * @param dsl 流程图
     * @return 去重后的流程ID列表
     */
    private List<Integer> referencedFlows(CollectDslDTO dsl) {
        List<Integer> ids = new ArrayList<>();
        if (dsl == null || dsl.nodes() == null) {
            return ids;
        }
        for (CollectNodeDTO node : dsl.nodes()) {
            if (node == null || !CollectProcessExecutor.SHAPE.equals(node.shape()) || node.params() == null) {
                continue;
            }
            for (CollectParamDTO param : node.params()) {
                if (param == null || !CollectProcessExecutor.FLOW_ID.equals(param.name())
                        || param.value() == null || param.value().isBlank()) {
                    continue;
                }
                String raw = param.value().trim();
                try {
                    Integer target = Integer.valueOf(raw);
                    if (!ids.contains(target)) {
                        ids.add(target);
                    }
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("子流程节点的目标流程ID不是数字: " + raw);
                }
            }
        }
        return ids;
    }

    /**
     * 把引用链拼成"甲 → 乙"文案。
     *
     * @param stack 引用链上的流程ID
     * @param names 流程ID到名称
     * @return 链路说明
     */
    private String chain(LinkedHashSet<Integer> stack, Map<Integer, String> names) {
        StringBuilder builder = new StringBuilder();
        for (Integer item : stack) {
            if (builder.length() > 0) {
                builder.append(" → ");
            }
            builder.append(names.getOrDefault(item, "流程 " + item));
        }
        return builder.toString();
    }

    /**
     * 启用调度前的能力检查：当前部署必须真的带调度器。
     *
     * <p>没有 job-starter 时拒绝把流程置为"启用调度"——否则用户以为在定时跑、实际永远
     * 不触发，比直接报错更难排查。手动执行不受此限制。</p>
     */
    private void requireScheduling() {
        if (!schedulePort.schedulingAvailable()) {
            throw new IllegalArgumentException("当前部署未引入 job-starter，不支持定时调度；流程仍可手动执行");
        }
    }

    /**
     * 启用调度前的表达式检查：必须有表达式，且能算出下一个触发点。
     *
     * <p>表达式合法性由调度台账的登记动作用运行期同一套触发器再校验一次，
     * 这里只负责给出业务可读的提示。</p>
     *
     * @param flow 流程实体
     */
    private void requireSchedulable(CollectFlow flow) {
        String cron = flow.getCollectFlowCron();
        if (CollectCronSupport.isBlank(cron)) {
            throw new IllegalArgumentException("启用调度必须填写定时表达式");
        }
        CollectCronSupport.validate(cron);
        if (CollectCronSupport.nextList(cron, System.currentTimeMillis(), 1).length == 0) {
            throw new IllegalArgumentException("定时表达式没有后续的触发时间: " + cron);
        }
    }

    /**
     * 登记/更新流程对应的排期：按 jobKey 幂等写入 cron 与启停状态。
     *
     * <p>未填 cron 的流程登记成"只入台账、不参与自动派发"，这样"能不能定时跑"在台账里
     * 一眼可查，不需要再猜流程表的下次触发时间。没有 job-starter 时该动作由排期端口
     * 静默忽略。</p>
     *
     * @param flow 流程实体（已落库，含主键）
     */
    private void ensureJob(CollectFlow flow) {
        Integer flowId = flow.getCollectFlowId();
        if (flowId == null) {
            return;
        }
        String cron = flow.getCollectFlowCron() == null ? "" : flow.getCollectFlowCron().trim();
        boolean enabled = flow.getCollectFlowStatus() != null && flow.getCollectFlowStatus() == 1;
        if (enabled) {
            requireSchedulable(flow);
        }
        schedulePort.declare(CollectFlowScheduleSpec.of(flowId, flow.getCollectFlowName(), cron, enabled));
    }

    /**
     * 注销流程的调度行与执行器。
     *
     * @param flowId 流程ID
     */
    private void releaseJob(Integer flowId) {
        if (flowId == null) {
            return;
        }
        schedulePort.remove(CollectFlowScheduleKeys.jobKeyOf(flowId));
    }

    /**
     * 一次取回本组流程的调度运行态，供列表合并展示，避免逐行查库。
     *
     * @return 任务键到运行态快照的映射
     */
    private Map<String, CollectFlowScheduleState> jobRuntimes() {
        return schedulePort.listRuntimes();
    }

    /**
     * 启动对账：为存量流程补齐执行器与调度行。
     *
     * <p>调度行按 jobKey 幂等 upsert，所以重启不会重复产生任务；cron 以流程表为准，
     * 因为流程表是用户在采集平台上直接编辑的那一份。</p>
     *
     * @return 对账的流程数
     */
    public int restoreScheduling() {
        int restored = 0;
        for (CollectFlow flow : list(Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowIsDeleted, 0))) {
            try {
                ensureJob(flow);
                restored++;
            } catch (RuntimeException ex) {
                log.warn("[collect] 流程 {} 调度对账失败，该流程暂不参与自动派发: {}",
                        flow.getCollectFlowId(), ex.getMessage());
            }
        }
        return restored;
    }

    @Override
    public void run(ApplicationArguments args) {
        schedulePort.removeLegacyTick();
        if (!schedulePort.schedulingAvailable()) {
            log.info("[collect] 当前部署未引入 job-starter（或 plugin.job.enable=false），"
                    + "跳过排期对账，流程仅支持手动执行");
            return;
        }
        log.info("[collect] 调度对账完成: {} 个流程已登记到调度台账", restoreScheduling());
    }

    /**
     * 统计运行中的任务数。
     *
     * @param flowId 流程ID
     * @return 运行中任务数
     */
    private long runningTasks(Integer flowId) {
        return taskMapper.selectCount(Wrappers.<CollectTask>lambdaQuery()
                .eq(CollectTask::getCollectTaskFlowId, flowId)
                .eq(CollectTask::getCollectTaskStatus, CollectTaskStatus.RUNNING));
    }

    /**
     * 构造查询条件。
     *
     * @param condition 条件实体
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CollectFlow> buildWrapper(CollectFlow condition) {
        CollectFlow safe = condition == null ? new CollectFlow() : condition;
        return Wrappers.<CollectFlow>lambdaQuery()
                .like(notBlank(safe.getCollectFlowName()), CollectFlow::getCollectFlowName, safe.getCollectFlowName())
                .like(notBlank(safe.getCollectFlowCode()), CollectFlow::getCollectFlowCode, safe.getCollectFlowCode())
                .eq(safe.getCollectFlowCategoryId() != null,
                        CollectFlow::getCollectFlowCategoryId, safe.getCollectFlowCategoryId())
                .eq(safe.getCollectFlowStatus() != null, CollectFlow::getCollectFlowStatus, safe.getCollectFlowStatus())
                .orderByDesc(CollectFlow::getCollectFlowId);
    }

    /**
     * 未删除流程的通用条件。
     *
     * @param condition 条件实体
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CollectFlow> alive(CollectFlow condition) {
        return buildWrapper(condition).eq(CollectFlow::getCollectFlowIsDeleted, 0);
    }

    /**
     * 校验编码唯一。
     *
     * @param flow 待保存的流程
     */
    private void checkCodeUnique(CollectFlow flow) {
        long same = count(Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowCode, flow.getCollectFlowCode())
                .eq(CollectFlow::getCollectFlowIsDeleted, 0)
                .ne(flow.getCollectFlowId() != null, CollectFlow::getCollectFlowId, flow.getCollectFlowId()));
        if (same > 0) {
            throw new IllegalArgumentException("流程编码已存在: " + flow.getCollectFlowCode());
        }
    }

    /**
     * 分类ID到名称。
     *
     * @return 分类映射
     */
    private Map<Integer, String> categoryNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        names.put(UNCATEGORIZED, "未分类");
        for (CollectCategory category : categoryMapper.selectList(null)) {
            names.put(category.getCollectCategoryId(), category.getCollectCategoryName());
        }
        return names;
    }

    /**
     * 实体转出参。
     *
     * @param flow       流程实体
     * @param categories 分类映射
     * @param runtime    调度台账里的运行态快照，没有登记过时为空
     * @return 出参
     */
    private CollectFlowVO toVo(CollectFlow flow, Map<Integer, String> categories, CollectFlowScheduleState runtime) {
        LocalDateTime nextExec = null;
        if (runtime != null && runtime.nextFireMs() > 0) {
            nextExec = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(runtime.nextFireMs()), ZoneId.systemDefault());
        }
        LocalDateTime lastExec = flow.getCollectFlowLastExecTime();
        if (lastExec == null && runtime != null && runtime.lastFireMs() > 0) {
            lastExec = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(runtime.lastFireMs()), ZoneId.systemDefault());
        }
        return new CollectFlowVO(
                flow.getCollectFlowId(),
                flow.getCollectFlowName(),
                flow.getCollectFlowCategoryId(),
                categories.getOrDefault(
                        flow.getCollectFlowCategoryId() == null ? UNCATEGORIZED : flow.getCollectFlowCategoryId(), "未分类"),
                flow.getCollectFlowCode(),
                flow.getCollectFlowCron(),
                flow.getCollectFlowStatus(),
                flow.getCollectFlowConcurrency(),
                threadCountOf(flow),
                flow.getCollectFlowRemark(),
                lastExec,
                nextExec,
                flow.getCollectFlowExecCount(),
                flow.getCollectFlowRecordCount(),
                null,
                null,
                engine.isFlowRunning(flow.getCollectFlowId()),
                runtime != null && runtime.handlerRegistered(),
                null,
                flow.getCreateName(),
                flow.getCreateTime(),
                flow.getUpdateTime());
    }

    /**
     * 流程并行线程数，字段为空取平台默认。
     *
     * @param flow 流程
     * @return 线程数
     */
    private int threadCountOf(CollectFlow flow) {
        Integer count = flow.getCollectFlowThreadCount();
        return count == null || count <= 0 ? properties.getDefaultThreadCount() : count;
    }

    /**
     * 生成流程编码。
     *
     * @return 编码
     */
    private String newCode() {
        return "flow-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 校验必填文本与长度。
     *
     * @param text   文本
     * @param label  字段名
     * @param maxLen 最大长度
     * @return 规整后的文本
     */
    private String requireText(String text, String label, int maxLen) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value.length() > maxLen) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLen);
        }
        return value;
    }

    /**
     * 数值缺省与上限校验。
     *
     * @param value      提交值
     * @param fallback   缺省值
     * @param max        上限
     * @param label      字段名
     * @return 规整后的值
     */
    private int requirePositive(Integer value, int fallback, int max, String label) {
        int result = value == null || value <= 0 ? fallback : value;
        if (result > max) {
            throw new IllegalArgumentException(label + "不能超过 " + max);
        }
        return result;
    }

    /**
     * 文本是否非空。
     *
     * @param text 文本
     * @return 非空返回 true
     */
    private boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * 超长截断。
     *
     * @param text 文本
     * @param max  最大长度
     * @return 截断后的文本
     */
    private String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}