package com.chua.starter.eduai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduIdiomGradeMapper;
import com.chua.starter.eduai.model.EduIdiom;
import com.chua.starter.eduai.model.EduIdiomGrade;
import com.chua.starter.eduai.service.IEduIdiomService;
import com.chua.starter.eduai.support.util.EduUserContext;
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
 * 成语游戏控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/idiom")
@RequiredArgsConstructor
@Tag(name = "成语游戏")
public class EduIdiomController {

    private final IEduIdiomService idiomService;

    private final EduIdiomGradeMapper idiomGradeMapper;

    @Operation(summary = "获取成语列表")
    @GetMapping("/list")
    public ReturnResult<List<EduIdiom>> list(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "20") int count) {
        // 列表上限，防止扫全表
        int safeCount = count <= 0 ? 20 : Math.min(count, 200);
        return ReturnResult.ok(idiomService.listByGradeLevel(gradeLevel, grade, safeCount));
    }

    @Operation(summary = "分页查询成语")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduIdiom>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        // 分页参数兜底，防止 service 层 NPE
        int pNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int pSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        return ReturnResult.ok(idiomService.pageQuery(keyword, gradeLevel, grade, pNum, pSize));
    }

    @Operation(summary = "生成成语消消乐游戏数据")
    @GetMapping("/game/generate")
    public ReturnResult<Map<String, Object>> generateGame(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "10") int count) {
        int safeCount = count <= 0 ? 10 : Math.min(count, 50);
        return ReturnResult.ok(idiomService.generateIdiomGame(gradeLevel, grade, safeCount));
    }

    /**
     * 成语接龙：服务端自动出题（首尾相接）。
     *
     * @param gradeLevel 学段（可空）
     * @param grade      年级（可空）
     * @param length     期望步数（服务端夹在 3~10）
     * @return 起始成语 + 步数（不含答案）
     */
    @Operation(summary = "成语接龙-生成题目")
    @GetMapping("/chain/start")
    public ReturnResult<Map<String, Object>> chainStart(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "5") int length) {
        return ReturnResult.ok(idiomService.generateChain(gradeLevel, grade, length));
    }

    /**
     * 成语接龙：校验一步。
     *
     * @param params 含 current（当前成语）与 answer（用户接的成语）
     * @return ok/message/next/meaning
     */
    @Operation(summary = "成语接龙-校验一步")
    @PostMapping("/chain/validate")
    public ReturnResult<Map<String, Object>> chainValidate(@RequestBody Map<String, Object> params) {
        if (params == null) {
            throw new BusinessException("请求体不能为空");
        }
        String current = params.get("current") == null ? null : params.get("current").toString();
        String answer = params.get("answer") == null ? null : params.get("answer").toString();
        return ReturnResult.ok(idiomService.validateChain(current, answer));
    }

    /**
     * 成语接龙：给当前成语一个提示答案。
     *
     * @param current 当前成语
     * @return ok + content/pinyin/meaning
     */
    @Operation(summary = "成语接龙-提示")
    @GetMapping("/chain/hint")
    public ReturnResult<Map<String, Object>> chainHint(@RequestParam String current) {
        return ReturnResult.ok(idiomService.chainHint(current));
    }

    /**
     * 校验Pair。
     *
     * @param params 参数，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "验证成语配对")
    @PostMapping("/game/validate")
    public ReturnResult<Boolean> validatePair(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("idiomId") == null) {
            throw new BusinessException("idiomId 不能为空");
        }
        Long idiomId = Long.valueOf(params.get("idiomId").toString());
        String pinyin = (String) params.get("pinyin");
        String meaning = (String) params.get("meaning");
        return ReturnResult.ok(idiomService.validateIdiomPair(idiomId, pinyin, meaning));
    }

    /**
     * detail。
     *
     * @param idiomId idiomID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "获取成语详情")
    @GetMapping("/detail/{idiomId}")
    public ReturnResult<EduIdiom> detail(@PathVariable Long idiomId) {
        if (idiomId == null) {
            throw new BusinessException("idiomId 不能为空");
        }
        EduIdiom idiom = idiomService.getById(idiomId);
        if (idiom != null) {
            EduUserContext.checkReadable(idiom.getOwnerUserId());
            fillIdiomGrades(idiom);
        }
        if (idiom == null) {
            throw new BusinessException("成语不存在");
        }
        return ReturnResult.ok(idiom);
    }

    /**
     * 保存。
     *
     * @param idiom 方法入参 idiom
     * @return Return结果 对象
     */
    @Operation(summary = "添加成语")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody EduIdiom idiom) {
        if (idiom.getIdiomId() == null) {
            idiom.setOwnerUserId(EduUserContext.requireUserId());
        } else {
            EduIdiom exists = idiomService.getById(idiom.getIdiomId());
            if (exists == null) {
                throw new BusinessException("成语不存在");
            }
            EduUserContext.checkWritable(exists.getOwnerUserId());
            idiom.setOwnerUserId(exists.getOwnerUserId());
        }
        Boolean saved = idiomService.saveOrUpdate(idiom);
        syncIdiomGrades(idiom);
        return ReturnResult.ok(saved);
    }

    /**
     * 保存成语的学段/年级映射（edu_idiom_grade）：先清空再按提交的学段×年级重建。
     * 未提交 gradeLevels 时视为不改动映射（兼容仅改内容的调用方）。
     * <p>学段值统一存中文名（小学/初中/高中/大学），与前端下拉及消消乐取数保持一致。</p>
     *
     * @param idiom 已保存的成语（含 gradeLevels/grades）
     */
    private void syncIdiomGrades(EduIdiom idiom) {
        List<String> levels = idiom.getGradeLevels();
        if (levels == null || idiom.getIdiomId() == null) {
            return;
        }
        idiomGradeMapper.delete(new QueryWrapper<EduIdiomGrade>().eq("idiom_id", idiom.getIdiomId()));
        List<String> grades = idiom.getGrades();
        for (String level : levels) {
            if (level == null || level.trim().isEmpty()) {
                continue;
            }
            String normalized = normalizeLevel(level.trim());
            if (grades == null || grades.isEmpty()) {
                insertIdiomGrade(idiom, normalized, null);
            } else {
                for (String grade : grades) {
                    insertIdiomGrade(idiom, normalized, grade == null || grade.trim().isEmpty() ? null : grade.trim());
                }
            }
        }
    }

    /**
     * 学段值归一化：把 PRIMARY/JUNIOR/SENIOR/UNIVERSITY 等码值统一为中文名，避免同一学段出现两种写法。
     *
     * @param level 原始学段值
     * @return 归一化后的学段名
     */
    private String normalizeLevel(String level) {
        return switch (level.toUpperCase(java.util.Locale.ROOT)) {
            case "PRIMARY", "小学" -> "小学";
            case "JUNIOR", "MIDDLE", "初中" -> "初中";
            case "SENIOR", "HIGH", "高中" -> "高中";
            case "UNIVERSITY", "大学" -> "大学";
            default -> level;
        };
    }

    /**
     * 插入单条成语学段映射。
     *
     * @param idiom      成语
     * @param gradeLevel 学段
     * @param grade      年级（可为空）
     */
    private void insertIdiomGrade(EduIdiom idiom, String gradeLevel, String grade) {
        EduIdiomGrade row = new EduIdiomGrade();
        row.setIdiomId(idiom.getIdiomId());
        row.setGradeLevel(gradeLevel);
        row.setGrade(grade);
        row.setOwnerUserId(idiom.getOwnerUserId());
        idiomGradeMapper.insert(row);
    }

    /**
     * 回填成语的学段/年级映射，供编辑回显。
     *
     * @param idiom 成语
     */
    private void fillIdiomGrades(EduIdiom idiom) {
        List<EduIdiomGrade> rows = idiomGradeMapper.selectList(
                new QueryWrapper<EduIdiomGrade>().eq("idiom_id", idiom.getIdiomId()));
        idiom.setGradeLevels(rows.stream().map(EduIdiomGrade::getGradeLevel).filter(java.util.Objects::nonNull).distinct().toList());
        idiom.setGrades(rows.stream().map(EduIdiomGrade::getGrade).filter(java.util.Objects::nonNull).distinct().toList());
    }

    /**
     * 删除。
     *
     * @param idiomId idiomID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "删除成语")
    @DeleteMapping("/delete/{idiomId}")
    public ReturnResult<Boolean> delete(@PathVariable Long idiomId) {
        EduIdiom exists = idiomService.getById(idiomId);
        if (exists == null) {
            return ReturnResult.ok(false);
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        idiomGradeMapper.delete(new QueryWrapper<EduIdiomGrade>().eq("idiom_id", idiomId));
        return ReturnResult.ok(idiomService.removeById(idiomId));
    }
}
