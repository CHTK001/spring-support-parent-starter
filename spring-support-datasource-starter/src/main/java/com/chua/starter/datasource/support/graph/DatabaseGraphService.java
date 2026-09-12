package com.chua.starter.datasource.support.graph;

import com.chua.common.support.lang.document.DocumentData;
import com.chua.common.support.lang.document.DocumentParser;
import com.chua.starter.datasource.properties.DocumentProperties;
import com.chua.starter.datasource.support.DataSourceDocumentService;
import com.chua.starter.datasource.support.TablePatternMatcher;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据库关系图谱服务
 * <p>提供：</p>
 * <ul>
 *     <li>{@link #apply()} 强制重建（每次最新）</li>
 *     <li>{@link #preview()} 命中缓存则返回</li>
 *     <li>输出格式：{@link GraphPayload}，可直接喂给 relation-graph</li>
 * </ul>
 *
 * <h3>节点 data 字段结构：</h3>
 * <pre>
 * {
 *   schema, type, remark,
 *   columnCount, columnNames, primaryKeys,
 *   hasForeignKey, inRefCount, outRefCount,
 *   triggers: [{name, event, timing, status, granularity}],
 *   procedures: [{name, type, schema}]
 * }
 * </pre>
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class DatabaseGraphService {

    /** 数据来源 */
    private final DataSource dataSource;
    /** 属性 */
    private final DocumentProperties properties;

    /** 锁 */
    private final ReentrantLock lock = new ReentrantLock();
    private volatile GraphPayload cached; // 缓存
    private volatile long cachedAt; // 缓存at

    /**
      * 创建 database图计算服务 实例
     * @param dataSource 数据源
     * @param properties 文档属性
     * @param properties 属性
     */
    public DatabaseGraphService(DataSource dataSource, DocumentProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * 应用
     *
     * @return apply的结果
     */
    public GraphPayload apply() {
        lock.lock();
        try {
            GraphPayload p = doBuild();
            this.cached = p;
            this.cachedAt = System.currentTimeMillis();
            log.info("数据库图谱已刷新: {} 张表，{} 条边，{} 个触发器，{} 个存储过程",
                    p.nodes.size(), p.lines.size(), p.triggerCount, p.procedureCount);
            return p;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Preview
     *
     * @return preview的结果
     */
    public GraphPayload preview() {
        if (properties.isCacheable() && cached != null) {
            return cached;
        }
        return apply();
    }

    /**
     * 获取缓存at
     *
     * @return 获取缓存at的结果
     */
    public long getCachedAt() {
        return cachedAt;
    }

    // ============================ 内部构建 ============================

    /**
     * 执行构建
     *
     * @return 执行构建的结果
     */
    private GraphPayload doBuild() {
        // 1) 读取表 + FK 边
        TableGraphReader tableReader = new TableGraphReader(dataSource);
        TableGraphReader.GraphReadResult tableResult;
        try {
            tableResult = tableReader.read();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "读取表结构失败" "读取表结构失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("读取表结构失败", e);
        }

        // 2) 表过滤
        List<TablePatternMatcher.Matcher> includes = TablePatternMatcher.build(properties.getIncludeTables());
        List<TablePatternMatcher.Matcher> excludes = TablePatternMatcher.build(properties.getExcludeTables());
        Map<String, TableNodeInfo> tableMap = new LinkedHashMap<>();
        for (TableNodeInfo t : tableResult.tables) {
            if (!includes.isEmpty() && !TablePatternMatcher.anyMatch(includes, t.getName())) {
                continue;
            }
            if (!excludes.isEmpty() && TablePatternMatcher.anyMatch(excludes, t.getName())) {
                continue;
            }
            tableMap.put(t.getName(), t);
        }

        // 3) 读取触发器 / 存储过程，并按表名串联
        List<TriggerInfo> triggers = List.of();
        List<ProcedureInfo> procedures = List.of();
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection conn = dataSource.getConnection()) {
            TriggerReader.DatabaseDialect dialect = TriggerReader.DatabaseDialect.resolve(conn.getMetaData());
            triggers = new TriggerReader(dialect).readAll(conn);
            procedures = new ProcedureReader(dialect).readAll(conn);
        } catch (Exception e) {
            log.warn("读取触发器/存储过程失败：{}", e.getMessage());
        }

        for (TriggerInfo t : triggers) {
            TableNodeInfo node = tableMap.get(t.getTableName());
            if (node != null) {
                node.getTriggers().add(t);
            }
        }
        for (ProcedureInfo p : procedures) {
            // 1) 严格匹配：procedure 名 = 表名 或 procedure 名以 表名_ 开头
 // 2) 启发式：扫描 procedure 定义里出现的 创建/插入/更新/删除/从/连接 + 表名（此处不解析 SQL）
            String matched = matchTable(p, tableMap.keySet());
            if (matched != null) {
                p.setTableName(matched);
                TableNodeInfo node = tableMap.get(matched);
                if (node != null) {
                    node.getProcedures().add(p);
                }
            } else {
 // 无归属：归到 synthetic 节点 "（未关联）" 之外的根节点不归任何表，这里直接挂在 模式 级
                // 简化处理：归到一个虚拟节点 "__procedures__"
                // 当前实现选择：放到 procedures_unbound 列表，由前端决定如何展示
            }
        }

        // 4) 过滤 FK 边：两端都必须存在
        List<GraphEdge> edges = new ArrayList<>();
        for (GraphEdge e : tableResult.edges) {
            if (tableMap.containsKey(e.getFrom()) && tableMap.containsKey(e.getTo())) {
                edges.add(e);
            }
        }

 // 5) 组装 relation-图计算 payload
        List<Map<String, Object>> nodes = new ArrayList<>();
        String rootId = null;
        int maxDegree = -1;
        for (TableNodeInfo t : tableMap.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", t.getName());
            node.put("text", t.getName());
            node.put("width", nodeWidth(t));
            node.put("height", nodeHeight(t));
 // 节点 数据：所有表详情，供前端 slot 渲染
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schema", t.getSchema());
            data.put("type", t.getType());
            data.put("remark", t.getRemark());
            data.put("columnCount", t.getColumnCount());
            data.put("columnNames", t.getColumnNames());
            data.put("primaryKeys", t.getPrimaryKeys());
            data.put("hasForeignKey", t.isHasForeignKey());
            data.put("inRefCount", t.getInRefCount());
            data.put("outRefCount", t.getOutRefCount());
            data.put("triggers", t.getTriggers());
            data.put("procedures", t.getProcedures());
            data.put("triggerCount", t.getTriggers().size());
            data.put("procedureCount", t.getProcedures().size());
            node.put("data", data);
            nodes.add(node);

            int degree = t.getInRefCount() + t.getOutRefCount();
            if (degree > maxDegree) {
                maxDegree = degree;
                rootId = t.getName();
            }
        }

        // 未归属的存储过程/函数挂到虚拟节点
        List<ProcedureInfo> unboundProcedures = new ArrayList<>();
        for (ProcedureInfo p : procedures) {
            if (p.getTableName() == null) {
                unboundProcedures.add(p);
            }
        }
        if (!unboundProcedures.isEmpty()) {
            Map<String, Object> virtual = new LinkedHashMap<>();
            virtual.put("id", "__unbound__");
            virtual.put("text", "（未关联）");
            virtual.put("width", 140);
            virtual.put("height", 60);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "VIRTUAL");
            data.put("procedures", unboundProcedures);
            data.put("remark", "未归属到具体表的存储过程/函数");
            virtual.put("data", data);
            nodes.add(virtual);
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        for (GraphEdge e : edges) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("from", e.getFrom());
            line.put("to", e.getTo());
            line.put("text", e.getText());
            line.put("data", Map.of(
                    "fkName", e.getFkName() == null ? "" : e.getFkName(),
                    "updateRule", e.getUpdateRule() == null ? "" : e.getUpdateRule(),
                    "deleteRule", e.getDeleteRule() == null ? "" : e.getDeleteRule()));
            lines.add(line);
        }

        GraphPayload payload = new GraphPayload();
        payload.rootId = rootId;
        payload.nodes = nodes;
        payload.lines = lines;
        payload.triggerCount = triggers.size();
        payload.procedureCount = procedures.size();
        payload.cachedAt = cachedAt == 0 ? System.currentTimeMillis() : cachedAt;
        return payload;
    }

    /**
      * 启发式匹配：procedure 名直接 = 表名；或 形如 {@code proc_table_action}；或 模式 内表名前缀
     * @param p p
     * @param tables tables
     * @return 匹配table的结果
     */
    private static String matchTable(ProcedureInfo p, java.util.Set<String> tables) {
        if (p.getName() == null) {
            return null;
        }
        String name = p.getName().toLowerCase();
        if (tables.contains(p.getName())) {
            return p.getName();
        }
        for (String t : tables) {
            String tLower = t.toLowerCase();
            if (name.equals(tLower) || name.startsWith(tLower + "_") || name.startsWith(tLower + "$")) {
                return t;
            }
        }
        return null;
    }

    /**
     * 节点宽度：表名长度 + 是否有 FK 加宽
     * @param t t
     * @return 节点width的结果
     */
    private static int nodeWidth(TableNodeInfo t) {
        int base = Math.max(120, t.getName().length() * 10 + 40);
        if (t.isHasForeignKey() || t.getInRefCount() > 0) {
            base += 20;
        }
        return Math.min(base, 280);
    }

    /**
     * 节点高度：基础 60 + 触发器 * 16 + 存储过程 * 16（封顶）
     * @param t t
     * @return 节点height的结果
     */
    private static int nodeHeight(TableNodeInfo t) {
        int h = 60;
        h += Math.min(t.getTriggers().size(), 5) * 16;
        h += Math.min(t.getProcedures().size(), 5) * 16;
        if (t.isHasForeignKey()) {
            h += 8;
        }
        return Math.min(h, 200);
    }

    /**
      * relation-图计算 兼容的 payload
     * @author CH
     * @since 4.0.0
     */
    public static class GraphPayload {
        /** 根级标识 */
        public String rootId;
        public List<Map<String, Object>> nodes; // 节点
        public List<Map<String, Object>> lines; // 线
        /** Trigger数量 */
        public int triggerCount;
        /** Procedure数量 */
        public int procedureCount;
        /** 缓存at */
        public long cachedAt;
    }
}
