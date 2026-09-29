package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduPaperHistoryMapper;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.mapper.EduPaperQuestionMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.EduPaperHistory;
import com.chua.starter.eduai.model.EduPaperQuestion;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.PaperComposeUpdateDTO;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.dto.PaperQuestionItemDTO;
import com.chua.starter.eduai.model.vo.PaperHistoryVO;
import com.chua.starter.eduai.model.vo.PaperQuestionQrVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.model.vo.QuestionVO;
import com.chua.starter.eduai.service.IEduPaperService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 试卷服务实现
 *
 * <p>AI 组卷流程：
 * <ol>
 *   <li>先按学科/学段/年级/题型从题库中筛出候选题目（兜底数据）</li>
 *   <li>调用 {@link EduAiService#generatePaperOutline} 让模型挑选合适的题目 ID</li>
 *   <li>AI 不可用/返回为空时回退到候选前 N 条</li>
 *   <li>写入 {@code edu_paper} 与 {@code edu_paper_question} 关联表</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduPaperServiceImpl extends ServiceImpl<EduPaperMapper, EduPaper> implements IEduPaperService {

    /**
     * 手动组卷试卷默认满分（未显式配置满分时的兜底值）
     */
    private static final int PAPER_FULL_SCORE = 100;

    /**
     * 取试卷满分。
     *
     * <p>满分可配置（建卷时通过 totalScore 指定），发布校验与「均分」都以试卷自身的
     * 满分为基准；老数据或异常值（null / 非正数）回退到默认满分，避免出现除零或
     * 「满分 0 分」这类不可能通过的校验。</p>
     *
     * @param paper 试卷，不能为 null
     * @return 该试卷的满分，始终为正数
     */
    private int resolveFullScore(EduPaper paper) {
        Integer configured = paper.getPaperTotalScore();
        return configured == null || configured <= 0 ? PAPER_FULL_SCORE : configured;
    }

    /**
     * 空白卷默认名称
     */
    private static final String DEFAULT_PAPER_NAME = "未命名试卷";

    /**
     * 试卷编号时间部分格式
     */
    private static final DateTimeFormatter PAPER_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 试卷编号随机部分上限（4 位十六进制）
     */
    private static final int PAPER_NO_RANDOM_BOUND = 0x10000;

    /**
     * 试卷编号生成最大重试次数
     */
    private static final int PAPER_NO_MAX_ATTEMPTS = 10;

    /**
     * 历史快照 JSON 序列化器（LinkedHashMap 保序，保证同一状态摘要稳定）
     */
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    /** 试卷状态：草稿。可编辑；学生端（扫码）不可见。 */
    private static final int STATUS_DRAFT = 0;

    /** 试卷状态：已发布。学生端可见；改动前必须先下架。 */
    private static final int STATUS_PUBLISHED = 1;

    /** 试卷状态：已归档。只读留档，不可修改、不可再发布。 */
    private static final int STATUS_ARCHIVED = 2;

    /**
     * Paperquestionmapper
    */
    private final EduPaperQuestionMapper paperQuestionMapper;

    /**
     * 试卷历史mapper
    */
    private final EduPaperHistoryMapper paperHistoryMapper;
    /**
     * Questionmapper
    */
    private final EduQuestionMapper questionMapper;
    /**
     * EDUAI服务
    */
    private final EduAiService eduAiService;
    /**
     * 属性
    */
    private final EduAiProperties properties;

    @Override
    /**
     * Page查询
    */
    public PageResult<PaperVO> pageQuery(PaperQueryDTO query) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        // 列表下拉提交的是学段码值（PRIMARY/JUNIOR/…），而库里 paper_grade_level 存的是中文名。
        // mapper 是原值等值比较，不归一化就会「选了学段查不到任何卷」。normalize 对中文名幂等，两种入参都安全。
        query.setGradeLevel(GradeLevels.normalize(query.getGradeLevel()));
        // 分页参数来自请求，必须收敛边界（页码<1 归 1；pageSize 封顶，否则 pageSize=1000000 会拉全表）
        Page<EduPaper> page = new Page<>(PageBounds.pageNum(query.getPageNum()),
                PageBounds.pageSize(query.getPageSize()));
        IPage<EduPaper> result = baseMapper.pageSelect(page, query, scope);

        List<PaperVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.<PaperVO>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(voList)
                .build();
    }

    @Override
    /**
     * 获取paperdetail
    */
    public PaperVO getPaperDetail(Long paperId) {
        EduPaper paper = baseMapper.selectById(paperId);
        if (paper == null) {
            /**
             * 业务异常。
             * @param "试卷不存在" "试卷不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("试卷不存在");
        }
        EduUserContext.checkReadable(paper.getOwnerUserId());
        PaperVO vo = toVO(paper);

        // 读详情即补齐历史缺失的题项编号：编号是每题二维码的内容，缺失会导致二维码为空
        backfillMissingQuestionNos(paper);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);

        if (!relations.isEmpty()) {
            List<Long> questionIds = relations.stream()
                    .map(EduPaperQuestion::getPaperQuestionQuestionId)
                    .collect(Collectors.toList());
            Map<Long, EduQuestion> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                    .collect(Collectors.toMap(EduQuestion::getQuestionId, q -> q, (a, b) -> a));
            List<QuestionVO> questionVOs = new ArrayList<>();
            for (EduPaperQuestion relation : relations) {
                EduQuestion q = questionMap.get(relation.getPaperQuestionQuestionId());
                if (q == null) {
                    continue;
                }
                QuestionVO qv = new QuestionVO();
                qv.setId(q.getQuestionId());
                qv.setType(q.getQuestionType());
                qv.setStem(q.getQuestionStem());
                qv.setOptions(q.getQuestionOptions());
                qv.setAnswer(q.getQuestionAnswer());
                qv.setAnalysis(q.getQuestionAnalysis());
                qv.setScore(relation.getPaperQuestionScore());
                qv.setQuestionNo(relation.getPaperQuestionNo());
                qv.setOrder(relation.getPaperQuestionOrder());
                qv.setSection(relation.getPaperQuestionSection());
                questionVOs.add(qv);
            }
            vo.setQuestions(questionVOs);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * generatepaper
     *
     * @param dto dto
     * @return generatePaper的结果
     */
    public Long generatePaper(PaperGenerateDTO dto) {
        log.info("[EduAi] AI 组卷: subject={}, knowledgePoints={}", dto.getSubject(), dto.getKnowledgePointIds());

        // 1) 创建试卷草稿
        EduPaper paper = new EduPaper();
        paper.setPaperName(dto.getName());
        paper.setPaperSubject(dto.getSubject());
        // 学段与手动组卷保持同一口径：库里存中文名（PRIMARY -> 小学）。
        // 此前 AI 路径直接存前端传来的码值，会让「学段」筛选查不到 AI 组的卷。
        paper.setPaperGradeLevel(GradeLevels.normalize(dto.getGradeLevel()));
        paper.setPaperGrade(dto.getGrade());
        paper.setPaperTotalScore(dto.getTotalScore() != null ? dto.getTotalScore() : properties.getPaper().getMaxTotalScore());
        paper.setPaperDuration(dto.getDuration());
        paper.setPaperStatus(0); // 草稿
        paper.setPaperSource("AI");
        paper.setPaperNo(generatePaperNo());
        paper.setPaperVersion(1);
        paper.setPaperPageSize("A4");
        paper.setPaperPageOrientation("PORTRAIT");
        paper.setPaperTemplate("default");
        paper.setPaperSubjectLocked(0);
        paper.setOwnerUserId(EduUserContext.requireUserId());
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());;
        baseMapper.insert(paper);

        int count = dto.getQuestionCount() != null ? dto.getQuestionCount() : properties.getPaper().getDefaultQuestionCount();
        int perScore = dto.getScorePerQuestion() != null ? dto.getScorePerQuestion() : properties.getPaper().getDefaultScorePerQuestion();

        // 2) 候选题目（兜底数据，仅本人题库）：先按学段收敛，学段无题时降级到同学科全集
        List<EduQuestion> candidates = loadCandidates(dto.getSubject(), dto.getGradeLevel(), count);
        if (candidates.isEmpty() && StringUtils.hasText(dto.getGradeLevel())) {
            log.warn("[EduAi] 题库没有 {} 学段的 {} 题目，降级为同学科全部题目组卷", dto.getGradeLevel(), dto.getSubject());
            candidates = loadCandidates(dto.getSubject(), null, count);
        }
        if (candidates.isEmpty()) {
            log.warn("[EduAi] 题库为空，无法组卷: subject={}", dto.getSubject());
            writeHistory(paper, List.of(), "CREATE");
            return paper.getPaperId();
        }

        // 3) 知识点评分过滤（如果指定）
        List<Long> candidateIds = new ArrayList<>();
        if (dto.getKnowledgePointIds() != null && !dto.getKnowledgePointIds().isEmpty()) {
            for (EduQuestion q : candidates) {
                String kpIds = q.getQuestionKnowledgePointIds();
                if (!StringUtils.hasText(kpIds)) {
                    continue;
                }
                for (Long kp : dto.getKnowledgePointIds()) {
                    if (kpIds.contains(String.valueOf(kp))) {
                        candidateIds.add(q.getQuestionId());
                        break;
                    }
                }
            }
            if (candidateIds.isEmpty()) {
                candidateIds = candidates.stream().map(EduQuestion::getQuestionId).collect(Collectors.toList());
            }
        } else {
            candidateIds = candidates.stream().map(EduQuestion::getQuestionId).collect(Collectors.toList());
        }

        // 4) AI 挑选
        List<String> types = null;
        if (dto.getTypeConfigs() != null && !dto.getTypeConfigs().isEmpty()) {
            types = dto.getTypeConfigs().stream()
                    .map(PaperGenerateDTO.QuestionTypeConfig::getType)
                    .collect(Collectors.toList());
        }
        List<Long> knowledgeIds = dto.getKnowledgePointIds();
        List<Long> aiIds = eduAiService.generatePaperOutline(
                dto.getSubject(), dto.getGradeLevel(), dto.getGrade(),
                types, knowledgeIds, count, candidateIds);
        if (aiIds.size() > count) {
            aiIds = aiIds.subList(0, count);
        }

        // 5) 入库关联
        int order = 1;
        int totalScore = 0;
        for (Long qid : aiIds) {
            EduPaperQuestion pq = new EduPaperQuestion();
            pq.setPaperQuestionPaperId(paper.getPaperId());
            pq.setPaperQuestionQuestionId(qid);
            pq.setPaperQuestionOrder(order);
            pq.setPaperQuestionScore(perScore);
            pq.setPaperQuestionNo(paper.getPaperNo() + "-" + formatOrder(order));
            pq.setPaperQuestionVersion(paper.getPaperVersion());
            pq.setOwnerUserId(paper.getOwnerUserId());
            pq.setCreateTime(LocalDateTime.now());
            paperQuestionMapper.insert(pq);
            totalScore += perScore;
            order++;
        }

        // 6) 回写试卷总分/题目数
        paper.setPaperTotalScore(totalScore);
        paper.setPaperQuestionCount(aiIds.size());
        paper.setUpdateTime(LocalDateTime.now());;
        baseMapper.updateById(paper);
        writeHistory(paper, paperQuestionMapper.selectByPaperId(paper.getPaperId()), "CREATE");

        log.info("[EduAi] AI 组卷完成: paperId={}, questionCount={}", paper.getPaperId(), aiIds.size());
        return paper.getPaperId();
    }

    /**
     * 按学科（可选学段）拉取本人题库中可用于组卷的候选题目。
     *
     * <p>候选窗按题目 ID 倒序取样：正向取 limit 会把窗口永久钉在最早入库的历史样例上，
     * 之后采集到的题目无论多少都进不了组卷。</p>
     *
     * @param subject    学科
     * @param gradeLevel 学段，为空时不限学段
     * @param count      目标题数，候选量按其三倍取样
     * @return 候选题目列表
     */
    private List<EduQuestion> loadCandidates(String subject, String gradeLevel, int count) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        // 组卷页提交 PRIMARY 等码值，题目库存中文名，不归一化会误落到「同学科全部」的降级分支
        return questionMapper.selectCandidates(subject, GradeLevels.normalize(gradeLevel), scope, Math.max(count * 3, count));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 替换question
     *
     * @param paperId paperid
     * @param oldQuestionId 旧questionid
     * @param newQuestionId 新questionid
     * @return 替换question的结果
     */
    public boolean replaceQuestion(Long paperId, Long oldQuestionId, Long newQuestionId) {
        EduPaper paper = baseMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        EduUserContext.checkWritable(paper.getOwnerUserId());
        paperQuestionMapper.deleteByPaperAndQuestion(paperId, oldQuestionId);

        EduPaperQuestion pq = new EduPaperQuestion();
        pq.setPaperQuestionPaperId(paperId);
        pq.setPaperQuestionQuestionId(newQuestionId);
        pq.setPaperQuestionOrder(0);
        pq.setPaperQuestionScore(properties.getPaper().getDefaultScorePerQuestion());
        pq.setOwnerUserId(paper.getOwnerUserId());
        pq.setCreateTime(LocalDateTime.now());
        paperQuestionMapper.insert(pq);

        log.info("[EduAi] 替换题目成功: paperId={}, oldId={}, newId={}", paperId, oldQuestionId, newQuestionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新建空白试卷
     *
     * @param dto dto
     * @return 新建空白试卷的结果
     */
    public Long createManualPaper(PaperComposeUpdateDTO dto) {
        EduPaper paper = new EduPaper();
        paper.setPaperName(DEFAULT_PAPER_NAME);
        paper.setPaperPageSize("A4");
        paper.setPaperPageOrientation("PORTRAIT");
        paper.setPaperTemplate("default");
        paper.setPaperSubjectLocked(0);
        if (dto != null) {
            if (StringUtils.hasText(dto.name())) {
                paper.setPaperName(dto.name());
            }
            /*
              paper_subject / paper_grade_level / paper_grade 在 edu_paper 上是 NOT NULL 且无默认值，
              缺失时若直接落库会抛 SQLIntegrityConstraintViolation（HTTP 500 「必填字段不能为空」），
              对使用者是不可理解的报错。这里前置校验，给出可操作的提示。
             */
            if (!StringUtils.hasText(dto.subject())) {
                throw new BusinessException("学科不能为空");
            }
            if (!StringUtils.hasText(dto.gradeLevel())) {
                throw new BusinessException("学段不能为空");
            }
            if (!StringUtils.hasText(dto.grade())) {
                throw new BusinessException("年级不能为空");
            }
            paper.setPaperSubject(dto.subject());
            paper.setPaperGradeLevel(GradeLevels.normalize(dto.gradeLevel()));
            paper.setPaperGrade(dto.grade());
            paper.setPaperDuration(dto.duration());
            if (StringUtils.hasText(dto.pageSize())) {
                paper.setPaperPageSize(dto.pageSize());
            }
            if (StringUtils.hasText(dto.pageOrientation())) {
                paper.setPaperPageOrientation(dto.pageOrientation());
            }
            if (StringUtils.hasText(dto.template())) {
                paper.setPaperTemplate(dto.template());
            }
            paper.setPaperHeader(dto.header());
            paper.setPaperFooter(dto.footer());
            if (dto.subjectLocked() != null) {
                paper.setPaperSubjectLocked(dto.subjectLocked());
            }
            if (dto.difficulty() != null) {
                paper.setPaperDifficulty(dto.difficulty());
            }
            paper.setPaperTemplateId(dto.templateId());
        }
        // 满分：优先取入参，未传时按默认满分；负数/0 视为未传
        Integer wantScore = dto == null ? null : dto.totalScore();
        paper.setPaperTotalScore(wantScore != null && wantScore > 0 ? wantScore : PAPER_FULL_SCORE);
        paper.setPaperQuestionCount(0);
        paper.setPaperStatus(0);
        paper.setPaperSource("MANUAL");
        paper.setPaperNo(generatePaperNo());
        paper.setPaperVersion(1);
        paper.setOwnerUserId(EduUserContext.requireUserId());
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(paper);
        writeHistory(paper, List.of(), "CREATE");
        log.info("[EduAi] 新建空白试卷成功: paperId={}, paperNo={}", paper.getPaperId(), paper.getPaperNo());
        return paper.getPaperId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 批量加题
     *
     * @param paperId paperid
     * @param items items
     * @return 批量加题的结果
     */
    public int addQuestions(Long paperId, List<PaperQuestionItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        EduPaper paper = requireEditableDraft(paperId);
        int nextSeq = nextQuestionSeq(paperId, paper.getPaperNo());
        int nextOrder = nextOrder(paperId);
        int added = 0;
        int updated = 0;
        for (PaperQuestionItemDTO item : items) {
            if (item == null || item.questionId() == null) {
                throw new BusinessException("题目标识不能为空");
            }
            EduPaperQuestion exists = paperQuestionMapper.selectByPaperAndQuestion(paperId, item.questionId());
            if (exists != null) {
                /*
                  已存在的题按「同题幂等写入」处理：分值变了必须更新。
                  前端改分会复用本接口（契约里没有单独的改分接口），
                  若这里直接跳过，界面上改的分值永远落不了库，
                  会导致「前端显示合计已满、后端实际合计不符」→ 发布被满分闸门拒掉。
                */
                int wantedScore = item.score() == null ? 0 : item.score();
                Integer currentScore = exists.getPaperQuestionScore();
                if (currentScore != null && currentScore == wantedScore) {
                    log.debug("[EduAi] 题目已在试卷中且分值未变，跳过: paperId={}, questionId={}", paperId, item.questionId());
                    continue;
                }
                exists.setPaperQuestionScore(wantedScore);
                exists.setPaperQuestionVersion(currentVersion(paper) + 1);
                paperQuestionMapper.updateById(exists);
                updated++;
                log.info("[EduAi] 题目已在试卷中，更新分值: paperId={}, questionId={}, {} -> {}",
                        paperId, item.questionId(), currentScore, wantedScore);
                continue;
            }
            EduQuestion question = questionMapper.selectById(item.questionId());
            if (question == null) {
                throw new BusinessException("题目不存在: " + item.questionId());
            }
            EduUserContext.checkReadable(question.getOwnerUserId());

            EduPaperQuestion relation = new EduPaperQuestion();
            relation.setPaperQuestionPaperId(paperId);
            relation.setPaperQuestionQuestionId(item.questionId());
            relation.setPaperQuestionOrder(nextOrder);
            relation.setPaperQuestionScore(item.score() == null ? 0 : item.score());
            relation.setPaperQuestionNo(paper.getPaperNo() + "-" + formatOrder(nextSeq));
            relation.setPaperQuestionVersion(currentVersion(paper) + 1);
            relation.setOwnerUserId(paper.getOwnerUserId());
            relation.setCreateTime(LocalDateTime.now());
            paperQuestionMapper.insert(relation);
            // 题库「被加入次数」+1：只在新插入时计，改分/重复提交不重复累加
            questionMapper.incrAddCount(item.questionId());
            nextSeq++;
            nextOrder++;
            added++;
        }
        if (added > 0 || updated > 0) {
            syncVersionAndPersist(paper, "ADD_QUESTION");
        }
        log.info("[EduAi] 批量加题完成: paperId={}, added={}, updated={}", paperId, added, updated);
        return added + updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 移除题目
     *
     * @param paperId paperid
     * @param questionId questionid
     * @return 移除题目的结果
     */
    public boolean removeQuestion(Long paperId, Long questionId) {
        EduPaper paper = requireEditableDraft(paperId);
        EduPaperQuestion exists = paperQuestionMapper.selectByPaperAndQuestion(paperId, questionId);
        if (exists == null) {
            throw new BusinessException("题目不在该试卷中");
        }
        paperQuestionMapper.deleteByPaperAndQuestion(paperId, questionId);
        syncVersionAndPersist(paper, "REMOVE_QUESTION");
        log.info("[EduAi] 移除题目完成: paperId={}, questionId={}", paperId, questionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 按编号列表重排题目
     *
     * @param paperId paperid
     * @param questionNos questionnos
     * @return 按编号列表重排题目的结果
     */
    public boolean sortQuestions(Long paperId, List<String> questionNos) {
        EduPaper paper = requireEditableDraft(paperId);
        // 历史行可能没有题项编号，先补齐再校验，否则会被判为「重复或缺失」直接拒掉保存
        backfillMissingQuestionNos(paper);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
        if (questionNos == null || questionNos.size() != relations.size()) {
            throw new BusinessException("排序编号数量与试卷题目不一致");
        }
        Map<String, EduPaperQuestion> relationMap = new LinkedHashMap<>();
        for (EduPaperQuestion relation : relations) {
            if (relation.getPaperQuestionNo() == null || relationMap.put(relation.getPaperQuestionNo(), relation) != null) {
                throw new BusinessException("试卷存在重复或缺失的题项编号，无法排序");
            }
        }
        int order = 1;
        for (String questionNo : questionNos) {
            EduPaperQuestion relation = relationMap.remove(questionNo);
            if (relation == null) {
                throw new BusinessException("排序编号不属于该试卷: " + questionNo);
            }
            relation.setPaperQuestionOrder(order);
            paperQuestionMapper.updateById(relation);
            order++;
        }
        if (!relationMap.isEmpty()) {
            throw new BusinessException("排序编号未覆盖全部题目");
        }
        syncVersionAndPersist(paper, "REORDER");
        log.info("[EduAi] 题目重排完成: paperId={}, questionCount={}", paperId, questionNos.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 按题数均分满分
     *
     * @param paperId paperid
     * @return 按题数均分满分的结果
     */
    public boolean evenQuestionScores(Long paperId) {
        EduPaper paper = requireEditableDraft(paperId);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
        if (relations.isEmpty()) {
            throw new BusinessException("试卷没有题目，无法均分");
        }
        int count = relations.size();
        int fullScore = resolveFullScore(paper);
        int base = fullScore / count;
        int remainder = fullScore % count;
        for (int index = 0; index < count; index++) {
            EduPaperQuestion relation = relations.get(index);
            relation.setPaperQuestionScore(base + (index < remainder ? 1 : 0));
            paperQuestionMapper.updateById(relation);
        }
        syncVersionAndPersist(paper, "EVEN");
        log.info("[EduAi] 分值均分完成: paperId={}, questionCount={}", paperId, count);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新试卷卷头信息
     *
     * @param paperId paperid
     * @param dto dto
     * @return 更新试卷卷头信息的结果
     */
    public PaperVO updatePaper(Long paperId, PaperComposeUpdateDTO dto) {
        EduPaper paper = requireEditableDraft(paperId);
        if (dto == null) {
            return toVO(paper);
        }
        boolean changed = false;
        if (dto.name() != null) {
            paper.setPaperName(dto.name());
            changed = true;
        }
        if (dto.subject() != null) {
            paper.setPaperSubject(dto.subject());
            changed = true;
        }
        if (dto.gradeLevel() != null) {
            paper.setPaperGradeLevel(GradeLevels.normalize(dto.gradeLevel()));
            changed = true;
        }
        if (dto.grade() != null) {
            paper.setPaperGrade(dto.grade());
            changed = true;
        }
        if (dto.duration() != null) {
            paper.setPaperDuration(dto.duration());
            changed = true;
        }
        if (dto.pageSize() != null) {
            paper.setPaperPageSize(dto.pageSize());
            changed = true;
        }
        if (dto.pageOrientation() != null) {
            paper.setPaperPageOrientation(dto.pageOrientation());
            changed = true;
        }
        if (dto.template() != null) {
            paper.setPaperTemplate(dto.template());
            changed = true;
        }
        if (dto.titleStyle() != null) {
            paper.setPaperTitleStyle(dto.titleStyle());
            changed = true;
        }
        if (dto.header() != null) {
            paper.setPaperHeader(dto.header());
            changed = true;
        }
        if (dto.footer() != null) {
            paper.setPaperFooter(dto.footer());
            changed = true;
        }
        if (dto.subjectLocked() != null) {
            paper.setPaperSubjectLocked(dto.subjectLocked());
            changed = true;
        }
        if (dto.difficulty() != null) {
            paper.setPaperDifficulty(dto.difficulty());
            changed = true;
        }
        if (dto.templateId() != null) {
            paper.setPaperTemplateId(dto.templateId());
            changed = true;
        }
        // 满分可改：仅接受正数，避免把满分误置为 0/负数
        if (dto.totalScore() != null && dto.totalScore() > 0) {
            paper.setPaperTotalScore(dto.totalScore());
            changed = true;
        }
        if (!changed) {
            return toVO(paper);
        }
        syncVersionAndPersist(paper, "UPDATE");
        log.info("[EduAi] 更新试卷卷头完成: paperId={}, version={}", paperId, paper.getPaperVersion());
        return toVO(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 发布试卷
     *
     * @param paperId paperid
     * @return 发布试卷的结果
     */
    public boolean publishPaper(Long paperId) {
        // 加锁取卷：并发发布/下架在同一张卷上串行执行
        EduPaper paper = requirePaperLocked(paperId);
        EduUserContext.checkWritable(paper.getOwnerUserId());
        int status = paper.getPaperStatus() == null ? STATUS_DRAFT : paper.getPaperStatus();
        if (status == STATUS_ARCHIVED) {
            throw new BusinessException("试卷已归档，不能再发布");
        }
        if (status == STATUS_PUBLISHED) {
            throw new BusinessException("试卷已发布；如需修改请先下架，再重新发布");
        }
        backfillMissingQuestionNos(paper);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
        int total = 0;
        for (EduPaperQuestion relation : relations) {
            total += relation.getPaperQuestionScore() == null ? 0 : relation.getPaperQuestionScore();
        }
        int expectScore = resolveFullScore(paper);
        if (total != expectScore) {
            throw new BusinessException("试卷总分必须为 " + expectScore + " 分，当前为 " + total + " 分");
        }
        // 原子占位：并发下只有一个请求能把「草稿」改成「已发布」。
        // 先读判断再写会 TOCTOU —— 多个请求同时通过判断后会一起去改题目版本号而当 500。
        int won = baseMapper.updateStatusIfCurrent(paperId, STATUS_DRAFT, STATUS_PUBLISHED);
        if (won == 0) {
            throw new BusinessException("试卷已发布；如需修改请先下架，再重新发布");
        }
        EduPaper latest = requirePaper(paperId);
        syncVersionAndPersist(latest, "PUBLISH");
        log.info("[EduAi] 发布试卷完成: paperId={}, version={}", paperId, latest.getPaperVersion());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 下架试卷（已发布 → 草稿）
     *
     * @param paperId paperid
     * @return 下架试卷的结果
     */
    public boolean unpublishPaper(Long paperId) {
        EduPaper paper = requirePaperLocked(paperId);
        EduUserContext.checkWritable(paper.getOwnerUserId());
        // 原子占位：并发下只有一个请求能把「已发布」改回「草稿」
        int won = baseMapper.updateStatusIfCurrent(paperId, STATUS_PUBLISHED, STATUS_DRAFT);
        if (won == 0) {
            int status = paper.getPaperStatus() == null ? STATUS_DRAFT : paper.getPaperStatus();
            throw new BusinessException(status == STATUS_ARCHIVED
                    ? "试卷已归档，不能下架"
                    : "只有已发布的试卷可以下架");
        }
        EduPaper latest = requirePaper(paperId);
        syncVersionAndPersist(latest, "UNPUBLISH");
        log.info("[EduAi] 下架试卷完成: paperId={}, version={}", paperId, latest.getPaperVersion());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 归档试卷（草稿/已发布 → 已归档，之后只读）
     *
     * @param paperId paperid
     * @return 归档试卷的结果
     */
    public boolean archivePaper(Long paperId) {
        EduPaper paper = requirePaperLocked(paperId);
        EduUserContext.checkWritable(paper.getOwnerUserId());
        // 原子占位：并发下只有一个请求能归档（草稿与已发布都可归档，已归档则 affected=0）
        int won = baseMapper.archiveIfNotArchived(paperId, STATUS_ARCHIVED, STATUS_ARCHIVED);
        if (won == 0) {
            throw new BusinessException("试卷已归档，无需重复归档");
        }
        EduPaper latest = requirePaper(paperId);
        syncVersionAndPersist(latest, "ARCHIVE");
        log.info("[EduAi] 归档试卷完成: paperId={}, version={}", paperId, latest.getPaperVersion());
        return true;
    }

    @Override
    /**
     * 试卷历史列表
     *
     * @param paperId paperid
     * @return 试卷历史列表的结果
     */
    public List<PaperHistoryVO> listHistory(Long paperId) {
        EduPaper paper = requirePaper(paperId);
        EduUserContext.checkReadable(paper.getOwnerUserId());
        return paperHistoryMapper.selectByPaperId(paperId).stream()
                .map(history -> toHistoryVO(history, null, null))
                .collect(Collectors.toList());
    }

    @Override
    /**
     * 历史快照详情
     *
     * @param historyId historyid
     * @return 历史快照详情的结果
     */
    public PaperHistoryVO getHistoryDetail(Long historyId) {
        EduPaperHistory history = paperHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new BusinessException("历史记录不存在");
        }
        EduUserContext.checkReadable(history.getOwnerUserId());
        boolean hashValid = verifyHistoryHash(history.getSnapshot(), history.getHash());
        return toHistoryVO(history, hashValid, history.getSnapshot());
    }

    @Override
    /**
     * 按题项编号反查
     *
     * @param questionNo questionno
     * @return 按题项编号反查的结果
     */
    public PaperQuestionQrVO queryQuestionByNo(String questionNo) {
        EduPaperQuestion relation = paperQuestionMapper.selectByQuestionNo(questionNo);
        if (relation == null) {
            throw new BusinessException("题项不存在");
        }
        EduPaper paper = requirePaper(relation.getPaperQuestionPaperId());
        EduUserContext.checkReadable(paper.getOwnerUserId());
        // 方案 A：未发布的卷学生端不可见。已发布任何人可扫码看；
        // 草稿/归档只允许本人（或管理员）扫码核对，避免草稿内容提前外泄。
        int qrStatus = paper.getPaperStatus() == null ? STATUS_DRAFT : paper.getPaperStatus();
        if (qrStatus != STATUS_PUBLISHED) {
            EduUserContext.checkWritable(paper.getOwnerUserId());
        }
        EduQuestion question = questionMapper.selectById(relation.getPaperQuestionQuestionId());
        return new PaperQuestionQrVO(
                paper.getPaperId(),
                paper.getPaperNo(),
                paper.getPaperName(),
                paper.getPaperVersion(),
                relation.getPaperQuestionVersion(),
                relation.getPaperQuestionQuestionId(),
                relation.getPaperQuestionNo(),
                question == null ? null : question.getQuestionType(),
                question == null ? null : question.getQuestionStem(),
                question == null ? null : question.getQuestionOptions(),
                question == null ? null : question.getQuestionAnswer(),
                question == null ? null : question.getQuestionAnalysis(),
                relation.getPaperQuestionScore(),
                relation.getPaperQuestionOrder(),
                relation.getPaperQuestionSection());
    }

    /**
     * 按主键取试卷，不存在直接抛业务异常
     *
     * @param paperId 试卷标识
     * @return 试卷实体
     */
    private EduPaper requirePaper(Long paperId) {
        EduPaper paper = baseMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        return paper;
    }

    /**
     * 取试卷并加行锁（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>所有改卷的入口都走这里：把**同一张卷**的「读-改-写」串行化，
     * 避免并发下多个事务同时改 {@code edu_paper_question} 而抛
     * 「Record has changed since last read」（实测并发发布/下架会冒 500）。
     * 不同试卷之间不互相阻塞。</p>
     *
     * <p>必须在 {@code @Transactional} 内调用，锁随事务结束释放。</p>
     *
     * @param paperId 试卷标识
     * @return 加了行锁的试卷
     */
    private EduPaper requirePaperLocked(Long paperId) {
        EduPaper paper = baseMapper.selectByIdForUpdate(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        return paper;
    }

    /**
     * 取试卷并校验「必须是草稿」，同时校验写权限。
     *
     * <p>业务口径（发布=冻结）：只有草稿可改。已发布要先下架，已归档只读留档。
     * 所有改动试卷内容的入口都必须走这里，否则会出现「学生扫过的卷还能被改」。</p>
     *
     * @param paperId 试卷标识
     * @return 处于草稿态的试卷
     */
    private EduPaper requireEditableDraft(Long paperId) {
        // 加锁取卷：同一张卷的编辑串行化，避免并发改题时的事务冲突
        EduPaper paper = requirePaperLocked(paperId);
        EduUserContext.checkWritable(paper.getOwnerUserId());
        int status = paper.getPaperStatus() == null ? 0 : paper.getPaperStatus();
        if (status == STATUS_PUBLISHED) {
            throw new BusinessException("试卷已发布，改动前请先下架");
        }
        if (status == STATUS_ARCHIVED) {
            throw new BusinessException("试卷已归档，不能再修改");
        }
        return paper;
    }

    /**
     * 取当前版本号（空值按 0 处理，便于 +1）
     *
     * @param paper 试卷实体
     * @return 当前版本号
     */
    private int currentVersion(EduPaper paper) {
        return paper.getPaperVersion() == null ? 0 : paper.getPaperVersion();
    }

    /**
     * 计算下一个题项序号（按现有编号最大后缀 +1，保证编号不复用）
     *
     * @param paperId 试卷标识
     * @param paperNo 试卷唯一编号
     * @return 下一题项序号
     */
    private int nextQuestionSeq(Long paperId, String paperNo) {
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
        String prefix = paperNo + "-";
        int max = 0;
        for (EduPaperQuestion relation : relations) {
            String no = relation.getPaperQuestionNo();
            if (no == null || !no.startsWith(prefix)) {
                continue;
            }
            try {
                int seq = Integer.parseInt(no.substring(prefix.length()));
                if (seq > max) {
                    max = seq;
                }
            } catch (NumberFormatException e) {
                log.debug("[EduAi] 题项编号后缀非数字，已跳过: {}", no);
            }
        }
        return max + 1;
    }

    /**
     * 为缺失题项编号的历史行补号。
     *
     * <p>{@code paper_question_no} 是后加的列，早于该列建表的历史题项这一列全为 NULL。
     * 后果有二：排序接口会因「重复或缺失的题项编号」直接拒绝保存，
     * 且每题二维码内容为空（编号就是二维码内容）。</p>
     *
     * <p>补号规则与 {@link #addQuestions} 新增时一致：{@code 卷号-序号}，
     * 序号接着现有最大后缀往后排，因此不会与已有编号冲突。
     * 只补 NULL 与空串，已有编号一律不动，保证幂等。</p>
     *
     * @param paper 试卷
     * @return 实际补号的行数；无需补号时为 0
     */
    private int backfillMissingQuestionNos(EduPaper paper) {
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paper.getPaperId());
        if (relations.isEmpty()) {
            return 0;
        }
        /*
          paper_no 同样是后加的列，历史试卷可能为 NULL。
          题项编号以卷号为前缀，卷号缺失会拼出字面量 "null-001" 这种编号
          （而编号就是每题二维码的内容），因此先补卷号再补题项编号。
        */
        if (paper.getPaperNo() == null || paper.getPaperNo().isBlank()) {
            paper.setPaperNo(generatePaperNo());
            baseMapper.updateById(paper);
            log.info("[EduAi] 历史试卷补卷号: paperId={}, paperNo={}", paper.getPaperId(), paper.getPaperNo());
        }
        int next = nextQuestionSeq(paper.getPaperId(), paper.getPaperNo());
        int fixed = 0;
        for (EduPaperQuestion relation : relations) {
            String no = relation.getPaperQuestionNo();
            if (no != null && !no.isBlank()) {
                continue;
            }
            relation.setPaperQuestionNo(paper.getPaperNo() + "-" + formatOrder(next));
            paperQuestionMapper.updateById(relation);
            next++;
            fixed++;
        }
        if (fixed > 0) {
            log.info("[EduAi] 历史题项补编号: paperId={}, fixed={}", paper.getPaperId(), fixed);
        }
        return fixed;
    }

    /**
     * 计算下一个题目顺序（按现有最大顺序 +1）
     *
     * @param paperId 试卷标识
     * @return 下一题目顺序
     */
    private int nextOrder(Long paperId) {
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
        int max = 0;
        for (EduPaperQuestion relation : relations) {
            Integer order = relation.getPaperQuestionOrder();
            if (order != null && order > max) {
                max = order;
            }
        }
        return max + 1;
    }

    /**
     * 版本 +1、同步题项版本、回写题目数与更新时间并落历史快照
     *
     * @param paper  试卷实体
     * @param action 动作
     */
    private void syncVersionAndPersist(EduPaper paper, String action) {
        int nextVersion = currentVersion(paper) + 1;
        paper.setPaperVersion(nextVersion);
        paperQuestionMapper.updateVersionByPaperId(paper.getPaperId(), nextVersion);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paper.getPaperId());
        paper.setPaperQuestionCount(relations.size());
        paper.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(paper);
        writeHistory(paper, relations, action);
    }

    /**
     * 写一条不可变历史快照（动作 + 全量试卷 JSON + SHA-256 摘要）
     *
     * @param paper     试卷实体
     * @param relations 题项列表
     * @param action    动作
     */
    private void writeHistory(EduPaper paper, List<EduPaperQuestion> relations, String action) {
        String snapshot = buildSnapshot(paper, relations);
        EduPaperHistory history = new EduPaperHistory();
        history.setOwnerUserId(paper.getOwnerUserId());
        history.setPaperId(paper.getPaperId());
        history.setPaperNo(paper.getPaperNo());
        history.setVersion(paper.getPaperVersion());
        history.setAction(action);
        history.setSnapshot(snapshot);
        history.setHash(sha256Hex(snapshot));
        history.setCreateTime(LocalDateTime.now());
        history.setCreateBy(EduUserContext.currentUsername());
        paperHistoryMapper.insert(history);
    }

    /**
     * 构造试卷快照 JSON（卷头 + 题项数组，字段顺序固定以保证摘要稳定）
     *
     * @param paper     试卷实体
     * @param relations 题项列表
     * @return 快照 JSON
     */
    private String buildSnapshot(EduPaper paper, List<EduPaperQuestion> relations) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("paperId", paper.getPaperId());
        root.put("paperNo", paper.getPaperNo());
        root.put("paperName", paper.getPaperName());
        root.put("paperSubject", paper.getPaperSubject());
        root.put("paperGradeLevel", paper.getPaperGradeLevel());
        root.put("paperGrade", paper.getPaperGrade());
        root.put("paperTotalScore", paper.getPaperTotalScore());
        root.put("paperDuration", paper.getPaperDuration());
        root.put("paperQuestionCount", paper.getPaperQuestionCount());
        root.put("paperStatus", paper.getPaperStatus());
        root.put("paperSource", paper.getPaperSource());
        root.put("paperPageSize", paper.getPaperPageSize());
        root.put("paperPageOrientation", paper.getPaperPageOrientation());
        root.put("paperTemplate", paper.getPaperTemplate());
        root.put("paperHeader", paper.getPaperHeader());
        root.put("paperFooter", paper.getPaperFooter());
        root.put("paperVersion", paper.getPaperVersion());
        root.put("paperSubjectLocked", paper.getPaperSubjectLocked());
        root.put("paperDifficulty", paper.getPaperDifficulty());
        root.put("paperTemplateId", paper.getPaperTemplateId());

        List<Map<String, Object>> questions = new ArrayList<>();
        if (relations != null) {
            for (EduPaperQuestion relation : relations) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("order", relation.getPaperQuestionOrder());
                item.put("score", relation.getPaperQuestionScore());
                item.put("no", relation.getPaperQuestionNo());
                item.put("questionId", relation.getPaperQuestionQuestionId());
                item.put("section", relation.getPaperQuestionSection());
                item.put("version", relation.getPaperQuestionVersion());
                questions.add(item);
            }
        }
        root.put("questions", questions);
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new BusinessException("试卷快照序列化失败");
        }
    }

    /**
     * 历史实体转VO
     *
     * @param history   历史实体
     * @param hashValid 摘要校验结果，列表场景为 null
     * @param snapshot  快照，列表场景为 null
     * @return 历史VO
     */
    private PaperHistoryVO toHistoryVO(EduPaperHistory history, Boolean hashValid, String snapshot) {
        return new PaperHistoryVO(
                history.getPaperHistoryId(),
                history.getPaperId(),
                history.getPaperNo(),
                history.getVersion(),
                history.getAction(),
                history.getHash(),
                hashValid,
                history.getCreateTime(),
                history.getCreateBy(),
                snapshot);
    }

    /**
     * 生成试卷唯一编号：P + yyyyMMddHHmmss + 4 位随机大写十六进制，查重后返回
     *
     * @return 试卷唯一编号
     */
    private String generatePaperNo() {
        for (int attempt = 0; attempt < PAPER_NO_MAX_ATTEMPTS; attempt++) {
            String candidate = "P" + LocalDateTime.now().format(PAPER_NO_TIME_FORMATTER)
                    + String.format(Locale.ROOT, "%04X", ThreadLocalRandom.current().nextInt(PAPER_NO_RANDOM_BOUND));
            if (baseMapper.selectByPaperNo(candidate) == null) {
                return candidate;
            }
        }
        throw new BusinessException("试卷编号生成失败，请重试");
    }

    /**
     * 题目顺序格式化为 3 位序号
     *
     * @param order 顺序
     * @return 3 位序号
     */
    private static String formatOrder(int order) {
        return String.format(Locale.ROOT, "%03d", order);
    }

    /**
     * 计算文本的 SHA-256 摘要（十六进制小写）
     *
     * @param text 原文
     * @return 十六进制小写摘要
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 校验历史快照是否与摘要一致（防篡改留痕）
     *
     * @param snapshot 快照 JSON
     * @param hash     期望的 SHA-256 摘要
     * @return 一致返回 true，否则 false
     */
    public static boolean verifyHistoryHash(String snapshot, String hash) {
        if (snapshot == null || hash == null) {
            return false;
        }
        return hash.equals(sha256Hex(snapshot));
    }

    /**
     * 转为vo
     *
     * @param paper paper
     * @return 转为vo的结果
     */
    private PaperVO toVO(EduPaper paper) {
        PaperVO vo = new PaperVO();
        vo.setPaperId(paper.getPaperId());
        vo.setPaperName(paper.getPaperName());
        vo.setPaperSubject(paper.getPaperSubject());
        vo.setPaperGradeLevel(paper.getPaperGradeLevel());
        vo.setPaperGrade(paper.getPaperGrade());
        vo.setPaperTotalScore(paper.getPaperTotalScore());
        vo.setPaperDuration(paper.getPaperDuration());
        vo.setPaperQuestionCount(paper.getPaperQuestionCount());
        vo.setPaperStatus(paper.getPaperStatus());
        vo.setPaperSource(paper.getPaperSource());
        vo.setPaperNo(paper.getPaperNo());
        vo.setPaperPageSize(paper.getPaperPageSize());
        vo.setPaperPageOrientation(paper.getPaperPageOrientation());
        vo.setPaperTemplate(paper.getPaperTemplate());
        vo.setPaperHeader(paper.getPaperHeader());
        vo.setPaperFooter(paper.getPaperFooter());
        vo.setPaperVersion(paper.getPaperVersion());
        vo.setPaperSubjectLocked(paper.getPaperSubjectLocked());
        vo.setPaperDifficulty(paper.getPaperDifficulty());
        vo.setPaperTemplateId(paper.getPaperTemplateId());
        vo.setPaperTitleStyle(paper.getPaperTitleStyle());
        vo.setCreateTime(paper.getCreateTime());
        return vo;
    }
}
