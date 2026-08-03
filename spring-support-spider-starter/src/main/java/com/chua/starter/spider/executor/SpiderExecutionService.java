package com.chua.starter.spider.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.spider.support.Spider;
import com.chua.spider.support.model.SpiderResult;
import com.chua.spider.support.model.SpiderSite;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.entity.SpiderExecutionRecord;
import com.chua.starter.spider.enums.SpiderExecutionStatus;
import com.chua.starter.spider.mapper.SpiderDataRecordMapper;
import com.chua.starter.spider.mapper.SpiderDefinitionMapper;
import com.chua.starter.spider.mapper.SpiderExecutionRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 爬虫执行服务。
 *
 * <p>将 {@code Spider} 执行引擎与数据库打通：
 * <ul>
 *   <li>{@link #start(String)} 根据爬虫编码加载定义，创建执行记录，异步运行 Spider</li>
 *   <li>{@link #stop(String)} 停止指定批次的执行</li>
 *   <li>{@link #status(String)} 查询执行进度</li>
 *   <li>采集的每条数据实时写入 {@code spider_data_record} 表</li>
 *   <li>执行结束/异常时更新 {@code spider_execution_record} 状态</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderExecutionService {

    private final SpiderDefinitionMapper definitionMapper;
    private final SpiderExecutionRecordMapper executionRecordMapper;
    private final SpiderDataRecordMapper dataRecordMapper;
    private final SpiderExecutionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动爬虫执行。
     *
     * @param spiderCode 爬虫业务编码
     * @return 执行批次号，外部按此轮询进度
     */
    public String start(String spiderCode) {
        return start(spiderCode, "MANUAL");
    }

    /**
     * 启动爬虫执行（指定触发方式）。
     *
     * @param spiderCode 爬虫业务编码
     * @param triggerType 触发方式：MANUAL / CRON
     * @return 执行批次号
     */
    @Transactional(rollbackFor = Exception.class)
    public String start(String spiderCode, String triggerType) {
        SpiderDefinition definition = findByCode(spiderCode);
        if (definition == null) {
            throw new IllegalArgumentException("爬虫定义不存在: " + spiderCode);
        }
        if (definition.getSpiderStatus() == null || definition.getSpiderStatus() == 0) {
            throw new IllegalStateException("爬虫已禁用: " + spiderCode);
        }

        String executionNo = UUID.randomUUID().toString().replace("-", "");

        // 写入执行记录
        SpiderExecutionRecord record = new SpiderExecutionRecord();
        record.setSpiderExecutionNo(executionNo);
        record.setSpiderDefinitionCode(spiderCode);
        record.setSpiderTriggerType(triggerType);
        record.setSpiderExecutionStatus(SpiderExecutionStatus.RUNNING.getCode());
        record.setSpiderStartedAt(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        executionRecordMapper.insert(record);

        // 构建执行上下文
        SpiderExecutionContext context = SpiderExecutionContext.builder()
                .executionRecordId(record.getSpiderId())
                .spiderCode(spiderCode)
                .executionNo(executionNo)
                .status(SpiderExecutionStatus.RUNNING.getCode())
                .triggerType(triggerType)
                .startedAt(System.currentTimeMillis())
                .build();
        registry.register(context);

        // 异步执行
        Thread worker = new Thread(() -> runSpider(definition, context), "spider-" + executionNo.substring(0, 8));
        worker.setDaemon(true);
        worker.start();

        log.info("[Spider] 启动执行: spiderCode={}, executionNo={}", spiderCode, executionNo);
        return executionNo;
    }

    /**
     * 真正执行 Spider 的工作方法。
     */
    private void runSpider(SpiderDefinition definition, SpiderExecutionContext context) {
        try {
            SpiderSite site = SpiderSite.builder()
                    .domain(definition.getSpiderSiteHost())
                    .maxDepth(definition.getSpiderMaxDepth() == null ? 0 : definition.getSpiderMaxDepth())
                    .maxPages(definition.getSpiderMaxPages() == null ? 50 : definition.getSpiderMaxPages())
                    .build();

            Spider.Builder builder = Spider.create()
                    .site(site)
                    .addUrl(definition.getSpiderEntryUrl())
                    .threads(3)
                    .pipeline((java.util.function.Consumer<SpiderResult>) result -> onPipeline(definition, context, result));

            // 可选：深度采集
            if (definition.getSpiderCollectDeepUrl() != null && definition.getSpiderCollectDeepUrl() == 1) {
                // 内置 linkExtractor 会自动跟踪，无需额外配置
            }

            Spider spider = builder.build();
            context.setSpider(spider);
            spider.runSync();
            context.setStatus(SpiderExecutionStatus.SUCCESS.getCode());
            markFinished(context, null);
        } catch (Throwable t) {
            log.error("[Spider] 执行失败: executionNo={}", context.getExecutionNo(), t);
            context.setStatus(SpiderExecutionStatus.FAILED.getCode());
            markFinished(context, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        } finally {
            registry.unregister(context.getExecutionNo());
        }
    }

    /**
     * Pipeline 回调：每抓取到一条数据时实时入库。
     */
    private void onPipeline(SpiderDefinition definition, SpiderExecutionContext context, SpiderResult result) {
        context.getResults().add(result);
        context.setCompletedCount(context.getCompletedCount() + 1);
        context.setCollectedCount(context.getCollectedCount() + 1);
        if (result.getDepth() > context.getMaxDepthReached()) {
            context.setMaxDepthReached(result.getDepth());
        }

        try {
            SpiderDataRecord data = new SpiderDataRecord();
            data.setSpiderDefinitionCode(definition.getSpiderCode());
            data.setSpiderExecutionNo(context.getExecutionNo());
            data.setSpiderTargetUrl(result.getUrl());
            data.setSpiderDataName(result.getTitle());
            data.setSpiderDataPayload(objectMapper.writeValueAsString(toPayload(result)));
            data.setSpiderDataStatus("SUCCESS");
            data.setSpiderDepth(result.getDepth());
            data.setCreateTime(LocalDateTime.now());
            dataRecordMapper.insert(data);
        } catch (Exception e) {
            log.warn("[Spider] 写入 data_record 失败: url={}, err={}", result.getUrl(), e.getMessage());
        }

        // 周期性写回执行进度
        if (context.getCompletedCount() % 5 == 0) {
            updateProgress(context);
        }
    }

    /**
     * 构造数据载荷：标题、纯文本、结构化字段、深度。
     */
    private Map<String, Object> toPayload(SpiderResult result) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", result.getTitle());
        payload.put("text", result.getText());
        payload.put("structured", result.getStructured());
        payload.put("depth", result.getDepth());
        payload.put("extractedAt", result.getExtractedAt());
        payload.put("aiSummary", result.getAiSummary());
        payload.put("aiCategory", result.getAiCategory());
        return payload;
    }

    /**
     * 标记执行结束并更新执行记录。
     */
    private void markFinished(SpiderExecutionContext context, String errorMessage) {
        try {
            LambdaUpdateWrapper<SpiderExecutionRecord> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SpiderExecutionRecord::getSpiderExecutionNo, context.getExecutionNo())
                    .set(SpiderExecutionRecord::getSpiderExecutionStatus, context.getStatus())
                    .set(SpiderExecutionRecord::getSpiderFinishedAt, LocalDateTime.now())
                    .set(SpiderExecutionRecord::getSpiderCollectedCount, context.getCollectedCount())
                    .set(SpiderExecutionRecord::getSpiderUrlCompleted, context.getCompletedCount())
                    .set(SpiderExecutionRecord::getSpiderMaxDepthReached, context.getMaxDepthReached())
                    .set(SpiderExecutionRecord::getSpiderErrorMessage,
                            StringUtils.isBlank(errorMessage) ? null
                                    : (errorMessage.length() > 65000 ? errorMessage.substring(0, 65000) : errorMessage));
            executionRecordMapper.update(null, wrapper);
        } catch (Exception e) {
            log.error("[Spider] 写入执行结果失败: executionNo={}", context.getExecutionNo(), e);
        }
    }

    /**
     * 周期性更新执行进度（未结束时）。
     */
    private void updateProgress(SpiderExecutionContext context) {
        try {
            LambdaUpdateWrapper<SpiderExecutionRecord> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SpiderExecutionRecord::getSpiderExecutionNo, context.getExecutionNo())
                    .set(SpiderExecutionRecord::getSpiderCollectedCount, context.getCollectedCount())
                    .set(SpiderExecutionRecord::getSpiderUrlCompleted, context.getCompletedCount())
                    .set(SpiderExecutionRecord::getSpiderMaxDepthReached, context.getMaxDepthReached());
            executionRecordMapper.update(null, wrapper);
        } catch (Exception e) {
            log.debug("[Spider] 进度更新失败: {}", e.getMessage());
        }
    }

    /**
     * 停止指定批次的执行。
     *
     * @param executionNo 批次号
     * @return 是否成功停止（批次不存在也返回 false）
     */
    public boolean stop(String executionNo) {
        SpiderExecutionContext context = registry.get(executionNo);
        if (context == null) {
            return false;
        }
        Spider spider = context.getSpider();
        if (spider != null) {
            try {
                spider.stop();
            } catch (Exception e) {
                log.warn("[Spider] 调用 stop() 异常: {}", e.getMessage());
            }
        }
        context.setStatus(SpiderExecutionStatus.STOPPED.getCode());
        markFinished(context, "用户手动停止");
        registry.unregister(executionNo);
        return true;
    }

    /**
     * 查询执行进度。
     *
     * @param executionNo 批次号
     * @return 内存中的实时上下文；如已结束会从 DB 重新加载
     */
    public SpiderExecutionContext status(String executionNo) {
        SpiderExecutionContext context = registry.get(executionNo);
        if (context != null) {
            return context;
        }
        // 内存中没有则查 DB
        LambdaQueryWrapper<SpiderExecutionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderExecutionRecord::getSpiderExecutionNo, executionNo);
        SpiderExecutionRecord record = executionRecordMapper.selectOne(wrapper);
        if (record == null) {
            return null;
        }
        return SpiderExecutionContext.builder()
                .executionRecordId(record.getSpiderId())
                .spiderCode(record.getSpiderDefinitionCode())
                .executionNo(executionNo)
                .status(record.getSpiderExecutionStatus())
                .completedCount(record.getSpiderUrlCompleted() == null ? 0 : record.getSpiderUrlCompleted())
                .totalCount(record.getSpiderUrlTotal() == null ? 0 : record.getSpiderUrlTotal())
                .collectedCount(record.getSpiderCollectedCount() == null ? 0 : record.getSpiderCollectedCount())
                .maxDepthReached(record.getSpiderMaxDepthReached() == null ? 0 : record.getSpiderMaxDepthReached())
                .startedAt(record.getCreateTime() == null ? 0L
                        : record.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    /**
     * 按编码查询定义。
     */
    private SpiderDefinition findByCode(String spiderCode) {
        if (spiderCode == null || spiderCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<SpiderDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderDefinition::getSpiderCode, spiderCode);
        wrapper.last("LIMIT 1");
        return definitionMapper.selectOne(wrapper);
    }

    /**
     * 列出所有正在执行的上下文。
     */
    public List<SpiderExecutionContext> listRunning() {
        return List.copyOf(registry.all());
    }
}
