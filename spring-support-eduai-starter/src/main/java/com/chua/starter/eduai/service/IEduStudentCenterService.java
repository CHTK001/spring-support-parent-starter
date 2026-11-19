package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudentPaperSubmitDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.model.vo.StudentWrongQuestionVO;

import java.util.List;
import java.util.Map;

/**
 * 学生中心服务接口
 *
 * <p>与管理端（{@code /v2/edu/dimension/students}，管理员维护学生档案）的分工：
 * 这里全部是**学生本人视角**的接口 —— 当前登录账号绑定了谁、我的学习数据是什么。
 * 归属一律取自登录态，不接受客户端传入学生 ID，避免越权看别人的数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduStudentCenterService {

    /**
     * 当前账号的绑定状态
     *
     * @return bound=false 时前端必须弹出绑定表单；bound=true 时含 student / className 等信息
     */
    Map<String, Object> me();

    /**
     * 绑定当前登录账号到学生档案（学号不存在则建档）
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    Map<String, Object> bind(StudentBindDTO dto);

    /**
     * 学生端首页数据：今日 / 本周 / 累计统计 + 模块进度 + 分类排名 + 今日推荐
     *
     * @return 首页数据；未绑定时 bound=false
     */
    Map<String, Object> overview();

    /**
     * 上报一次学习行为（累加模块进度与当日汇总）
     *
     * @param dto 本次学习数据
     * @return 是否记录成功
     */
    boolean reportStudy(StudyReportDTO dto);

    /**
     * 我的答题/阅卷记录：按任务上的学生标识等于本人学号过滤
     *
     * @param limit 最多返回条数；非正数按 20、上限 100
     * @return 阅卷任务列表
     */
    List<EduMarkingTask> myMarkingTasks(int limit);

    /**
     * 查看我自己的某次阅卷结果（任务上的学生标识必须是本人）
     *
     * @param taskId 任务标识
     * @return 阅卷结果
     */
    MarkingResultVO myMarkingResult(String taskId);

    /**
     * 我的错题卡（带题干/选项/答案），可按学科与掌握度筛选
     *
     * @param subject      学科，可空
     * @param masteryLevel 掌握度筛选，可空
     * @return 错题卡列表
     */
    List<StudentWrongQuestionVO> myWrongQuestions(String subject, Integer masteryLevel);

    /**
     * 更新我的错题掌握度
     *
     * @param wrongId      错题记录标识；必须属于本人
     * @param masteryLevel 掌握度：0 未掌握 / 1 复习中 / 2 已掌握
     * @return 是否成功
     */
    boolean updateMyWrongMastery(Long wrongId, Integer masteryLevel);

    /**
     * 一键订正我的错题。
     *
     * <p>订正是学生在错题卡上重新作答后的客观标记（与掌握度主观自评独立）。
     * 标记后 {@code wrong_question_corrected=1} 并记录订正时间。</p>
     *
     * @param wrongId 错题记录标识；必须属于本人
     * @return 是否成功
     */
    boolean markMyWrongCorrected(Long wrongId);

    /**
     * 学生：开始作答某张布置给本班的试卷（脱敏详情）。
     *
     * <p>按「布置给我班 + 试卷已发布」授权（与我的班级试卷同一口径）；
     * 题目的标准答案与解析一律抹除 —— 在线作答若带答案，作答就失去意义。</p>
     *
     * @param paperId 试卷标识
     * @return 试卷详情（含题目，不含答案/解析）
     */
    PaperVO myAnswerPaper(Long paperId);

    /**
     * 学生：提交在线作答，进入 AI 阅卷。
     *
     * <p>身份取登录态；考试按（试卷，班级）自动就位（老师布置即视为一场班级测验，
     * 首次有学生作答时补建考试行，归属记为布置老师）。同一考试同一学生只允许一份
     * 答题卡（数据库唯一键兜底），重复提交会被拒绝。</p>
     *
     * @param paperId 试卷标识
     * @param dto     逐题作答内容
     * @return 阅卷任务标识（用 {@code GET /v2/edu/student/marking/result/{taskId}} 轮询）
     */
    String submitMyPaperAnswer(Long paperId, StudentPaperSubmitDTO dto);

    /**
     * 学生：查询我在某张试卷下的剩余作答时间。
     *
     * <p>计时锚点是服务端为本人创建的草稿答题卡时间（首次拉卷即建），客户端时钟不可信；
     * 交卷时服务端还会再做一次权威校验。考试按（试卷，班级）自动就位。</p>
     *
     * @param paperId 试卷标识
     * @return timed 是否限时；durationMinutes 考试时长（分钟）；
     *         remainingSeconds 剩余秒数（不限时为 -1，已交卷为 0）
     */
    Map<String, Object> myExamTimer(Long paperId);
}
