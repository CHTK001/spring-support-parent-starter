package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.model.SyncTaskRunLogVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
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
 * 同步执行调试日志的内存缓冲：执行过程逐步写入，按任务保留最近若干次执行。
 *
 * <p><b>为什么只在内存</b>：调试日志是排障用的过程数据（量大、寿命短、后端重启即失效），
 * 落库会把写放大到每次执行几百行；内存环形缓冲——每次执行最多
 * {@value #MAX_LINES_PER_RUN} 行、每任务最多 {@value #MAX_RUNS_PER_TASK} 次、
 * 全局最多 {@value #MAX_TRACKED_TASKS} 个任务——足够覆盖「刚跑完就来看日志」的排障场景。</p>
 *
 * <p><b>并发模型</b>：同一任务由 {@code runningTasks} 保证同一时刻只有一次执行
 * （begin 与 end 严格成对嵌套），不同任务可并行：进行中的日志按 taskId 分槽存放，
 * 已完成历史用 {@link LinkedHashMap}（插入序，超限淘汰最旧任务）并以整体锁保护。</p>
 *
 * <p>无进行中执行时写入一律静默丢弃（如预览路径复用了会打日志的私有方法），
 * 保证本组件不干扰任何业务分支。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Component
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
     * 单行内容最大字符数（超出截断；堆栈也受此上限约束）
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
     * 结束一次执行：定格状态与耗时，转存到该任务的历史（最多 {@value #MAX_RUNS_PER_TASK} 次）。
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
    }

    /**
     * 查询某任务的执行日志：进行中的执行（若在跑）在最前，其后是已完成历史（新在前）。
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
        return result;
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
