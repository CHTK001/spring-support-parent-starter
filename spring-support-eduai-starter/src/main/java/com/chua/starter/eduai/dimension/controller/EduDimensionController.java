package com.chua.starter.eduai.dimension.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.dimension.entity.*;
import com.chua.starter.eduai.dimension.service.EduDimensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /** 维度服务 */
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
     */
    public ReturnResult<List<EduGrade>> listGrades(@RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(dimensionService.listGrades(gradeLevel));
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
     * @param entity 实体
     * @return 保存student的结果
     */
    public ReturnResult<Long> saveStudent(@RequestBody EduStudent entity) {
        return ReturnResult.ok(dimensionService.saveStudent(entity));
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

    // ===== 教师 =====

    @Operation(summary = "教师列表")
    @GetMapping("/teachers")
    /**
     * 列表teachers
     *
     * @param false false
     * @return 列表teachers的结果
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