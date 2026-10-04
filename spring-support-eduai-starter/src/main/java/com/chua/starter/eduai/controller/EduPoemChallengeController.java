package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduPoem;
import com.chua.starter.eduai.service.IEduPoemService;
import com.chua.starter.eduai.support.util.PoemGameGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 古诗文趣味游戏控制器
 *
 * <p>与「古诗补全」（{@code /v2/edu/poem/game/generate}，选下一句）互补，
 * 这里提供七种机制不同的玩法；数据都取自 edu_poem，按学段/年级筛选。</p>
 *
 * <p>接口形态统一为 {@code /v2/edu/poem-challenge/{mode}}，返回
 * {@code {mode, items, totalItems, ...}}，前端一套渲染逻辑即可承载。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/poem-challenge")
@RequiredArgsConstructor
@Tag(name = "古诗文趣味游戏")
public class EduPoemChallengeController {

    /**
     * 单次取诗的上限：题目要干扰项，取题目数的若干倍足够，避免全表扫描
     */
    private static final int POOL_MULTIPLIER = 6;

    private final IEduPoemService poemService;

    /**
     * 诗句排序：整首打乱后还原句序。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表
     */
    @Operation(summary = "诗句排序")
    @GetMapping("/order")
    public ReturnResult<Map<String, Object>> order(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count);
        return ReturnResult.ok(wrap("order", PoemGameGenerator.order(pool, count, new Random()), gradeLevel, grade));
    }

    /**
     * 飞花令：给定关键字，挑出所有含该字的诗句。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param keyword    关键字（单字，可空则随机）
     * @param count      题目数量
     * @return 题目列表
     */
    @Operation(summary = "飞花令")
    @GetMapping("/feihualing")
    public ReturnResult<Map<String, Object>> feihualing(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "6") int count) {
        // 飞花令要在句子里找字，取更大样本提高命中率
        List<EduPoem> pool = pool(gradeLevel, grade, count * POOL_MULTIPLIER);
        return ReturnResult.ok(
                wrap("feihualing", PoemGameGenerator.feihualing(pool, keyword, count, new Random()),
                        gradeLevel, grade));
    }

    /**
     * 看句猜题：给句子选诗题。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表
     */
    @Operation(summary = "看句猜题")
    @GetMapping("/title")
    public ReturnResult<Map<String, Object>> title(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count);
        return ReturnResult.ok(wrap("title", PoemGameGenerator.title(pool, count, new Random()), gradeLevel, grade));
    }

    /**
     * 诗归其主：给句子选作者（干扰项优先同朝代）。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表
     */
    @Operation(summary = "诗归其主")
    @GetMapping("/author")
    public ReturnResult<Map<String, Object>> author(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count);
        return ReturnResult.ok(wrap("author", PoemGameGenerator.author(pool, count, new Random()), gradeLevel, grade));
    }

    /**
     * 朝代溯源：给句子判断朝代。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表；诗库只有一个朝代时 items 为空
     */
    @Operation(summary = "朝代溯源")
    @GetMapping("/dynasty")
    public ReturnResult<Map<String, Object>> dynasty(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count * POOL_MULTIPLIER);
        Map<String, Object> data = wrap("dynasty",
                PoemGameGenerator.dynasty(pool, count, new Random()), gradeLevel, grade);
        if (((List<?>) data.get("items")).isEmpty()) {
            data.put("hint", "当前诗库朝代不足两种，无法生成朝代判断题；请先采集更多朝代的作品");
        }
        return ReturnResult.ok(data);
    }

    /**
     * 炼字填空：挖掉一处字词四选一。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表
     */
    @Operation(summary = "炼字填空")
    @GetMapping("/fill")
    public ReturnResult<Map<String, Object>> fill(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count * POOL_MULTIPLIER);
        return ReturnResult.ok(wrap("fill", PoemGameGenerator.fill(pool, count, new Random()), gradeLevel, grade));
    }

    /**
     * 题材归类：给诗句判断所属题材（思乡/边塞/田园…）。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      题目数量
     * @return 题目列表；标签里没有题材词时为空并给提示
     */
    @Operation(summary = "题材归类")
    @GetMapping("/theme")
    public ReturnResult<Map<String, Object>> theme(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "8") int count) {
        List<EduPoem> pool = pool(gradeLevel, grade, count * POOL_MULTIPLIER);
        Map<String, Object> data = wrap("theme",
                PoemGameGenerator.theme(pool, count, new Random()), gradeLevel, grade);
        if (((List<?>) data.get("items")).isEmpty()) {
            data.put("hint", "取到的诗没有题材标签，无法生成题材归类题；请先补充 poem_tags（采集源带 type 字段即可）");
        }
        return ReturnResult.ok(data);
    }

    /**
     * 飞花令可用关键字清单（前端做筛选项）。
     *
     * @return 关键字列表
     */
    @Operation(summary = "飞花令关键字清单")
    @GetMapping("/keywords")
    public ReturnResult<List<String>> keywords() {
        return ReturnResult.ok(PoemGameGenerator.FEIHUALING_KEYWORDS);
    }

    /**
     * 按学段取诗池。
     *
     * @param gradeLevel 学段，可空
     * @param grade      年级，可空
     * @param count      需要的基础条数
     * @return 诗池
     */
    private List<EduPoem> pool(String gradeLevel, String grade, int count) {
        int need = Math.max(count * POOL_MULTIPLIER, 60);
        List<EduPoem> poems = poemService.listByGradeLevel(gradeLevel, grade, need);
        boolean hasLevel = gradeLevel != null && !gradeLevel.isBlank();
        if (hasLevel && (poems == null || poems.isEmpty())) {
            // 本学段查不到诗（映射缺失/传了码值）：回落全库，否则各玩法都会出空题
            poems = poemService.listByGradeLevel(null, null, need);
        }
        return poems == null ? new ArrayList<>() : poems;
    }

    /**
     * 统一响应包装。
     *
     * @param mode       模式
     * @param items      题目
     * @param gradeLevel 学段
     * @param grade      年级
     * @return 响应体
     */
    private Map<String, Object> wrap(String mode, List<Map<String, Object>> items,
                                     String gradeLevel, String grade) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", mode);
        data.put("items", items);
        data.put("totalItems", items.size());
        data.put("gradeLevel", gradeLevel);
        data.put("grade", grade);
        return data;
    }
}
