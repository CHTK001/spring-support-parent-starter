package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.entity.MsSyncTaskRunLog;
import com.chua.starter.monitor.support.mapper.MsSyncTaskRunLogMapper;
import com.chua.starter.monitor.support.model.SyncTaskRunLogVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步执行调试日志：内存缓冲（进行中实时追加）+ 数据库留档（历史可查）。
 *
 * <p><b>为什么两套存储</b>：进行中的执行每 1.5 秒被界面轮询一次，只能走内存
 * （反复读库会被日志写入放大）；而内存「每任务 10 次、后端重启即清空」的容量
 * 决定了它无法回答「昨天那次为什么失败」。因此执行结束时把定格快照写入
 * {@code ms_sync_task_run_log}，历史查询在「内存未命中」时回落到库。</p>
 *
 * <p><b>并发模型</b>：同一任务由 {@code runningTasks} 保证同一时刻只有一次执行
 * （begin 与 end 严格成对嵌套），不同任务可并行：进行中的日志按 taskId 分槽存放，
 * 已完成历史用 {@link LinkedHashMap}（插入序，超限淘汰最旧任务）并以整体锁保护。</p>
 *
 * <p>无进行中执行时写入一律静默丢弃（如预览路径复用了会打日志的私有方法），
 * 保证本组件不干扰任何业务分支。入库失败只记 WARN，不影响执行结果本身。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskRunLogBuffer {

    /**
     * 单次执行最多保留的行数（超出后追加一行截断标记，后续丢弃）
     */
    private static final int MAX_LINES_PER_RUN = 500;

    /**
     * 每个任务保留的执行次数（超限丢弃最旧一次）
     */
    private static final int MAX_RUNS_PER_TASK = 10;

    /**
     * 全局保留的任务数（超限淘汰最久未新增执行的任务）
     */
    private static final int MAX_TRACKED_TASKS = 300;

    /**
     * 单个任务从库里回捞的历史执行条数上限
     */
    private static final int MAX_DB_RUNS_PER_TASK = 50;

    /**
     * 历史日志留档天数：超过的由 {@link #purgeExpired()} 清理
     */
    private static final int KEEP_DAYS = 30;

    /**
     * 一次执行里单行内容的最大字符数（超出截断；堆栈也受此上限约束）
     */
    private static final int MAX_MESSAGE_CHARS = 4000;

    /**
     * 日志行时间格式（时:分:秒.毫秒）
     */
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 执行开始时刻格式（年-月-日 时:分:秒）
     */
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 执行日志映射器（历史留档；入库失败不影响执行本身）
     */
    private final MsSyncTaskRunLogMapper runLogMapper;

    /**
     * 进行中的执行（任务 ID → 运行日志）
     */
    private final Map<Long, ActiveRun> active = new ConcurrentHashMap<>();

    /**
     * 已完成执行的历史（任务 ID → 最近 N 次，新在前）
     */
    private final Map<Long, Deque<SyncTaskRunLogVO>> completed = new LinkedHashMap<>();

    /**
     * 开始一次执行：建立该任务的日志槽。同一任务的 begin/end 由执行互斥锁保证成对。
     *
     * @param taskId  任务 ID
     * @param trigger 触发方式（见 {@link SyncTaskRunLogVO} 的 TRIGGER_* 常量）
     */
    public void begin(Long taskId, String trigger) {
        if (taskId == null) {
            return;
        }
        active.put(taskId, new ActiveRun(trigger));
    }

    /**
     * 追加一条 INFO 级行。
     *
     * @param taskId 任务 ID
     * @param message 内容
     */
    public void info(Long taskId, String message) {
        append(taskId, "INFO", message);
    }

    /**
     * 追加一条 WARN 级行。
     *
     * @param taskId  任务 ID
     * @param message 内容
     */
    public void warn(Long taskId, String message) {
        append(taskId, "WARN", message);
    }

    /**
     * 追加一条 ERROR 级行；堆栈完整落入日志（截断至 {@value #MAX_MESSAGE_CHARS} 字符）。
     *
     * @param taskId    任务 ID
     * @param message   内容
     * @param throwable 异常（可为 null）
     */
    public void error(Long taskId, String message, Throwable throwable) {
        append(taskId, "ERROR", formatError(message, throwable));
    }

    /**
     * 结束一次执行：定格状态与耗时，转存到该任务的历史（内存保留最近
     * {@value #MAX_RUNS_PER_TASK} 次），同时写库留档（历史可跨重启查询）。
     *
     * @param taskId 任务 ID
     * @param status 结束状态（{@code SUCCESS} / {@code FAILED}；见 {@link SyncTaskRunVO}）
     * @param costMs 本次总耗时（毫秒）
     */
    public void end(Long taskId, String status, long costMs) {
        if (taskId == null) {
            return;
        }
        ActiveRun run = active.remove(taskId);
        if (run == null) {
            return;
        }
        SyncTaskRunLogVO vo = run.snapshot(status, costMs);
        synchronized (completed) {
            Deque<SyncTaskRunLogVO> history =
                    completed.computeIfAbsent(taskId, key -> new ArrayDeque<>(MAX_RUNS_PER_TASK));
            history.addFirst(vo);
            while (history.size() > MAX_RUNS_PER_TASK) {
                history.pollLast();
            }
            while (completed.size() > MAX_TRACKED_TASKS) {
                Iterator<Long> oldest = completed.keySet().iterator();
                if (!oldest.hasNext()) {
                    break;
                }
                oldest.next();
                oldest.remove();
            }
        }
        persist(taskId, vo);
    }

    /**
     * 把一次执行的定格快照写库留档。
     *
     * <p>任何异常都只记 WARN：日志留档失败不应让已经成功的数据同步被判定为失败。</p>
     *
     * @param taskId 任务 ID
     * @param vo     定格快照
     */
    private void persist(Long taskId, SyncTaskRunLogVO vo) {
        try {
            MsSyncTaskRunLog entity = new MsSyncTaskRunLog();
            entity.setRunLogTaskId(taskId);
            entity.setRunLogTrigger(vo.trigger());
            entity.setRunLogStatus(vo.status());
            entity.setRunLogRunTime(LocalDateTime.parse(vo.runTime(), RUN_TIME));
            entity.setRunLogCostMs(vo.costMs());
            entity.setRunLogLines(joinLines(vo.lines()));
            entity.setRunLogLineCount(vo.lines() == null ? 0 : vo.lines().size());
            entity.setRunLogCreateTime(LocalDateTime.now());
            runLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[sync-log] 执行日志留档失败 taskId={}, runTime={}", taskId, vo.runTime(), e);
        }
    }

    /**
     * 把过程行拼成「时间\t级别\t内容」逐行文本。
     *
     * <p><b>正文里的 \t 不能替换</b>：Java 堆栈每一帧都以 {@code \tat ...} 开头，
     * 替换成空格会永久改写堆栈内容（实测重启后 {@code \tat com.chua...} 变成
     * {@code  at com.chua...}，与重启前不一致）。因此只按「行首第一个 \t 是时间、
     * 第二个 \t 是级别」切分，正文里的 \t 原样保留。</p>
     *
     * @param lines 过程行
     * @return 正文；无行时为空串
     */
    private static String joinLines(List<SyncTaskRunLogVO.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(lines.size() * 96);
        for (SyncTaskRunLogVO.Line line : lines) {
            sb.append(line.time()).append('\t')
                    .append(line.level()).append('\t')
                    .append(line.message() == null ? "" : line.message())
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析库里的正文，还原成过程行。
     *
     * <p>只按每行前两处 \t 切分：正文里的换行（ERROR 堆栈）属于同一行内容。
     * 行首必须匹配「时间\t级别\t」，因此堆栈里的 {@code \tat} 不会被误判为分隔。</p>
     *
     * @param text 正文
     * @return 过程行；空正文返回空列表
     */
    private static List<SyncTaskRunLogVO.Line> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<SyncTaskRunLogVO.Line> lines = new ArrayList<>();
        // 一条记录以「时间\t级别\t」开头，按此模式切块，正文中的换行不会误切
        String[] blocks = text.split("\n(?=\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\t)");
        for (String block : blocks) {
            if (block.isBlank()) {
                continue;
            }
            // joinLines 在每条记录末尾追加 '\n'；除最后一条外，这个 '\n' 已被
            // split 的前瞻当作分隔符吃掉，最后一条则残留在块尾，读回会多出一个字符
            // （实测 4010 → 4011，与原文不再逐字节一致）。这里去掉残留的那一个。
            if (block.endsWith("\n")) {
                block = block.substring(0, block.length() - 1);
            }
            int first = block.indexOf('\t');
            int second = first < 0 ? -1 : block.indexOf('\t', first + 1);
            if (first < 0 || second < 0) {
                continue;
            }
            lines.add(new SyncTaskRunLogVO.Line(
                    block.substring(0, first),
                    block.substring(first + 1, second),
                    block.substring(second + 1)));
        }
        return lines;
    }

    /**
     * 查询某任务的执行日志：进行中的执行（若在跑）在最前，其后是已完成历史（新在前）。
     *
     * <p>内存里只有最近 {@value #MAX_RUNS_PER_TASK} 次，超出部分从库里补：
     * 按 {@code runTime} 去重（近似的秒级时刻足够区分同任务的不同执行，
     * 同一秒内重复执行本身已被 {@code runningTasks} 互斥排除）。</p>
     *
     * @param taskId 任务 ID
     * @return 日志列表；无记录时为空列表
     */
    public List<SyncTaskRunLogVO> list(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<SyncTaskRunLogVO> result = new ArrayList<>();
        ActiveRun running = active.get(taskId);
        if (running != null) {
            result.add(running.snapshot(SyncTaskRunLogVO.STATUS_RUNNING,
                    System.currentTimeMillis() - running.startMillis));
        }
        synchronized (completed) {
            Deque<SyncTaskRunLogVO> history = completed.get(taskId);
            if (history != null) {
                result.addAll(history);
            }
        }
        appendDbHistory(taskId, result);
        return result;
    }

    /**
     * 把库里的历史执行追加到结果尾部（内存已有同 {@code runTime} 的跳过）。
     *
     * @param taskId 任务 ID
     * @param sink   结果列表（就地追加）
     */
    private void appendDbHistory(Long taskId, List<SyncTaskRunLogVO> sink) {
        try {
            List<MsSyncTaskRunLog> rows = runLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsSyncTaskRunLog>()
                            .eq(MsSyncTaskRunLog::getRunLogTaskId, taskId)
                            .orderByDesc(MsSyncTaskRunLog::getRunLogRunTime)
                            .last("LIMIT " + MAX_DB_RUNS_PER_TASK));
            if (rows == null || rows.isEmpty()) {
                return;
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (SyncTaskRunLogVO vo : sink) {
                seen.add(vo.runTime());
            }
            for (MsSyncTaskRunLog row : rows) {
                String runTime = row.getRunLogRunTime() == null
                        ? "" : row.getRunLogRunTime().format(RUN_TIME);
                if (!seen.add(runTime)) {
                    continue;
                }
                sink.add(new SyncTaskRunLogVO(
                        row.getRunLogTrigger(),
                        row.getRunLogStatus(),
                        runTime,
                        row.getRunLogCostMs(),
                        splitLines(row.getRunLogLines())));
            }
        } catch (Exception e) {
            log.warn("[sync-log] 读取执行日志历史失败 taskId={}", taskId, e);
        }
    }

    /**
     * 清理超过 {@value #KEEP_DAYS} 天的执行日志。
     *
     * <p>由定时任务每天调用；删除失败只记 WARN，不影响其它功能。</p>
     *
     * @return 删除行数
     */
    public int purgeExpired() {
        try {
            LocalDateTime deadline = LocalDateTime.now().minusDays(KEEP_DAYS);
            return runLogMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsSyncTaskRunLog>()
                            .lt(MsSyncTaskRunLog::getRunLogRunTime, deadline));
        } catch (Exception e) {
            log.warn("[sync-log] 清理过期执行日志失败", e);
            return 0;
        }
    }

    /**
     * 解析库里某条执行的日志正文，还原成过程行。
     *
     * <p>供「同步日志」详情查询复用：单条执行从库里整体读出正文后，
     * 用同一套 {@link #splitLines} 规则还原为行，保证「内存实时行」与
     * 「落库后读回的行」在界面上的渲染结果完全一致。</p>
     *
     * @param text 日志正文（{@code 时间\t级别\t内容} 逐行拼接）
     * @return 过程行；空正文返回空列表
     */
    public List<SyncTaskRunLogVO.Line> parseLines(String text) {
        return splitLines(text);
    }

    /**
     * 丢弃某任务的全部日志（删除任务时调用）。
     *
     * @param taskId 任务 ID
     */
    public void remove(Long taskId) {
        if (taskId == null) {
            return;
        }
        active.remove(taskId);
        synchronized (completed) {
            completed.remove(taskId);
        }
    }

    /**
     * 追加一行；没有进行中执行时静默丢弃。
     *
     * @param taskId 任务 ID
     * @param level  级别
     * @param message 内容
     */
    private void append(Long taskId, String level, String message) {
        if (taskId == null) {
            return;
        }
        ActiveRun run = active.get(taskId);
        if (run == null) {
            return;
        }
        run.add(level, message == null ? "" : message);
    }

    /**
     * 拼接「消息 + 完整堆栈」，超长截断。
     *
     * @param message   消息
     * @param throwable 异常（可为 null）
     * @return 单段文本
     */
    private static String formatError(String message, Throwable throwable) {
        String text = message == null ? "" : message;
        if (throwable == null) {
            return truncate(text);
        }
        StringWriter writer = new StringWriter(text.length() + 256);
        writer.write(text);
        writer.write(System.lineSeparator());
        throwable.printStackTrace(new PrintWriter(writer));
        return truncate(writer.toString());
    }

    /**
     * 按 {@value #MAX_MESSAGE_CHARS} 截断，超出加截断标记。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private static String truncate(String text) {
        if (text.length() <= MAX_MESSAGE_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_CHARS) + "…（堆栈过长已截断）";
    }

    /**
     * 一次进行中的执行：行列表 + 起始时刻。
     *
     * <p>add 与 snapshot 共用实例锁，行序与快照一致；超过行上限后只写一次截断标记。</p>
     */
    private static final class ActiveRun {

        /**
         * 触发方式
         */
        private final String trigger;

        /**
         * 执行开始时刻（格式化文本）
         */
        private final String runTime;

        /**
         * 执行开始毫秒时间戳
         */
        private final long startMillis = System.currentTimeMillis();

        /**
         * 逐步过程行（持实例锁访问）
         */
        private final List<SyncTaskRunLogVO.Line> lines = new ArrayList<>();

        /**
         * 是否已写入截断标记
         */
        private boolean truncated;

        private ActiveRun(String trigger) {
            this.trigger = trigger;
            this.runTime = LocalDateTime.now().format(RUN_TIME);
        }

        private void add(String level, String message) {
            synchronized (this) {
                if (lines.size() < MAX_LINES_PER_RUN) {
                    lines.add(new SyncTaskRunLogVO.Line(
                            LocalDateTime.now().format(LINE_TIME), level, truncate(message)));
                } else if (!truncated) {
                    truncated = true;
                    lines.add(new SyncTaskRunLogVO.Line(
                            LocalDateTime.now().format(LINE_TIME), "WARN",
                            "…（超过 " + MAX_LINES_PER_RUN + " 行，后续日志已截断）"));
                }
            }
        }

        private SyncTaskRunLogVO snapshot(String status, Long costMs) {
            synchronized (this) {
                return new SyncTaskRunLogVO(trigger, status, runTime, costMs, List.copyOf(lines));
            }
        }
    }
}
