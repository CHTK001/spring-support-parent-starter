package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphEntity;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphRef;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphRelation;
import com.chua.starter.ai.support.entity.SysAiKnowledgeLibrary;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeGraphEntityMapper;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeGraphRefMapper;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeGraphRelationMapper;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeLibraryMapper;
import com.chua.starter.ai.support.model.KnowledgeGraphEntityDTO;
import com.chua.starter.ai.support.model.KnowledgeGraphExtractionDTO;
import com.chua.starter.ai.support.model.KnowledgeGraphRelationDTO;
import com.chua.starter.ai.support.model.vo.KnowledgeGraphEdgeVO;
import com.chua.starter.ai.support.model.vo.KnowledgeGraphNodeVO;
import com.chua.starter.ai.support.model.vo.KnowledgeGraphViewVO;
import com.chua.starter.ai.support.service.KnowledgeGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现。
 *
 * <p>与 RAG 知识库的映射放在 {@code sys_ai_knowledge_library} 的 name 上，取
 * {@code kb:<knowledge_base_id>}。这样做是因为本模块的 init SQL 刻意不输出兼容
 * ALTER（MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，留着只会每次启动报错并被
 * continue-on-error 静默吞掉），无法给该表补一列 knowledge_base_id；而 UUID 天然
 * 唯一，正好满足该表 name 上的唯一键，不会因两个知识库重名而冲突。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    /**
     * 图谱库名称前缀，后接 RAG 知识库ID。
     */
    private static final String LIBRARY_NAME_PREFIX = "kb:";

    /**
     * 知识点路径列宽，对应 VARCHAR(255)。
     */
    private static final int DOC_PATH_MAX_LENGTH = 255;

    /**
     * 关联目标类型：实体。
     */
    private static final String TARGET_ENTITY = "entity";

    /**
     * 关联目标类型：关系。
     */
    private static final String TARGET_RELATION = "relation";

    /**
     * 抽取来源标记。
     */
    private static final String SOURCE_LLM = "llm_extracted";

    /**
     * 兜底实体类型：类型未知。
     *
     * <p>在 {@link #mergeType} 里被视为「可被覆盖」——已有类型是它时用更具体的类型升级，
     * 反过来则保留库里已有的更具体类型。</p>
     */
    private static final String TYPE_OTHER = "other";

    /**
     * 状态：有效。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 实体类型 → 显示颜色。
     */
    private static final Map<String, String> TYPE_COLORS = Map.of(
            "person", "#1677ff",
            "location", "#52c41a",
            "event", "#fa8c16",
            "organization", "#722ed1",
            "other", "#8c8c8c"
    );

    /**
     * 实体类型别名归一化表。
     */
    private static final Map<String, String> TYPE_ALIAS = Map.ofEntries(
            Map.entry("person", "person"),
            Map.entry("人物", "person"),
            Map.entry("per", "person"),
            Map.entry("location", "location"),
            Map.entry("地点", "location"),
            Map.entry("位置", "location"),
            Map.entry("loc", "location"),
            Map.entry("gpe", "location"),
            Map.entry("event", "event"),
            Map.entry("事件", "event"),
            Map.entry("organization", "organization"),
            Map.entry("组织", "organization"),
            Map.entry("机构", "organization"),
            Map.entry("org", "organization")
    );

    /**
     * 关系类型缺省值：模型没给类型时的兜底，与 {@link #normalizeRelationType} 共用。
     */
    private static final String DEFAULT_RELATION_TYPE = "相关";

    /**
     * 关系分组键里拼字段用的分隔符。
     *
     * <p>取 U+0000 是因为它不可能出现在 uid 里，不会造成「(ab,c) 与 (a,bc) 拼出同一个键」
     * 这种假合并。<b>必须写成转义而不是裸字节</b>：裸 NUL 会让源文件被 read/grep 判成二进制，
     * 而且任何做编码规范化的工具都可能把它悄悄丢掉，分隔符就退化成空串、进而误判重复。</p>
     */
    private static final String KEY_SEPARATOR = "\u0000";

    /**
     * 关系类型别名归一化表。
     *
     * <p><b>为什么需要</b>：关系类型一直是模型输出的自由文本，同一条语义边换个说法就多出一行。
     * 实测线上同一对实体出现 2 种以上类型的组有 16 个，类型分布里也满是同义变体
     * （参与 43 / 参与者 11、结为异姓兄弟 8、结义地点 8、拜访 15、镇守 8、失守 6…），
     * 于是「同内容重复抽取」时关系数一轮轮往上走。</p>
     *
     * <p><b>为什么是枚举表而不是按相似度合并</b>：字符串相似度会把「任职地」和「任职地点」
     * 判成相近、把「镇守」和「退兵」也判成相近，属于误伤。这里只登记<b>已确证同义</b>的
     * 变体，未登记的类型原样透传（宁可多一种类型，也不要错并两种语义）。</p>
     */
    private static final Map<String, String> RELATION_TYPE_ALIAS = Map.ofEntries(
            Map.entry("参与者", "参与"),
            Map.entry("参加", "参与"),
            Map.entry("参加者", "参与"),
            Map.entry("参与人物", "参与"),
            Map.entry("结拜", "结义"),
            Map.entry("结为兄弟", "结义"),
            Map.entry("结为异姓兄弟", "结义"),
            Map.entry("义兄弟", "结义"),
            Map.entry("出生地", "籍贯"),
            Map.entry("祖籍", "籍贯"),
            Map.entry("访问", "拜访"),
            Map.entry("造访", "拜访"),
            Map.entry("杀死", "斩杀"),
            Map.entry("诛杀", "斩杀"),
            Map.entry("归附", "依附"),
            Map.entry("投效", "依附"),
            Map.entry("谋划", "图谋"),
            Map.entry("献策", "图谋"),
            Map.entry("袭击", "袭取"),
            Map.entry("夺取", "袭取"),
            Map.entry("驻守", "镇守"),
            Map.entry("屯守", "镇守"),
            Map.entry("归降", "投靠"),
            Map.entry("投奔", "投靠"),
            Map.entry("俘虏", "擒获"),
            Map.entry("生擒", "擒获"),
            Map.entry("联系", "联络"),
            Map.entry("沟通", "联络"),
            Map.entry("求降", "请降"),
            Map.entry("请罪", "请降"),
            Map.entry("伏诛", "被斩"),
            Map.entry("遇害", "被斩"),
            Map.entry("驻扎", "屯驻"),
            Map.entry("败退", "退兵"),
            Map.entry("溃退", "退兵"),
            Map.entry("失地", "失守"),
            Map.entry("丢失", "失守"),
            Map.entry("失去", "失守"),
            Map.entry("离职地", "任职"),
            Map.entry("任职地", "任职"),
            Map.entry("任职地点", "任职"),
            Map.entry("发生于", "发生地点"),
            Map.entry("事发地", "发生地点")
    );

    /**
     * 图谱库 映射器
     */
    private final SysAiKnowledgeLibraryMapper knowledgeLibraryMapper;
    /**
     * 图谱实体 映射器
     */
    private final SysAiKnowledgeGraphEntityMapper graphEntityMapper;
    /**
     * 图谱关系 映射器
     */
    private final SysAiKnowledgeGraphRelationMapper graphRelationMapper;
    /**
     * 图谱关联 映射器
     */
    private final SysAiKnowledgeGraphRefMapper graphRefMapper;

    /**
     * JSON 序列化器（properties 是 JSON 列，必须写入合法 JSON）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeKnowledgePointGraph(String knowledgeBaseId, String knowledgeBaseName,
                                         String docPath, KnowledgeGraphExtractionDTO extraction) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return;
        }
        String path = normalizeDocPath(docPath);
        Integer libraryId = ensureLibrary(knowledgeBaseId, knowledgeBaseName);
        // 合并语义：这里原本有一句 clearRefs 把该知识点的旧关联整体删掉再重建，
        // 导致「上传时异步抽取」与「手动再抽一次」互相抹掉——抽得少的那次会覆盖抽得多的那次
        // （实测 18 实体/11 关系 被重抽退回 11 实体/8 关系）。
        // 去重交给下面两步，它们本身幂等：
        //   upsertEntity：按「库 + 名称」唯一复用同一条实体，类型只是可升级的属性
        //   addRef：按唯一键 (target_type, target_uid, kb_id, doc_path) 查后改，未命中才 insert
        if (extraction != null && !extraction.isEmpty()) {
            Map<String, String> entityUidByName = new LinkedHashMap<>();
            List<KnowledgeGraphEntityDTO> entities = extraction.entities();
            if (entities != null) {
                for (KnowledgeGraphEntityDTO entity : entities) {
                    if (entity == null || !StringUtils.hasText(entity.name())) {
                        continue;
                    }
                    String uid = upsertEntity(libraryId, entity.name(), normalizeType(entity.type()),
                            entity.description());
                    entityUidByName.putIfAbsent(normalizeName(entity.name()), uid);
                    addRef(libraryId, TARGET_ENTITY, uid, knowledgeBaseId, path);
                }
            }
            List<KnowledgeGraphRelationDTO> relations = extraction.relations();
            if (relations != null) {
                for (KnowledgeGraphRelationDTO relation : relations) {
                    if (relation == null || !StringUtils.hasText(relation.source())
                            || !StringUtils.hasText(relation.target())) {
                        continue;
                    }
                    // 关系两端可能是大模型漏抽的实体，按 other 类型补建，避免整条关系丢失
                    String sourceUid = resolveEntityUid(libraryId, entityUidByName, relation.source());
                    String targetUid = resolveEntityUid(libraryId, entityUidByName, relation.target());
                    if (sourceUid.equals(targetUid)) {
                        continue;
                    }
                    addRef(libraryId, TARGET_ENTITY, sourceUid, knowledgeBaseId, path);
                    addRef(libraryId, TARGET_ENTITY, targetUid, knowledgeBaseId, path);
                    // 关系类型先归一化再入库：模型换个说法（结义/结为异姓兄弟）不该多出一条边
                    String relationUid = upsertRelation(libraryId, sourceUid, targetUid,
                            normalizeRelationType(relation.type()));
                    addRef(libraryId, TARGET_RELATION, relationUid, knowledgeBaseId, path);
                }
            }
        }
        pruneOrphans(libraryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgePoint(String knowledgeBaseId, String docPath) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return;
        }
        clearRefs(libraryId, knowledgeBaseId, normalizeDocPath(docPath));
        pruneOrphans(libraryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeBase(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return;
        }
        graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId));
        pruneOrphans(libraryId);
    }

    @Override
    public KnowledgeGraphViewVO getKnowledgeBaseGraph(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return KnowledgeGraphViewVO.empty();
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return KnowledgeGraphViewVO.empty();
        }
        // 只展示仍被该知识库引用的实体与关系，避免历史替换残留的孤儿节点出现在图上
        Set<String> entityUids = referencedUids(libraryId, TARGET_ENTITY);
        Set<String> relationUids = referencedUids(libraryId, TARGET_RELATION);
        return buildView(libraryId, entityUids, relationUids);
    }

    @Override
    public KnowledgeGraphViewVO getKnowledgePointGraph(String knowledgeBaseId, String docPath) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return KnowledgeGraphViewVO.empty();
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return KnowledgeGraphViewVO.empty();
        }
        String path = normalizeDocPath(docPath);
        List<SysAiKnowledgeGraphRef> refs = graphRefMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                        .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(SysAiKnowledgeGraphRef::getDocPath, path));
        Set<String> entityUids = new LinkedHashSet<>();
        Set<String> relationUids = new LinkedHashSet<>();
        for (SysAiKnowledgeGraphRef ref : refs) {
            if (TARGET_ENTITY.equals(ref.getTargetType())) {
                entityUids.add(ref.getTargetUid());
            } else if (TARGET_RELATION.equals(ref.getTargetType())) {
                relationUids.add(ref.getTargetUid());
            }
        }
        return buildView(libraryId, entityUids, relationUids);
    }

    @Override
    public int[] countKnowledgeBaseGraph(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return new int[]{0, 0};
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return new int[]{0, 0};
        }
        int entityCount = Math.toIntExact(graphRefMapper.selectCount(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                        .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_ENTITY)));
        int relationCount = Math.toIntExact(graphRefMapper.selectCount(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                        .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_RELATION)));
        return new int[]{entityCount, relationCount};
    }

    /**
     * 按知识库ID查找图谱库ID。
     *
     * @param knowledgeBaseId RAG 知识库ID
     * @return 图谱库ID；未创建时返回 null
     */
    private Integer findLibraryId(String knowledgeBaseId) {
        SysAiKnowledgeLibrary library = knowledgeLibraryMapper.selectOne(
                new LambdaQueryWrapper<SysAiKnowledgeLibrary>()
                        .eq(SysAiKnowledgeLibrary::getName, LIBRARY_NAME_PREFIX + knowledgeBaseId)
                        .last("limit 1"));
        return library == null ? null : library.getId();
    }

    /**
     * 取或创建知识库对应的图谱库。
     *
     * @param knowledgeBaseId RAG 知识库ID
     * @param knowledgeBaseName RAG 知识库名称
     * @return 图谱库ID
     */
    private Integer ensureLibrary(String knowledgeBaseId, String knowledgeBaseName) {
        Integer existing = findLibraryId(knowledgeBaseId);
        if (existing != null) {
            return existing;
        }
        SysAiKnowledgeLibrary library = new SysAiKnowledgeLibrary();
        library.setName(LIBRARY_NAME_PREFIX + knowledgeBaseId);
        library.setDescription(StringUtils.hasText(knowledgeBaseName)
                ? knowledgeBaseName : knowledgeBaseId);
        library.setStatus(STATUS_ENABLED);
        knowledgeLibraryMapper.insert(library);
        return library.getId();
    }

    /**
     * 取或建立实体，命中时累加权重。
     *
     * <p><b>实体的身份是「名称」，不是「名称 + 类型」。</b>大模型对同一个实体在不同轮次
     * 会给出不同类型（同一个「七军」一次判 {@code other}、一次判 {@code organization}），
     * 早期按「名称+类型」去重就会为每个类型各建一个节点。后果是实测可见的：
     * 同名实体 3 个（{@code 七军}×3、{@code 建安二十四年}×2），指向它们的边也跟着重复
     * （{@code 关羽 -> 七军} 三条），<b>同内容重抽一轮关系数就涨 26 条</b>（66→99→125）。
     * 图谱里同名实体就是同一个东西，类型只是属性，所以这里改成按名称唯一。</p>
     *
     * <p>类型冲突的处置（不丢信息、不抖动）：已有类型是 {@code other} 而本次更具体时
     * <b>升级</b>；已有类型具体而本次是 {@code other} 时<b>保留</b>（不降级）；
     * 双方都具体但不同（person vs location）时保留先到的，并打 debug 日志。
     * 这样反复抽取不会来回改类型，也不会因为一次误判把已正确的类型改坏。</p>
     *
     * <p>代价：确实存在同名不同物的语料（如两个都叫「华佗」的人）会被合并成一个节点。
     * 知识图谱以名称为身份是通行做法，若将来遇到这类数据，应在<b>抽取提示词</b>里要求
     * 带限定词（如「华佗-东汉」「华佗-晚唐」），而不是在这里放宽去重键。</p>
     *
     * @param libraryId 图谱库ID
     * @param name 实体名称
     * @param type 已归一化的实体类型
     * @param description 实体说明，可为空
     * @return 实体 uid
     */
    private String upsertEntity(Integer libraryId, String name, String type, String description) {
        String trimmed = name.trim();
        List<SysAiKnowledgeGraphEntity> same = graphEntityMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphEntity::getName, trimmed));
        if (same != null && !same.isEmpty()) {
            // 主记录取权重最高的一条，保证多次抽取后仍是同一条在累加
            SysAiKnowledgeGraphEntity exists = same.stream()
                    .max(Comparator.comparingDouble(
                            e -> e.getWeight() == null ? 0.0D : e.getWeight()))
                    .orElse(same.get(0));
            SysAiKnowledgeGraphEntity update = new SysAiKnowledgeGraphEntity();
            update.setId(exists.getId());
            update.setWeight((exists.getWeight() == null ? 1.0D : exists.getWeight()) + 1.0D);
            if (StringUtils.hasText(description) && !StringUtils.hasText(exists.getProperties())) {
                update.setProperties(descriptionJson(description));
            }
            String mergedType = mergeType(exists.getType(), type);
            if (!mergedType.equals(exists.getType())) {
                update.setType(mergedType);
            }
            graphEntityMapper.updateById(update);
            return exists.getUid();
        }
        SysAiKnowledgeGraphEntity entity = new SysAiKnowledgeGraphEntity();
        entity.setUid(newId());
        entity.setLibraryId(libraryId);
        entity.setName(trimmed);
        entity.setType(type);
        entity.setProperties(descriptionJson(description));
        entity.setSource(SOURCE_LLM);
        entity.setWeight(1.0D);
        entity.setStatus(STATUS_ENABLED);
        graphEntityMapper.insert(entity);
        return entity.getUid();
    }

    /**
     * 合并同名实体的两个类型取值。
     *
     * <p>规则：{@code other} 视为「未知」，可被任何具体类型覆盖；两个都具体且不同时
     * 保留先到的，避免反复抽取时类型来回跳。</p>
     *
     * @param current 已有的类型
     * @param incoming 本次抽到的类型
     * @return 合并后应采用的类型
     */
    private String mergeType(String current, String incoming) {
        String a = StringUtils.hasText(current) ? current : TYPE_OTHER;
        String b = StringUtils.hasText(incoming) ? incoming : TYPE_OTHER;
        if (a.equals(b)) {
            return a;
        }
        if (TYPE_OTHER.equals(a)) {
            return b;
        }
        if (TYPE_OTHER.equals(b)) {
            return a;
        }
        log.debug("[KnowledgeGraph] 同名实体类型不一致，保留先到的: current={} incoming={}", a, b);
        return a;
    }

    /**
     * 收敛历史遗留的同名实体：把同一库内同名的多条记录合并成一条主记录，
     * 权重相加、类型按 {@link #mergeType} 合并、说明取先有非空的。
     *
     * <p>合并后重指向：把指向「被合并掉的那些 uid」的关系端点改为主 uid，
     * 重复的关系行（同源同目标同类型）只保留一条，最后删除多余的实体行与其 ref。</p>
     *
     * <p>不重指向就会留下悬空边——那些边指向已删除的 uid，图上表现为「连到看不见的节点」。
     * 所以这里必须连同 {@code sys_ai_knowledge_graph_relation} 一起改。</p>
     *
     * @param libraryId 图谱库ID
     * @return 被合并掉的实体行数
     */
    private int collapseDuplicateEntities(Integer libraryId) {
        List<SysAiKnowledgeGraphEntity> all = graphEntityMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId));
        if (all == null || all.isEmpty()) {
            return 0;
        }
        Map<String, List<SysAiKnowledgeGraphEntity>> byName = all.stream()
                .filter(e -> StringUtils.hasText(e.getName()))
                .collect(Collectors.groupingBy(SysAiKnowledgeGraphEntity::getName, LinkedHashMap::new, Collectors.toList()));
        int removed = 0;
        for (List<SysAiKnowledgeGraphEntity> group : byName.values()) {
            if (group.size() < 2) {
                continue;
            }
            // 主记录：权重最高，其次 id 最小，保证多次执行结果稳定
            SysAiKnowledgeGraphEntity primary = group.stream()
                    .max(Comparator.<SysAiKnowledgeGraphEntity>comparingDouble(
                                    e -> e.getWeight() == null ? 0.0D : e.getWeight())
                            .thenComparing(e -> e.getId() == null ? Integer.MAX_VALUE : e.getId()))
                    .orElse(group.get(0));
            List<String> losers = group.stream()
                    .map(SysAiKnowledgeGraphEntity::getUid)
                    .filter(uid -> !uid.equals(primary.getUid()))
                    .toList();
            if (losers.isEmpty()) {
                continue;
            }
            // 1) 权重与类型合并到主记录
            double weightSum = group.stream()
                    .mapToDouble(e -> e.getWeight() == null ? 1.0D : e.getWeight())
                    .sum();
            String type = null;
            String description = null;
            for (SysAiKnowledgeGraphEntity e : group) {
                type = type == null ? e.getType() : mergeType(type, e.getType());
                if (description == null && StringUtils.hasText(e.getProperties())) {
                    description = e.getProperties();
                }
            }
            SysAiKnowledgeGraphEntity merged = new SysAiKnowledgeGraphEntity();
            merged.setId(primary.getId());
            merged.setWeight(weightSum);
            if (type != null) {
                merged.setType(type);
            }
            if (description != null) {
                merged.setProperties(description);
            }
            graphEntityMapper.updateById(merged);

            // 2) 关系端点重指向到主 uid
            for (String loser : losers) {
                graphRelationMapper.update(null, new LambdaUpdateWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphRelation::getSourceEntityUid, loser)
                        .set(SysAiKnowledgeGraphRelation::getSourceEntityUid, primary.getUid()));
                graphRelationMapper.update(null, new LambdaUpdateWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphRelation::getTargetEntityUid, loser)
                        .set(SysAiKnowledgeGraphRelation::getTargetEntityUid, primary.getUid()));
            }
            // 3) ref 改挂主 uid；addRef 按 (target_type,target_uid,kb_id,doc_path) 幂等，
            //    所以这里先删 losers 的 ref，再逐条补到主 uid 上
            List<SysAiKnowledgeGraphRef> loserRefs = graphRefMapper.selectList(
                    new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                            .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_ENTITY)
                            .in(SysAiKnowledgeGraphRef::getTargetUid, losers));
            if (loserRefs != null) {
                for (SysAiKnowledgeGraphRef ref : loserRefs) {
                    addRef(libraryId, TARGET_ENTITY, primary.getUid(),
                            ref.getKnowledgeBaseId(), ref.getDocPath());
                }
            }
            graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                    .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_ENTITY)
                    .in(SysAiKnowledgeGraphRef::getTargetUid, losers));
            // 4) 删除多余实体行
            removed += graphEntityMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                    .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId)
                    .in(SysAiKnowledgeGraphEntity::getUid, losers));
        }
        // 5) 端点合并后可能出现 (源,目标,类型) 完全重复的关系行，只保留一条
        removed += dedupeRelations(libraryId);
        return removed;
    }

    /**
     * 删除同一对实体之间的重复关系行，保留 weight 最高的一条。
     *
     * <p>分组键是「源 + 目标」<b>不含 type</b>：线上存量里同一对实体挂着多种类型
     * （结义 / 结为异姓兄弟、失守 / 退兵…），只按「源+目标+类型」分组收不掉这些换皮行。
     * {@code upsertRelation} 改用同一套分组键后新数据不会再产生，这里负责收历史存量。</p>
     *
     * @param libraryId 图谱库ID
     * @return 删除的行数
     */
    private int dedupeRelations(Integer libraryId) {
        List<SysAiKnowledgeGraphRelation> all = graphRelationMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId));
        if (all == null || all.isEmpty()) {
            return 0;
        }
        Map<String, List<SysAiKnowledgeGraphRelation>> byKey = all.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSourceEntityUid() + KEY_SEPARATOR + r.getTargetEntityUid(),
                        LinkedHashMap::new, Collectors.toList()));
        int removed = 0;
        for (List<SysAiKnowledgeGraphRelation> group : byKey.values()) {
            if (group.size() < 2) {
                continue;
            }
            SysAiKnowledgeGraphRelation primary = group.stream()
                    .max(Comparator.comparingDouble(
                            r -> r.getWeight() == null ? 0.0D : r.getWeight()))
                    .orElse(group.get(0));
            List<String> losers = group.stream()
                    .map(SysAiKnowledgeGraphRelation::getUid)
                    .filter(uid -> !uid.equals(primary.getUid()))
                    .toList();
            if (losers.isEmpty()) {
                continue;
            }
            // 重复边的 ref 改挂保留的那条，否则知识点一删就误删了仍需保留的边
            List<SysAiKnowledgeGraphRef> loserRefs = graphRefMapper.selectList(
                    new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                            .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_RELATION)
                            .in(SysAiKnowledgeGraphRef::getTargetUid, losers));
            if (loserRefs != null) {
                for (SysAiKnowledgeGraphRef ref : loserRefs) {
                    addRef(libraryId, TARGET_RELATION, primary.getUid(),
                            ref.getKnowledgeBaseId(), ref.getDocPath());
                }
            }
            graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                    .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_RELATION)
                    .in(SysAiKnowledgeGraphRef::getTargetUid, losers));
            removed += graphRelationMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                    .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                    .in(SysAiKnowledgeGraphRelation::getUid, losers));
        }
        return removed;
    }

    /**
     * 把实体说明包装成合法 JSON。
     *
     * <p>{@code sys_ai_knowledge_graph_entity_properties} 是 JSON 类型列，直接写入
     * 纯文本会被数据库以 invalid JSON 拒绝，必须序列化成对象。</p>
     *
     * @param description 实体说明，可为空
     * @return JSON 字符串；无说明时返回 null（列可空）
     */
    private String descriptionJson(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("description", description.trim()));
        } catch (Exception e) {
            // 说明文本是附加信息，序列化失败不影响实体本身
            log.warn("[KnowledgeGraph] 实体说明序列化失败，已丢弃: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析关系端点对应的实体 uid：先查本轮已抽出的实体，再查库内同实体，最后补建。
     *
     * @param libraryId 图谱库ID
     * @param known 本轮已抽出的「名称 → uid」
     * @param name 端点实体名称
     * @return 实体 uid
     */
    private String resolveEntityUid(Integer libraryId, Map<String, String> known, String name) {
        String key = normalizeName(name);
        String uid = known.get(key);
        if (uid != null) {
            return uid;
        }
        // 真正去库内按名称找一次：upsertEntity 的去重键是「名称 + 类型」，
        // 直接拿 "other" 去 upsert 会给一个已存在的同名 person/location 再造一个 other 副本。
        String existing = findEntityUidByName(libraryId, name);
        String result = existing != null ? existing : upsertEntity(libraryId, name, "other", null);
        known.put(key, result);
        return result;
    }

    /**
     * 按名称（忽略类型、忽略大小写）查库内已有实体。
     *
     * <p>{@link #upsertEntity} 的去重键是「名称 + 类型」，所以「找一个叫关羽的人」
     * 不能走它——那样会因类型不同而新建一个副本。这里按名称单独查一次，
     * 供关系端点解析与手工连边复用。</p>
     *
     * @param libraryId 图谱库ID
     * @param name      实体名称
     * @return 命中的实体 uid；没有则 null
     */
    private String findEntityUidByName(Integer libraryId, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        List<SysAiKnowledgeGraphEntity> hits = graphEntityMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphEntity::getName, name.trim()));
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        // 同一名称可能因历史数据存在多条（不同类型），取任意一条即可，
        // 后续 pruneOrphans 会把没被引用的那些回收掉
        return hits.get(0).getUid();
    }

    /**
     * 取或建立关系。
     *
     * @param libraryId 图谱库ID
     * @param sourceUid 源实体 uid
     * @param targetUid 目标实体 uid
     * @param type 关系类型
     * @return 关系 uid
     */
    /**
     * 取或建立关系。
     *
     * <p><b>唯一键是「库 + 源 + 目标」，不含 type</b>：同一对实体在图上只应有一条边。
     * 早先把 type 也放进键里，结果是模型换个说法（结义 / 结为异姓兄弟 / 结拜）就多出一行边，
     * 同一篇内容重复抽取时关系数一轮轮往上走——实测线上同一对实体挂着 2 种以上类型的组有 16 个。
     * 现在 type 退化成<b>可升级属性</b>：命中已有边时只累加 weight，
     * 仅当已有类型是缺省值或与本次归一化后不同、且本次更具体时才改写，
     * 以免类型在多次抽取之间来回跳。</p>
     *
     * @param libraryId 图谱库ID
     * @param sourceUid 源实体 uid
     * @param targetUid 目标实体 uid
     * @param type      关系类型（应已由 {@link #normalizeRelationType} 归一化）
     * @return 关系 uid
     */
    private String upsertRelation(Integer libraryId, String sourceUid, String targetUid, String type) {
        String canonical = normalizeRelationType(type);
        SysAiKnowledgeGraphRelation exists = graphRelationMapper.selectOne(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphRelation::getSourceEntityUid, sourceUid)
                        .eq(SysAiKnowledgeGraphRelation::getTargetEntityUid, targetUid)
                        .orderByDesc(SysAiKnowledgeGraphRelation::getWeight)
                        .last("limit 1"));
        if (exists != null) {
            SysAiKnowledgeGraphRelation update = new SysAiKnowledgeGraphRelation();
            update.setId(exists.getId());
            update.setWeight((exists.getWeight() == null ? 1.0D : exists.getWeight()) + 1.0D);
            // 已有类型是缺省值、或归一化后确实变了（老数据里存的是未归一化的写法）才改写
            boolean existingBlank = !StringUtils.hasText(exists.getType())
                    || DEFAULT_RELATION_TYPE.equals(exists.getType());
            if (!DEFAULT_RELATION_TYPE.equals(canonical) && (existingBlank || !canonical.equals(exists.getType()))) {
                update.setType(canonical);
            }
            graphRelationMapper.updateById(update);
            return exists.getUid();
        }
        SysAiKnowledgeGraphRelation relation = new SysAiKnowledgeGraphRelation();
        relation.setUid(newId());
        relation.setLibraryId(libraryId);
        relation.setSourceEntityUid(sourceUid);
        relation.setTargetEntityUid(targetUid);
        relation.setType(canonical);
        relation.setSource(SOURCE_LLM);
        relation.setWeight(1.0D);
        relation.setStatus(STATUS_ENABLED);
        graphRelationMapper.insert(relation);
        return relation.getUid();
    }

    /**
     * 新增一条「目标 ↔ 知识点」关联，重复时累加提及次数。
     *
     * @param libraryId 图谱库ID
     * @param targetType entity 或 relation
     * @param targetUid 目标 uid
     * @param knowledgeBaseId 知识库ID
     * @param docPath 知识点相对路径
     */
    private void addRef(Integer libraryId, String targetType, String targetUid,
                        String knowledgeBaseId, String docPath) {
        SysAiKnowledgeGraphRef exists = graphRefMapper.selectOne(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                        .eq(SysAiKnowledgeGraphRef::getTargetType, targetType)
                        .eq(SysAiKnowledgeGraphRef::getTargetUid, targetUid)
                        .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(SysAiKnowledgeGraphRef::getDocPath, docPath)
                        .last("limit 1"));
        // 已存在就直接返回：去重由唯一键 (target_type, target_uid, kb_id, doc_path) 保证，
        // 这里**不做任何累加**。
        //
        // 原先这里有个 mentions = exists + 1 的计数器，已删除，原因有二：
        //   1) 它统计的不是「关系数」。同一次抽取里，实体循环 addRef 一次，
        //      随后每条关系又对它的两个端点各 addRef 一次，热门实体（比如关羽
        //      参与 6 条关系）单次抽取就被加 7 次；实测单条 ref 的 mentions 涨到 59。
        //   2) 它只写不读——全仓没有任何 VO / 接口 / 前端消费这个字段，
        //      是一个没人看的「分析统计」。
        // 真正的关系数一律在读时从关系表算：节点的 degree 由图谱视图遍历边表统计得出，
        // 某个知识点引用了多少条关系也应当对 relation 类型的 ref 直接 count，
        // 不需要、也不应该维护一个会漂移的冗余计数器。
        if (exists != null) {
            return;
        }
        SysAiKnowledgeGraphRef ref = new SysAiKnowledgeGraphRef();
        ref.setUid(newId());
        ref.setLibraryId(libraryId);
        ref.setTargetType(targetType);
        ref.setTargetUid(targetUid);
        ref.setKnowledgeBaseId(knowledgeBaseId);
        ref.setDocPath(docPath);
        ref.setStatus(STATUS_ENABLED);
        graphRefMapper.insert(ref);
    }

    /**
     * 删除某知识点在本图谱库下的全部关联。
     *
     * @param libraryId 图谱库ID
     * @param knowledgeBaseId 知识库ID
     * @param docPath 知识点相对路径
     */
    private void clearRefs(Integer libraryId, String knowledgeBaseId, String docPath) {
        graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                .eq(SysAiKnowledgeGraphRef::getLibraryId, libraryId)
                .eq(SysAiKnowledgeGraphRef::getKnowledgeBaseId, knowledgeBaseId)
                .eq(SysAiKnowledgeGraphRef::getDocPath, docPath));
    }

    /**
     * 回收不再被任何知识点引用的孤立实体与关系。
     *
     * <p>只删「没有任何关联记录」的行，因此被其它知识点共用的实体不会被误删。</p>
     *
     * @param libraryId 图谱库ID
     */
    private void pruneOrphans(Integer libraryId) {
        Set<String> aliveEntities = referencedUids(libraryId, TARGET_ENTITY);
        Set<String> aliveRelations = referencedUids(libraryId, TARGET_RELATION);
        List<SysAiKnowledgeGraphEntity> entities = graphEntityMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId));
        Set<String> existingEntityUids = new HashSet<>();
        for (SysAiKnowledgeGraphEntity entity : entities) {
            if (!aliveEntities.contains(entity.getUid())) {
                graphEntityMapper.deleteById(entity.getId());
                // 必须 continue：被删掉的实体不能再计入「仍存在」。否则下面判断关系端点时
                // 会认为它还在，端点已消失的悬空边这一轮就漏掉了；要等下一次 pruneOrphans
                // 才清，而库往往已被弃用，于是永久残留（表现为删库后关系表仍有大量行）
                continue;
            }
            existingEntityUids.add(entity.getUid());
        }
        List<SysAiKnowledgeGraphRelation> relations = graphRelationMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId));
        for (SysAiKnowledgeGraphRelation relation : relations) {
            boolean orphan = !aliveRelations.contains(relation.getUid())
                    // 端点实体已消失时关系也必须删，否则图上会出现指向空节点的悬空边
                    || !existingEntityUids.contains(relation.getSourceEntityUid())
                    || !existingEntityUids.contains(relation.getTargetEntityUid());
            if (orphan) {
                graphRelationMapper.deleteById(relation.getId());
            }
        }
    }

    /**
     * 取某图谱库下仍被引用（且有效）的目标 uid 集合。
     *
     * @param libraryId 图谱库ID
     * @param targetType entity 或 relation
     * @return 目标 uid 集合
     */
    private Set<String> referencedUids(Integer libraryId, String targetType) {
        List<SysAiKnowledgeGraphRef> refs = graphRefMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                        .eq(SysAiKnowledgeGraphRef::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphRef::getTargetType, targetType)
                        .eq(SysAiKnowledgeGraphRef::getStatus, STATUS_ENABLED));
        Set<String> uids = new LinkedHashSet<>();
        for (SysAiKnowledgeGraphRef ref : refs) {
            uids.add(ref.getTargetUid());
        }
        return uids;
    }

    /**
     * 按 uid 集合装配图谱视图。
     *
     * @param libraryId 图谱库ID
     * @param entityUids 要展示的实体 uid
     * @param relationUids 要展示的关系 uid
     * @return 图谱视图
     */
    private KnowledgeGraphViewVO buildView(Integer libraryId, Set<String> entityUids,
                                           Set<String> relationUids) {
        if (entityUids.isEmpty() && relationUids.isEmpty()) {
            return KnowledgeGraphViewVO.empty();
        }
        List<SysAiKnowledgeGraphEntity> entities = graphEntityMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId));
        Map<String, SysAiKnowledgeGraphEntity> entityByUid = new HashMap<>();
        for (SysAiKnowledgeGraphEntity entity : entities) {
            if (entityUids.contains(entity.getUid())) {
                entityByUid.put(entity.getUid(), entity);
            }
        }
        List<SysAiKnowledgeGraphRelation> relations = graphRelationMapper.selectList(
                new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                        .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId));
        List<KnowledgeGraphEdgeVO> edges = new ArrayList<>();
        // 关系的两端必须在可见实体集合内，否则前端会画出指向不存在节点的边
        Set<String> visibleEntityUids = new LinkedHashSet<>(entityByUid.keySet());
        for (SysAiKnowledgeGraphRelation relation : relations) {
            if (!relationUids.contains(relation.getUid())) {
                continue;
            }
            if (!visibleEntityUids.contains(relation.getSourceEntityUid())
                    || !visibleEntityUids.contains(relation.getTargetEntityUid())) {
                continue;
            }
            edges.add(new KnowledgeGraphEdgeVO(relation.getUid(),
                    relation.getSourceEntityUid(), relation.getTargetEntityUid(),
                    relation.getType(), "#94a3b8"));
        }
        List<KnowledgeGraphNodeVO> nodes = new ArrayList<>();
        // 度数 = 该实体在本次视图里的关系条数（出边 + 入边），前端据此缩放节点大小
        Map<String, Integer> degreeByUid = new HashMap<>();
        for (KnowledgeGraphEdgeVO edge : edges) {
            degreeByUid.merge(edge.from(), 1, Integer::sum);
            degreeByUid.merge(edge.to(), 1, Integer::sum);
        }
        for (SysAiKnowledgeGraphEntity entity : entityByUid.values()) {
            nodes.add(new KnowledgeGraphNodeVO(entity.getUid(), entity.getName(), entity.getType(),
                    TYPE_COLORS.getOrDefault(entity.getType(), TYPE_COLORS.get("other")),
                    entity.getWeight(),
                    degreeByUid.getOrDefault(entity.getUid(), 0),
                    entity.getSource()));
        }
        return new KnowledgeGraphViewVO(nodes, edges, nodes.size(), edges.size(), false);
    }

    /**
     * 知识点相对路径归一化：去空白并截断到列宽。
     *
     * <p>{@code sys_ai_knowledge_graph_ref_doc_path} 是 VARCHAR(255)，
     * 超长路径会让 INSERT 直接失败（严格模式下报 Data too long）。
     * 写入、删除、查询三处都走这里，保证截断口径一致，否则会出现
     * 「写进去了但按原路径删不掉」的残留。</p>
     *
     * @param docPath 原始相对路径，可为空
     * @return 归一化后的相对路径
     */
    private String normalizeDocPath(String docPath) {
        if (docPath == null) {
            return "";
        }
        String trimmed = docPath.trim();
        return trimmed.length() > DOC_PATH_MAX_LENGTH
                ? trimmed.substring(0, DOC_PATH_MAX_LENGTH) : trimmed;
    }

    /**
     * 实体类型归一化：把中文与大模型常见别名收敛到固定枚举。
     *
     * @param raw 原始类型
     * @return person / location / event / organization / other 之一
     */
    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "other";
        }
        String key = raw.trim().toLowerCase();
        return TYPE_ALIAS.getOrDefault(key, "other");
    }

    /**
     * 关系类型归一化：把模型输出的同义变体收敛到 {@link #RELATION_TYPE_ALIAS} 登记的标准写法。
     *
     * <p>空白类型回落 {@link #DEFAULT_RELATION_TYPE}；未登记的类型原样保留（去首尾空白），
     * 不做相似度猜测——宁可多一种类型，也不要把两种不同语义错并成一种。</p>
     *
     * @param raw 原始关系类型
     * @return 归一化后的关系类型，不为 null
     */
    private String normalizeRelationType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_RELATION_TYPE;
        }
        String key = raw.trim();
        return RELATION_TYPE_ALIAS.getOrDefault(key, key);
    }

    /**
     * 实体名称归一化，用于同一知识点内的名称去重。
     *
     * @param name 实体名称
     * @return 去空白并转小写后的名称
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    /**
     * 生成无横线 UUID 作为业务主键。
     *
     * @return 32 位十六进制字符串
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int collapseDuplicateGraph(String knowledgeBaseId) {
        if (!StringUtils.hasText(knowledgeBaseId)) {
            return 0;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return 0;
        }
        int removed = collapseDuplicateEntities(libraryId);
        pruneOrphans(libraryId);
        if (removed > 0) {
            log.info("[KnowledgeGraph] 知识库 {} 收敛重复图谱，合并/删除实体 {} 条", knowledgeBaseId, removed);
        }
        return removed;
    }

    // ==================== 手工维护：实体 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addManualEntity(String knowledgeBaseId, String knowledgeBaseName,
                                  String name, String type, String description) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(name)) {
            return null;
        }
        Integer libraryId = ensureLibrary(knowledgeBaseId, knowledgeBaseName);
        String uid = upsertEntity(libraryId, name.trim(), normalizeType(type), description);
        addRef(libraryId, TARGET_ENTITY, uid, knowledgeBaseId, MANUAL_DOC_PATH);
        pruneOrphans(libraryId);
        return uid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateEntity(String knowledgeBaseId, String uid, String name,
                                String type, String description) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(uid)) {
            return false;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return false;
        }
        SysAiKnowledgeGraphEntity exists = graphEntityMapper.selectOne(
                new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                        .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId)
                        .eq(SysAiKnowledgeGraphEntity::getUid, uid)
                        .last("limit 1"));
        if (exists == null) {
            return false;
        }
        SysAiKnowledgeGraphEntity update = new SysAiKnowledgeGraphEntity();
        update.setId(exists.getId());
        if (StringUtils.hasText(name)) {
            update.setName(name.trim());
        }
        if (StringUtils.hasText(type)) {
            update.setType(normalizeType(type));
        }
        if (description != null) {
            update.setProperties(descriptionJson(description));
        }
        graphEntityMapper.updateById(update);
        pruneOrphans(libraryId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEntity(String knowledgeBaseId, String uid) {
        return deleteEntities(knowledgeBaseId, List.of(uid)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEntities(String knowledgeBaseId, Collection<String> uids) {
        if (!StringUtils.hasText(knowledgeBaseId) || uids == null || uids.isEmpty()) {
            return 0;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return 0;
        }
        List<String> targets = uids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            return 0;
        }
        // 1) 先删这些实体身上的关联，避免它们还被引用时又被孤立回收逻辑"顺手"处理
        graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_ENTITY)
                .in(SysAiKnowledgeGraphRef::getTargetUid, targets));
        // 2) 删以它们为端点的关系：手工删了「关羽」，指向关羽的边必须一起走，
        //    否则会留下指向不存在节点的悬空边（pruneOrphans 只回收没被任何 ref 引用的关系，
        //    而这些边自己可能还挂在别的知识点上）
        graphRelationMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                .and(w -> w.in(SysAiKnowledgeGraphRelation::getSourceEntityUid, targets)
                        .or().in(SysAiKnowledgeGraphRelation::getTargetEntityUid, targets)));
        // 3) 最后删实体本身
        int removed = graphEntityMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphEntity>()
                .eq(SysAiKnowledgeGraphEntity::getLibraryId, libraryId)
                .in(SysAiKnowledgeGraphEntity::getUid, targets));
        pruneOrphans(libraryId);
        return removed;
    }

    // ==================== 手工维护：关系 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addManualRelation(String knowledgeBaseId, String knowledgeBaseName,
                                    String source, String target, String type) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return null;
        }
        Integer libraryId = ensureLibrary(knowledgeBaseId, knowledgeBaseName);
        // 端点先按名称找库内已有实体（忽略类型），找不到才建 other 占位。
        // 直接 upsertEntity(..., "other", ...) 会给已存在的同名 person 再造一个副本。
        String sourceUid = findOrCreatePlaceholder(libraryId, source);
        String targetUid = findOrCreatePlaceholder(libraryId, target);
        if (sourceUid.equals(targetUid)) {
            // 自环没有意义，直接拒绝而不是插一条自己指向自己的边
            return null;
        }
        String uid = upsertRelation(libraryId, sourceUid, targetUid, normalizeRelationType(type));
        addRef(libraryId, TARGET_ENTITY, sourceUid, knowledgeBaseId, MANUAL_DOC_PATH);
        addRef(libraryId, TARGET_ENTITY, targetUid, knowledgeBaseId, MANUAL_DOC_PATH);
        addRef(libraryId, TARGET_RELATION, uid, knowledgeBaseId, MANUAL_DOC_PATH);
        pruneOrphans(libraryId);
        return uid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRelation(String knowledgeBaseId, String uid) {
        return deleteRelations(knowledgeBaseId, List.of(uid)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRelations(String knowledgeBaseId, Collection<String> uids) {
        if (!StringUtils.hasText(knowledgeBaseId) || uids == null || uids.isEmpty()) {
            return 0;
        }
        Integer libraryId = findLibraryId(knowledgeBaseId);
        if (libraryId == null) {
            return 0;
        }
        List<String> targets = uids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            return 0;
        }
        graphRefMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRef>()
                .eq(SysAiKnowledgeGraphRef::getTargetType, TARGET_RELATION)
                .in(SysAiKnowledgeGraphRef::getTargetUid, targets));
        int removed = graphRelationMapper.delete(new LambdaQueryWrapper<SysAiKnowledgeGraphRelation>()
                .eq(SysAiKnowledgeGraphRelation::getLibraryId, libraryId)
                .in(SysAiKnowledgeGraphRelation::getUid, targets));
        // 端点实体可能只被这条边引用，pruneOrphans 会把它们一并回收
        pruneOrphans(libraryId);
        return removed;
    }

    /**
     * 找实体名对应的 uid：库内已有（忽略类型）就用它，没有才建一个 {@code other} 占位。
     *
     * @param libraryId 图谱库ID
     * @param name      实体名称
     * @return 实体 uid
     */
    private String findOrCreatePlaceholder(Integer libraryId, String name) {
        String existing = findEntityUidByName(libraryId, name);
        return existing != null ? existing : upsertEntity(libraryId, name, "other", null);
    }
}
