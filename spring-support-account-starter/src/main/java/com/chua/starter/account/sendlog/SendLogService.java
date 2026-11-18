package com.chua.starter.account.sendlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.account.sendlog.mapper.SysAccountSendLogMapper;
import com.chua.starter.account.sendlog.store.SysAccountSendLog;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一发送日志服务 —— 异步写入、分页查询与按时间清理。
 *
 * <p>写入走模块内自建的有界线程池：发送主流程只负责投递，队列打满时丢弃
 * 最旧日志并告警，绝不阻塞或拖垮发送链路。不依赖宿主开启
 * {@code @EnableAsync}，装配即可用。</p>
 *
 * <p>查询 / 清理供「发送日志」管理页使用：按种类、结果、账号、收件人、
 * 关键字与时间范围过滤，支持按截止时间批量清理。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendLogService {

    /**
     * 正文入库长度上限（MEDIUMTEXT 足够，防御异常超大邮件正文挤占带宽）
     */
    private static final int CONTENT_MAX_LENGTH = 60_000;

    /**
     * 异步写入队列容量：超出后按丢弃最旧策略保护发送主流程
     */
    private static final int QUEUE_CAPACITY = 5_000;

    /**
     * 异步写入线程数：日志写入是单表顺序插入，单线程足够
     */
    private static final int WRITER_THREADS = 1;

    /**
     * 关闭时等待存量日志写完的时间（秒）
     */
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    /**
     * 单页上限
     */
    private static final int PAGE_SIZE_MAX = 200;

    /**
     * 发送日志映射器
     */
    private final SysAccountSendLogMapper mapper;

    /**
     * 异步写入线程池（{@code @PostConstruct} 启动，{@code @PreDestroy} 优雅关闭）
     */
    private ExecutorService writer;

    /**
     * 已丢弃日志条数（可观测性：丢弃时 warn，避免静默丢日志）
     */
    private final AtomicLong discarded = new AtomicLong();

    /**
     * 启动异步写入线程池。
     */
    @PostConstruct
    public void start() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "account-send-log-" + index.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        writer = new ThreadPoolExecutor(WRITER_THREADS, WRITER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY), factory,
                (runnable, executor) -> {
                    long total = discarded.incrementAndGet();
                    if (total % 100 == 1) {
                        log.warn("[send-log] 发送日志队列已满，丢弃日志（累计丢弃 {} 条）", total);
                    }
                });
        log.info("[send-log] 发送日志异步写入线程池已启动: threads={}, queue={}", WRITER_THREADS, QUEUE_CAPACITY);
    }

    /**
     * 优雅关闭：等待存量日志写完。
     */
    @PreDestroy
    public void stop() {
        if (writer == null) {
            return;
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    /**
     * 异步落一条发送日志。
     *
     * <p>任何字段异常都不影响调用方：日志是旁路，失败只 warn。</p>
     *
     * @param entity 日志实体（标识与写入时间留空时自动补）
     */
    public void record(SysAccountSendLog entity) {
        if (entity == null || writer == null) {
            return;
        }
        if (StringUtils.hasText(entity.getSendLogContent())
                && entity.getSendLogContent().length() > CONTENT_MAX_LENGTH) {
            entity.setSendLogContent(entity.getSendLogContent().substring(0, CONTENT_MAX_LENGTH));
        }
        writer.execute(() -> {
            try {
                if (!StringUtils.hasText(entity.getSendLogId())) {
                    entity.setSendLogId(UUID.randomUUID().toString().replace("-", ""));
                }
                if (entity.getSendLogCreatedAt() == null) {
                    entity.setSendLogCreatedAt(LocalDateTime.now());
                }
                mapper.insert(entity);
            } catch (Exception e) {
                log.warn("[send-log] 写入发送日志失败: target={}, error={}",
                        entity.getSendLogTarget(), e.getMessage());
            }
        });
    }

    /**
     * 分页查询发送日志。
     *
     * @param query 查询条件（种类 / 结果 / 账号 / 收件人 / 关键字 / 时间范围 / 分页）
     * @return 分页结果
     */
    public SendLogPage page(SendLogQuery query) {
        SendLogQuery effective = query == null ? new SendLogQuery() : query;
        int page = effective.page() == null || effective.page() < 1 ? 1 : effective.page();
        int pageSize = effective.pageSize() == null || effective.pageSize() < 1
                ? 20 : Math.min(effective.pageSize(), PAGE_SIZE_MAX);

        LambdaQueryWrapper<SysAccountSendLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(effective.kind())) {
            wrapper.eq(SysAccountSendLog::getSendLogKind, effective.kind().trim().toUpperCase());
        }
        if (effective.success() != null) {
            wrapper.eq(SysAccountSendLog::getSendLogSuccess, effective.success());
        }
        if (StringUtils.hasText(effective.accountId())) {
            wrapper.eq(SysAccountSendLog::getSendLogAccountId, effective.accountId().trim());
        }
        if (StringUtils.hasText(effective.source())) {
            wrapper.eq(SysAccountSendLog::getSendLogSource, effective.source().trim());
        }
        if (StringUtils.hasText(effective.target())) {
            wrapper.like(SysAccountSendLog::getSendLogTarget, effective.target().trim());
        }
        if (StringUtils.hasText(effective.keyword())) {
            String keyword = effective.keyword().trim();
            wrapper.and(inner -> inner.like(SysAccountSendLog::getSendLogSubject, keyword)
                    .or().like(SysAccountSendLog::getSendLogContent, keyword)
                    .or().like(SysAccountSendLog::getSendLogError, keyword));
        }
        if (effective.startTime() != null) {
            wrapper.ge(SysAccountSendLog::getSendLogTime, effective.startTime());
        }
        if (effective.endTime() != null) {
            wrapper.le(SysAccountSendLog::getSendLogTime, effective.endTime());
        }
        wrapper.orderByDesc(SysAccountSendLog::getSendLogTime)
                .orderByDesc(SysAccountSendLog::getSendLogCreatedAt);

        long total = mapper.selectCount(wrapper);
        List<SysAccountSendLog> records = total == 0 ? List.of()
                : mapper.selectList(wrapper.last("limit " + (page - 1) * pageSize + ", " + pageSize));
        return new SendLogPage(records, total, page, pageSize);
    }

    /**
     * 清理指定时间之前的日志。
     *
     * @param before 截止时间（含）
     * @return 删除条数
     */
    public int clearBefore(LocalDateTime before) {
        if (before == null) {
            return 0;
        }
        int removed = mapper.delete(Wrappers.<SysAccountSendLog>lambdaQuery()
                .le(SysAccountSendLog::getSendLogTime, before));
        log.info("[send-log] 清理 {} 之前的发送日志 {} 条", before, removed);
        return removed;
    }

    /**
     * 当前累计丢弃条数（运维观测用）。
     *
     * @return 丢弃条数
     */
    public long discardedCount() {
        return discarded.get();
    }
}
