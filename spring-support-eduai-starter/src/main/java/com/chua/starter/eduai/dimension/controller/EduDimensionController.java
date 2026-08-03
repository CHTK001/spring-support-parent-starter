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
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/edu/dimension")
@RequiredArgsConstructor
@Tag(name = "教育AI-维度管理")
public class EduDimensionController {

    private final EduDimensionService dimensionService;

    // ===== 字典下拉 =====

    /**
     * 全量字典下拉（学段 + 学科 + 年级）。
     */
    @Operation(summary = "全量字典下拉")
    @GetMapping("/dropdown")
    public ReturnResult<EduDimensionService.DimensionDropdown> dropdown() {
        return ReturnResult.ok(dimensionService.getDropdown());
    }

    // ===== 学科 =====

    @Operation(summary = "学科列表")
    @GetMapping("/subjects")
    public ReturnResult<List<EduSubject>> listSubjects(@RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(dimensionService.listSubjects(gradeLevel));
    }

    @Operation(summary = "保存学科")
    @PostMapping("/subjects/save")
    public ReturnResult<Long> saveSubject(@RequestBody EduSubject entity) {
        return ReturnResult.ok(dimensionService.saveSubject(entity));
    }

    @Operation(summary = "删除学科")
    @DeleteMapping("/subjects/{id}")
    public ReturnResult<Boolean> deleteSubject(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteSubject(id));
    }

    // ===== 年级 =====

    @Operation(summary = "年级列表")
    @GetMapping("/grades")
    public ReturnResult<List<EduGrade>> listGrades(@RequestParam(required = false) String gradeLevel) {
        return ReturnResult.ok(dimensionService.listGrades(gradeLevel));
    }

    // ===== 班级 =====

    @Operation(summary = "班级列表")
    @GetMapping("/classes")
    public ReturnResult<List<EduClass>> listClasses(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) Long teacherId) {
        return ReturnResult.ok(dimensionService.listClasses(gradeLevel, teacherId));
    }

    @Operation(summary = "保存班级")
    @PostMapping("/classes/save")
    public ReturnResult<Long> saveClass(@RequestBody EduClass entity) {
        return ReturnResult.ok(dimensionService.saveClass(entity));
    }

    @Operation(summary = "删除班级")
    @DeleteMapping("/classes/{id}")
    public ReturnResult<Boolean> deleteClass(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteClass(id));
    }

    // ===== 学生 =====

    @Operation(summary = "学生列表")
    @GetMapping("/students")
    public ReturnResult<List<EduStudent>> listStudents(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade) {
        return ReturnResult.ok(dimensionService.listStudents(classId, gradeLevel, grade));
    }

    @Operation(summary = "保存学生")
    @PostMapping("/students/save")
    public ReturnResult<Long> saveStudent(@RequestBody EduStudent entity) {
        return ReturnResult.ok(dimensionService.saveStudent(entity));
    }

    @Operation(summary = "删除学生")
    @DeleteMapping("/students/{id}")
    public ReturnResult<Boolean> deleteStudent(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteStudent(id));
    }

    // ===== 教师 =====

    @Operation(summary = "教师列表")
    @GetMapping("/teachers")
    public ReturnResult<List<EduTeacher>> listTeachers(@RequestParam(required = false) String subjectCode) {
        return ReturnResult.ok(dimensionService.listTeachers(subjectCode));
    }

    @Operation(summary = "保存教师")
    @PostMapping("/teachers/save")
    public ReturnResult<Long> saveTeacher(@RequestBody EduTeacher entity) {
        return ReturnResult.ok(dimensionService.saveTeacher(entity));
    }

    @Operation(summary = "删除教师")
    @DeleteMapping("/teachers/{id}")
    public ReturnResult<Boolean> deleteTeacher(@PathVariable Long id) {
        return ReturnResult.ok(dimensionService.deleteTeacher(id));
    }
}