package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduIdiom;
import com.chua.starter.eduai.service.IEduIdiomService;
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

    @Operation(summary = "获取成语列表")
    @GetMapping("/list")
    public ReturnResult<List<EduIdiom>> list(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "20") int count) {
        return ReturnResult.ok(idiomService.listByGradeLevel(gradeLevel, grade, count));
    }

    @Operation(summary = "生成成语消消乐游戏数据")
    @GetMapping("/game/generate")
    public ReturnResult<Map<String, Object>> generateGame(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "10") int count) {
        return ReturnResult.ok(idiomService.generateIdiomGame(gradeLevel, grade, count));
    }

    @Operation(summary = "验证成语配对")
    @PostMapping("/game/validate")
    public ReturnResult<Boolean> validatePair(@RequestBody Map<String, Object> params) {
        Long idiomId = Long.valueOf(params.get("idiomId").toString());
        String pinyin = (String) params.get("pinyin");
        String meaning = (String) params.get("meaning");
        return ReturnResult.ok(idiomService.validateIdiomPair(idiomId, pinyin, meaning));
    }

    @Operation(summary = "获取成语详情")
    @GetMapping("/detail/{idiomId}")
    public ReturnResult<EduIdiom> detail(@PathVariable Long idiomId) {
        return ReturnResult.ok(idiomService.getById(idiomId));
    }

    @Operation(summary = "添加成语")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody EduIdiom idiom) {
        return ReturnResult.ok(idiomService.saveOrUpdate(idiom));
    }

    @Operation(summary = "删除成语")
    @DeleteMapping("/delete/{idiomId}")
    public ReturnResult<Boolean> delete(@PathVariable Long idiomId) {
        return ReturnResult.ok(idiomService.removeById(idiomId));
    }
}
