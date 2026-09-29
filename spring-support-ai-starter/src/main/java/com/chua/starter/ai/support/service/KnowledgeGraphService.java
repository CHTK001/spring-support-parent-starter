package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.KnowledgeGraphExtractionDTO;
import com.chua.starter.ai.support.model.vo.KnowledgeGraphViewVO;

/**
 * 知识图谱服务：把关系抽取结果落库，并向关系图谱页面提供查询。
 *
 * <p>实体 / 关系在「图谱库」内唯一，一个知识点可以关联多个实体与关系，一个实体也可
 * 被多个知识点提及，多对多关系由 {@code sys_ai_knowledge_graph_ref} 承载。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface KnowledgeGraphService {

    /**
     * 手工实体的保留挂载路径。
     *
     * <p>手工补的实体/关系不属于任何知识点，用这个保留值挂在 {@code ref} 表上：
     * 删除知识点时按 {@code doc_path} 清理不会碰到它们，删除整个知识库时又能一并清空。</p>
     */
    String MANUAL_DOC_PATH = "__manual__";

    /**
     * 把某个知识点（知识库ID + 相对路径）新抽出的实体与关系**合并**进图谱。
     *
     * <p><b>合并而非替换</b>：只补本次抽到、此前没有的关联，已存在的关联原样保留。
     * 这保证「上传时异步抽取」与「用户手动再抽一次」不会互相抹掉——两者都是 LLM 单次输出，
     * 抽得少的那次不应该把抽得多的那次覆盖掉。实测未合并前，异步抽取存了 18 实体/11 关系后
     * 手动重抽，结果会退回 11 实体/8 关系。</p>
     *
     * <p>去重由两层保证：实体按「库 + 名称」复用同一条记录（{@code upsertEntity}），
     * 关系按「库 + 源 + 目标」复用同一条边（{@code upsertRelation}，<b>type 不参与唯一键</b>，
     * 它只是可升级属性，写入前还会经 {@code normalizeRelationType} 把同义变体收敛），
     * 关联按唯一键 {@code (target_type, target_uid, kb_id, doc_path)} 幂等写入
     * （{@code addRef}），因此反复调用不会产生重复节点或重复边。</p>
     *
     * <p><b>不维护任何冗余计数器</b>：关系数在读时从关系表算（节点 degree 由图谱视图遍历边表
     * 得出，某知识点的关系数对 relation 类型的 ref 直接 count）。原先 ref 表上有个
     * {@code mentions} 累加字段，已删除——它统计的不是关系数（同一次抽取里实体循环与每条关系的
     * 两个端点都会各加一次，实测单条 ref 涨到 59），而且只写不读。</p>
     *
     * <p>代价：原文被修改后，那些只存在于旧版本的实体会留在图谱里。要彻底清理走
     * {@link #deleteByKnowledgePoint(String, String)}（删除知识点时自动调用），
     * 不要靠重复抽取来「刷新」。</p>
     *
     * @param knowledgeBaseId 知识库ID
     * @param knowledgeBaseName 知识库名称，用于首次创建图谱库时的展示名
     * @param docPath 知识点在知识库内的相对路径
     * @param extraction 抽取结果
     */
    void mergeKnowledgePointGraph(String knowledgeBaseId, String knowledgeBaseName,
                                  String docPath, KnowledgeGraphExtractionDTO extraction);

    /**
     * 删除某个知识点的全部图谱关联，并回收孤立实体与关系。
     *
     * @param knowledgeBaseId 知识库ID
     * @param docPath 知识点在知识库内的相对路径
     */
    void deleteByKnowledgePoint(String knowledgeBaseId, String docPath);

    /**
     * 删除整个知识库的图谱关联，并回收孤立实体与关系。
     *
     * @param knowledgeBaseId 知识库ID
     */
    void deleteByKnowledgeBase(String knowledgeBaseId);

    /**
     * 查询整个知识库的关系图谱。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 图谱视图（节点 + 连线）
     */
    KnowledgeGraphViewVO getKnowledgeBaseGraph(String knowledgeBaseId);

    /**
     * 查询单个知识点的关系图谱（只含该知识点关联到的实体与它们之间的关系）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param docPath 知识点在知识库内的相对路径
     * @return 图谱视图（节点 + 连线）
     */
    KnowledgeGraphViewVO getKnowledgePointGraph(String knowledgeBaseId, String docPath);

    /**
     * 统计某知识库的实体与关系数量。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 长度为 2 的数组：[实体数, 关系数]
     */
    int[] countKnowledgeBaseGraph(String knowledgeBaseId);

    /**
     * 收敛历史遗留的重复节点与重复边。
     *
     * <p>早期实体的去重键是「名称 + 类型」，大模型对同一实体给出不同类型时会建出多个节点
     * （实测 {@code 七军} 三个、{@code 建安二十四年} 两个），指向它们的边也随之重复，
     * 于是同内容重抽一轮关系数就涨二十多条。本方法把同名实体合并成一条（权重相加、
     * 类型按「other 可被具体类型覆盖」合并）、把关系端点重指向主节点，
     * 并删除 (源, 目标, 类型) 完全重复的边行。</p>
     *
     * <p>这是数据修复入口，不参与日常抽取流程（每抽一次就全库扫描代价太高）。
     * 边侧的分组键是「源 + 目标」（不含 type），所以同名实体合并时挂着的多种类型边
     * 也会一并收成一条；新产生的数据不会再有重复，因为 {@code upsertEntity} 已按名称唯一、
     * {@code upsertRelation} 已按 (库, 源, 目标) 唯一且 type 经过归一化。</p>
     *
     * @param knowledgeBaseId 知识库ID
     * @return 被合并或删除的实体行数（不含重复边）
     */
    int collapseDuplicateGraph(String knowledgeBaseId);

    // ==================== 手工维护（增 / 改 / 删） ====================

    /**
     * 手工新增一个实体。
     *
     * <p>手工实体用保留的 {@code docPath}（{@link #MANUAL_DOC_PATH}）挂载，不归属任何知识点。
     * 这样删除知识点时 {@link #deleteByKnowledgePoint} 不会连带删掉用户手工补的实体，
     * 而 {@link #deleteByKnowledgeBase} 仍会把整个知识库的图谱清空。</p>
     *
     * <p>同名同类型的实体走 upsert 复用（不新建），因此手工补录与 LLM 抽取撞名时
     * 会合并成同一个节点，而不是出现两个「关羽」。</p>
     *
     * @param knowledgeBaseId   知识库ID
     * @param knowledgeBaseName 知识库名称（首次创建图谱库时用）
     * @param name              实体名称
     * @param type              实体类型（person/location/event/organization/other）
     * @param description       实体说明，可为空
     * @return 实体 uid（已存在时返回原 uid）
     */
    String addManualEntity(String knowledgeBaseId, String knowledgeBaseName,
                           String name, String type, String description);

    /**
     * 手工新增一条关系。
     *
     * <p>源/目标用<b>实体名称</b>（与 LLM 抽取结果的 DTO 口径一致），不存在时按名称创建
     * 占位实体（类型 {@code other}），这样用户不必先去建实体再连边。</p>
     *
     * @param knowledgeBaseId   知识库ID
     * @param knowledgeBaseName 知识库名称
     * @param source            源实体名称
     * @param target            目标实体名称
     * @param type              关系类型，如「镇守」「结义」
     * @return 关系 uid
     */
    String addManualRelation(String knowledgeBaseId, String knowledgeBaseName,
                             String source, String target, String type);

    /**
     * 修改实体（可改名称/类型/说明）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             实体 uid
     * @param name            新名称，null 表示不改
     * @param type            新类型，null 表示不改
     * @param description     新说明，null 表示不改
     * @return 是否命中并更新
     */
    boolean updateEntity(String knowledgeBaseId, String uid, String name, String type, String description);

    /**
     * 删除实体（连带删除它的全部关联与不再被引用的关系）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             实体 uid
     * @return 是否命中并删除
     */
    boolean deleteEntity(String knowledgeBaseId, String uid);

    /**
     * 批量删除实体。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uids            实体 uid 集合
     * @return 实际删除的实体数
     */
    int deleteEntities(String knowledgeBaseId, java.util.Collection<String> uids);

    /**
     * 删除关系（连带删除它的关联与不再被引用的实体）。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uid             关系 uid
     * @return 是否命中并删除
     */
    boolean deleteRelation(String knowledgeBaseId, String uid);

    /**
     * 批量删除关系。
     *
     * @param knowledgeBaseId 知识库ID
     * @param uids            关系 uid 集合
     * @return 实际删除的关系数
     */
    int deleteRelations(String knowledgeBaseId, java.util.Collection<String> uids);
}
