package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.chua.starter.eduai.model.vo.QuestionImportResultVO;
import com.chua.starter.eduai.model.vo.TeacherQuestionUploadVO;
import com.chua.starter.eduai.service.EduAppConfigService;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import com.chua.starter.eduai.support.util.GradeLevels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** 老师上传的来源类型 */
    private static final String SOURCE_TYPE_TEACHER = "TEACHER";

    /** 手动录入未填来源名时的兜底文案 */
    private static final String DEFAULT_SOURCE_NAME = "手动录入";

    /** 老师上传的来源名 */
    private static final String DEFAULT_TEACHER_SOURCE_NAME = "老师上传";

    /**
     * 属性
     */
    private final EduAiService eduAiService;

    /**
     * 应用配置：老师上传题目是否自动通过（V1.14 起）
     */
    private final EduAppConfigService appConfigService;

    @Override
    /**
     * Page查询
     * @param query 查询
     * @return 返回值
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
        boolean auditOnSave = Boolean.TRUE.equals(dto.auditOnSave());
        if (auditOnSave && !admin) {
            throw new BusinessException("只有管理员可以使用保存并审核");
        }
        EduQuestion entity = toEntity(dto);

        if (dto.questionId() != null && dto.questionId() > 0) {
            // 更新：归属校验后锁定为原归属，客户端传值无效
            EduQuestion exists = baseMapper.selectById(dto.questionId());
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
            entity.setQuestionId(dto.questionId());
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新题目成功，ID: {}{}", dto.questionId(), dirty ? "（内容变更，已打回待审）" : "");
            return dto.questionId();
        } else {
            return insertNew(dto, entity, auditOnSave);
        }
    }

    /**
     * 题目保存字段拷贝（DTO → 实体）：新增与更新共用同一份拷贝，
     * 避免两处字段列表漂移（漏拷一个字段即「改了不生效」）。
     *
     * @param dto 题目数据
     * @return 已拷贝字段的实体（主键 / 归属 / 审核态不在此处设置）
     */
    private EduQuestion toEntity(QuestionSaveDTO dto) {
        EduQuestion entity = new EduQuestion();
        entity.setQuestionType(dto.questionType());
        entity.setQuestionSubject(dto.questionSubject());
        entity.setQuestionGradeLevel(GradeLevels.normalize(dto.questionGradeLevel()));
        entity.setQuestionGrade(dto.questionGrade());
        entity.setQuestionYear(dto.questionYear());
        entity.setQuestionDifficulty(dto.questionDifficulty());
        entity.setQuestionScore(dto.questionScore() != null && dto.questionScore() > 0 ? dto.questionScore() : 5);
        entity.setQuestionKnowledgePointIds(dto.questionKnowledgePointIds());
        entity.setQuestionStem(dto.questionStem());
        entity.setQuestionStemImages(dto.questionStemImages());
        entity.setQuestionOptions(unwrapJsonText(dto.questionOptions()));
        entity.setQuestionAnswer(dto.questionAnswer());
        entity.setQuestionAnalysis(dto.questionAnalysis());
        entity.setQuestionSource(dto.questionSource());
        entity.setQuestionSourceType(dto.questionSourceType());
        entity.setQuestionStatus(dto.questionStatus() != null ? dto.questionStatus() : 1);
        return entity;
    }

    /**
     * 新增题目入库（管理员保存 / 老师上传共用）。
     *
     * @param dto      题目数据
     * @param entity   已拷贝字段的实体
     * @param approved 审核态的服务端决策：true=新建即已通过
     *                 （管理员「保存并审核」或老师上传自动通过配置开启）；
     *                 false=待审。审核态不经客户端传值。
     * @return 题目标识
     */
    private Long insertNew(QuestionSaveDTO dto, EduQuestion entity, boolean approved) {
        // 新增：请求线程以登录态为准；异步链路（采集）继承任务归属，二者皆无则拒绝
        String owner = EduUserContext.currentUserId();
        if (!StringUtils.hasText(owner)) {
            owner = StringUtils.hasText(dto.ownerUserId()) ? dto.ownerUserId() : EduUserContext.requireUserId();
        }
        entity.setOwnerUserId(owner);
        entity.setQuestionUseCount(0);
        if (approved) {
            // 管理员「保存并审核」/ 老师上传自动通过：新建即通过。这次保存本身就是一次审核动作，
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

    /**
     * 老师上传题目（教师端入口）：逐条容错，失败原因随结果返回，不因单条坏数据整体回滚。
     *
     * <p>与 {@link #importQuestions(List)} 的区别：来源一律标为「老师上传」，
     * 审核态由服务端按「老师上传自动通过」配置决定——开启则直接入库为已通过，
     * 否则进入待审核队列（默认），由管理员在题库「审核状态」里审批。
     * 客户端传的来源 / 审核标记一律不采信（与 save 接口同一安全口径）。</p>
     *
     * @param list 题目列表
     * @return 上传结果（成功/失败条数 + 逐条失败原因 + 是否自动通过）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherQuestionUploadVO teacherImportQuestions(List<QuestionSaveDTO> list) {
        boolean autoApprove = appConfigService.isTeacherQuestionAutoApprove();
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            QuestionSaveDTO dto = list.get(i);
            String missing = missingRequiredFields(dto);
            if (missing != null) {
                errors.add("第" + (i + 1) + "条" + missing);
                continue;
            }
            try {
                // 服务端落库口径：来源标为老师上传，审核态由自动通过配置决定。
                QuestionSaveDTO effective = new QuestionSaveDTO(
                        dto.questionId(), dto.questionType(), dto.questionSubject(), dto.questionGradeLevel(),
                        dto.questionGrade(), dto.questionYear(), dto.questionDifficulty(), dto.questionScore(),
                        dto.questionKnowledgePointIds(), dto.questionStem(), dto.questionStemImages(),
                        dto.questionOptions(), dto.questionAnswer(), dto.questionAnalysis(),
                        SOURCE_TYPE_TEACHER, DEFAULT_TEACHER_SOURCE_NAME,
                        dto.questionAuditStatus(), dto.questionAuditRemark(), false,
                        dto.questionStatus(), dto.ownerUserId());
                insertNew(effective, toEntity(effective), autoApprove);
                count++;
            } catch (Exception e) {
                log.warn("老师上传第 {} 条题目失败: {}", i + 1, e.getMessage());
                errors.add("第" + (i + 1) + "条" + rootMessage(e));
            }
        }
        log.info("老师上传题目完成，自动通过={}，成功 {} 条，失败 {} 条", autoApprove, count, list.size() - count);
        return new TeacherQuestionUploadVO(count, list.size() - count, errors, autoApprove);
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

    /**
     * equals Text
     * @param a 字符串
     * @param b 字符串
     * @return 返回值
    */
    private static boolean equalsText(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    /**
     * equals Int
     * @param a 整数
     * @param b 整数
     * @return 返回值
    */
    private static boolean equalsInt(Integer a, Integer b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    /**
     * trim Or Null
     * @param s 字符串
     * @return 返回值
    */
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
     * <p><b>失败必须可见</b>：此前逐条吞异常后只回成功条数，payload 缺
     * NOT NULL 字段（学段/年级等）时接口回 200 {@code data:0}，前端只显示
     * 「导入成功 0 条」，用户无从得知原因。现改为：逐条前置校验必填字段，
     * 并把每条失败原因随结果一并返回（{@link QuestionImportResultVO}）。</p>
     *
     * @param list 列表
     * @return 导入结果（成功/失败条数 + 逐条失败原因）
     */
    public QuestionImportResultVO importQuestions(List<QuestionSaveDTO> list) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String missing = missingRequiredFields(list.get(i));
            if (missing != null) {
                errors.add("第" + (i + 1) + "条" + missing);
                continue;
            }
            try {
                /**
                 * 保存question。
                 * @param dto dto
                 */
                saveQuestion(list.get(i));
                count++;
            } catch (Exception e) {
                log.warn("导入第 {} 条题目失败: {}", i + 1, e.getMessage());
                errors.add("第" + (i + 1) + "条" + rootMessage(e));
            }
        }
        log.info("批量导入完成，成功 {} 条，失败 {} 条", count, list.size() - count);
        return new QuestionImportResultVO(count, list.size() - count, errors);
    }

    /**
     * 导入前的必填字段校验：与 edu_question 的 NOT NULL 列一一对应。
     *
     * <p>缺这些字段直接落库会报 {@code Field 'x' doesn't have a default value}
     * （500 且信息对用户无意义），这里提前拦成人话。</p>
     *
     * @param dto 单条导入数据
     * @return 缺失字段的中文描述；齐全时返回 {@code null}
     */
    private static String missingRequiredFields(QuestionSaveDTO dto) {
        if (dto == null) {
            return "数据为空";
        }
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(dto.questionType())) {
            missing.add("题型");
        }
        if (!StringUtils.hasText(dto.questionSubject())) {
            missing.add("学科");
        }
        if (!StringUtils.hasText(dto.questionGradeLevel())) {
            missing.add("学段");
        }
        if (!StringUtils.hasText(dto.questionGrade())) {
            missing.add("年级");
        }
        if (!StringUtils.hasText(dto.questionStem())) {
            missing.add("题干");
        }
        if (!StringUtils.hasText(dto.questionAnswer())) {
            missing.add("答案");
        }
        return missing.isEmpty() ? null : "缺少必填字段：" + String.join("、", missing);
    }

    /**
     * 取异常根因的单行消息（MyBatis 异常消息是多行堆栈摘要，直接透传会刷屏）。
     *
     * @param e 异常
     * @return 根因消息首行（超长截断）；无消息时退回异常类名
     */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = StringUtils.hasText(root.getMessage()) ? root.getMessage() : e.getMessage();
        if (!StringUtils.hasText(msg)) {
            return root.getClass().getSimpleName();
        }
        String first = msg.lines().findFirst().orElse(msg).trim();
        return first.length() > 120 ? first.substring(0, 120) + "..." : first;
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
     * @param dto 参数
     * @return 返回值
    */
    public List<EduQuestion> aiSelect(QuestionAiSelectDTO dto) {
        int count = dto.count() == null || dto.count() <= 0 ? 10 : Math.min(dto.count(), 100);
        // 前端可能传码值（PRIMARY），而库里存中文名，不归一化会选不出题
        String gradeLevel = GradeLevels.normalize(dto.gradeLevel());
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();

        // 规则选题作为兜底：AI 不可用或解析失败时由 EduAiService 直接返回这份候选
        List<EduQuestion> candidates =
                baseMapper.selectCandidates(dto.subject(), gradeLevel, scope, Math.max(count * 3, count));
        List<Long> fallbackIds = candidates.stream()
                .map(EduQuestion::getQuestionId)
                .collect(Collectors.toList());
        if (fallbackIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = eduAiService.generatePaperOutline(
                dto.subject(), gradeLevel, dto.grade(),
                dto.types(), dto.knowledgePointIds(), count, fallbackIds);
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
                dto.subject(), gradeLevel, count, fallbackIds.size(), ordered.size());
        return ordered;
    }

    @Override
    public Map<String, Object> questionStats() {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        QueryWrapper<EduQuestion> base = new QueryWrapper<>();
        base.ne("question_status", 0);
        if (scope.ownerPredicate() != null) {
            base.and(w -> w.isNull("question_owner_user_id").or().eq("question_owner_user_id", scope.ownerPredicate()));
        }
        long total = baseMapper.selectCount(base);
        QueryWrapper<EduQuestion> pendingWrapper = base.clone().eq("question_audit_status", 0);
        long pending = baseMapper.selectCount(pendingWrapper);
        QueryWrapper<EduQuestion> todayWrapper = base.clone().ge("create_time", LocalDate.now().atStartOfDay());
        long today = baseMapper.selectCount(todayWrapper);
        List<Object> subjects = baseMapper.selectObjs(base.clone()
                .select("DISTINCT question_subject")
                .isNotNull("question_subject")
                .ne("question_subject", ""));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pendingAudit", pending);
        stats.put("todayNew", today);
        stats.put("subjectCount", subjects == null ? 0 : subjects.stream().filter(java.util.Objects::nonNull).count());
        return stats;
    }

    @Override
    public String exportQuestions(String keyword, String subject, String gradeLevel,
                                  String questionType, Integer auditStatus, int limit) {
        if (limit <= 0) {
            limit = 5000;
        }
        limit = Math.min(limit, 10000);
        QueryWrapper<EduQuestion> q = new QueryWrapper<>();
        q.orderByDesc("question_id").last("LIMIT " + limit);
        if (keyword != null && !keyword.isBlank()) {
            q.like("question_stem", keyword.trim());
        }
        if (subject != null && !subject.isBlank()) {
            q.eq("question_subject", subject);
        }
        if (gradeLevel != null && !gradeLevel.isBlank()) {
            q.eq("question_grade_level", gradeLevel);
        }
        if (questionType != null && !questionType.isBlank()) {
            q.eq("question_type", questionType);
        }
        if (auditStatus != null) {
            q.eq("question_audit_status", auditStatus);
        }
        List<EduQuestion> rows = baseMapper.selectList(q);
        StringBuilder sb = new StringBuilder(8192);
        // CSV header
        sb.append("题目ID,题型,学科,学段,年级,难度,题干,选项,答案,解析,来源,状态,创建时间\n");
        for (EduQuestion q2 : rows) {
            sb.append(nullSafe(q2.getQuestionId())).append(',');
            sb.append(escapeCsv(q2.getQuestionType())).append(',');
            sb.append(escapeCsv(q2.getQuestionSubject())).append(',');
            sb.append(escapeCsv(q2.getQuestionGradeLevel())).append(',');
            sb.append(escapeCsv(q2.getQuestionGrade())).append(',');
            sb.append(escapeCsv(nullSafe(q2.getQuestionDifficulty()))).append(',');
            sb.append(escapeCsv(q2.getQuestionStem())).append(',');
            sb.append(escapeCsv(q2.getQuestionOptions())).append(',');
            sb.append(escapeCsv(q2.getQuestionAnswer())).append(',');
            sb.append(escapeCsv(q2.getQuestionAnalysis())).append(',');
            sb.append(escapeCsv(q2.getQuestionSource())).append(',');
            sb.append(escapeCsv(String.valueOf(q2.getQuestionAuditStatus()))).append(',');
            sb.append(escapeCsv(q2.getCreateTime() != null ? q2.getCreateTime().toString() : "")).append('\n');
        }
        return sb.toString();
    }

    /**
     * escape Csv
     * @param v 字符串
     * @return 返回值
    */
    private static String escapeCsv(String v) {
        if (v == null) {
            return "";
        }
        // 双重转义双引号，包裹含逗号/换行/双引号的字段
        if (v.contains(",") || v.contains("\n") || v.contains("\r") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /**
     * null Safe
     * @param v 对象
     * @return 返回值
    */
    private static String nullSafe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
