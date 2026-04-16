package com.chua.starter.spider.support.store;

import com.chua.common.support.annotations.Spi;
import com.chua.spider.Request;
import com.chua.spider.store.UrlStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于数据库的 URL 存储器（spider_url_store 表）。
 *
 * <p>实现 {@link UrlStore} SPI，支持爬虫任务的 URL 持久化、状态管理和任务恢复。</p>
 *
 * @author CH
 */
@Slf4j
@Spi("database")
@Component
@RequiredArgsConstructor
public class DatabaseUrlStore implements UrlStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(Request request) {
        if (request == null || request.getUrl() == null) return;
        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO spider_url_store (id, task_id, url, status, depth) VALUES (?, ?, ?, ?, ?)",
                    System.currentTimeMillis(), extractTaskId(request), request.getUrl(), "PENDING", 0
            );
        } catch (Exception e) {
            log.warn("[DatabaseUrlStore] 保存 URL 失败: {}", e.getMessage());
        }
    }

    @Override
    public void saveBatch(List<Request> requests) {
        if (requests == null || requests.isEmpty()) return;
        requests.forEach(this::save);
    }

    @Override
    public List<Request> getPending(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT url FROM spider_url_store WHERE status = 'PENDING' LIMIT ?",
                    (rs, rowNum) -> new Request(rs.getString("url")),
                    limit
            );
        } catch (Exception e) {
            log.warn("[DatabaseUrlStore] 查询待处理 URL 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateStatus(Request request, UrlStatus status) {
        if (request == null || request.getUrl() == null) return;
        try {
            jdbcTemplate.update(
                    "UPDATE spider_url_store SET status = ? WHERE url = ?",
                    status.name(), request.getUrl()
            );
        } catch (Exception e) {
            log.warn("[DatabaseUrlStore] 更新 URL 状态失败: {}", e.getMessage());
        }
    }

    @Override
    public UrlStatus getStatus(Request request) {
        if (request == null || request.getUrl() == null) return UrlStatus.PENDING;
        try {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM spider_url_store WHERE url = ? LIMIT 1",
                    String.class, request.getUrl()
            );
            return status != null ? UrlStatus.valueOf(status) : UrlStatus.PENDING;
        } catch (Exception e) {
            return UrlStatus.PENDING;
        }
    }

    @Override
    public void remove(Request request) {
        if (request == null || request.getUrl() == null) return;
        try {
            jdbcTemplate.update("DELETE FROM spider_url_store WHERE url = ?", request.getUrl());
        } catch (Exception e) {
            log.warn("[DatabaseUrlStore] 删除 URL 失败: {}", e.getMessage());
        }
    }

    @Override
    public void clear() {
        try {
            jdbcTemplate.update("DELETE FROM spider_url_store");
        } catch (Exception e) {
            log.warn("[DatabaseUrlStore] 清空 URL 存储失败: {}", e.getMessage());
        }
    }

    @Override
    public long count() {
        try {
            Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spider_url_store", Long.class);
            return cnt != null ? cnt : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long extractTaskId(Request request) {
        Object taskId = request.getExtra("spider.platformTaskId");
        if (taskId == null) return null;
        try { return Long.parseLong(taskId.toString()); } catch (NumberFormatException e) { return null; }
    }
}
