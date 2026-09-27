package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.job.support.log.JobLogQuery;
import com.chua.starter.job.support.log.JobLogRecord;
import com.chua.starter.job.support.log.JobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MyBatis-Plus 版 {@link JobLogRepository}, 从 {@code sys_job_log} 读执行日志。
 *
 * <p>{@code sys_job_log} 没有逻辑删除外的可见性约定, 查询一律带 {@code sys_job_log_is_deleted=0};
 * 清理是物理删除, 日志表只增不改, 留着没有回收价值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class MybatisJobLogRepository implements JobLogRepository {

    /**
     * 一天的毫秒数
     */
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /**
     * 单页最大条数, 防止页面把整张日志表拉回去
     */
    private static final int MAX_PAGE_SIZE = 500;

    /**
     * 按台账任务名反查时最多带回的编号数, 关键字太短时不至于把整张日志表 OR 进去
     */
    private static final int LEDGER_HIT_LIMIT = 200;

    /**
     * 日志映射器
     */
    private final JobLogMapper jobLogMapper;

    /**
     * 任务台账映射器, 用于「按台账里的任务名搜日志」; 缺失时关键字只命中日志自身列
     */
    private final JobMapper jobMapper;

    @Override
    public PageResult<JobLogRecord> page(JobLogQuery query) {
        JobLogQuery condition = query == null ? new JobLogQuery() : query;
        int pageNum = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        int pageSize = normalizePageSize(condition.getPageSize());
        Page<JobLogEntity> page = new Page<>(pageNum, pageSize);
        // 排序挂在分页对象上, 不放进条件包装器: 包装器里的 ORDER BY 会被 MyBatis-Plus
        // 原样带进自动生成的 count 语句, 让 count 也背上排序 —— 部分数据库对此直接报错,
        // 且属于无谓开销(count 不需要顺序)。放在分页对象上只影响数据页查询。
        page.addOrder(OrderItem.desc("sys_job_log_trigger_time"), OrderItem.desc("sys_job_log_id"));
        IPage<JobLogEntity> result = jobLogMapper.selectPage(page, filter(condition));
        List<JobLogRecord> rows = new ArrayList<>();
        if (result.getRecords() != null) {
            for (JobLogEntity entity : result.getRecords()) {
                rows.add(toRecord(entity));
            }
        }
        return PageResult.<JobLogRecord>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(rows)
                .build();
    }

    @Override
    public long count(JobLogQuery query) {
        Long counted = jobLogMapper.selectCount(filter(query == null ? new JobLogQuery() : query));
        return counted == null ? 0L : counted;
    }

    @Override
    public int cleanup(int retentionDays) {
        LambdaQueryWrapper<JobLogEntity> wrapper = Wrappers.<JobLogEntity>lambdaQuery()
                .gt(JobLogEntity::getSysJobLogId, 0L);
        if (retentionDays > 0) {
            wrapper.lt(JobLogEntity::getSysJobLogTriggerTime,
                    new Date(System.currentTimeMillis() - retentionDays * DAY_MS));
        }
        int deleted = jobLogMapper.delete(wrapper);
        log.info("[job] 执行日志已清理 retentionDays={} deleted={}", retentionDays, deleted);
        return deleted;
    }

    /**
     * 构造查询条件: 未删除 + 关键字/任务键/结果码/时间窗。
     *
     * <p>只放条件不放排序: 条件包装器里的 ORDER BY 会被 MyBatis-Plus 原样带进自动生成的
     * count 语句, 让 count 也背上排序 —— 部分数据库对此直接报错, 且属于无谓开销。
     * 排序统一挂在分页对象上, 见 {@link #page(JobLogQuery)}。</p>
     *
     * @param condition 查询条件
     * @return 条件包装器
     */
    private LambdaQueryWrapper<JobLogEntity> filter(JobLogQuery condition) {
        LambdaQueryWrapper<JobLogEntity> wrapper = Wrappers.<JobLogEntity>lambdaQuery()
                .eq(JobLogEntity::getSysJobLogIsDeleted, 0);
        if (isNotBlank(condition.getJobKey())) {
            String key = condition.getJobKey().trim();
            wrapper.and(w -> w.eq(JobLogEntity::getSysJobLogTriggerBean, key)
                    .or().eq(JobLogEntity::getSysJobNo, key));
        }
        if (isNotBlank(condition.getExecuteCode())) {
            wrapper.eq(JobLogEntity::getSysJobLogExecuteCode, condition.getExecuteCode().trim().toUpperCase());
        }
        if (isNotBlank(condition.getGroup())) {
            // 按「作业归属分组」收敛, 而不是按日志产生节点过滤:
            // sys_job_log 没有分组列, 只能经 sys_job_id 回查台账的 sys_job_group;
            // 而 sys_job_log_node_id 记的是实际执行并落日志的那个节点, 远程作业在执行器上跑完时
            // 写的是执行器 nodeId, 调度中心按自己的 nodeId 过滤会把执行器上的失败全部漏掉。
            // 这里刻意不设 LIMIT: 分组内任务数不可预知, 截断会让日志"凭空少一段", 等于重演本次要修的失真。
            List<Long> jobIds = ledgerJobIdsByGroup(condition.getGroup().trim());
            if (jobIds.isEmpty()) {
                // 该分组查不到任何任务: 用恒假条件收敛。绝不能退化成「不过滤」,
                // 那等于把全库失败都算进概览, 正是本次要修的串味。
                wrapper.eq(JobLogEntity::getSysJobLogId, -1L);
            } else {
                wrapper.in(JobLogEntity::getSysJobId, jobIds);
            }
        }
        if (isNotBlank(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            List<String> ledgerJobNos = ledgerJobNosByName(keyword);
            wrapper.and(w -> w.like(JobLogEntity::getSysJobLogTriggerBean, keyword)
                    .or().like(JobLogEntity::getSysJobNo, keyword)
                    .or().like(JobLogEntity::getSysJobLogNodeId, keyword)
                    .or().like(JobLogEntity::getSysJobLogTriggerMsg, keyword)
                    // 页面「任务」列显示的是台账里的任务名，日志行只存编号，所以按名字搜要先回查台账
                    .or(!ledgerJobNos.isEmpty(),
                            x -> x.in(JobLogEntity::getSysJobNo, ledgerJobNos)));
        }
        if (condition.getStartTime() != null && condition.getStartTime() > 0L) {
            wrapper.ge(JobLogEntity::getSysJobLogTriggerTime, new Date(condition.getStartTime()));
        }
        if (condition.getEndTime() != null && condition.getEndTime() > 0L) {
            wrapper.le(JobLogEntity::getSysJobLogTriggerTime, new Date(condition.getEndTime()));
        }
        return wrapper;
    }

    /**
     * 按台账任务名反查任务编号。
     *
     * <p>日志行只落 Bean 名与任务编号，任务名留在台账上；页面把台账任务名渲染在「任务」列，
     * 用户照抄屏幕上那串字来搜，不回查台账就会搜空。</p>
     *
     * <p>只认未删除的台账行：已逻辑删除的任务名不该再把一批历史日志重新召回到搜索结果里，
     * 台账页看不到的名称，日志页也不该搜得到。</p>
     *
     * @param keyword 关键字
     * @return 命中的任务编号, 无台账映射器时返回空
     */
    private List<String> ledgerJobNosByName(String keyword) {
        if (jobMapper == null) {
            return List.of();
        }
        try {
            return jobMapper.selectList(Wrappers.<JobEntity>lambdaQuery()
                            .select(JobEntity::getSysJobNo)
                            .eq(JobEntity::getSysJobIsDeleted, 0)
                            .like(JobEntity::getSysJobName, keyword)
                            .last("LIMIT " + LEDGER_HIT_LIMIT))
                    .stream()
                    .map(JobEntity::getSysJobNo)
                    .filter(no -> no != null && !no.isBlank())
                    .distinct()
                    .toList();
        } catch (Throwable ex) {
            log.warn("[job] 按任务名反查台账失败 keyword={} cause={}", keyword, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 按台账分组取任务主键集合。
     *
     * <p>{@code sys_job_log} 自身没有分组列, 日志查询要按分组收敛只能经 {@code sys_job_id}
     * 关联台账的 {@code sys_job_group}, 这里负责把分组翻译成主键集合。</p>
     *
     * <p>只认未删除的台账行, 与 {@link #ledgerJobNosByName(String)} 保持同一口径。</p>
     *
     * <p>刻意不设 {@code LIMIT}: 分组内任务数不可预知, 截断会让日志查询静默少返回一段,
     * 那与本次要修的「概览失真」是同一类错误, 不能用一个上限换另一个。</p>
     *
     * @param group 分组
     * @return 任务主键集合, 无台账映射器或查询失败时返回空
     */
    private List<Long> ledgerJobIdsByGroup(String group) {
        if (jobMapper == null) {
            return List.of();
        }
        try {
            return jobMapper.selectList(Wrappers.<JobEntity>lambdaQuery()
                            .select(JobEntity::getSysJobId)
                            .eq(JobEntity::getSysJobIsDeleted, 0)
                            .eq(JobEntity::getSysJobGroup, group))
                    .stream()
                    .map(JobEntity::getSysJobId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
        } catch (Throwable ex) {
            log.warn("[job] 按分组反查台账失败 group={} cause={}", group, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 实体转读取记录。
     *
     * @param entity 实体
     * @return 记录
     */
    private JobLogRecord toRecord(JobLogEntity entity) {
        JobLogRecord record = new JobLogRecord();
        record.setLogId(entity.getSysJobLogId() == null ? null : String.valueOf(entity.getSysJobLogId()));
        record.setJobId(entity.getSysJobId() == null ? null : String.valueOf(entity.getSysJobId()));
        record.setJobNo(entity.getSysJobNo());
        record.setJobKey(entity.getSysJobLogTriggerBean());
        record.setApp(entity.getSysJobLogApp());
        record.setTriggerType(entity.getSysJobLogTriggerType());
        record.setTriggerCode(entity.getSysJobLogTriggerCode());
        record.setExecuteCode(entity.getSysJobLogExecuteCode());
        BigDecimal cost = entity.getSysJobLogCost();
        record.setCostMs(cost == null ? null : cost.longValue());
        record.setMessage(entity.getSysJobLogTriggerMsg());
        record.setExecuteParam(entity.getSysJobLogTriggerParam());
        record.setAddress(entity.getSysJobLogTriggerAddress());
        record.setNodeId(entity.getSysJobLogNodeId());
        record.setFilePath(entity.getSysJobLogFilePath());
        Date triggerTime = entity.getSysJobLogTriggerTime();
        record.setTriggerTimeMs(triggerTime == null ? null : triggerTime.getTime());
        return record;
    }

    /**
     * 规整每页条数。
     *
     * @param pageSize 请求条数
     * @return 实际条数
     */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 字符串是否非空。
     *
     * @param value 取值
     * @return 非空白返回 true
     */
    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
