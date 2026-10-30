package com.chua.starter.template.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 来源模板同步器注册表
 *
 * <p>收割上下文里的全部 {@link SourceSyncer}，提供：
 * 启动时全量同步、按来源刷新、按来源统计。多个来源的同步器均通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动加入宿主上下文（消息 / 教育模块各提供一个）。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
public class SourceSyncerRegistry {

    /** 所有已注册的同步器，按 source 排序 */
    private final List<SourceSyncer> syncerList = new CopyOnWriteArrayList<>();

    /** Source → 同步器 */
    private final Map<String, SourceSyncer> syncerMap = new ConcurrentHashMap<>();

    /** 来源计数：source → 库中该来源的模板数（最后充满的统计） */
    private final Map<String, Integer> countMap = new ConcurrentHashMap<>();

    public SourceSyncerRegistry(List<SourceSyncer> syncers) {
        if (syncers == null || syncers.isEmpty()) {
            log.info("[Template] 未发现来源模板同步器，库内仅展示自行创建的模板");
            return;
        }
        for (SourceSyncer syncer : syncers) {
            if (syncer == null) {
                continue;
            }
            String source = syncer.source();
            if (source == null || source.trim().isEmpty()) {
                log.warn("[Template] SourceSyncer 返回的来源标识为空，已忽略: {}", syncer.getClass().getName());
                continue;
            }
            source = source.trim();
            if (syncerMap.putIfAbsent(source, syncer) != null) {
                log.warn("[Template] 来源 {} 已注册同步器，忽略重复: {}", source, syncer.getClass().getName());
                continue;
            }
            syncerList.add(syncer);
            log.info("[Template] 注册来源同步器: source={} class={}", source, syncer.getClass().getName());
        }
        syncerList.sort((a, b) -> a.source().compareTo(b.source()));
    }

    /**
     * 启动时全量同步（由各来源模块的同步器自行修复全量）
     */
    public void syncAll() throws Exception {
        if (syncerList.isEmpty()) {
            log.info("[Template] 无来源同步器，跳过启动同步");
            return;
        }
        log.info("[Template] 启动全量来源同步，来源数: {}", syncerList.size());
        for (SourceSyncer syncer : syncerList) {
            try {
                int count = syncer.sync();
                countMap.put(syncer.source(), count);
                log.info("[Template] 来源 {} 同步完毕，库中模板数: {}", syncer.source(), count);
            } catch (Exception e) {
                log.error("[Template] 来源同步失败: source={}", syncer.source(), e);
            }
        }
        log.info("[Template] 来源同步完成，来源数: {}, 各来源模板数: {}", syncerList.size(), countMap);
    }

    /**
     * 按来源刷新（启动热刷新按钮用）
     *
     * @param source 来源标识（message / paper），空 = 全部
     * @return source → 同步后该来源在库中的模板数量
     */
    public Map<String, Integer> sync(String source) {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        List<SourceSyncer> list = source == null
                ? syncerList
                : syncerList.stream()
                .filter(s -> source.equalsIgnoreCase(s.source()))
                .toList();
        for (SourceSyncer syncer : list) {
            try {
                int count = syncer.sync();
                result.put(syncer.source(), count);
                countMap.put(syncer.source(), count);
            } catch (Exception e) {
                log.error("[Template] 来源同步失败: source={}", syncer.source(), e);
            }
        }
        return result;
    }

    /**
     * 按来源查询已注册的同步器
     *
     * @param source 来源标识
     * @return 同步器（未找到返回 null）
     */
    public SourceSyncer get(String source) {
        return source == null ? null : syncerMap.get(source);
    }

    /**
     * 当前已同步的来源数
     *
     * @return 来源数
     */
    public int size() {
        return syncerList.size();
    }

    /**
     * 来源计数：source → 库中该来源的模板数（最后充满的统计）
     *
     * @return source → 模板数
     */
    public Map<String, Integer> sourceCountMap() {
        return countMap;
    }

    /**
     * 启动时全量来源同步器（无同步器时不执行源行修复，只打日志）
     *
     * @return 应用启动器
     */
    @Bean
    public ApplicationRunner sourceSyncRunner() {
        return args -> {
            if (syncerList.isEmpty()) {
                log.info("[Template] 未发现来源同步器（message / paper），跳过启动同步");
                return;
            }
            try {
                syncAll();
            } catch (Exception e) {
                log.error("[Template] 启动全量来源同步失败", e);
            }
        };
    }
}
