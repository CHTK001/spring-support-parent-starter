package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.dto.EduResetPasswordDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
import com.chua.starter.eduai.model.vo.HomeworkCompletionVO;
import com.chua.starter.eduai.model.vo.MarkingStatsVO;
import com.chua.starter.eduai.model.vo.MarkingDashboardStatsVO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.model.vo.TeacherQuestionUploadVO;
import com.chua.starter.eduai.service.IEduPaperService;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.service.IEduTeacherCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 教师中心控制器
 *
 * <p>教师端（{@code /teacher}）专用；管理端的教师档案维护仍在
 * {@code /v2/edu/dimension/teachers}。归属取自登录态。</p>
 *
 * <p>本控制器同时承载教师端三大能力：<b>管理我自己的班级学生 / 组卷 / 阅卷</b>。
 * 其中阅卷直接复用 {@code /v2/edu/marking/**}，组卷列表走教师中心 {@code /papers}
 * （强制本人 owner，管理员当教师时也只看自己的卷），
 * 手动编辑仍复用 {@code /v2/edu/paper/**}。
 * 教师中心只额外提供「我发起的阅卷任务列表」与
 * 「教师端字典下拉」（管理端下拉是管理员专属，教师令牌会 403）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/teacher")
@RequiredArgsConstructor
@Tag(name = "教师中心")
public class EduTeacherCenterController {

    private final IEduTeacherCenterService teacherCenterService;

    /**
     * 题目服务：老师上传题目（待审核，管理员可配自动通过）
     */
    private final IEduQuestionService questionService;

    /**
     * 试卷服务：教师端「我的试卷」分页强制本人 owner
     */
    private final IEduPaperService paperService;

    /**
     * 当前账号的教师绑定状态
     *
     * @return bound=false 表示还没绑定
     */
    @Operation(summary = "当前账号的教师绑定状态")
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me() {
        return ReturnResult.ok(teacherCenterService.me());
    }

    /**
     * 绑定当前登录账号到教师档案
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    @Operation(summary = "绑定教师档案")
    @PostMapping("/bind")
    public ReturnResult<Map<String, Object>> bind(@RequestBody TeacherBindDTO dto) {
        return ReturnResult.ok(teacherCenterService.bind(dto));
    }

    /**
     * 教师工作台数据
     *
     * @return 我带的班级 + 每班人数 + 学生清单
     */
    @Operation(summary = "教师工作台数据")
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview() {
        return ReturnResult.ok(teacherCenterService.overview());
    }

    // ==================== 我的班级 ====================

    /**
     * 我带的班级列表
     *
     * @return 我创建的班级
     */
    @Operation(summary = "我的班级列表")
    @GetMapping("/classes")
    public ReturnResult<List<EduClass>> classes() {
        return ReturnResult.ok(teacherCenterService.myClasses());
    }

    /**
     * 新建或修改我自己的班级
     *
     * @param entity 班级实体
     * @return 班级标识
     */
    @Operation(summary = "保存我的班级")
    @PostMapping("/classes")
    public ReturnResult<Long> saveClass(@RequestBody EduClass entity) {
        return ReturnResult.ok(teacherCenterService.saveClass(entity));
    }

    /**
     * 删除我自己的班级
     *
     * @param id 班级标识
     * @return 是否删除成功
     */
    @Operation(summary = "删除我的班级")
    @DeleteMapping("/classes/{id}")
    public ReturnResult<Boolean> deleteClass(@PathVariable Long id) {
        return ReturnResult.ok(teacherCenterService.deleteClass(id));
    }

    // ==================== 我的班级学生 ====================

    /**
     * 我某个班的学生清单
     *
     * @param classId 班级标识
     * @return 该班学生列表
     */
    @Operation(summary = "我的班级学生列表")
    @GetMapping("/classes/{classId}/students")
    public ReturnResult<List<EduStudent>> students(@PathVariable Long classId) {
        return ReturnResult.ok(teacherCenterService.classStudents(classId));
    }

    /**
     * 在「我的班级」下新增或修改学生
     *
     * @param classId 班级标识（路径参数，覆盖请求体里的 studentClassId）
     * @param entity  学生实体
     * @return 学生标识
     */
    @Operation(summary = "保存我班学生")
    @PostMapping("/classes/{classId}/students")
    public ReturnResult<Long> saveStudent(@PathVariable Long classId, @RequestBody EduStudent entity) {
        entity.setStudentClassId(classId);
        return ReturnResult.ok(teacherCenterService.saveStudent(entity));
    }

    /**
     * 删除我班里的学生
     *
     * @param id 学生标识
     * @return 是否删除成功
     */
    @Operation(summary = "删除我班学生")
    @DeleteMapping("/students/{id}")
    public ReturnResult<Boolean> deleteStudent(@PathVariable Long id) {
        return ReturnResult.ok(teacherCenterService.deleteStudent(id));
    }

    /**
     * 重置我班学生的登录口令
     *
     * @param id  学生标识
     * @param dto 新口令（请求体里的 studentId 被忽略，以路径为准）
     * @return 是否成功
     */
    @Operation(summary = "重置我班学生口令")
    @PostMapping("/students/{id}/reset-password")
    public ReturnResult<Boolean> resetPassword(@PathVariable Long id, @RequestBody EduResetPasswordDTO dto) {
        return ReturnResult.ok(teacherCenterService.resetStudentPassword(id, dto.newPassword()));
    }

    // ==================== 字典 / 阅卷任务 ====================

    /**
     * 教师端字典下拉（学段 + 学科 + 年级）
     *
     * @return 级联下拉数据
     */
    @Operation(summary = "教师端字典下拉")
    @GetMapping("/dropdown")
    public ReturnResult<EduDimensionService.DimensionDropdown> dropdown() {
        return ReturnResult.ok(teacherCenterService.dropdown());
    }

    // ==================== 我的试卷 ====================

    /**
     * 我的试卷（分页，只返回当前账号本人创建的试卷）
     *
     * <p>教师端组卷列表不能走通用 {@code /v2/edu/paper/page}：通用接口对管理员
     * 会放行全量数据，而老师端在老新账户拆分前也常以 admin 身份绑定教师档案。
     * 该端点强制按当前账号精确过滤 owner，保证「新增 / 查看都只能是自己的卷」。</p>
     *
     * @param query 查询条件
     * @return 我的试卷分页
     */
    @Operation(summary = "我的试卷（本人分页）")
    @GetMapping("/papers")
    public ReturnResult<PageResult<PaperVO>> papers(PaperQueryDTO query) {
        return ReturnResult.ok(paperService.pageMyPapers(query));
    }

    /**
     * 我发起的阅卷任务
     *
     * @param limit 最多返回条数，默认 20、上限 100
     * @return 阅卷任务列表
     */
    @Operation(summary = "我的阅卷任务")
    @GetMapping("/marking/tasks")
    public ReturnResult<List<EduMarkingTask>> markingTasks(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ReturnResult.ok(teacherCenterService.myMarkingTasks(limit));
    }

    /**
     * 我发起的阅卷任务（分页）
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 阅卷任务分页结果
     */
    @Operation(summary = "我的阅卷任务（分页）")
    @GetMapping("/marking/tasks/page")
    public ReturnResult<PageResult<EduMarkingTask>> pageMarkingTasks(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer teacherFinished) {
        return ReturnResult.ok(teacherCenterService.pageMyMarkingTasks(pageNum, pageSize, teacherFinished));
    }

    // ==================== 老师上传题目 ====================

    /**
     * 老师上传题目（待审核，管理员可配自动通过）
     *
     * <p>与批量导入同格式的 JSON 数组；来源一律标为「老师上传」，
     * 审核态由服务端按「老师上传自动通过」配置决定，
     * 客户端传的审核标记不采信。</p>
     *
     * @param list 题目列表
     * @return 上传结果（成功/失败条数 + 逐条失败原因 + 是否自动通过）
     */
    @Operation(summary = "老师上传题目")
    @PostMapping("/questions")
    public ReturnResult<TeacherQuestionUploadVO> uploadQuestions(@RequestBody List<QuestionSaveDTO> list) {
        return ReturnResult.ok(questionService.teacherImportQuestions(list));
    }

    /**
     * 成绩分布统计：某阅卷任务的分数段分布、平均分、最高/最低分。
     *
     * @param taskId 阅卷任务业务标识
     * @return 成绩分布统计
     */
    @Operation(summary = "成绩分布统计")
    @GetMapping("/marking/stats/{taskId}")
    public ReturnResult<MarkingStatsVO> markingStats(@PathVariable String taskId) {
        return ReturnResult.ok(teacherCenterService.markingStats(taskId));
    }

    /**
     * 阅卷总览统计报表（当前账号归属范围内）。
     *
     * @return 阅卷总览统计：任务状态分布、老师完成状态、得分率分布、
     *         错题 TOP 榜与订正率
     */
    @Operation(summary = "阅卷总览统计")
    @GetMapping("/marking/dashboard-stats")
    public ReturnResult<MarkingDashboardStatsVO> markingDashboardStats() {
        return ReturnResult.ok(teacherCenterService.markingDashboardStats());
    }

    /**
     * 作业完成率看板：按班级维度统计某试卷/考试的提交与完成情况。
     *
     * @param paperId 试卷ID（可选，与 examId 二选一）
     * @param examId  考试ID（可选）
     * @return 完成率看板
     */
    @Operation(summary = "作业完成率看板")
    @GetMapping("/dashboard/completion")
    public ReturnResult<HomeworkCompletionVO> homeworkCompletion(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long examId) {
        return ReturnResult.ok(teacherCenterService.homeworkCompletion(paperId, examId));
    }
}
