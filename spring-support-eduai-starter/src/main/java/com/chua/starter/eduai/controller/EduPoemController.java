package com.chua.starter.eduai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduPoemGradeMapper;
import com.chua.starter.eduai.model.EduPoem;
import com.chua.starter.eduai.model.EduPoemGrade;
import com.chua.starter.eduai.service.IEduPoemService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
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
import java.util.Objects;

/**
 * 古诗控制器：古诗库 CRUD + 趣味游戏（补全下句）
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/poem")
@RequiredArgsConstructor
@Tag(name = "古诗库与古诗游戏")
public class EduPoemController {

    private final IEduPoemService poemService;

    private final EduPoemGradeMapper poemGradeMapper;

    @Operation(summary = "获取古诗列表")
    @GetMapping("/list")
    public ReturnResult<List<EduPoem>> list(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "20") int count) {
        return ReturnResult.ok(poemService.listByGradeLevel(gradeLevel, grade, count));
    }

    @Operation(summary = "分页查询古诗")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduPoem>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ReturnResult.ok(poemService.pageQuery(keyword, gradeLevel, grade, pageNum, pageSize));
    }

    @Operation(summary = "生成古诗游戏数据（补全下句）")
    @GetMapping("/game/generate")
    public ReturnResult<Map<String, Object>> generateGame(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "10") int count) {
        return ReturnResult.ok(poemService.generatePoemGame(gradeLevel, grade, count));
    }

    /**
     * 获取古诗详情。
     *
     * @param poemId 古诗ID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "获取古诗详情")
    @GetMapping("/detail/{poemId}")
    public ReturnResult<EduPoem> detail(@PathVariable Long poemId) {
        EduPoem poem = poemService.getById(poemId);
        if (poem != null) {
            EduUserContext.checkReadable(poem.getOwnerUserId());
            fillPoemGrades(poem);
        }
        return ReturnResult.ok(poem);
    }

    /**
     * 新增或更新古诗。
     *
     * @param poem 古诗
     * @return Return结果 对象
     */
    @Operation(summary = "添加/编辑古诗")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody EduPoem poem) {
        if (poem.getPoemId() == null) {
            poem.setOwnerUserId(EduUserContext.requireUserId());
        } else {
            EduPoem exists = poemService.getById(poem.getPoemId());
            if (exists == null) {
                throw new BusinessException("古诗不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            poem.setOwnerUserId(exists.getOwnerUserId());
        }
        Boolean saved = poemService.saveOrUpdate(poem);
        syncPoemGrades(poem);
        return ReturnResult.ok(saved);
    }

    /**
     * 删除古诗（含学段映射）。
     *
     * @param poemId 古诗ID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "删除古诗")
    @DeleteMapping("/delete/{poemId}")
    public ReturnResult<Boolean> delete(@PathVariable Long poemId) {
        EduPoem exists = poemService.getById(poemId);
        if (exists == null) {
            return ReturnResult.ok(false);
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        poemGradeMapper.delete(new QueryWrapper<EduPoemGrade>().eq("poem_id", poemId));
        return ReturnResult.ok(poemService.removeById(poemId));
    }

    /**
     * 保存古诗的学段/年级映射（edu_poem_grade）：先清空再按提交的学段×年级重建。
     * 未提交 gradeLevels 时视为不改动映射（兼容仅改内容的调用方）。
     *
     * @param poem 已保存的古诗（含 gradeLevels/grades）
     */
    private void syncPoemGrades(EduPoem poem) {
        List<String> levels = poem.getGradeLevels();
        if (levels == null || poem.getPoemId() == null) {
            return;
        }
        poemGradeMapper.delete(new QueryWrapper<EduPoemGrade>().eq("poem_id", poem.getPoemId()));
        List<String> grades = poem.getGrades();
        for (String level : levels) {
            if (level == null || level.trim().isEmpty()) {
                continue;
            }
            String normalized = GradeLevels.normalize(level.trim());
            if (grades == null || grades.isEmpty()) {
                insertPoemGrade(poem, normalized, null);
            } else {
                for (String grade : grades) {
                    insertPoemGrade(poem, normalized,
                            grade == null || grade.trim().isEmpty() ? null : grade.trim());
                }
            }
        }
    }

    /**
     * 插入单条古诗学段映射。
     *
     * @param poem       古诗
     * @param gradeLevel 学段
     * @param grade      年级（可为空）
     */
    private void insertPoemGrade(EduPoem poem, String gradeLevel, String grade) {
        EduPoemGrade row = new EduPoemGrade();
        row.setPoemId(poem.getPoemId());
        row.setGradeLevel(gradeLevel);
        row.setGrade(grade);
        row.setOwnerUserId(poem.getOwnerUserId());
        poemGradeMapper.insert(row);
    }

    /**
     * 回填古诗的学段/年级映射，供编辑回显。
     *
     * @param poem 古诗
     */
    private void fillPoemGrades(EduPoem poem) {
        List<EduPoemGrade> rows = poemGradeMapper.selectList(
                new QueryWrapper<EduPoemGrade>().eq("poem_id", poem.getPoemId()));
        poem.setGradeLevels(rows.stream().map(EduPoemGrade::getGradeLevel)
                .filter(Objects::nonNull).distinct().toList());
        poem.setGrades(rows.stream().map(EduPoemGrade::getGrade)
                .filter(Objects::nonNull).distinct().toList());
    }
}
