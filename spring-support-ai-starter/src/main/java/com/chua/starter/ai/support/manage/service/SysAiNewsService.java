// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.news.AiHotNewsFetcher;
import com.chua.common.support.ai.news.AiHotNewsItem;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.mapper.SysAiNewsMapper;
import com.chua.starter.ai.support.manage.store.SysAiNews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AIHOT 精选资讯同步与查询。
 *
 * <p>从 {@code https://aihot.news/feed.xml}（RSS 2.0，无需 Key）抓取最新精选，
 * 按 {@code link} 唯一键 upsert 进 {@code sys_ai_news}：同一条资讯反复同步只更新，
 * 不重复插入。单条落库失败只记日志跳过，不影响其余条目（逐条幂等，无需整批事务
 * —— 抓取是外部 HTTP 调用，不宜持有数据库事务跨网络等待）。</p>
 *
 * <p>查询侧提供发布时间倒序的 {@code latest}；表为空时（新库首次访问）
 * {@code latestWithBootstrap} 会顺带做一次懒同步，避免「面板永远空白、
 * 又没有入口触发同步」的死锁。</p>
 *
 * @since 4.0.0.70
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiNewsService {

    /**
     * 摘要入库截断长度（TEXT 列足够，这里只防异常超长描述）
     */
    private static final int SUMMARY_MAX_LEN = 2000;

    /**
     * 懒同步失败后的最小重试间隔：10 分钟（避免 aihot.news 不可达时每次查询都白等 25s 超时）
     */
    private static final long BOOTSTRAP_RETRY_INTERVAL = 10L * 60L * 1000L;

    private final SysAiNewsMapper newsMapper;
    private final AiHotNewsFetcher fetcher = new AiHotNewsFetcher();

    /**
     * 上次懒同步尝试时间（含失败），配合 {@link #BOOTSTRAP_RETRY_INTERVAL} 限频
     */
    private volatile long lastBootstrapAt = 0L;

    /**
     * 从 AIHOT RSS 同步最新资讯（按 link 去重 upsert）。
     *
     * @return 统计：inserted（新增）/ updated（更新）/ total（来源条数）
     */
    public Map<String, Integer> syncFromAiHot() {
        List<AiHotNewsItem> items = fetcher.fetch();
        Map<String, Integer> result = new HashMap<>(4);
        result.put("total", items.size());
        int inserted = 0;
        int updated = 0;
        if (items.isEmpty()) {
            log.info("[AiNews] AIHOT 资讯同步：来源无数据（total=0）");
            result.put("inserted", 0);
            result.put("updated", 0);
            return result;
        }
        long now = System.currentTimeMillis();
        for (AiHotNewsItem item : items) {
            if (item == null || StringUtils.isBlank(item.getLink()) || StringUtils.isBlank(item.getTitle())) {
                continue;
            }
            String link = item.getLink().trim();
            String title = truncate(item.getTitle().trim(), 500);
            String summary = truncate(item.getDescriptionText(), SUMMARY_MAX_LEN);
            Long publishedAt = item.getPublishedAt();
            try {
                SysAiNews cur = newsMapper.selectOne(new LambdaQueryWrapper<SysAiNews>()
                        .eq(SysAiNews::getLink, link)
                        .last("LIMIT 1"));
                if (cur == null) {
                    SysAiNews entity = new SysAiNews();
                    entity.setTitle(title);
                    entity.setLink(link);
                    entity.setOriginUrl(item.getOriginUrl());
                    entity.setSummary(summary);
                    entity.setCategory(truncate(item.getCategory(), 64));
                    entity.setSource(AiHotNewsFetcher.SOURCE);
                    entity.setPublishedAt(publishedAt);
                    entity.setCreateTime(now);
                    entity.setUpdateTime(now);
                    newsMapper.insert(entity);
                    inserted++;
                } else if (isChanged(cur, title, summary, item, publishedAt)) {
                    cur.setTitle(title);
                    cur.setOriginUrl(item.getOriginUrl());
                    cur.setSummary(summary);
                    cur.setCategory(truncate(item.getCategory(), 64));
                    cur.setPublishedAt(publishedAt);
                    cur.setUpdateTime(now);
                    newsMapper.updateById(cur);
                    updated++;
                }
            } catch (Exception e) {
                log.warn("[AiNews] 单条资讯落库失败 link={}: {}", link, e.getMessage());
            }
        }
        log.info("[AiNews] AIHOT 资讯同步完成：来源 {} 条，新增 {}，更新 {}", items.size(), inserted, updated);
        result.put("inserted", inserted);
        result.put("updated", updated);
        return result;
    }

    /**
     * 最近资讯（发布时间倒序，同秒按 id 倒序稳定排序）。
     *
     * @param limit 条数（1~100，越界取边界）
     * @return 资讯列表
     */
    public List<SysAiNews> latest(int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        return newsMapper.selectList(new LambdaQueryWrapper<SysAiNews>()
                .orderByDesc(SysAiNews::getPublishedAt)
                .orderByDesc(SysAiNews::getId)
                .last("LIMIT " + safe));
    }

    /**
     * 最近资讯；表为空时懒同步一次再查（新库首次访问即可见，限频防雪崩）。
     *
     * @param limit 条数
     * @return 资讯列表
     */
    public List<SysAiNews> latestWithBootstrap(int limit) {
        List<SysAiNews> rows = latest(limit);
        if (!rows.isEmpty()) {
            return rows;
        }
        synchronized (this) {
            rows = latest(limit);
            if (!rows.isEmpty()) {
                return rows;
            }
            long now = System.currentTimeMillis();
            if (now - lastBootstrapAt < BOOTSTRAP_RETRY_INTERVAL) {
                return rows;
            }
            lastBootstrapAt = now;
            try {
                syncFromAiHot();
            } catch (Exception e) {
                log.warn("[AiNews] 懒同步失败（面板本次为空）: {}", e.getMessage());
            }
            return latest(limit);
        }
    }

    /**
     * 判断同步内容是否变化（避免无意义的重复 UPDATE）。
     *
     * @param cur         库中现有行
     * @param title       新标题
     * @param summary     新摘要
     * @param item        来源条目
     * @param publishedAt 新发布时间
     * @return 有变化返回 true
     */
    private boolean isChanged(SysAiNews cur, String title, String summary,
                              AiHotNewsItem item, Long publishedAt) {
        return !equalsSafe(cur.getTitle(), title)
                || !equalsSafe(cur.getSummary(), summary)
                || !equalsSafe(cur.getOriginUrl(), item.getOriginUrl())
                || !equalsSafe(cur.getCategory(), item.getCategory())
                || !equalsSafe(cur.getPublishedAt(), publishedAt);
    }

    /**
     * equalsSafe
     * @param a 参数
     * @param b 参数
     * @return 返回值
     */
    private static boolean equalsSafe(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * 截断到指定长度（null 安全）。
     *
     * @param text      文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
