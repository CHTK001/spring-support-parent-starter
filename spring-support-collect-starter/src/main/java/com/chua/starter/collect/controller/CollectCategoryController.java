package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectCategory;
import com.chua.starter.collect.service.CollectCategoryService;
import com.chua.starter.collect.vo.CollectCategoryVO;
import com.chua.starter.collect.vo.CollectOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集分类接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-分类")
@RestController
@RequestMapping("/v2/collect/category")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectCategoryController {

    /**
     * 分类服务。
     */
    private final CollectCategoryService categoryService;

    /**
     * 分类树。
     *
     * @return 树形分类
     */
    @Operation(summary = "分类树")
    @GetMapping("/tree")
    public ReturnResult<List<CollectCategoryVO>> tree() {
        return ReturnResult.ok(categoryService.tree());
    }

    /**
     * 分类下拉选项。
     *
     * @return 选项列表
     */
    @Operation(summary = "分类下拉选项")
    @GetMapping("/options")
    public ReturnResult<List<CollectOptionVO>> options() {
        return ReturnResult.ok(categoryService.options());
    }

    /**
     * 新增分类。
     *
     * @param category 分类
     * @return 保存后的分类
     */
    @Operation(summary = "新增分类")
    @PostMapping("/save")
    public ReturnResult<CollectCategory> save(@RequestBody CollectCategory category) {
        return ReturnResult.ok(categoryService.addCategory(category));
    }

    /**
     * 修改分类。
     *
     * @param category 分类
     * @return 是否成功
     */
    @Operation(summary = "修改分类")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody CollectCategory category) {
        return ReturnResult.ok(categoryService.editCategory(category));
    }

    /**
     * 删除分类，存在子分类或流程时拒绝。
     *
     * @param categoryId 分类ID
     * @return 是否成功
     */
    @Operation(summary = "删除分类")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer categoryId) {
        return ReturnResult.ok(categoryService.deleteCategory(categoryId));
    }
}
