package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.collect.job.EduCollectJobHandler;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.CollectScriptSampleLoader;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.ImportProcessor;
import com.chua.starter.eduai.collect.support.PreProcessor;
import com.chua.starter.eduai.collect.support.QuestionSourceProviderRegistry;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduCollectLogMapper;
import com.chua.starter.eduai.mapper.EduCollectTaskMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.dto.CollectTaskSaveDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.CollectMetaVO;
import com.chua.starter.eduai.model.vo.CollectScriptSampleVO;
import com.chua.starter.eduai.model.vo.CollectTaskVO;
import com.chua.starter.eduai.model.vo.CollectTriggerVO;
import com.chua.starter.eduai.service.IEduCollectTaskService;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.operate.JobOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 题库采集任务服务实现：任务 CRUD、SPI 取题、去重入库、执行日志。
 *
 * <p>调度侧不再自建扫描循环：每个采集任务对应 sys_job 里的一条任务
 * (键 {@code edu-collect-<id>})，cron、启停、下次/上次派发时刻都由 job-starter 的
 * {@link JobOperator} 持有，到点由调度器派发给 {@link EduCollectJobHandler} 执行。
 * 本服务只在写入时同步登记/变更/摘除对应的调度任务。</p>
 *
 * <p>启动时以 {@link ApplicationRunner} 对账一次：为存量任务注册执行器，并补齐缺失的调度任务行
 * (调度表被清空或任务先于本模块建出时自愈)。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduCollectTaskServiceImpl extends ServiceImpl<EduCollectTaskMapper, EduCollectTask>
        implements IEduCollectTaskService, ApplicationRunner {

    /**
     * 学科兜底值
     */
    private static final String DEFAULT_SUBJECT = "综合";

    /**
     * 学段兜底值
     */
    private static final String DEFAULT_GRADE_LEVEL = "OTHER";

    /**
     * 年级兜底值
     */
    private static final String DEFAULT_GRADE = "其他";

    /**
     * 配置缺省时的最后兜底 cron (每 30 分钟)
     */
    private static final String FALLBACK_CRON = "0 0/30 * * * ?";

    /**
     * Groovy 脚本采集来源类型 (与 {@code GroovyQuestionSourceProvider} 的 SPI 名一致)
     */
    private static final String SOURCE_TYPE_GROOVY = "GROOVY";

    /**
     * 执行日志映射器
     */
    private final EduCollectLogMapper collectLogMapper;

    /**
     * 采集数据源注册表
     */
    private final QuestionSourceProviderRegistry providerRegistry;

    /**
     * 题目服务
     */
    private final IEduQuestionService questionService;

    /**
     * 教育 AI 配置
     */
    private final EduAiProperties properties;

    /**
     * 题目映射器
     */
    private final EduQuestionMapper questionMapper;

    /**
     * 任务治理接口 (cron / 启停 / 下次触发时刻的落库入口)
     */
    private final JobOperator jobOperator;

    /**
     * 执行中的任务，防并发重入
     */
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    @Override
    /**
     * Page查询
    */
    public PageResult<CollectTaskVO> pageQuery(CollectTaskQueryDTO query) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduCollectTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EduCollectTask> result = baseMapper.pageSelect(page, query, scope);
        Map<String, JobRuntime> runtimes = jobRuntimes();
        List<CollectTaskVO> rows = new ArrayList<>(result.getRecords().size());
        for (EduCollectTask task : result.getRecords()) {
            rows.add(toVo(task, runtimes.get(EduCollectJobHandler.jobKeyOf(task.getCollectTaskId()))));
        }
        return PageResult.<CollectTaskVO>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(rows)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 保存Task
     *
     * @param dto task入参
     * @return 保存task的结果
     */
    public Long saveTask(CollectTaskSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCollectTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (providerRegistry.find(dto.getCollectTaskSourceType()) == null) {
            throw new IllegalArgumentException("未注册的采集来源类型: " + dto.getCollectTaskSourceType()
                    + "，可用: " + providerRegistry.availableTypes());
        }
        EduCollectTask task = new EduCollectTask();
        task.setCollectTaskId(dto.getCollectTaskId());
        task.setCollectTaskName(dto.getCollectTaskName().trim());
        task.setCollectTaskSourceType(dto.getCollectTaskSourceType().trim());
        task.setCollectTaskSourceConfig(dto.getCollectTaskSourceConfig());
        task.setCollectTaskSubject(dto.getCollectTaskSubject());
        task.setCollectTaskGradeLevel(dto.getCollectTaskGradeLevel());
        task.setCollectTaskGrade(dto.getCollectTaskGrade());
        task.setCollectTaskDifficulty(dto.getCollectTaskDifficulty());
        task.setCollectTaskStatus(dto.getCollectTaskStatus());
        task.setCollectTaskRemark(dto.getCollectTaskRemark());
        if (hasGroovySource(task) && !hasScriptSource(task)) {
            throw new IllegalArgumentException(
                    "GROOVY 任务需要在来源配置中提供 script（内联脚本）或 scriptPath（服务器脚本文件）");
        }
        if (task.getCollectTaskStatus() == null) {
            task.setCollectTaskStatus(1);
        }
        if (task.getCollectTaskDifficulty() == null) {
            task.setCollectTaskDifficulty(3);
        }
        if (task.getCollectTaskId() == null) {
            task.setOwnerUserId(EduUserContext.requireUserId());
            task.setCollectTaskTotalCount(0);
            save(task);
        } else {
            EduCollectTask exists = getById(task.getCollectTaskId());
            if (exists == null) {
                throw new IllegalArgumentException("采集任务不存在: " + task.getCollectTaskId());
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            task.setOwnerUserId(exists.getOwnerUserId());
            task.setCollectTaskTotalCount(exists.getCollectTaskTotalCount());
            task.setCollectTaskLastRunTime(exists.getCollectTaskLastRunTime());
            task.setCollectTaskLastRunStatus(exists.getCollectTaskLastRunStatus());
            task.setCollectTaskLastRunCount(exists.getCollectTaskLastRunCount());
            updateById(task);
        }
        ensureJob(task, cronOrDefault(dto.getCron()));
        return task.getCollectTaskId();
    }

    @Override
    /**
     * 删除Task
     *
     * @param id id
     * @return 是否成
     */
    public boolean deleteTask(Long id) {
        EduCollectTask task = getById(id);
        if (task == null) {
            return false;
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        String jobKey = EduCollectJobHandler.jobKeyOf(id);
        boolean removed = removeById(id);
        jobOperator.remove(jobKey);
        JobHandlerFactory.getInstance().deregister(jobKey);
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更改状态
     *
     * @param id id
     * @param status status
     * @return 是否成
     */
    public boolean changeStatus(Long id, Integer status) {
        EduCollectTask task = getById(id);
        if (task == null) {
            throw new IllegalArgumentException("采集任务不存在: " + id);
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        boolean enabled = status != null && status == 1;
        task.setCollectTaskStatus(enabled ? 1 : 0);
        boolean updated = updateById(task);
        String jobKey = EduCollectJobHandler.jobKeyOf(id);
        if (!jobOperator.changeStatus(jobKey, enabled)) {
            // 调度行缺失 (建任务时登记失败或被外部删除) 时按当前配置补登记
            ensureJob(task, cronOrDefault(null));
        }
        return updated;
    }

    @Override
    /**
     * 触发器
     *
     * @param id id
     * @return 结果
     */
    public CollectTriggerVO trigger(Long id) {
        EduCollectTask task = getById(id);
        if (task == null) {
            return new CollectTriggerVO("fail", "采集任务不存在");
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        if (task.getCollectTaskStatus() == null || task.getCollectTaskStatus() != 1) {
            return new CollectTriggerVO("skip", "任务未启用");
        }
        if (runningTaskIds.contains(id)) {
            return new CollectTriggerVO("skip", "任务正在执行中");
        }
        return runTask(task, "MANUAL");
    }

    @Override
    /**
     * 调度触发的采集执行入口
     *
     * @param id id
     */
    public void runScheduled(Long id) {
        EduCollectTask task = getById(id);
        if (task == null) {
            log.info("[collect] 调度派发时任务已删除, 跳过: task={}", id);
            return;
        }
        if (task.getCollectTaskStatus() == null || task.getCollectTaskStatus() != 1) {
            log.info("[collect] 调度派发时任务已停用, 跳过: task={}", id);
            return;
        }
        runTask(task, "AUTO");
    }

    @Override
    /**
     * Page日志
     *
     * @param taskId task标识
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<EduCollectLog> pageLogs(Long taskId, Integer pageNum, Integer pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduCollectLog> page = new Page<>(pageNum == null ? 1 : pageNum,
                pageSize == null ? 10 : pageSize);
        IPage<EduCollectLog> result = collectLogMapper.pageSelect(page, taskId, scope);
        return PageResult.<EduCollectLog>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    /**
     * 采集页元数据
     *
     * @return 结果
     */
    public CollectMetaVO collectMeta() {
        CollectMetaVO meta = new CollectMetaVO();
        meta.setServerTime(System.currentTimeMillis());
        meta.setDefaultCron(defaultCron());
        return meta;
    }

    @Override
    /**
     * 调度对账
     *
     * @return 条数
     */
    public int restoreScheduling() {
        int restored = 0;
        for (EduCollectTask task : list()) {
            try {
                // 已有调度行时沿用其 cron, 只把启停状态按业务表纠正, 避免对账把用户改过的时间刷回默认值
                JobRuntime runtime = jobOperator.runtime(EduCollectJobHandler.jobKeyOf(task.getCollectTaskId()));
                ensureJob(task, cronOrDefault(runtime == null ? null : runtime.getScheduleTime()));
                restored++;
            } catch (Exception ex) {
                log.warn("[collect] 任务调度对账失败, 该任务暂不参与自动派发: task={} cause={}",
                        task.getCollectTaskId(), ex.getMessage());
            }
        }
        return restored;
    }

    @Override
    /**
     * 应用启动后执行
     *
     * @param args args
     */
    public void run(ApplicationArguments args) {
        int restored = restoreScheduling();
        log.info("[collect] 采集调度对账完成: 任务 {} 个已登记到 job-starter", restored);
    }

    @Override
    /**
     * 可用来源类型
     *
     * @return 列表结果
     */
    public List<String> availableSourceTypes() {
        return new ArrayList<>(providerRegistry.availableTypes());
    }

    @Override
    /**
     * 内置采集脚本样例
     *
     * @return 列表结果
     */
    public List<CollectScriptSampleVO> scriptSamples() {
        return CollectScriptSampleLoader.samples();
    }

    private CollectTriggerVO runTask(EduCollectTask task, String triggerType) {
        Long taskId = task.getCollectTaskId();
        if (!runningTaskIds.add(taskId)) {
            return new CollectTriggerVO("skip", "任务正在执行中");
        }
        EduCollectLog logRow = new EduCollectLog();
        logRow.setCollectLogTaskId(taskId);
        logRow.setCollectLogTaskName(task.getCollectTaskName());
        logRow.setCollectLogTriggerType(triggerType);
        logRow.setCollectLogSourceType(task.getCollectTaskSourceType());
        logRow.setOwnerUserId(task.getOwnerUserId());
        logRow.setCollectLogStatus(0);
        logRow.setCollectLogQuestionCount(0);
        logRow.setCollectLogStartTime(LocalDateTime.now());
        collectLogMapper.insert(logRow);
        int saved = 0;
        boolean success = false;
        String message;
        try {
            saved = doCollect(task);
            logRow.setCollectLogStatus(1);
            logRow.setCollectLogQuestionCount(saved);
            success = true;
            message = "新增 " + saved + " 条（重复自动跳过）";
        } catch (Exception e) {
            log.warn("采集任务执行失败: task={} type={}", taskId, task.getCollectTaskSourceType(), e);
            logRow.setCollectLogStatus(2);
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logRow.setCollectLogErrorMsg(error.length() > 1900 ? error.substring(0, 1900) : error);
            message = "采集失败: " + (error.length() > 120 ? error.substring(0, 120) : error);
        } finally {
            logRow.setCollectLogEndTime(LocalDateTime.now());
            collectLogMapper.updateById(logRow);
            runningTaskIds.remove(taskId);
        }
        // 只回写本次运行产生的计量列，避免用执行前加载的旧实体覆盖用户在运行期间的启用/定时改动
        baseMapper.writeBackRunMetrics(taskId, logRow.getCollectLogStartTime(),
                success ? 1 : 0, saved, saved);
        return new CollectTriggerVO(success ? "success" : "fail", message);
    }

    private int doCollect(EduCollectTask task) {
        QuestionSourceProvider provider = providerRegistry.find(task.getCollectTaskSourceType());
        if (provider == null && StringUtils.hasText(task.getCollectTaskSourceConfig())) {
            // 兼容：早期「成语词库(IDIOM)/数据表入库(DB)」等自落库来源已并入统一管线（TEXT/URL/API/FILE/GROOVY + 前置/入库处理器），
            // 旧任务类型保留但按通用取数（HTTP/文件/文本）处理，避免报「未注册的采集来源类型」。
            provider = providerRegistry.find("API");
        }
        if (provider == null) {
            throw new IllegalStateException("未注册的采集来源类型: " + task.getCollectTaskSourceType());
        }
        QuestionSourceContext context = QuestionSourceContext.builder()
                .taskId(task.getCollectTaskId())
                .taskName(task.getCollectTaskName())
                .sourceType(task.getCollectTaskSourceType())
                .sourceConfig(task.getCollectTaskSourceConfig())
                .subject(task.getCollectTaskSubject())
                .gradeLevel(task.getCollectTaskGradeLevel())
                .grade(task.getCollectTaskGrade())
                .difficulty(task.getCollectTaskDifficulty())
                .ownerUserId(task.getOwnerUserId())
                .build();
        // 统一入库管线：配置了「入库处理器」的来源，一律走 前置处理器 → 入库处理器
        String configJson = task.getCollectTaskSourceConfig();
        String importJson = SourceConfigReader.json(configJson, "importProcessor");
        if (StringUtils.hasText(importJson)) {
            return runImportPipeline(task, provider, context, configJson, importJson);
        }
        // 自落库型来源（成语等非题目数据）：Provider 自己写库并返回条数
        if (provider.selfPersisted()) {
            return provider.persist(context);
        }
        List<CollectedQuestion> collected = provider.fetch(context);
        int saved = 0;
        for (CollectedQuestion item : collected) {
            if (item == null || !StringUtils.hasText(item.getStem())) {
                continue;
            }
            String subject = firstText(item.getSubject(), task.getCollectTaskSubject(), DEFAULT_SUBJECT);
            if (existsSameQuestion(item.getStem().trim(), subject, task.getOwnerUserId())) {
                continue;
            }
            questionService.saveQuestion(toSaveDto(task, item, subject));
            saved++;
        }
        return saved;
    }

    /**
     * 统一入库管线：取原始数据 → 前置处理器（jsonpath/groovy）转成一行一个 → 入库处理器（表/唯一键/冲突策略/字段映射）写入。
     *
     * @param task       采集任务
     * @param provider   来源 Provider
     * @param context    采集上下文
     * @param configJson 来源配置 JSON
     * @param importJson 入库处理器配置 JSON
     * @return 写入条数
     */
    private int runImportPipeline(EduCollectTask task, QuestionSourceProvider provider,
                                  QuestionSourceContext context, String configJson, String importJson) {
        String preJson = SourceConfigReader.json(configJson, "preProcessor");
        Object raw = provider.fetchRaw(context);
        if (raw == null) {
            // 1) 本地文件（FILE 来源：服务器上的 JSON 文件）
            String filePath = SourceConfigReader.text(configJson, "path");
            if (StringUtils.hasText(filePath) && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(filePath.trim()))) {
                try {
                    raw = java.nio.file.Files.readString(java.nio.file.Path.of(filePath.trim()));
                } catch (IOException e) {
                    throw new IllegalStateException("本地文件读取失败: " + e.getMessage(), e);
                }
            }
            // 2) 粘贴文本（TEXT 来源：直接作为原始数据）
            if (raw == null) {
                String text = SourceConfigReader.text(configJson, "text");
                if (StringUtils.hasText(text)) {
                    raw = text;
                }
            }
            // 3) HTTP 地址（URL/API 来源）
            if (raw == null) {
                String rawUrl = firstText(
                        SourceConfigReader.text(configJson, "url"),
                        SourceConfigReader.text(configJson, "apiUrl"));
                if (StringUtils.hasText(rawUrl)) {
                    raw = fetchRawWithFallback(configJson, rawUrl.trim());
                }
            }
            // 4) 兜底：Provider 产出的题目列表转行（如 GROOVY 脚本）
            if (raw == null) {
                List<CollectedQuestion> questions = provider.fetch(context);
                if (questions != null && !questions.isEmpty()) {
                    raw = questions.stream().map(this::questionToMap).toList();
                }
            }
        }
        if (raw == null) {
            throw new IllegalStateException("该来源没有可取数据：请在配置里填写 url/apiUrl，或改用支持 JSON 的来源");
        }
        String mappingJson = SourceConfigReader.json(preJson, "mapping");
        List<Map<String, Object>> rows;
        if (StringUtils.hasText(mappingJson) && !mappingJson.trim().equals("{}")) {
            // 前置处理器 = 字段映射：key=JSONPath，value=输出给入库的字段名（一行一个）
            rows = PreProcessor.toRowsByMapping(mappingJson, raw);
        } else {
            String mode = firstText(SourceConfigReader.text(preJson, "type"), "jsonpath");
            rows = PreProcessor.toRows(
                    mode,
                    SourceConfigReader.text(preJson, "expr"),
                    SourceConfigReader.text(preJson, "script"),
                    null,
                    raw);
        }
        if (rows.isEmpty()) {
            // 粘贴纯文本兜底：raw 是「非 JSON 形状」的文本时，PreProcessor 的 JSONPath 解析不出任何行，
            // 此时改走 Provider 自身的三级提取（JSON 直解 → AI 提取 → 正则兜底），把提取出的题目转成行。
            // 典型场景：来源类型 TEXT，用户直接粘贴「1. 题干 / A. 选项 / 答案：X」这类纯文本。
            List<CollectedQuestion> extracted = provider.fetch(context);
            if (extracted != null && !extracted.isEmpty()) {
                rows = extracted.stream().map(this::questionToMap).toList();
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("前置处理器没有得到任何行，请检查 JSONPath/脚本表达式");
        }
        // 单次入库条数上限（importProcessor.limit 优先，其次顶层 limit，默认 100）
        String limitText = firstText(SourceConfigReader.text(importJson, "limit"), SourceConfigReader.text(configJson, "limit"));
        int limit = 100;
        if (StringUtils.hasText(limitText)) {
            try {
                limit = Math.max(1, Integer.parseInt(limitText.trim()));
            } catch (NumberFormatException ignored) {
                limit = 100;
            }
        }
        // 起始偏移（顶层 offset）
        int offset = 0;
        String offsetText = SourceConfigReader.text(configJson, "offset");
        if (StringUtils.hasText(offsetText)) {
            try {
                offset = Math.max(0, Integer.parseInt(offsetText.trim()));
            } catch (NumberFormatException ignored) {
                offset = 0;
            }
        }
        // 分块连续扫描：已存在被跳过时继续向后取，直到本次写入达到 limit 或数据取尽
        int changed = 0;
        int cursor = Math.min(offset, rows.size());
        while (cursor < rows.size() && changed < limit) {
            int end = Math.min(rows.size(), cursor + limit);
            changed += ImportProcessor.save(importJson, rows.subList(cursor, end), task.getOwnerUserId(),
                    task.getCollectTaskGradeLevel(), task.getCollectTaskGrade());
            cursor = end;
        }
        return changed;
    }

    /**
     * 题目对象转行数据（供前置处理器做 JSONPath 映射后入库）。
     *
     * @param question 采到的题目
     * @return 行 Map（key 与题目字段同名）
     */
    private Map<String, Object> questionToMap(CollectedQuestion question) {
        Map<String, Object> row = new HashMap<>();
        row.put("questionType", question.getQuestionType());
        row.put("stem", question.getStem());
        row.put("optionsJson", question.getOptionsJson());
        row.put("answer", question.getAnswer());
        row.put("analysis", question.getAnalysis());
        row.put("subject", question.getSubject());
        row.put("gradeLevel", question.getGradeLevel());
        row.put("grade", question.getGrade());
        row.put("difficulty", question.getDifficulty());
        row.put("source", question.getSource());
        return row;
    }

    /**
     * 拉取原始数据：主地址失败时依次尝试 fallbackUrls；未配置且是公开词库时用内置镜像。
     *
     * @param configJson 来源配置
     * @param rawUrl     主地址
     * @return 原始 JSON 文本
     */
    private Object fetchRawWithFallback(String configJson, String rawUrl) {
        java.util.List<String> candidates = new ArrayList<>();
        candidates.add(rawUrl);
        String fallback = SourceConfigReader.text(configJson, "fallbackUrls");
        if (StringUtils.hasText(fallback)) {
            for (String part : fallback.split(",")) {
                String candidate = part.trim();
                if (!candidate.isEmpty() && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        } else if (rawUrl.contains("chinese-xinhua")) {
            for (String mirror : new String[]{
                    "https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
                    "https://fastly.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
                    "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json",
                    "https://ghproxy.net/https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json"}) {
                if (!candidates.contains(mirror)) {
                    candidates.add(mirror);
                }
            }
        }
        IOException last = null;
        for (String candidate : candidates) {
            try {
                return HttpFetchSupport.get(candidate);
            } catch (IOException e) {
                last = e;
                log.warn("[collect] 数据源不可用，尝试下一个: {} 原因: {}", candidate, describe(e));
            }
        }
        throw new IllegalStateException("数据源拉取失败: " + (last == null ? rawUrl : describe(last)), last);
    }

    /**
     * 描述一次拉取失败的原因。{@link java.net.ConnectException} 等异常自带的
     * {@code getMessage()} 常为 null，直接拼进提示会显示成「原因: null」，
     * 这里在消息缺失时退回异常类名，保证日志与接口提示可读。
     *
     * @param e 拉取异常
     * @return 可读的原因描述
     */
    private String describe(IOException e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    private boolean existsSameQuestion(String stem, String subject, String owner) {
        return questionMapper.countSameQuestion(stem, subject, owner) > 0;
    }

    private QuestionSaveDTO toSaveDto(EduCollectTask task, CollectedQuestion item, String subject) {
        QuestionSaveDTO dto = new QuestionSaveDTO();
        dto.setQuestionType(firstText(item.getQuestionType(), "SHORT_ANSWER"));
        dto.setQuestionSubject(subject);
        dto.setQuestionGradeLevel(firstText(item.getGradeLevel(), task.getCollectTaskGradeLevel(), DEFAULT_GRADE_LEVEL));
        dto.setQuestionGrade(firstText(item.getGrade(), task.getCollectTaskGrade(), DEFAULT_GRADE));
        dto.setQuestionDifficulty(item.getDifficulty() != null ? item.getDifficulty() : task.getCollectTaskDifficulty());
        dto.setQuestionStem(item.getStem().trim());
        dto.setQuestionOptions(item.getOptionsJson());
        dto.setQuestionAnswer(item.getAnswer() == null ? "" : item.getAnswer());
        dto.setQuestionAnalysis(item.getAnalysis());
        dto.setQuestionSource(firstText(item.getSource(), task.getCollectTaskName()));
        dto.setQuestionSourceType("CRAWL");
        dto.setQuestionAuditStatus(0);
        dto.setOwnerUserId(task.getOwnerUserId());
        return dto;
    }

    /**
     * 取本次写入使用的 cron：请求未填时回落到配置默认值。
     *
     * @param cron 请求里的 cron, 可为空
     * @return 生效的 cron
     */
    private String cronOrDefault(String cron) {
        return StringUtils.hasText(cron) ? cron.trim() : defaultCron();
    }

    /**
     * 配置默认 cron, 未配置时用固定兜底周期。
     *
     * @return 默认 cron
     */
    private String defaultCron() {
        String configured = properties.getCollect().getDefaultCron();
        return StringUtils.hasText(configured) ? configured.trim() : FALLBACK_CRON;
    }

    /**
     * 是否 Groovy 脚本来源 (需要在来源配置里带 script/scriptPath)。
     *
     * @param task task
     * @return 结果
     */
    private boolean hasGroovySource(EduCollectTask task) {
        return SOURCE_TYPE_GROOVY.equalsIgnoreCase(task.getCollectTaskSourceType());
    }

    /**
     * 登记/更新任务对应的调度行：注册本节点执行器, 再按 jobKey 幂等写入 cron 与启停状态。
     *
     * <p>cron 合法性由 {@link JobOperator#declare} 用运行期同一套触发器校验, 校验不过直接抛
     * {@link IllegalArgumentException}, 由调用方的事务回滚, 因此不会留下"有任务无调度"的半截状态。</p>
     *
     * @param task 采集任务 (已落库, 含主键)
     * @param cron 生效的 cron
     */
    private void ensureJob(EduCollectTask task, String cron) {
        String jobKey = EduCollectJobHandler.jobKeyOf(task.getCollectTaskId());
        JobHandlerFactory.getInstance().register(new EduCollectJobHandler(
                task.getCollectTaskId(), task.getCollectTaskName(), this));
        JobSpec spec = new JobSpec();
        spec.setJobKey(jobKey);
        spec.setJobNo(jobKey);
        spec.setJobName(task.getCollectTaskName());
        spec.setDesc("题库采集任务: " + task.getCollectTaskName());
        spec.setGroup(EduCollectJobHandler.JOB_GROUP);
        spec.setScheduleType(JobSpec.TYPE_CRON);
        spec.setScheduleTime(cron);
        spec.setEnabled(task.getCollectTaskStatus() != null && task.getCollectTaskStatus() == 1);
        spec.setExecuteParam(String.valueOf(task.getCollectTaskId()));
        jobOperator.declare(spec);
    }

    /**
     * 一次取回本组任务的调度运行态, 供分页列表合并展示, 避免逐行查库。
     *
     * @return 任务键到运行态快照的映射
     */
    private Map<String, JobRuntime> jobRuntimes() {
        Map<String, JobRuntime> runtimes = new HashMap<>();
        for (JobRuntime runtime : jobOperator.list(EduCollectJobHandler.JOB_GROUP)) {
            runtimes.put(runtime.getJobKey(), runtime);
        }
        return runtimes;
    }

    /**
     * 业务列 + 调度态合并成列表出参。
     *
     * @param task 采集任务
     * @param runtime 调度运行态, 无调度行时为空
     * @return 出参
     */
    private CollectTaskVO toVo(EduCollectTask task, JobRuntime runtime) {
        CollectTaskVO vo = new CollectTaskVO();
        vo.setCollectTaskId(task.getCollectTaskId());
        vo.setCollectTaskName(task.getCollectTaskName());
        vo.setCollectTaskSourceType(task.getCollectTaskSourceType());
        vo.setCollectTaskSourceConfig(task.getCollectTaskSourceConfig());
        vo.setCollectTaskSubject(task.getCollectTaskSubject());
        vo.setCollectTaskGradeLevel(task.getCollectTaskGradeLevel());
        vo.setCollectTaskGrade(task.getCollectTaskGrade());
        vo.setCollectTaskDifficulty(task.getCollectTaskDifficulty());
        vo.setCollectTaskStatus(task.getCollectTaskStatus());
        vo.setCollectTaskRemark(task.getCollectTaskRemark());
        vo.setCollectTaskTotalCount(task.getCollectTaskTotalCount());
        vo.setCollectTaskLastRunTime(task.getCollectTaskLastRunTime());
        vo.setCollectTaskLastRunStatus(task.getCollectTaskLastRunStatus());
        vo.setCollectTaskLastRunCount(task.getCollectTaskLastRunCount());
        vo.setJobKey(EduCollectJobHandler.jobKeyOf(task.getCollectTaskId()));
        if (runtime != null) {
            vo.setCollectTaskStatus(runtime.isEnabled() ? 1 : 0);
            vo.setCron(runtime.getScheduleTime());
            vo.setLastFireTime(runtime.getLastFireMs() > 0L ? runtime.getLastFireMs() : null);
            vo.setNextFireTime(runtime.getNextFireMs() > 0L ? runtime.getNextFireMs() : null);
            vo.setHandlerRegistered(runtime.isHandlerRegistered());
        }
        return vo;
    }

    private boolean hasScriptSource(EduCollectTask task) {
        String script = SourceConfigReader.text(task.getCollectTaskSourceConfig(), "script");
        String path = SourceConfigReader.text(task.getCollectTaskSourceConfig(), "scriptPath");
        return (script != null && !script.isBlank()) || (path != null && !path.isBlank());
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
