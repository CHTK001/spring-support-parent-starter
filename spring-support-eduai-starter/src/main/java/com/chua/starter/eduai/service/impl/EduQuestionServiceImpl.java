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
        entity.setQuestionAuditStatus(dto.getQuestionAuditStatus() != null ? dto.getQuestionAuditStatus() : 0);
        entity.setQuestionStatus(dto.getQuestionStatus() != null ? dto.getQuestionStatus() : 1);

        if (dto.getQuestionId() != null && dto.getQuestionId() > 0) {
            // 更新：归属校验后锁定为原归属，客户端传值无效
            EduQuestion exists = baseMapper.selectById(dto.getQuestionId());
            if (exists == null) {
                throw new BusinessException("题目不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            entity.setOwnerUserId(exists.getOwnerUserId());
            entity.setQuestionId(dto.getQuestionId());
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新题目成功，ID: {}", dto.getQuestionId());
            return dto.getQuestionId();
        } else {
            // 新增：请求线程以登录态为准；异步链路（采集）继承任务归属，二者皆无则拒绝
            String owner = EduUserContext.currentUserId();
            if (!StringUtils.hasText(owner)) {
                owner = StringUtils.hasText(dto.getOwnerUserId()) ? dto.getOwnerUserId() : EduUserContext.requireUserId();
            }
            entity.setOwnerUserId(owner);
            entity.setQuestionUseCount(0);
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
     * 剥掉 JSON 字符串标量的外层引号：历史 JSON 列会把绑定的字符串再包一层，
     * 老数据回读后再次保存会不断叠加转义，收敛后落库恒为数组文本。
     *
     * @param value 客户端提交的选项文本
     * @return 去掉多余字符串包装后的文本
     */
    private static String unwrapJsonText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
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
