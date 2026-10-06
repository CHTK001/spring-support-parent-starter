package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionAiSelectDTO;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import com.chua.starter.eduai.support.util.GradeLevels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduQuestionServiceImpl extends ServiceImpl<EduQuestionMapper, EduQuestion> implements IEduQuestionService {

    /** 用于收敛选项文本的 JSON 包装层 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 手动录入的来源类型 */
    private static final String SOURCE_TYPE_MANUAL = "MANUAL";

    /** 手动录入未填来源名时的兜底文案 */
    private static final String DEFAULT_SOURCE_NAME = "手动录入";

    /**
     * 属性
     */
    private final EduAiService eduAiService;

    @Override
    /**
     * Page查询
    */
    public PageResult<EduQuestion> pageQuery(QuestionQueryDTO query) {
        // 未传审核过滤（null 或负数哨兵）时只看已通过：组卷侧（QuestionMarketDialog 不传）
        // 与题库管理全量拉取（前端传 -1 表“不过滤”意图的兜底）分流——
        // null → 默认 1（组卷/外部调用）；负数 → 置 null 不过滤（管理端全量+前端本地筛）。
        if (query.getAuditStatus() == null) {
            query.setAuditStatus(1);
        } else if (query.getAuditStatus() < 0) {
            query.setAuditStatus(null);
        }
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        // 前端下拉传 PRIMARY/JUNIOR 等码值，库里存中文名，不归一化学段筛选恒为 0 条
        query.setGradeLevel(GradeLevels.normalize(query.getGradeLevel()));
        // 分页参数来自请求，必须收敛边界（页码<1 归 1；pageSize 封顶，否则 pageSize=1000000 会拉全表）
        Page<EduQuestion> page = new Page<>(PageBounds.pageNum(query.getPageNum()),
                PageBounds.pageSize(query.getPageSize()));
        IPage<EduQuestion> result = baseMapper.pageSelect(page, query, scope);
        return PageResult.<EduQuestion>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 保存Question
     *
     * @param dto dto
     * @return 保存question的结果
     */
    public Long saveQuestion(QuestionSaveDTO dto) {
        // 审核状态/理由只经专用审核接口流转：save 接口一律不采信客户端传值，
        // 否则任何作者都能把自己的题标成已通过（此前 line 106 原样采信 dto.getQuestionAuditStatus）。
        boolean admin = EduUserContext.isAdmin();
        boolean auditOnSave = Boolean.TRUE.equals(dto.getAuditOnSave());
        if (auditOnSave && !admin) {
            throw new BusinessException("只有管理员可以使用保存并审核");
        }
        EduQuestion entity = new EduQuestion();
        entity.setQuestionType(dto.getQuestionType());
        entity.setQuestionSubject(dto.getQuestionSubject());
        entity.setQuestionGradeLevel(GradeLevels.normalize(dto.getQuestionGradeLevel()));
        entity.setQuestionGrade(dto.getQuestionGrade());
        entity.setQuestionYear(dto.getQuestionYear());
        entity.setQuestionDifficulty(dto.getQuestionDifficulty());
        entity.setQuestionScore(dto.getQuestionScore() != null && dto.getQuestionScore() > 0 ? dto.getQuestionScore() : 5);
        entity.setQuestionKnowledgePointIds(dto.getQuestionKnowledgePointIds());
        entity.setQuestionStem(dto.getQuestionStem());
        entity.setQuestionStemImages(dto.getQuestionStemImages());
        entity.setQuestionOptions(unwrapJsonText(dto.getQuestionOptions()));
        entity.setQuestionAnswer(dto.getQuestionAnswer());
        entity.setQuestionAnalysis(dto.getQuestionAnalysis());
        entity.setQuestionSource(dto.getQuestionSource());
        entity.setQuestionSourceType(dto.getQuestionSourceType());
        entity.setQuestionStatus(dto.getQuestionStatus() != null ? dto.getQuestionStatus() : 1);

        if (dto.getQuestionId() != null && dto.getQuestionId() > 0) {
            // 更新：归属校验后锁定为原归属，客户端传值无效
            EduQuestion exists = baseMapper.selectById(dto.getQuestionId());
            if (exists == null) {
                throw new BusinessException("题目不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            // 保存并审核只用于新建：编辑走正常更新+脏检查，不允许顺手自审
            if (auditOnSave) {
                throw new BusinessException("保存并审核仅用于新建题目");
            }
            // 真实改动才打回待审：比对内容字段，改了任一才重置；
            // 仅调分值/状态/排序的保存不触发重审（纯运营动作不算改数据）。
            boolean dirty = contentDirty(exists, entity);
            if (dirty && !admin) {
                entity.setQuestionAuditStatus(0);
                entity.setQuestionAuditRemark(null);
            } else {
                // 管理员编辑不改内容时保持原审核态；改了内容同样打回待审（与作者同口径）
                entity.setQuestionAuditStatus(dirty ? 0 : exists.getQuestionAuditStatus());
                entity.setQuestionAuditRemark(dirty ? null : exists.getQuestionAuditRemark());
            }
            entity.setQuestionAuditCount(exists.getQuestionAuditCount());
            entity.setOwnerUserId(exists.getOwnerUserId());
            entity.setQuestionId(dto.getQuestionId());
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新题目成功，ID: {}{}", dto.getQuestionId(), dirty ? "（内容变更，已打回待审）" : "");
            return dto.getQuestionId();
        } else {
            // 新增：请求线程以登录态为准；异步链路（采集）继承任务归属，二者皆无则拒绝
            String owner = EduUserContext.currentUserId();
            if (!StringUtils.hasText(owner)) {
                owner = StringUtils.hasText(dto.getOwnerUserId()) ? dto.getOwnerUserId() : EduUserContext.requireUserId();
            }
            entity.setOwnerUserId(owner);
            entity.setQuestionUseCount(0);
            if (auditOnSave) {
                // 管理员保存并审核：新建即通过。这次保存本身就是一次审核动作，
                // 审核次数从 1 起算，否则列表里这条题显示「已通过」却没有 ×N，
                // 与后续每次审核 +1 的口径对不上。
                entity.setQuestionAuditStatus(1);
                entity.setQuestionAuditRemark(null);
                entity.setQuestionAuditCount(1);
            } else {
                entity.setQuestionAuditCount(0);
                // 老师新建 / 管理员普通保存 / 采集入库：一律待审
                entity.setQuestionAuditStatus(0);
                entity.setQuestionAuditRemark(null);
            }
            // 题库手动新增未填来源时兜底为“手动录入”；采集链路已在 toSaveDto 填好来源，此处不覆盖
            if (!StringUtils.hasText(entity.getQuestionSourceType())) {
                entity.setQuestionSourceType(SOURCE_TYPE_MANUAL);
            }
            if (!StringUtils.hasText(entity.getQuestionSource())
                    && SOURCE_TYPE_MANUAL.equalsIgnoreCase(entity.getQuestionSourceType())) {
                entity.setQuestionSource(DEFAULT_SOURCE_NAME);
            }
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
            log.info("新增题目成功，ID: {}", entity.getQuestionId());
            return entity.getQuestionId();
        }
    }

    /**
     * 内容脏检查：只有“真实改过数据”才算。题型/学科/学段/年级/年份/难度/
     * 题干/题干图/选项/答案/解析/知识点/来源 之一变化即为脏；分值/状态/
     * 审核态等运营字段不参与（纯调分、上下架不触发重审）。
     *
     * <p>选项入参先过 unwrapJsonText 再比，保证与落库口径一致，
     * 避免「去层转义」这种无实质变化误判为改动。</p>
     *
     * @param exists 库里旧行
     * @param entity 本次组装的新行
     * @return 是否真实改动
     */
    private static boolean contentDirty(EduQuestion exists, EduQuestion entity) {
        return !equalsText(exists.getQuestionType(), entity.getQuestionType())
                || !equalsText(exists.getQuestionSubject(), entity.getQuestionSubject())
                || !equalsText(exists.getQuestionGradeLevel(), entity.getQuestionGradeLevel())
                || !equalsText(exists.getQuestionGrade(), entity.getQuestionGrade())
                || !equalsText(exists.getQuestionYear(), entity.getQuestionYear())
                || !equalsInt(exists.getQuestionDifficulty(), entity.getQuestionDifficulty())
                || !equalsText(trimOrNull(exists.getQuestionKnowledgePointIds()), trimOrNull(entity.getQuestionKnowledgePointIds()))
                || !equalsText(trimOrNull(exists.getQuestionStem()), trimOrNull(entity.getQuestionStem()))
                || !equalsText(trimOrNull(exists.getQuestionStemImages()), trimOrNull(entity.getQuestionStemImages()))
                || !equalsText(trimOrNull(exists.getQuestionOptions()), trimOrNull(entity.getQuestionOptions()))
                || !equalsText(trimOrNull(exists.getQuestionAnswer()), trimOrNull(entity.getQuestionAnswer()))
                || !equalsText(trimOrNull(exists.getQuestionAnalysis()), trimOrNull(entity.getQuestionAnalysis()))
                || !equalsText(trimOrNull(exists.getQuestionSource()), trimOrNull(entity.getQuestionSource()))
                || !equalsText(trimOrNull(exists.getQuestionSourceType()), trimOrNull(entity.getQuestionSourceType()));
    }

    private static boolean equalsText(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static boolean equalsInt(Integer a, Integer b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 审核题目（通过/驳回）：仅管理员可调。
     *
     * @param id     题目标识
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填，通过时忽略并清空）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditQuestion(Long id, boolean passed, String remark) {
        if (!EduUserContext.isAdmin()) {
            throw new BusinessException("只有管理员可以审核题目");
        }
        EduQuestion exists = baseMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("题目不存在");
        }
        if (!passed && !StringUtils.hasText(remark)) {
            throw new BusinessException("驳回请填写理由");
        }
        EduQuestion patch = new EduQuestion();
        patch.setQuestionId(id);
        patch.setQuestionAuditStatus(passed ? 1 : 2);
        patch.setQuestionAuditRemark(passed ? null : remark.trim());
        patch.setQuestionAuditCount((exists.getQuestionAuditCount() == null ? 0 : exists.getQuestionAuditCount()) + 1);
        patch.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(patch);
        log.info("审核题目，ID: {} 结果: {}", id, passed ? "通过" : "驳回");
    }

    /**
     * 批量审核题目（通过/驳回）：仅管理员可调。单条失败不影响其余，
     * 返回实际成功条数（与 importQuestions 同一容错风格）。
     *
     * @param ids    题目ID列表
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填）
     * @return 成功条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditQuestions(List<Long> ids, boolean passed, String remark) {
        if (!EduUserContext.isAdmin()) {
            throw new BusinessException("只有管理员可以审核题目");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (!passed && !StringUtils.hasText(remark)) {
            throw new BusinessException("驳回请填写理由");
        }
        int count = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            try {
                auditQuestion(id, passed, remark);
                count++;
            } catch (Exception e) {
                log.warn("审核题目失败 ID {}: {}", id, e.getMessage());
            }
        }
        log.info("批量审核完成，通过={} 成功 {} 条，共 {} 条", passed, count, ids.size());
        return count;
    }

    /**
     * 剥掉 JSON 字符串标量的外层引号：历史 JSON 列会把绑定的字符串再包一层，
     * 老数据回读后再次保存会不断叠加转义，收敛后落库恒为数组文本。
     *
     * <p><b>空白入参一律归一为 null</b>：edu_question.question_options 上有
     * {@code CHECK (json_valid(question_options))}，而 MariaDB 的
     * {@code json_valid('')} 返回 <b>0</b>（不是 NULL），空串会被约束拒绝、
     * 报 {@code CONSTRAINT edu_question.question_options failed}（500）。
     * 判断题/填空题/简答题/作文本就没有选项，前端会传 {@code ""}，
     * 不归一就必然插不进去。NULL 走 {@code json_valid(NULL)} → NULL → 约束通过。
     *
     * @param value 客户端提交的选项文本
     * @return 去掉多余字符串包装后的文本；空白返回 {@code null}
     */
    private static String unwrapJsonText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String current = value.trim();
        for (int depth = 0; depth < 5 && current.startsWith("\""); depth++) {
            try {
                JsonNode node = JSON.readTree(current);
                if (!node.isTextual()) {
                    return current;
                }
                current = node.asText().trim();
            } catch (JsonProcessingException e) {
                return value;
            }
        }
        return current;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 导入questions
     *
     * @param list 列表
     * @return 导入questions的结果
     */
    public int importQuestions(List<QuestionSaveDTO> list) {
        int count = 0;
        for (QuestionSaveDTO dto : list) {
            try {
                /**
                 * 保存question。
                 * @param dto dto
                 */
                saveQuestion(dto);
                count++;
            } catch (Exception e) {
                log.warn("导入题目失败: {}", e.getMessage());
            }
        }
        log.info("批量导入完成，成功 {} 条，失败 {} 条", count, list.size() - count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除Question
     *
     * @param id 标识
     * @return 删除question的结果
     */
    public boolean deleteQuestion(Long id) {
        EduQuestion entity = baseMapper.selectById(id);
        if (entity == null) {
            /**
             * 业务异常。
             * @param "题目不存在" "题目不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("题目不存在");
        }
        EduUserContext.checkWritable(entity.getOwnerUserId());
        // 逻辑删除：将状态置为0
        entity.setQuestionStatus(0);
        entity.setUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(entity);
        log.info("删除题目，ID: {}", id);
        return rows > 0;
    }

    @Override
    /**
     * 列出题库中真实存在的学段年级组合
     *
     * @param status 状态
     * @return 组合维度列表
     */
    public List<QuestionGradeDimensionVO> listGradeDimensions(Integer status) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        return baseMapper.selectGradeDimensions(status, scope);
    }

    @Override
    /**
     * AI 选题
     */
    public List<EduQuestion> aiSelect(QuestionAiSelectDTO dto) {
        int count = dto.getCount() == null || dto.getCount() <= 0 ? 10 : Math.min(dto.getCount(), 100);
        // 前端可能传码值（PRIMARY），而库里存中文名，不归一化会选不出题
        String gradeLevel = GradeLevels.normalize(dto.getGradeLevel());
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();

        // 规则选题作为兜底：AI 不可用或解析失败时由 EduAiService 直接返回这份候选
        List<EduQuestion> candidates =
                baseMapper.selectCandidates(dto.getSubject(), gradeLevel, scope, Math.max(count * 3, count));
        List<Long> fallbackIds = candidates.stream()
                .map(EduQuestion::getQuestionId)
                .collect(Collectors.toList());
        if (fallbackIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = eduAiService.generatePaperOutline(
                dto.getSubject(), gradeLevel, dto.getGrade(),
                dto.getTypes(), dto.getKnowledgePointIds(), count, fallbackIds);
        if (ids.size() > count) {
            ids = new ArrayList<>(ids.subList(0, count));
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        // 按 AI 给出的顺序回读，不能直接 listByIds —— 那样会按主键顺序，丢掉 AI 的排序意图
        Map<Long, EduQuestion> byId = new HashMap<>();
        for (EduQuestion q : listByIds(ids)) {
            byId.put(q.getQuestionId(), q);
        }
        List<EduQuestion> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            EduQuestion q = byId.get(id);
            // 模型可能编出不存在的题号，跳过而不是塞 null 进结果
            if (q != null) {
                ordered.add(q);
            }
        }
        log.info("[EduAi] AI 选题: subject={}, gradeLevel={}, 期望={}, 候选={}, 实得={}",
                dto.getSubject(), gradeLevel, count, fallbackIds.size(), ordered.size());
        return ordered;
    }
}
