// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.ai.support.entity.SysAiKnowledgeBase;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeBaseMapper;
import com.chua.starter.ai.support.service.KnowledgeBaseStore;
import com.chua.starter.ai.support.util.AiMemoryOwnership;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link KnowledgeBaseStore} 的 MyBatis-Plus 实现。
 *
 * <p>转换规则集中在本类，保证「内存 Map 的键」与「数据库列」只有一个映射点，
 * 避免读写两侧各写一份转换而漂移。</p>
 *
 * <p>{@code root_exists} 是从磁盘派生的字段，不入库：启动载入时按
 * {@code root_path} 重新计算，避免把「目录当时在不在」这种瞬时状态持久化。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseStoreImpl implements KnowledgeBaseStore {

    /**
     * 空字符串视为无来源配置，避免把空 JSON 写进列里
     */
    private static final String EMPTY = "";

    private final SysAiKnowledgeBaseMapper mapper;

    @Override
    public List<Map<String, Object>> loadAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<SysAiKnowledgeBase> rows;
        try {
            rows = mapper.selectList(new QueryWrapper<>());
        } catch (Exception e) {
            // 表不存在（未执行迁移）或库未连通时不能拖垮启动
            log.warn("知识库元数据查询失败，本次启动按空库处理: {}", e.getMessage());
            return result;
        }
        // 逐行转换：单行数据异常只丢这一行，不能连带把整个库列表清空
        for (SysAiKnowledgeBase row : rows) {
            try {
                result.add(toMap(row));
            } catch (Exception e) {
                log.warn("知识库元数据行转换失败，已跳过该行: id={}, err={}", row.getId(), e.getMessage());
            }
        }
        log.info("知识库元数据载入完成: {} 条（库中共 {} 行）", result.size(), rows.size());
        return result;
    }

    @Override
    public String insert(Map<String, Object> base) {
        SysAiKnowledgeBase row = toEntity(base);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public int update(Map<String, Object> base) {
        SysAiKnowledgeBase row = toEntity(base);
        QueryWrapper<SysAiKnowledgeBase> w = new QueryWrapper<>();
        w.eq("sys_ai_knowledge_base_id", row.getId());
        return mapper.update(row, w);
    }

    @Override
    public int delete(String knowledgeBaseId) {
        QueryWrapper<SysAiKnowledgeBase> w = new QueryWrapper<>();
        w.eq("sys_ai_knowledge_base_id", knowledgeBaseId);
        return mapper.delete(w);
    }

    /**
     * 内存记录 → 数据库实体。
     *
     * @param base 内存中的知识库记录
     * @return 待落库实体
     */
    private SysAiKnowledgeBase toEntity(Map<String, Object> base) {
        SysAiKnowledgeBase row = new SysAiKnowledgeBase();
        row.setId(str(base.get("knowledge_base_id")));
        row.setName(str(base.get("name")));
        row.setDescription(str(base.get("description")));
        row.setRootPath(str(base.get("root_path")));
        row.setKind(str(base.get("kind")));
        row.setManaged(boolToInt(base.get("managed")));
        row.setIsPublic(boolToInt(base.get("is_public")));
        row.setTags(writeJson(base.get("tags")));
        row.setSource(writeJson(base.get("source")));
        row.setFileCount(num(base.get("file_count")));
        row.setTotalSize(num(base.get("total_size")));
        // 分片参数：缺省时回落到与后端 controller 相同的默认值，避免旧行为（全局单一分片器）
        row.setChunkMaxSize(base.containsKey("chunk_max_size") ? intOf(base.get("chunk_max_size")) : 1000);
        row.setChunkOverlapSize(base.containsKey("chunk_overlap_size") ? intOf(base.get("chunk_overlap_size")) : 100);
        row.setChunkMinSize(base.containsKey("chunk_min_size") ? intOf(base.get("chunk_min_size")) : 50);
        row.setUserId(str(base.get(AiMemoryOwnership.USER_ID_FIELD)));
        return row;
    }

    /**
     * 数据库实体 → 内存记录。
     *
     * <p>键名与创建路径保持一致（含 {@code root_exists}），这样启动载入的库
     * 与运行期新建的库在下游看来没有区别。</p>
     *
     * @param row 数据库行
     * @return 内存中的知识库记录
     */
    private Map<String, Object> toMap(SysAiKnowledgeBase row) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("knowledge_base_id", row.getId());
        base.put("name", row.getName());
        base.put("description", row.getDescription() == null ? EMPTY : row.getDescription());
        base.put("root_path", row.getRootPath());
        base.put("managed", row.getManaged() != null && row.getManaged() == 1);
        base.put("created_at", row.getCreateTime() == null ? 0L : row.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        base.put("updated_at", row.getUpdateTime() == null ? 0L : row.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        base.put("file_count", row.getFileCount() == null ? 0L : row.getFileCount());
        base.put("total_size", row.getTotalSize() == null ? 0L : row.getTotalSize());
        base.put("tags", readTags(row.getTags()));
        base.put("kind", row.getKind() == null ? "local" : row.getKind());
        base.put("is_public", row.getIsPublic() != null && row.getIsPublic() == 1);
        // 分片参数：老行可能为 null（迁移前建的库），回落到默认值而不是塞 null 进下游算术
        base.put("chunk_max_size", row.getChunkMaxSize() == null ? 1000 : row.getChunkMaxSize());
        base.put("chunk_overlap_size", row.getChunkOverlapSize() == null ? 100 : row.getChunkOverlapSize());
        base.put("chunk_min_size", row.getChunkMinSize() == null ? 50 : row.getChunkMinSize());
        if (row.getSource() != null && !row.getSource().isBlank()) {
            base.put("source", readJson(row.getSource()));
        }
        if (row.getUserId() != null && !row.getUserId().isBlank()) {
            base.put(AiMemoryOwnership.USER_ID_FIELD, row.getUserId());
        }
        // root_exists 是派生字段，落库没有意义，载入时按当前磁盘状态算
        base.put("root_exists", row.getRootPath() != null && !row.getRootPath().isBlank());
        return base;
    }

    /**
     * 取字符串，null 归一为空串。
     *
     * @param v 原始值
     * @return 非 null 字符串
     */
    private String str(Object v) {
        return v == null ? EMPTY : String.valueOf(v);
    }

    /**
     * 取数值，null 归一为 0。
     *
     * @param v 原始值
     * @return 非 null 数值
     */
    private Long num(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 取整数，null 或非法值归一为 0。
     *
     * @param v 原始值
     * @return 整数，解析失败为 0
     */
    private Integer intOf(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 布尔 → TINYINT。
     *
     * @param v 原始值
     * @return 1 表示真，0 表示假
     */
    private Integer boolToInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return Boolean.parseBoolean(String.valueOf(v)) ? 1 : 0;
    }

    /**
     * 对象 → JSON 文本。
     *
     * @param v 原始值（集合 / Map / 字符串）
     * @return JSON 文本，null 返回空串
     */
    private String writeJson(Object v) {
        if (v == null) {
            return EMPTY;
        }
        if (v instanceof String s) {
            return s;
        }
        try {
            return Json.toJson(v);
        } catch (Exception e) {
            log.warn("知识库字段序列化失败，按空处理: {}", e.getMessage());
            return EMPTY;
        }
    }

    /**
     * JSON 文本 → 对象。
     *
     * @param text JSON 文本
     * @return 解析结果，失败返回空 Map
     */
    private Object readJson(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return Json.fromJson(text, Object.class);
        } catch (Exception e) {
            log.warn("知识库 JSON 字段解析失败，按空处理: {}", e.getMessage());
            return new LinkedHashMap<String, Object>();
        }
    }

    /**
     * 标签 JSON 文本 → 字符串列表。
     *
     * <p>注意：必须用 {@code fromJson(text, Object.class)} 或 {@code fromJsonToList}，
     * 不能用单参的 {@code fromJson(String)}——那个重载的返回类型是
     * {@code Map<String, Object>}，喂 JSON 数组会解析失败，标签会被静默吞成空列表。</p>
     *
     * @param text JSON 数组文本
     * @return 标签列表，永不为 null
     */
    private List<String> readTags(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Object v = Json.fromJson(text, Object.class);
            if (v instanceof List<?> list) {
                List<String> tags = new ArrayList<>(list.size());
                for (Object o : list) {
                    tags.add(String.valueOf(o));
                }
                return tags;
            }
            log.warn("知识库标签不是 JSON 数组，按空列表处理: {}", text);
        } catch (Exception e) {
            log.warn("知识库标签解析失败，按空列表处理: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
}