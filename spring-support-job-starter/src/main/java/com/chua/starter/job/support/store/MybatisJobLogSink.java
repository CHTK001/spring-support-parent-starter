package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogSink;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MyBatis-Plus 版 {@link JobLogSink}, 写入 {@code sys_job_log} 表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class MybatisJobLogSink implements JobLogSink {

    /**
     * 日期_fmt
    */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /**
     * 日志归属应用名, 固定为 {@code scheduler}。
     *
     * <p><b>这是刻意写死, 不要改成可配置或从事件取值。</b>本 sink 运行在<em>调度端</em>,
     * {@code sys_job_log_app} 的语义是「这条日志属于哪个调度应用」, 而不是「哪个执行器跑的」。
     * EXECUTOR 端产出的 {@link JobLogEvent} 是一律<em>上行汇入</em>本调度器后才落库的
     * (见 {@code SchedulerSyncBinder}), 落库时它们已经属于本调度应用, 因此 app 记 scheduler 是正确的。
     * 真正的执行者身份由 {@code sys_job_log_node_id} 与台账关联列承担, 不靠 app 列区分。</p>
     *
     * <p>该列仅在 {@code JobLogVO.app} 展示, 全仓无任何按 app 的过滤或逻辑消费它。</p>
     */
    private static final String LOG_APP = "scheduler";

    /**
     * 结果说明最大长度（列 {@code sys_job_log_trigger_msg} 为 VARCHAR(4000)，Oracle 兼容）
     */
    private static final int MAX_MESSAGE_CHARS = 4000;

    /**
     * 运行客户端为本地时的展示值。
     *
     * <p>台账派发方式不是 {@code REMOTE}（含空值）时，这一轮就跑在本调度节点上，记「本地」
     * 而不是记节点标识：节点标识另有 {@code sys_job_log_node_id} 承载，两列记同一串会让
     * 「到底是不是远端跑的」看不出来。</p>
     */
    private static final String LOCAL_CLIENT = "本地";

    /**
     * 远端派发但台账没登记地址时的展示值。
     *
     * <p>记「本地」是错的（这一轮确实不在本机），记空又会被当成"没采集到"，
     * 所以显式说明缺什么。</p>
     */
    private static final String REMOTE_CLIENT_UNKNOWN = "远端（未登记地址）";

    /**
     * 作业日志映射器
    */
    private final JobLogMapper jobLogMapper;
    /**
     * 作业映射器, 用于把日志行关联到任务台账 (可为空)
    */
    private final JobMapper jobMapper;
    /**
     * 文件日志是否启用
    */
    private final boolean fileLoggingEnabled;

    /**
     * 本节点标识（形如 {@code pid@host-token}）。
     *
     * <p>用来判断某一轮执行是不是跑在本节点上：角色拆分（SCHEDULER + EXECUTOR）下执行器节点
     * 也会把日志回报到这里落库，只按台账派发方式判定会把它们错记成「本地」。允许为 空
     * （拿不到本节点标识时退化为只看派发方式）。</p>
     */
    private final String localNodeId;

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper) {
        this(jobLogMapper, null, true);
    }

    /**
     * 创建 MyBatis作业日志sink 实例
     * @param jobLogMapper 作业日志映射器
     * @param fileLoggingEnabled 文件日志已启用
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, boolean fileLoggingEnabled) {
        this(jobLogMapper, null, fileLoggingEnabled);
    }

    /**
     * 创建带台账关联的 MyBatis 作业日志 sink 实例。
     *
     * @param jobLogMapper       作业日志映射器
     * @param jobMapper          作业映射器, 为空则日志行不落任务编号
     * @param fileLoggingEnabled 文件日志已启用
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, JobMapper jobMapper, boolean fileLoggingEnabled) {
        this(jobLogMapper, jobMapper, fileLoggingEnabled, null);
    }

    /**
     * 创建带本节点标识的 MyBatis 作业日志 sink 实例。
     *
     * @param jobLogMapper       作业日志映射器
     * @param jobMapper          作业映射器, 为空则日志行不落任务编号
     * @param fileLoggingEnabled 文件日志是否启用
     * @param localNodeId        本节点标识, 允许为 空
     */
    public MybatisJobLogSink(JobLogMapper jobLogMapper, JobMapper jobMapper, boolean fileLoggingEnabled,
                             String localNodeId) {
        this.jobLogMapper = jobLogMapper;
        this.jobMapper = jobMapper;
        this.fileLoggingEnabled = fileLoggingEnabled;
        this.localNodeId = localNodeId;
    }

    @Override
    /**
     * 写入
     * @param event 参数
     * @return 返回值
    */
    public void write(JobLogEvent event) {
        if (event == null) {
            return;
        }
        try {
            JobLogEntity entity = toEntity(event);
            jobLogMapper.insert(entity);
        } catch (Throwable ex) {
            log.warn("[job] 写入 sys_job_log 失败 cause={}", ex.getMessage(), ex);
        }
    }

    /**
     * 事件转实体 (字段均按 sys_作业_日志_* 前缀)。
     *
     * @param event 事件
     * @return 实体
     */
    private JobLogEntity toEntity(JobLogEvent event) {
        JobLogEntity entity = new JobLogEntity();
        entity.setSysJobLogNo("JOBLOG" + System.currentTimeMillis());
        entity.setSysJobLogApp(LOG_APP);
        entity.setSysJobLogTriggerBean(event.jobName());
        JobEntity ledger = findLedger(event.jobName());
        attachLedger(entity, ledger);
        // 事件带了本轮触发方式就照记（手动派发不能记成定时），没带才回退台账的调度类型
        entity.setSysJobLogTriggerType(nonBlank(event.triggerType()) ? event.triggerType()
                : ledger == null || ledger.getSysJobScheduleType() == null
                ? "CRON" : ledger.getSysJobScheduleType());
        entity.setSysJobLogTriggerCode(event.status().name());
        entity.setSysJobLogExecuteCode(event.status().name());
        entity.setSysJobLogCost(BigDecimal.valueOf(event.costMs()));
        entity.setSysJobLogTriggerMsg(buildMessage(event));
        entity.setSysJobLogNodeId(event.nodeId());
        entity.setSysJobLogTriggerAddress(resolveClientAddress(event, ledger));
        Date executedAt = new Date(event.executedAtMs());
        entity.setSysJobLogTriggerTime(executedAt);
        entity.setSysJobLogTriggerDate(executedAt);
        if (fileLoggingEnabled) {
            entity.setSysJobLogFilePath(resolveLogFilePath(event));
        }
        entity.setSysJobLogIsDeleted(0);
        return entity;
    }

    /**
     * 组装结果说明：失败记异常原因，成功记 "ok"；执行日志正文（重试轨迹、回调执行、脚本输出）
     * 附在异常/ok 之后，保证控制台能看到"这一轮到底发生了什么"。
     *
     * <p>列宽 4000，超长截断（完整正文见文件日志）。</p>
     *
     * @param event 日志事件
     * @return 结果说明
     */
    private static String buildMessage(JobLogEvent event) {
        String exception = event.exception();
        String content = event.logContent();
        boolean failed = exception != null && !exception.isBlank();
        StringBuilder sb = new StringBuilder();
        if (failed) {
            sb.append(exception);
        } else if (content == null || content.isBlank()) {
            sb.append("ok");
        }
        if (content != null && !content.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n---\n");
            }
            sb.append(content);
        }
        String message = sb.toString();
        return message.length() <= MAX_MESSAGE_CHARS ? message : message.substring(0, MAX_MESSAGE_CHARS);
    }

    /**
     * 解析「运行客户端」地址。
     *
     * <p>判据按优先级：</p>
     * <ol>
     *   <li>本轮执行节点就是本节点 → {@link #LOCAL_CLIENT}；</li>
     *   <li>台账登记了远端执行器地址（{@code dispatchMode=REMOTE}）→ 记该地址，也就是「他们的 IP」；</li>
     *   <li>执行节点另有其人（角色拆分下的执行器、或事件由远端上行而来）→ 记该节点标识，
     *       <b>不能记「本地」</b>：那一轮确实不跑在本机，谎报本地会让排障时找错机器。</li>
     * </ol>
     *
     * <p>第 1 步依赖注入进来的本节点标识，拿不到时退化成只看台账派发方式。只按
     * {@code dispatchMode} 判定是不够的：角色拆分走作业同步通道，台账里是 LOCAL，
     * 但实际由执行器节点执行。</p>
     *
     * @param event  日志事件（取执行节点标识），允许为 null
     * @param ledger 台账行, 可为 空 (任务已删除或回查不到)
     * @return 运行客户端地址
     */
    private String resolveClientAddress(JobLogEvent event, JobEntity ledger) {
        String node = event == null ? null : event.nodeId();
        if (localNodeId != null && !localNodeId.isBlank() && localNodeId.equals(node)) {
            return LOCAL_CLIENT;
        }
        if (ledger != null && isRemoteDispatch(ledger)) {
            String address = ledger.getSysJobRemoteExecutorAddress();
            if (address == null || address.isBlank()) {
                return REMOTE_CLIENT_UNKNOWN;
            }
            return address.trim();
        }
        if (node != null && !node.isBlank()) {
            return node;
        }
        return LOCAL_CLIENT;
    }

    /**
     * 台账这一行是否为「远端派发」（按登记的远端执行器地址下发）。
     *
     * @param ledger 台账行
     * @return true 表示台账登记了远端派发
     */
    private static boolean isRemoteDispatch(JobEntity ledger) {
        String mode = ledger.getSysJobDispatchMode();
        return mode != null && "REMOTE".equalsIgnoreCase(mode.trim());
    }

    /**
     * 判断字符串有内容。
     *
     * @param text 文本
     * @return 非空白返回 true
     */
    private static boolean nonBlank(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * 回查任务台账行。
     *
     * <p>一个执行器 Bean 可以挂多条台账行（采集流程的镜像行 + 三方在调度中心为该 Bean 另建的作业行），
     * 只按 Bean 名取一条会随机归到没在跑的那一行。派发只可能来自启用的行，所以优先在启用行里取最近
     * 登记的一条；全都没启用时退回未删除行，保留可追溯性。</p>
     *
     * @param jobKey 执行器 Bean 名
     * @return 台账行, 无匹配返回 空
     */
    private JobEntity findLedger(String jobKey) {
        if (jobMapper == null || jobKey == null || jobKey.isEmpty()) {
            return null;
        }
        try {
            JobEntity running = jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                    .and(w -> w.eq(JobEntity::getSysJobExecuteBean, jobKey)
                            .or().eq(JobEntity::getSysJobName, jobKey))
                    .eq(JobEntity::getSysJobIsDeleted, 0)
                    .eq(JobEntity::getSysJobTriggerStatus, 1)
                    .orderByDesc(JobEntity::getSysJobId)
                    .last("LIMIT 1"));
            if (running != null) {
                return running;
            }
            return jobMapper.selectOne(Wrappers.<JobEntity>lambdaQuery()
                    .and(w -> w.eq(JobEntity::getSysJobExecuteBean, jobKey)
                            .or().eq(JobEntity::getSysJobName, jobKey))
                    .eq(JobEntity::getSysJobIsDeleted, 0)
                    .orderByDesc(JobEntity::getSysJobId)
                    .last("LIMIT 1"));
        } catch (Throwable ex) {
            log.debug("[job] 台账回查失败 job={} cause={}", jobKey, ex.getMessage());
            return null;
        }
    }

    /**
     * 把已回查到的台账主键与任务编号补到日志行。
     *
     * <p>控制台的按分组过滤和「任务编号」列都读 {@code sys_job_no}，不补这一列日志行就是孤儿行。</p>
     *
     * @param entity 日志实体
     * @param job    台账行, 可为 空
     */
    private void attachLedger(JobLogEntity entity, JobEntity job) {
        if (job == null) {
            return;
        }
        entity.setSysJobId(job.getSysJobId());
        entity.setSysJobNo(job.getSysJobNo());
    }

    /**
     * 解析记录日志文件路径
     *
     * @param event 事件
     * @return resolve日志文件路径的结果
     */
    private String resolveLogFilePath(JobLogEvent event) {
        String dateDir = DATE_FMT.format(Instant.now());
        String jobName = sanitize(event.jobName() != null ? event.jobName() : "unknown");
        return dateDir + "/" + jobName + "/" + event.executedAtMs() + ".log";
    }

    /**
     * Sanitize
     *
     * @param name 名称
     * @return sanitize的结果
     */
    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
