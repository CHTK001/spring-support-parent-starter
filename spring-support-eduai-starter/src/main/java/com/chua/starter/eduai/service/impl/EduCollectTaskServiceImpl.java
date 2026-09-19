package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.QuestionSourceProviderRegistry;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduCollectLogMapper;
import com.chua.starter.eduai.mapper.EduCollectTaskMapper;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.CollectScheduleOverviewVO;
import com.chua.starter.eduai.model.vo.CollectScheduleTaskVO;
import com.chua.starter.eduai.model.vo.CollectTriggerVO;
import com.chua.starter.eduai.service.IEduCollectTaskService;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 题库采集任务服务实现：任务 CRUD、SPI 取题、去重入库、执行日志与到期调度。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduCollectTaskServiceImpl extends ServiceImpl<EduCollectTaskMapper, EduCollectTask>
        implements IEduCollectTaskService {

    /**
     * 调度任务键（注册于 sys_job）
     */
    public static final String DISPATCH_JOB_KEY = "edu-collect-dispatch";

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
     * 执行中的任务，防并发重入
     */
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    @Override
    /**
     * Page查询
    */
    public PageResult<EduCollectTask> pageQuery(CollectTaskQueryDTO query) {
        LambdaQueryWrapper<EduCollectTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTaskName())) {
            wrapper.like(EduCollectTask::getCollectTaskName, query.getTaskName());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(EduCollectTask::getCollectTaskSourceType, query.getSourceType());
        }
        if (StringUtils.hasText(query.getSubject())) {
            wrapper.eq(EduCollectTask::getCollectTaskSubject, query.getSubject());
        }
        if (query.getStatus() != null) {
            wrapper.eq(EduCollectTask::getCollectTaskStatus, query.getStatus());
        }
        EduUserContext.scopeOwned(wrapper, EduCollectTask::getOwnerUserId);
        wrapper.orderByDesc(EduCollectTask::getCollectTaskId);
        Page<EduCollectTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EduCollectTask> result = baseMapper.selectPage(page, wrapper);
        return PageResult.<EduCollectTask>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    /**
     * 保存Task
     *
     * @param task task
     * @return 保存task的结果
     */
    public Long saveTask(EduCollectTask task) {
        if (!StringUtils.hasText(task.getCollectTaskName())) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (providerRegistry.find(task.getCollectTaskSourceType()) == null) {
            throw new IllegalArgumentException("未注册的采集来源类型: " + task.getCollectTaskSourceType()
                    + "，可用: " + providerRegistry.availableTypes());
        }
        String cron = task.getCollectTaskCron() == null ? "" : task.getCollectTaskCron().trim();
        if (!cron.isEmpty() && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("非法 Cron 表达式: " + cron);
        }
        task.setCollectTaskCron(cron.isEmpty() ? null : cron);
        if (task.getCollectTaskStatus() == null) {
            task.setCollectTaskStatus(1);
        }
        if (task.getCollectTaskDifficulty() == null) {
            task.setCollectTaskDifficulty(3);
        }
        if (task.getCollectTaskId() == null) {
            task.setOwnerUserId(EduUserContext.requireUserId());
            task.setCollectTaskTotalCount(0);
            task.setCollectTaskNextRunTime(task.getCollectTaskStatus() == 1
                    ? nextRunTime(effectiveCron(task)) : null);
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
            task.setCollectTaskNextRunTime(task.getCollectTaskStatus() == 1
                    ? nextRunTime(effectiveCron(task)) : null);
            updateById(task);
        }
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
        return removeById(id);
    }

    @Override
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
        task.setCollectTaskStatus(status != null && status == 1 ? 1 : 0);
        task.setCollectTaskNextRunTime(task.getCollectTaskStatus() == 1
                ? nextRunTime(effectiveCron(task)) : null);
        return updateById(task);
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
     * Page日志
     *
     * @param taskId task标识
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<EduCollectLog> pageLogs(Long taskId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<EduCollectLog> wrapper = new LambdaQueryWrapper<EduCollectLog>()
                .orderByDesc(EduCollectLog::getCollectLogId);
        if (taskId != null) {
            wrapper.eq(EduCollectLog::getCollectLogTaskId, taskId);
        }
        EduUserContext.scopeOwned(wrapper, EduCollectLog::getOwnerUserId);
        Page<EduCollectLog> page = new Page<>(pageNum == null ? 1 : pageNum,
                pageSize == null ? 10 : pageSize);
        IPage<EduCollectLog> result = collectLogMapper.selectPage(page, wrapper);
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
     * 调度器概览
     *
     * @return 结果
     */
    public CollectScheduleOverviewVO scheduleOverview() {
        CollectScheduleOverviewVO overview = new CollectScheduleOverviewVO();
        overview.setServerTime(LocalDateTime.now());
        overview.setDispatchJobKey(DISPATCH_JOB_KEY);
        overview.setDefaultCron(effectiveCronFromTask(null));
        overview.setSourceTypes(new ArrayList<>(providerRegistry.availableTypes()));
        List<CollectScheduleTaskVO> rows = new ArrayList<>();
        LambdaQueryWrapper<EduCollectTask> listWrapper = new LambdaQueryWrapper<EduCollectTask>()
                .orderByDesc(EduCollectTask::getCollectTaskStatus)
                .orderByAsc(EduCollectTask::getCollectTaskNextRunTime);
        EduUserContext.scopeOwned(listWrapper, EduCollectTask::getOwnerUserId);
        for (EduCollectTask task : list(listWrapper)) {
            CollectScheduleTaskVO row = new CollectScheduleTaskVO();
            row.setCollectTaskId(task.getCollectTaskId());
            row.setCollectTaskName(task.getCollectTaskName());
            row.setCollectTaskSourceType(task.getCollectTaskSourceType());
            row.setCollectTaskCron(task.getCollectTaskCron());
            row.setEffectiveCron(effectiveCron(task));
            row.setCollectTaskStatus(task.getCollectTaskStatus());
            row.setCollectTaskNextRunTime(task.getCollectTaskNextRunTime());
            row.setCollectTaskLastRunTime(task.getCollectTaskLastRunTime());
            row.setCollectTaskLastRunStatus(task.getCollectTaskLastRunStatus());
            row.setCollectTaskLastRunCount(task.getCollectTaskLastRunCount());
            row.setCollectTaskTotalCount(task.getCollectTaskTotalCount());
            rows.add(row);
        }
        overview.setTasks(rows);
        return overview;
    }

    @Override
    /**
     * 派发到期任务
     */
    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<EduCollectTask> dueTasks = list(new LambdaQueryWrapper<EduCollectTask>()
                .eq(EduCollectTask::getCollectTaskStatus, 1)
                .and(w -> w.isNull(EduCollectTask::getCollectTaskNextRunTime)
                        .or().le(EduCollectTask::getCollectTaskNextRunTime, now)));
        for (EduCollectTask task : dueTasks) {
            if (task.getCollectTaskNextRunTime() == null) {
                // 启用后首次补偿：只排期不执行，避免保存链路异常时立即风暴
                task.setCollectTaskNextRunTime(nextRunTime(effectiveCron(task)));
                updateById(task);
                continue;
            }
            runTask(task, "AUTO");
        }
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
            message = "新增 " + saved + " 题（重复题自动跳过）";
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
        task.setCollectTaskLastRunTime(logRow.getCollectLogStartTime());
        task.setCollectTaskLastRunStatus(success ? 1 : 0);
        task.setCollectTaskLastRunCount(saved);
        task.setCollectTaskTotalCount((task.getCollectTaskTotalCount() == null ? 0 : task.getCollectTaskTotalCount()) + saved);
        task.setCollectTaskNextRunTime(nextRunTime(effectiveCron(task)));
        updateById(task);
        return new CollectTriggerVO(success ? "success" : "fail", message);
    }

    private int doCollect(EduCollectTask task) {
        QuestionSourceProvider provider = providerRegistry.find(task.getCollectTaskSourceType());
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
                .build();
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

    private boolean existsSameQuestion(String stem, String subject, String owner) {
        return questionService.count(new LambdaQueryWrapper<EduQuestion>()
                .eq(EduQuestion::getQuestionStem, stem)
                .eq(EduQuestion::getQuestionSubject, subject)
                .eq(StringUtils.hasText(owner), EduQuestion::getOwnerUserId, owner)) > 0;
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
        dto.setQuestionSource(item.getSource());
        dto.setQuestionSourceType("CRAWL");
        dto.setQuestionAuditStatus(0);
        dto.setOwnerUserId(task.getOwnerUserId());
        return dto;
    }

    private String effectiveCron(EduCollectTask task) {
        return effectiveCronFromTask(task.getCollectTaskCron());
    }

    private String effectiveCronFromTask(String cron) {
        if (StringUtils.hasText(cron)) {
            return cron.trim();
        }
        String defaultCron = properties.getCollect().getDefaultCron();
        return StringUtils.hasText(defaultCron) ? defaultCron.trim() : "0 0/30 * * * ?";
    }

    private LocalDateTime nextRunTime(String cron) {
        try {
            return CronExpression.parse(cron).next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Cron 解析失败, 回退默认周期: cron={}", cron);
            return CronExpression.parse("0 0/30 * * * ?").next(LocalDateTime.now());
        }
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
