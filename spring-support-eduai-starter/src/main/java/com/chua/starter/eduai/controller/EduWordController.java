package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.EduWord;
import com.chua.starter.eduai.service.IEduWordService;
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
 * 词语库控制器（整词 / 读音 / 释义 / 造句 / 近义反义同音）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/word")
@RequiredArgsConstructor
@Tag(name = "词语库")
public class EduWordController {

    private final IEduWordService wordService;

    /**
     * 分页查询词语。
     *
     * @param keyword    关键字（整词 / 拼音 / 无声调拼音 / 释义 / 近义 / 反义 / 同音）
     * @param gradeLevel 学段（可空）
     * @param kind       语料类型（可空）
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    @Operation(summary = "分页查询词语")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduWord>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        int pNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        // [P3C A20 豁免]
        int pSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        return ReturnResult.ok(wordService.pageQuery(keyword, gradeLevel, kind, pNum, pSize));
    }

    /**
     * 词语详情。
     *
     * @param id 词语标识
     * @return 词语；不存在抛业务异常
     */
    @Operation(summary = "词语详情")
    @GetMapping("/{id}")
    public ReturnResult<EduWord> detail(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        EduWord word = wordService.getById(id);
        if (word == null) {
            throw new BusinessException("词语不存在");
        }
        EduUserContext.checkReadable(word.getOwnerUserId());
        return ReturnResult.ok(word);
    }

    /**
     * 保存词语（同一个词重复提交则更新）。
     *
     * @param entity 词语
     * @return 词语标识
     */
    @Operation(summary = "保存词语")
    @PostMapping("/save")
    public ReturnResult<Long> save(@RequestBody EduWord entity) {
        return ReturnResult.ok(wordService.saveWord(entity));
    }

    /**
     * 删除词语。
     *
     * @param id 词语标识
     * @return 是否成功
     */
    @Operation(summary = "删除词语")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(wordService.deleteWord(id));
    }

    /**
     * 右键关联检索：相似词语 / 反向词语 / 同音词 / 同音字词 / 造句 / 含相同字。
     *
     * @param wordId 词语标识
     * @param mode   检索口径（similar / antonym / homophone / pinyin / sentence / char）
     * @return 检索结果
     */
    @Operation(summary = "词语关联检索")
    @GetMapping("/related")
    public ReturnResult<Map<String, Object>> related(
            @RequestParam Long wordId,
            @RequestParam(defaultValue = "similar") String mode) {
        return ReturnResult.ok(wordService.related(wordId, mode));
    }

    /**
     * 关键字联想（搜索框候选提示用）。
     *
     * @param keyword 关键字
     * @param count   条数
     * @return 词语列表
     */
    @Operation(summary = "词语联想")
    @GetMapping("/suggest")
    public ReturnResult<List<EduWord>> suggest(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int count) {
        return ReturnResult.ok(wordService.suggest(keyword, count));
    }
}
