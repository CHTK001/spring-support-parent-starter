package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.service.SpiderDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 爬虫定义管理接口。
 *
 * <p>提供爬虫定义的增删改查与启用列表查询，路径前缀 {@code /spider-api/spider/definitions}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "爬虫定义管理")
@RestController
@RequestMapping("${spider.path:/spider-api/spider}/definitions")
public class SpiderDefinitionController {

    /**
     * 爬虫定义服务
     */
    private final SpiderDefinitionService definitionService;

    /**
     * 构造方法。
     *
     * @param definitionService 爬虫定义服务
     */
    public SpiderDefinitionController(SpiderDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    /**
     * 查询全部爬虫定义。
     *
     * @return 爬虫定义列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部爬虫定义")
    public ReturnResult<List<SpiderDefinition>> list() {
        return ReturnResult.ok(definitionService.list());
    }

    /**
     * 查询全部已启用爬虫定义。
     *
     * @return 已启用爬虫定义列表
     */
    @GetMapping("/list-enabled")
    @Operation(summary = "查询全部已启用爬虫定义")
    public ReturnResult<List<SpiderDefinition>> listEnabled() {
        return ReturnResult.ok(definitionService.listEnabled());
    }

    /**
     * 按 ID 查询爬虫定义。
     *
     * @param id 主键 ID
     * @return 爬虫定义
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询爬虫定义")
    public ReturnResult<SpiderDefinition> get(@PathVariable Long id) {
        return ReturnResult.ok(definitionService.getById(id));
    }

    /**
     * 新增爬虫定义。
     *
     * @param entity 爬虫定义
     * @return 是否成功
     */
    @PostMapping
    @Operation(summary = "新增爬虫定义")
    public ReturnResult<Boolean> save(@RequestBody SpiderDefinition entity) {
        return ReturnResult.ok(definitionService.saveWithTime(entity));
    }

    /**
     * 更新爬虫定义。
     *
     * @param entity 爬虫定义
     * @return 是否成功
     */
    @PutMapping
    @Operation(summary = "更新爬虫定义")
    public ReturnResult<Boolean> update(@RequestBody SpiderDefinition entity) {
        return ReturnResult.ok(definitionService.updateWithTime(entity));
    }

    /**
     * 删除爬虫定义。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除爬虫定义")
    public ReturnResult<Boolean> remove(@PathVariable Long id) {
        return ReturnResult.ok(definitionService.removeById(id));
    }
}