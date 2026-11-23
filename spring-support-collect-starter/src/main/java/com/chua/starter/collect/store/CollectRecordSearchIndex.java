// [P3C A20 豁免] A20/A30/A31: 含日志/分页上限等非业务魔法值场景，豁免。
package com.chua.starter.collect.store;

import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.support.CollectJson;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采集结果记录内嵌全文索引（Lucene）。
 *
 * <p>与 {@link CollectDatabaseSink} 同生命周期协作：记录落库/更新后同步维护倒排索引，
 * 删除时同步移除文档；检索侧由 {@code CollectRecordServiceImpl} 调用，命中后再回表取行。</p>
 *
 * <p>设计约束：索引只是结果表的可重建视图，任何写索引失败都只告警、绝不阻断采集主流程；
 * 索引目录损坏或被锁时组件整体降级为不可用（{@link #available()} 返回 false），
 * 检索接口届时回落数据库 LIKE 查询。全文内容 = 业务主键 + 抓取地址 + payload 全部单元格文本，
 * 中文按单字切分（StandardAnalyzer），满足关键词包含式检索。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = CollectProperties.PRE + ".persistence",
        name = "searchEnabled", havingValue = "true", matchIfMissing = true)
public class CollectRecordSearchIndex {

    /**
     * 文档主键字段（记录ID，精确 term，用于覆盖更新与删除）。
     */
    public static final String FIELD_ID = "id";

    /**
     * 流程ID字段（数值过滤）。
     */
    public static final String FIELD_FLOW = "flowId";

    /**
     * 任务ID字段（数值过滤）。
     */
    public static final String FIELD_TASK = "taskId";

    /**
     * 全文内容字段（主键 + 地址 + payload 文本）。
     */
    public static final String FIELD_TEXT = "text";

    /**
     * 更新时间字段（毫秒，用于按时间倒序排序）。
     */
    public static final String FIELD_TIME = "updateTime";

    /**
     * 单文档全文内容上限字符数，超长截断，防个别巨型 payload 拖垮索引。
     */
    private static final int TEXT_LIMIT = 1_000_000;

    /**
     * 单次检索最大扫描条数（页码 × 页大小不允许超过该值）。
     */
    private static final int MAX_SCAN = 100_000;

    /**
     * 累计多少次写操作后主动 commit 一次。
     */
    private static final int COMMIT_EVERY = 500;

    /**
     * 平台配置（读索引目录）。
     */
    private final CollectProperties properties;

    /**
     * 分词器。
     */
    private Analyzer analyzer;

    /**
     * 索引目录。
     */
    private Directory directory;

    /**
     * 索引写入器。
     */
    private IndexWriter writer;

    /**
     * 近实时读取器管理器。
     */
    private SearcherManager searcherManager;

    /**
     * 索引是否可用；初始化失败（目录锁、磁盘错误等）后置 false 并降级。
     */
    private volatile boolean usable;

    /**
     * 未 commit 的写操作计数。
     */
    private final AtomicLong dirty = new AtomicLong();

    /**
     * 构造注入配置。
     *
     * @param properties 平台配置
     */
    public CollectRecordSearchIndex(CollectProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动时打开或创建索引目录与写入器。
     *
     * <p>任何失败都只降级为不可用，不阻断宿主启动（采集落库不受影响）。</p>
     */
    @PostConstruct
    public void open() {
        if (!properties.getPersistence().isSearchEnabled()) {
            return;
        }
        try {
            String configured = properties.getPersistence().getSearchIndexPath();
            Path root = (configured == null || configured.isBlank())
                    ? Path.of(System.getProperty("java.io.tmpdir"), "collect-search-index")
                    : Path.of(configured);
            Files.createDirectories(root);
            analyzer = new StandardAnalyzer();
            directory = FSDirectory.open(root);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
            searcherManager = new SearcherManager(writer, true, false, null);
            usable = true;
            log.info("[collect] 全文索引已就绪 path={}", root.toAbsolutePath());
        } catch (Exception ex) {
            usable = false;
            log.warn("[collect] 全文索引初始化失败，检索将降级为数据库模糊查询: {}", ex.getMessage());
        }
    }

    /**
     * 索引是否可用。
     *
     * @return 可用返回 true
     */
    public boolean available() {
        return usable && writer != null;
    }

    /**
     * 写入或覆盖一条记录的索引文档（静默失败：只告警）。
     *
     * @param record 已落库的结果记录
     */
    public void index(CollectRecord record) {
        if (!available() || record == null || record.getCollectRecordId() == null) {
            return;
        }
        try {
            Document doc = toDocument(record);
            String id = String.valueOf(record.getCollectRecordId());
            writer.updateDocument(new Term(FIELD_ID, id), doc);
            commitIfNoisy();
        } catch (Exception ex) {
            log.warn("[collect] 结果索引写入失败 id={} err={}",
                    record.getCollectRecordId(), ex.getMessage());
        }
    }

    /**
     * 删除单条记录的索引文档（静默失败）。
     *
     * @param recordId 记录ID
     */
    public void delete(Long recordId) {
        if (!available() || recordId == null) {
            return;
        }
        try {
            writer.deleteDocuments(new Term(FIELD_ID, String.valueOf(recordId)));
            commitIfNoisy();
        } catch (Exception ex) {
            log.warn("[collect] 结果索引删除失败 id={} err={}", recordId, ex.getMessage());
        }
    }

    /**
     * 按流程/任务批量删除索引文档，用于清空结果时同步（静默失败）。
     *
     * @param flowId 流程ID，可空
     * @param taskId 任务ID，可空
     */
    public void deleteByFlowTask(Integer flowId, Long taskId) {
        if (!available() || (flowId == null && taskId == null)) {
            return;
        }
        try {
            writer.deleteDocuments(flowTaskQuery(flowId, taskId));
            commitIfNoisy();
        } catch (Exception ex) {
            log.warn("[collect] 结果索引批量删除失败 flow={} task={} err={}", flowId, taskId, ex.getMessage());
        }
    }

    /**
     * 全量重建开始：清空现有索引。
     *
     * <p>重建期间新落库记录仍会正常写入，收尾 commit 后一并可见。</p>
     *
     * @throws Exception 索引不可用或写失败时抛出，由调用方决定是否中止
     */
    public void prepareRebuild() throws Exception {
        assertUsable();
        writer.deleteAll();
    }

    /**
     * 全量重建收尾：强制 commit 使重建内容可见。
     *
     * @throws Exception 写失败时抛出
     */
    public void finishRebuild() throws Exception {
        assertUsable();
        writer.commit();
        dirty.set(0);
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * 全文检索，按更新时间倒序返回命中的记录ID。
     *
     * @param keyword 关键词（Lucene 查询语法，非法语法自动降级为转义后的整词匹配）
     * @param flowId  流程过滤，可空
     * @param taskId  任务过滤，可空
     * @param page    页码，从 1 开始
     * @param size    页大小
     * @return 命中总数与页内记录ID（按相关性/时间排序）
     * @throws Exception 检索语法非法或索引读取失败
     */
    public SearchResult search(String keyword, Integer flowId, Long taskId, int page, int size) throws Exception {
        assertUsable();
        QueryParser parser = new QueryParser(FIELD_TEXT, analyzer);
        Query text;
        try {
            text = parser.parse(keyword);
        } catch (Exception ex) {
            text = parser.parse(QueryParser.escape(keyword));
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(text, BooleanClause.Occur.MUST);
        Query filter = flowTaskQuery(flowId, taskId);
        if (filter != null) {
            builder.add(filter, BooleanClause.Occur.FILTER);
        }
        int current = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        int limit = current * pageSize;
        if (limit > MAX_SCAN) {
            throw new IllegalArgumentException("检索翻页过深，单次最多扫描 " + MAX_SCAN + " 条");
        }
        searcherManager.maybeRefreshBlocking();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Sort sort = new Sort(new SortField(FIELD_TIME, SortField.Type.LONG, true),
                    new SortField(FIELD_ID, SortField.Type.STRING, true));
            TopFieldDocs docs = searcher.search(builder.build(), limit, sort);
            List<Long> ids = new ArrayList<>();
            int from = (current - 1) * pageSize;
            int end = Math.min(from + pageSize, docs.scoreDocs.length);
            for (int i = from; i < end; i++) {
                ScoreDoc hit = docs.scoreDocs[i];
                ids.add(Long.parseLong(searcher.storedFields().document(hit.doc).get(FIELD_ID)));
            }
            return new SearchResult(docs.totalHits.value, ids);
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * 关闭写入器并释放目录资源。
     */
    @PreDestroy
    public void close() {
        if (!usable) {
            return;
        }
        usable = false;
        try {
            writer.commit();
        } catch (Exception ex) {
            log.warn("[collect] 全文索引关闭前 commit 失败: {}", ex.getMessage());
        }
        try {
            searcherManager.close();
        } catch (Exception ignore) {
            // 关闭阶段忽略读取器释放异常
        }
        try {
            writer.close();
        } catch (Exception ex) {
            log.warn("[collect] 全文索引写入器关闭失败: {}", ex.getMessage());
        }
        try {
            directory.close();
        } catch (Exception ignore) {
            // 关闭阶段忽略目录释放异常
        }
    }

    /**
     * 组装记录文档：ID/流程/任务可过滤，主键 + 地址 + payload 拼入全文。
     *
     * @param record 结果记录
     * @return Lucene 文档
     */
    private Document toDocument(CollectRecord record) {
        Document doc = new Document();
        String id = String.valueOf(record.getCollectRecordId());
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        if (record.getCollectRecordFlowId() != null) {
            doc.add(new IntPoint(FIELD_FLOW, record.getCollectRecordFlowId()));
            doc.add(new StoredField(FIELD_FLOW, record.getCollectRecordFlowId()));
        }
        if (record.getCollectRecordTaskId() != null) {
            doc.add(new LongPoint(FIELD_TASK, record.getCollectRecordTaskId()));
            doc.add(new StoredField(FIELD_TASK, record.getCollectRecordTaskId()));
        }
        long time = record.getUpdateTime() != null
                ? record.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
        doc.add(new NumericDocValuesField(FIELD_TIME, time));
        doc.add(new StoredField(FIELD_TIME, time));
        doc.add(new TextField(FIELD_TEXT, buildText(record), Field.Store.NO));
        return doc;
    }

    /**
     * 拼接全文内容：业务主键、抓取地址与 payload 的全部单元格文本。
     *
     * @param record 结果记录
     * @return 全文文本
     */
    private String buildText(CollectRecord record) {
        StringBuilder text = new StringBuilder();
        appendText(text, record.getCollectRecordKey());
        appendText(text, record.getCollectRecordUrl());
        appendText(text, record.getCollectRecordNodeId());
        try {
            Map<String, Object> payload = CollectJson.parseMap(record.getCollectRecordPayload());
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                appendText(text, entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    appendText(text, String.valueOf(value));
                } else if (value != null) {
                    appendText(text, CollectJson.toJson(value));
                }
            }
        } catch (Exception ex) {
            appendText(text, record.getCollectRecordPayload());
        }
        if (text.length() > TEXT_LIMIT) {
            text.setLength(TEXT_LIMIT);
        }
        return text.toString();
    }

    /**
     * 追加一段文本，自动补空格分隔。
     *
     * @param text 累积器
     * @param part 片段
     */
    private void appendText(StringBuilder text, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(part);
    }

    /**
     * 流程/任务过滤条件，均为空返回 null。
     *
     * @param flowId 流程ID
     * @param taskId 任务ID
     * @return 过滤查询
     */
    private Query flowTaskQuery(Integer flowId, Long taskId) {
        if (flowId == null && taskId == null) {
            return null;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        if (flowId != null) {
            builder.add(IntPoint.newExactQuery(FIELD_FLOW, flowId), BooleanClause.Occur.MUST);
        }
        if (taskId != null) {
            builder.add(LongPoint.newExactQuery(FIELD_TASK, taskId), BooleanClause.Occur.MUST);
        }
        return builder.build();
    }

    /**
     * 写操作攒批 commit，避免每条记录都刷盘。
     *
     * @throws Exception commit 失败
     */
    private void commitIfNoisy() throws Exception {
        if (dirty.incrementAndGet() >= COMMIT_EVERY) {
            dirty.set(0);
            writer.commit();
        }
    }

    /**
     * 断言索引可用。
     *
     * @throws IllegalStateException 不可用时抛出
     */
    private void assertUsable() {
        if (!available()) {
            throw new IllegalStateException("采集结果全文索引不可用");
        }
    }

    /**
     * 检索结果：命中总数与页内记录ID。
     *
     * @param total 命中总数
     * @param ids   页内记录ID
     */
    public record SearchResult(long total, List<Long> ids) {
    }
}
