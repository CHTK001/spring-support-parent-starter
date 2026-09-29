package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库元数据持久化。
 *
 * <p>存在的理由：知识库列表原先只存在于
 * {@code ApiKnowledgeController} 的内存 {@code ConcurrentHashMap}，
 * 后端重启即全部消失。改为「内存 Map 作读缓存 + 本接口写穿数据库」：
 * 启动时 {@link #loadAll()} 全量载入，之后每次创建/更新/删除同步落库。</p>
 *
 * <p>不把读路径也改成直接查库，是为了让既有 20 余处读取点行为完全不变
 * （含 {@code AiMemoryOwnership.visibleToCurrentUser} 的可见性过滤顺序），
 * 把改动面压到「4 个写点 + 1 个启动钩子」。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface KnowledgeBaseStore {

    /**
     * 载入全部知识库元数据，供启动时填充内存 Map。
     *
     * <p>表不存在或查询失败时返回空列表而不是抛异常：让服务照常启动，
     * 退化回「库列表为空」的表现，好过整个应用起不来。</p>
     *
     * @return 全部知识库记录，键为 {@code knowledge_base_id}
     */
    List<Map<String, Object>> loadAll();

    /**
     * 新增一个知识库的持久化记录。
     *
     * @param base 内存中的知识库记录
     * @return 落库后的主键（{@code knowledge_base_id}）
     */
    String insert(Map<String, Object> base);

    /**
     * 更新已有知识库的持久化记录。
     *
     * @param base 内存中的知识库记录
     * @return 受影响行数
     */
    int update(Map<String, Object> base);

    /**
     * 按主键删除知识库的持久化记录。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 受影响行数
     */
    int delete(String knowledgeBaseId);
}
