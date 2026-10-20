package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.dto.EduChangePasswordDTO;
import com.chua.starter.eduai.model.dto.StudentBindDTO;
import com.chua.starter.eduai.model.dto.StudyReportDTO;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.StudentWrongQuestionVO;
import com.chua.starter.eduai.service.IEduStudentCenterService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生中心控制器
 *
 * <p>学生端（{@code /student}）专用。与管理端 {@code /v2/edu/dimension/students} 的区别：
 * 这里所有归属都取自登录态，不接受客户端传入学生 ID，因此不存在越权查看他人数据的可能。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/student")
@RequiredArgsConstructor
@Tag(name = "学生中心")
public class EduStudentCenterController {

    private final IEduStudentCenterService studentCenterService;

    /**
     * 档案与口令服务：改密需要复用它的摘要与归属校验，不另起一份实现
     */
    private final EduDimensionService dimensionService;

    /**
     * 当前账号的绑定状态（学生端进入时必须先调这个）
     *
     * @return bound=false 表示还没绑定，前端要弹绑定表单
     */
    @Operation(summary = "当前账号的学生绑定状态")
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me() {
        return ReturnResult.ok(studentCenterService.me());
    }

    /**
     * 绑定当前登录账号到学生档案
     *
     * @param dto 绑定信息
     * @return 绑定后的档案信息
     */
    @Operation(summary = "绑定学生档案")
    @PostMapping("/bind")
    public ReturnResult<Map<String, Object>> bind(@RequestBody StudentBindDTO dto) {
        return ReturnResult.ok(studentCenterService.bind(dto));
    }

    /**
     * 学生端首页数据
     *
     * @return 今日/本周/累计统计 + 模块进度 + 班级排名 + 今日推荐
     */
    @Operation(summary = "学生端首页数据")
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview() {
        return ReturnResult.ok(studentCenterService.overview());
    }

    /**
     * 上报一次学习行为
     *
     * @param dto 本次学习数据
     * @return 是否记录成功
     */
    @Operation(summary = "上报学习行为")
    @PostMapping("/study/report")
    public ReturnResult<Boolean> report(@RequestBody StudyReportDTO dto) {
        return ReturnResult.ok(studentCenterService.reportStudy(dto));
    }

    /**
     * 学生自助修改登录口令。
     *
     * <p>放在学生中心而非管理端：登录态中的 {@code userId} 就是学号，
     * 由服务端自行解析，学生改不了别人的口令。管理员重置他人口令走
     * 管理端 {@code /v2/edu/dimension/students/reset-password}。</p>
     *
     * @param dto 旧口令与新口令
     * @return 是否修改成功
     */
    @Operation(summary = "学生自助修改登录口令")
    @PostMapping("/change-password")
    public ReturnResult<Boolean> changePassword(@RequestBody EduChangePasswordDTO dto) {
        return ReturnResult.ok(dimensionService.changeOwnPassword(
                EduUserContext.requireUserId(), dto.oldPassword(), dto.newPassword()));
    }

    // ==================== 我的答题 / 阅卷 ====================

    /**
     * 我的答题/阅卷记录（按任务上的学生标识等于本人学号）
     *
     * @param limit 最多返回条数，默认 20、上限 100
     * @return 阅卷任务列表
     */
    @Operation(summary = "我的答题/阅卷记录")
    @GetMapping("/marking/tasks")
    public ReturnResult<List<EduMarkingTask>> markingTasks(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ReturnResult.ok(studentCenterService.myMarkingTasks(limit));
    }

    /**
     * 查看我自己的某次阅卷结果
     *
     * @param taskId 任务标识
     * @return 阅卷结果
     */
    @Operation(summary = "我的阅卷结果")
    @GetMapping("/marking/result/{taskId}")
    public ReturnResult<MarkingResultVO> markingResult(@PathVariable String taskId) {
        return ReturnResult.ok(studentCenterService.myMarkingResult(taskId));
    }

    // ==================== 我的错题本 ====================

    /**
     * 我的错题卡（带题干/选项/答案）
     *
     * @param subject      学科，可空
     * @param masteryLevel 掌握度筛选，可空
     * @return 错题卡列表
     */
    @Operation(summary = "我的错题本")
    @GetMapping("/wrongbook")
    public ReturnResult<List<StudentWrongQuestionVO>> wrongbook(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "masteryLevel", required = false) Integer masteryLevel) {
        return ReturnResult.ok(studentCenterService.myWrongQuestions(subject, masteryLevel));
    }

    /**
     * 更新我的错题掌握度
     *
     * @param wrongId      错题记录标识
     * @param masteryLevel 掌握度 0/1/2
     * @return 是否成功
     */
    @Operation(summary = "更新我的错题掌握度")
    @PutMapping("/wrongbook/mastery")
    public ReturnResult<Boolean> updateWrongMastery(@RequestParam Long wrongId,
                                                    @RequestParam Integer masteryLevel) {
        return ReturnResult.ok(studentCenterService.updateMyWrongMastery(wrongId, masteryLevel));
    }

    /**
     * 一键订正我的错题。
     *
     * <p>订正是学生在错题卡上重新作答后的客观标记（与掌握度主观自评独立）。
     * 标记后 {@code wrong_question_corrected=1} 并记录订正时间，供老师端统计订正率。</p>
     *
     * @param wrongId 错题记录标识
     * @return 是否成功
     */
    @Operation(summary = "订正我的错题")
    @PutMapping("/wrongbook/correct/{wrongId}")
    public ReturnResult<Boolean> markCorrected(@PathVariable Long wrongId) {
        return ReturnResult.ok(studentCenterService.markMyWrongCorrected(wrongId));
    }
}
