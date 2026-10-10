package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.model.dto.EduResetPasswordDTO;
import com.chua.starter.eduai.model.dto.TeacherBindDTO;
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
 * 其中组卷与阅卷直接复用 {@code /v2/edu/paper/**}、{@code /v2/edu/marking/**}
 * 这两组已按归属隔离的接口，教师中心只额外提供「我发起的阅卷任务列表」与
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
            @RequestParam(required = false) Integer pageSize) {
        return ReturnResult.ok(teacherCenterService.pageMyMarkingTasks(pageNum, pageSize));
    }
}
