// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.store;

import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectRecordEntry;
import com.chua.starter.collect.engine.CollectSink;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.entity.CollectTaskLog;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.mapper.CollectTaskLogMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.service.CollectFieldService;
import com.chua.starter.collect.support.CollectJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行过程的数据库落库通道。
 *
 * <p>一次任务一个实例，由执行服务创建并注入 {@link CollectContext}。节点日志即时写入，
 * 计量按阈值批量回写任务行，字段定义在任务收尾时统一登记，避免每条记录都查一次元数据表。
 * 任何写库失败只记警告，不能把采集流程带崩。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class CollectDatabaseSink implements CollectSink {

    /**
     * 文本列宽，超出即截断。
     */
    private static final int COLUMN_LIMIT = 2000;

    /**
     * 业务主键列宽，与 {@code collect_record.collect_record_key} 的 DDL 保持一致。
     *
     * <p>主键不允许截断：截断后两个不同的长主键会落到同一前缀上，同键覆盖会静默吃掉
     * 一条记录，去重也会误判成已采过。超长由输出节点在落库前直接拒绝，把错误暴露在
     * 编排期，而不是等到数据丢了才发现。</p>
     */
    public static final int KEY_LIMIT = 255;

    /**
     * 两次计量回写的最小间隔毫秒。
     */
    private static final long FLUSH_INTERVAL_MS = 2000L;

    /**
     * 流程ID。
     */
    private final Integer flowId;

    /**
     * 任务ID。
     */
    private final Long taskId;

    /**
     * 是否把采集结果写入结果表。
     */
    private final boolean persist;

    /**
     * 节点日志表访问。
     */
    private final CollectTaskLogMapper taskLogMapper;

    /**
     * 结果表访问。
     */
    private final CollectRecordMapper recordMapper;

    /**
     * 任务表访问，用于回写计量。
     */
    private final CollectTaskMapper taskMapper;

    /**
     * 字段定义服务。
     */
    private final CollectFieldService fieldService;

    /**
     * 本次任务的上下文，创建后注入。
     */
    private volatile CollectContext context;

    /**
     * 上次已回写的节点执行数。
     */
    private final AtomicInteger flushedNodes = new AtomicInteger();

    /**
     * 上次已回写的抓取数。
     */
    private final AtomicInteger flushedRequests = new AtomicInteger();

    /**
     * 上次已回写的记录数。
     */
    private final AtomicInteger flushedRecords = new AtomicInteger();

    /**
     * 上次计量回写时间。
     */
    private volatile long lastFlushAt = System.currentTimeMillis();

    /**
     * 节点ID到本次出现的字段名集合。
     */
    private final Map<String, Set<String>> fieldNames = new ConcurrentHashMap<>();

    /**
     * 绑定执行上下文，用于读取计量计数。
     *
     * @param collectContext 执行上下文
     */
    public void attach(CollectContext collectContext) {
        this.context = collectContext;
        this.lastFlushAt = System.currentTimeMillis();
    }

    @Override
    public void nodeLog(CollectNode node, int iteration, String status, long costMs, String message, String preview) {
        writeNodeLog(flowId, node, iteration, status, costMs, message, preview);
    }

    @Override
    public CollectSink forFlow(Integer ownedFlowId) {
        return ownedFlowId == null || ownedFlowId.equals(flowId) ? this : new FlowLabelledSink(this, ownedFlowId);
    }

    /**
     * 写一条节点日志。
     *
     * @param owner     这段日志出自哪条流程，草稿执行为 null
     * @param node      节点
     * @param iteration 循环轮次
     * @param status    结果状态
     * @param costMs    耗时毫秒
     * @param message   摘要或异常信息
     * @param preview   出参预览
     */
    private void writeNodeLog(Integer owner, CollectNode node, int iteration, String status,
                              long costMs, String message, String preview) {
        if (node == null) {
            return;
        }
        try {
            CollectTaskLog entity = new CollectTaskLog();
            entity.setCollectTaskLogTaskId(taskId);
            entity.setCollectTaskLogFlowId(owner);
            entity.setCollectTaskLogNodeId(node.id());
            entity.setCollectTaskLogNodeName(cut(node.name(), 128));
            entity.setCollectTaskLogNodeShape(cut(node.shape(), 64));
            entity.setCollectTaskLogIteration(iteration);
            entity.setCollectTaskLogStatus(status);
            entity.setCollectTaskLogCost(costMs);
            entity.setCollectTaskLogMessage(cut(message, COLUMN_LIMIT));
            entity.setCollectTaskLogOutput(cut(preview, COLUMN_LIMIT));
            taskLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("[collect] 节点日志写入失败 task={} node={} err={}", taskId, node.id(), ex.getMessage());
        }
        flushProgress(false);
    }

    @Override
    public void record(CollectRecordEntry entry) {
        if (entry == null) {
            return;
        }
        String nodeId = entry.getNodeId() == null ? "" : entry.getNodeId();
        Set<String> names = fieldNames.computeIfAbsent(nodeId, key -> ConcurrentHashMap.newKeySet());
        Map<String, Object> payload = new LinkedHashMap<>();
        for (CollectParamDTO cell : entry.getCells()) {
            if (cell.name() == null || cell.name().isBlank()) {
                continue;
            }
            String name = cell.name().trim();
            names.add(name);
            payload.put(name, cell.value());
        }
        if (!persist) {
            return;
        }
        try {
            CollectRecord entity = new CollectRecord();
            entity.setCollectRecordFlowId(flowId);
            entity.setCollectRecordTaskId(taskId);
            entity.setCollectRecordNodeId(cut(nodeId, 64));
            // 主键与查重必须用同一个值：写入截断而查重不截断，同键覆盖会永远匹配不上
            String key = entry.getKey() == null || entry.getKey().isBlank()
                    ? null : cut(entry.getKey().trim(), KEY_LIMIT);
            entity.setCollectRecordKey(key);
            entity.setCollectRecordUrl(cut(entry.getUrl(), 1000));
            entity.setCollectRecordPayload(CollectJson.toJson(payload));
            Long existsId = key == null ? null : recordMapper.selectIdByKey(flowId, nodeId, key);
            if (existsId == null) {
                recordMapper.insert(entity);
            } else {
                entity.setCollectRecordId(existsId);
                recordMapper.updateById(entity);
            }
        } catch (Exception ex) {
            log.warn("[collect] 结果写入失败 task={} node={} err={}", taskId, nodeId, ex.getMessage());
        }
    }

    @Override
    public void trace(String message) {
        log.info("[collect] task={} {}", taskId, message);
    }

    /**
     * 任务收尾：回写全部计量并登记字段定义。
     */
    public void close() {
        flushProgress(true);
        if (!persist) {
            fieldNames.clear();
            return;
        }
        for (Map.Entry<String, Set<String>> entry : fieldNames.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            try {
                fieldService.registerMissing(flowId, entry.getKey(), new ArrayList<>(entry.getValue()));
            } catch (Exception ex) {
                log.warn("[collect] 字段登记失败 task={} node={} err={}",
                        taskId, entry.getKey(), ex.getMessage());
            }
        }
        fieldNames.clear();
    }

    /**
     * 按上下文计数回写任务行的增量计量。
     *
     * @param force 是否忽略间隔立即回写
     */
    private void flushProgress(boolean force) {
        CollectContext current = context;
        if (current == null || taskId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastFlushAt < FLUSH_INTERVAL_MS) {
            return;
        }
        lastFlushAt = now;
        int nodes = current.getNodeCount().get();
        int requests = current.getRequestCount().get();
        int records = current.getRecordCount().get();
        int dn = nodes - flushedNodes.getAndSet(nodes);
        int dr = requests - flushedRequests.getAndSet(requests);
        int dc = records - flushedRecords.getAndSet(records);
        if (dn <= 0 && dr <= 0 && dc <= 0) {
            return;
        }
        try {
            taskMapper.accumulate(taskId, dn, dr, dc);
        } catch (Exception ex) {
            log.warn("[collect] 计量回写失败 task={} err={}", taskId, ex.getMessage());
        }
    }

    /**
     * 文本截断到列宽。
     *
     * @param text 文本
     * @param max  列宽
     * @return 截断后的文本
     */
    private String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    /**
     * 只改节点日志流程归属的视图。
     *
     * <p>采集记录与计量仍然交给宿主通道按宿主流程记账，因此这里只接管 nodeLog。</p>
     *
     * @param owner       宿主通道，被委托执行实际写库的 {@code CollectDatabaseSink} 实例（一次采集任务一个实例）。
     *                    不允许为 {@code null}（规范构造器强制）。本视图不持有任何状态，{@code record} 与 {@code trace}
     *                    全部原样转发给它，因此结果行与任务计量仍归属宿主自己的 flowId
     * @param ownedFlowId 本视图归属的流程标识，即节点日志要写入的 {@code collect_task_log_flow_id}。不允许为 {@code null}
     *                    （规范构造器强制）。由外层 {@code forFlow(Integer)} 在传入的 flowId 与宿主 flowId 不相等时创建本视图，
     *                    相等时直接复用宿主本身不额外包装
     */
    private record FlowLabelledSink(CollectDatabaseSink owner, Integer ownedFlowId) implements CollectSink {

        /**
         * 规范构造器：两个组件都空值敌对。
         *
         * <p>value class 前置条件——引用组件不允许为 null。</p>
         *
         * @param owner       宿主通道
         * @param ownedFlowId 本视图归属的流程标识
         */
        public FlowLabelledSink {
            owner = Objects.requireNonNull(owner, "owner 不能为 null");
            ownedFlowId = Objects.requireNonNull(ownedFlowId, "ownedFlowId 不能为 null");
        }

        @Override
        public void nodeLog(CollectNode node, int iteration, String status, long costMs,
                            String message, String preview) {
            owner.writeNodeLog(ownedFlowId, node, iteration, status, costMs, message, preview);
        }

        @Override
        public void record(CollectRecordEntry entry) {
            owner.record(entry);
        }

        @Override
        public void trace(String message) {
            owner.trace(message);
        }
    }
}