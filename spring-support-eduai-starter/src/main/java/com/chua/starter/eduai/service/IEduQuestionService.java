package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionAiSelectDTO;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.QuestionGradeDimensionVO;
import com.chua.starter.eduai.model.vo.QuestionImportResultVO;
import com.chua.starter.eduai.model.vo.TeacherQuestionUploadVO;

import java.util.List;
import java.util.Map;

/**
 * 题目服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduQuestionService extends IService<EduQuestion> {

    /**
     * 分页查询题目
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EduQuestion> pageQuery(QuestionQueryDTO query);

    /**
     * 保存题目（新增或更新）
     *
     * @param dto 题目数据
     * @return 题目ID
     */
    Long saveQuestion(QuestionSaveDTO dto);

    /**
     * 批量导入题目。逐条容错，失败原因随结果返回，不因单条坏数据整体回滚。
     *
     * @param list 题目列表
     * @return 导入结果（成功/失败条数 + 逐条失败原因）
     */
    QuestionImportResultVO importQuestions(List<QuestionSaveDTO> list);

    /**
     * 老师上传题目（教师端入口）。逐条容错，失败原因随结果返回，不因单条坏数据整体回滚。
     *
     * <p>与 {@link #importQuestions(List)} 的区别：来源一律标为「老师上传」，
     * 审核态由服务端按「老师上传自动通过」配置决定——开启则直接入库为已通过，
     * 否则进入待审核队列（默认），由管理员在题库「审核状态」里审批。
     * 客户端传的来源 / 审核标记一律不采信（与 save 接口同一安全口径）。</p>
     *
     * @param list 题目列表
     * @return 上传结果（成功/失败条数 + 逐条失败原因 + 是否自动通过）
     */
    TeacherQuestionUploadVO teacherImportQuestions(List<QuestionSaveDTO> list);

    /**
     * 删除题目
     *
     * @param id 题目标识
     * @return 是否成功
     */
    boolean deleteQuestion(Long id);

    /**
     * 列出题库中真实存在的"学段 + 年级"组合，供前端级联筛选取值。
     *
     * @param status 题目状态；为空时只统计未停用（status&lt;&gt;0）的题目
     * @return 组合维度列表
     */
    List<QuestionGradeDimensionVO> listGradeDimensions(Integer status);

    /**
     * 审核题目（通过/驳回）：仅管理员可调。
     *
     * @param id     题目标识
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填，通过时忽略并清空）
     */
    void auditQuestion(Long id, boolean passed, String remark);

    /**
     * 批量审核题目（通过/驳回）：仅管理员可调。单条失败不影响其余，
     * 返回实际成功条数（与 importQuestions 同一容错风格）。
     *
     * @param ids    题目ID列表
     * @param passed 是否通过
     * @param remark 驳回理由（驳回必填）
     * @return 成功条数
     */
    int auditQuestions(List<Long> ids, boolean passed, String remark);

    /**
     * AI 选题：按学科/学段/题型/知识点向 AI 要一批题目，用于往**已存在的试卷**里补题.
     *
     * <p>AI 不可用（未引入 spring-support-ai-starter、总开关关闭或无可用模型）时
     * 自动回退到规则选题（按学科+学段取候选题），不抛异常。</p>
     *
     * @param dto 选题条件
     * @return 题目列表，保持 AI 给出的顺序
     */
    List<EduQuestion> aiSelect(QuestionAiSelectDTO dto);

    /**
     * 题目导出为 CSV 格式。
     *
     * <p>返回标准 CSV 文本，UTF-8 编码，供前端触发下载。
     * 字段：题目ID, 题型, 学科, 学段, 年级, 难度, 题干, 选项, 答案, 解析, 来源, 状态</p>
     *
     * @param keyword 关键字过滤（题干）
     * @param subject 学科过滤
     * @param gradeLevel 学段过滤
     * @param questionType 题型过滤
     * @param auditStatus 审核状态过滤（null=全部）
     * @param limit 最大导出条数（防止大结果集），默认 5000、上限 10000
     * @return CSV 文本（Content-Type: text/csv）
     */
    /** 题目管理统计（总数 / 待审 / 今日新增 / 学科数），供题库页统计卡使用 */
    Map<String, Object> questionStats();

    /**
     * 题目导出为 CSV 格式。
     *
     * <p>返回标准 CSV 文本，UTF-8 编码，供前端触发下载。
     * 字段：题目ID, 题型, 学科, 学段, 年级, 难度, 题干, 选项, 答案, 解析, 来源, 状态</p>
     *
     * @param keyword 关键字过滤（题干）
     * @param subject 学科过滤
     * @param gradeLevel 学段过滤
     * @param questionType 题型过滤
     * @param auditStatus 审核状态过滤（null=全部）
     * @param limit 最大导出条数（防止大结果集），默认 5000、上限 10000
     * @return CSV 文本（Content-Type: text/csv）
     */
    String exportQuestions(String keyword, String subject, String gradeLevel,
                           String questionType, Integer auditStatus, int limit);
}
