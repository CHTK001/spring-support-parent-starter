package com.chua.starter.eduai.dimension.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.dimension.entity.*;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import com.chua.starter.eduai.model.dto.EduImpersonateDTO;
import com.chua.starter.eduai.model.dto.EduResetPasswordDTO;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教育 AI 维度管理 REST 控制器。
 *
 * <p>对外暴露：学科 / 年级 / 班级 / 学生 / 教师 5 个维度的 CRUD；
 * 路径前缀 {@code /v2/edu/dimension/**}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/dimension")
@RequiredArgsConstructor
@Tag(name = "教育AI-维度管理")
public class EduDimensionController {

    /**
     * 维度服务
    */
    private final EduDimensionService dimensionService;

    // ===== 字典下拉 =====

    /**
     * 全量字典下拉（学段 + 学科 + 年级）。
     * @return dropdown的结果
     */
    @Operation(summary = "全量字典下拉")
    @GetMapping("/dropdown")
    public ReturnResult<EduDimensionService.DimensionDropdown> dropdown() {
        return ReturnResult.ok(dimensionService.getDropdown());
    }

    // ===== 学科 =====

    @Operation(summary = "学科列表")
    @GetMapping("/subjects")
    /**
     * 列表主题
     *
     * @param false false
     * @return 列表主题的结果
     * @param gradeLevel grade级别，不允许为 null
     */
    public ReturnResult<List<EduSubject>> listSubjects(@RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(dimensionService.listSubjects(gradeLevel));
    }

    @Operation(summary = "保存学科")
    @PostMapping("/subjects/save")
    /**
     * 保存主题
     *
     * @param entity 实体
     * @return 保存主题的结果
     */
    public ReturnResult<Long> saveSubject(@RequestBody EduSubject entity) {
        return ReturnResult.ok(dimensionService.saveSubject(entity));
    }

    @Operation(summary = "删除学科")
    @DeleteMapping("/subjects/{id}")
    /**
     * 删除主题
     *
     * @param id 标识
     * @return 删除主题的结果
     */
    public ReturnResult<Boolean> deleteSubject(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteSubject(id));
    }

    // ===== 年级 =====

    @Operation(summary = "年级列表")
    @GetMapping("/grades")
    /**
     * 列表grades
     *
     * @param false false
     * @return 列表grades的结果
     * @param gradeLevel grade级别，不允许为 null
     */
    public ReturnResult<List<EduGrade>> listGrades(@RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(dimensionService.listGrades(gradeLevel));
    }

    @Operation(summary = "保存年级")
    @PostMapping("/grades/save")
    /**
     * 保存grades
     *
     * @param entity 实体
     * @return 保存grades的结果
     */
    public ReturnResult<Long> saveGrade(@RequestBody EduGrade entity) {
        return ReturnResult.ok(dimensionService.saveGrade(entity));
    }

    @Operation(summary = "删除年级")
    @DeleteMapping("/grades/{id}")
    /**
     * 删除grades
     *
     * @param id 标识
     * @return 删除grades的结果
     */
    public ReturnResult<Boolean> deleteGrade(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteGrade(id));
    }

    // ===== 班级 =====

    @Operation(summary = "班级列表")
    @GetMapping("/classes")
    /**
     * 列表类
     * @param gradeLevel grade水平仪
     * @param teacherId teacherid
     * @param teacherId teacherid
     * @param entity 实体
     * @param id 标识
     * @param classId 类标识
     * @param gradeLevel grade水平仪
     * @param grade grade
     * @param gradeLevel grade水平仪
     * @param grade grade
     * @param entity 实体
     * @param id 标识
     * @param subjectCode 主题编码
     * @param entity 实体
     * @param id 标识
     */
    public ReturnResult<List<EduClass>> listClasses(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) Long teacherId) {
        return ReturnResult.ok(dimensionService.listClasses(gradeLevel, teacherId));
    }

    @Operation(summary = "保存班级")
    @PostMapping("/classes/save")
    /**
     * 保存类
     *
     * @param entity 实体
     * @return 保存类的结果
     */
    public ReturnResult<Long> saveClass(@RequestBody EduClass entity) {
        return ReturnResult.ok(dimensionService.saveClass(entity));
    }

    @Operation(summary = "删除班级")
    @DeleteMapping("/classes/{id}")
    /**
     * 删除类
     *
     * @param id 标识
     * @return 删除类的结果
     */
    public ReturnResult<Boolean> deleteClass(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteClass(id));
    }

    // ===== 学生 =====

    @Operation(summary = "学生列表")
    @GetMapping("/students")
    /**
     * 列表students
     * @param classId 类标识
     * @param gradeLevel grade水平仪
     * @param grade grade
     * @param gradeLevel grade水平仪
     * @param grade grade
     * @param entity 实体
     * @param id 标识
     * @param subjectCode 主题编码
     * @param entity 实体
     * @param id 标识
     */
    public ReturnResult<List<EduStudent>> listStudents(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade) {
        return ReturnResult.ok(dimensionService.listStudents(classId, gradeLevel, grade));
    }

    @Operation(summary = "保存学生")
    @PostMapping("/students/save")
    /**
     * 保存Student
     *
     * <p>学号由系统自动生成，请求里带的 {@code studentNo} 会被忽略；
     * 编辑时学号锁定不变（它是学生的登录身份）。</p>
     *
     * @param entity 实体
     * @return 保存student的结果
     */
    public ReturnResult<Long> saveStudent(@RequestBody EduStudent entity) {
        return ReturnResult.ok(dimensionService.saveStudent(entity));
    }

    @Operation(summary = "重置学生登录口令（管理员）")
    @PostMapping("/students/reset-password")
    /**
     * 管理员重置学生登录口令。
     *
     * <p>不需要旧口令——管理员本就掌握档案；但服务层仍校验归属，
     * 避免越权重置他人学生口令。学生自助改密走
     * {@code /v2/edu/student/change-password}，那条会校验旧口令。</p>
     *
     * @param dto 学生标识与新口令
     * @return 是否重置成功
     */
    public ReturnResult<Boolean> resetStudentPassword(@RequestBody EduResetPasswordDTO dto) {
        return ReturnResult.ok(dimensionService.resetStudentPassword(dto.studentId(), dto.newPassword()));
    }

    @Operation(summary = "删除学生")
    @DeleteMapping("/students/{id}")
    /**
     * 删除Student
     *
     * @param id 标识
     * @return 删除student的结果
     */
    public ReturnResult<Boolean> deleteStudent(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteStudent(id));
    }

    @Operation(summary = "代入指定学生（管理员）")
    @PostMapping("/students/impersonate")
    /**
     * 签发一个代入指定学生的临时令牌。
     *
     * <p>管理员用它打开某个学生的学生端页面排查问题，不必索要学生密码，
     * 也不会污染学生的学习记录。仅管理员可用，令牌只读、短效、且被限制在学生端路径内。</p>
     *
     * @param dto 学生标识、有效期与可访问路径
     * @return 临时令牌及其有效期、可访问路径；无权或档案不存在时返回错误
     */
    public ReturnResult<Map<String, Object>> impersonateStudent(@RequestBody EduImpersonateDTO dto) {
        LoginAuthResult result = dimensionService.impersonateStudent(
                dto.studentId(), dto.expireSeconds(), dto.allowedUrls());
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            String message = result == null ? null : result.getMessage();
            return ReturnResult.error(StringUtils.defaultString(message, "签发临时令牌失败"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.getToken());
        data.put("expires", result.getUserResume() == null ? null : result.getUserResume().getExpireTime());
        data.put("readonly", result.getUserResume() != null && result.getUserResume().isReadonly());
        data.put("userId", result.getUserResume() == null ? null : result.getUserResume().getUserId());
        data.put("nickname", result.getUserResume() == null ? null : result.getUserResume().getNickName());
        return ReturnResult.ok(data);
    }

    // ===== 教师 =====

    @Operation(summary = "教师列表")
    @GetMapping("/teachers")
    /**
     * 列表teachers
     *
     * @param false false
     * @return 列表teachers的结果
     * @param subjectCode subject编码，不允许为 null
     */
    public ReturnResult<List<EduTeacher>> listTeachers(@RequestParam(required = false) String subjectCode) {
        return ReturnResult.ok(dimensionService.listTeachers(subjectCode));
    }

    @Operation(summary = "保存教师")
    @PostMapping("/teachers/save")
    /**
     * 保存Teacher
     *
     * @param entity 实体
     * @return 保存teacher的结果
     */
    public ReturnResult<Long> saveTeacher(@RequestBody EduTeacher entity) {
        return ReturnResult.ok(dimensionService.saveTeacher(entity));
    }

    @Operation(summary = "删除教师")
    @DeleteMapping("/teachers/{id}")
    /**
     * 删除Teacher
     *
     * @param id 标识
     * @return 删除teacher的结果
     */
    public ReturnResult<Boolean> deleteTeacher(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteTeacher(id));
    }
}
