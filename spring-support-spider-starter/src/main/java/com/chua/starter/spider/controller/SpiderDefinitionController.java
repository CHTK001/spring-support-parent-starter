package com.chua.starter.spider.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.dto.PageResultVO;
import com.chua.starter.spider.dto.SpiderConverter;
import com.chua.starter.spider.dto.SpiderDefinitionVO;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.service.SpiderDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
* 爬虫定义管理接口。
*
* <p>提供爬虫定义的增删改查与启用列表查询，路径前缀 {@code /spider/definitions}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Tag(name = "爬虫定义管理")
@RestController
@RequestMapping("${spider.path:/spider}/definitions")
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
    * 分页查询爬虫定义。
    *
    * @param pageNo   页码
    * @param pageSize 每页条数
    * @param keyword  关键字（编码/名称模糊匹配）
    * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询爬虫定义")
    public ReturnResult<PageResultVO<SpiderDefinitionVO>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword) {
        IPage<SpiderDefinition> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderDefinition> wrapper =
                new LambdaQueryWrapper<SpiderDefinition>()
                        .orderByDesc(SpiderDefinition::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SpiderDefinition::getCode, keyword)
                    .or().like(SpiderDefinition::getName, keyword));
        }
        IPage<SpiderDefinition> result = definitionService.page(page, wrapper);
        PageResultVO<SpiderDefinitionVO> vo = new PageResultVO<>();
        vo.setPageNo(result.getCurrent());
        vo.setPageSize(result.getSize());
        vo.setTotal(result.getTotal());
        vo.setTotalPages(result.getPages());
        vo.setRecords(SpiderConverter.toDefinitionVOList(result.getRecords()));
        vo.setData(vo.getRecords());
        return ReturnResult.ok(vo);
    }

    /**
    * 按编码查询爬虫定义详情。
    *
    * @param spiderCode 爬虫编码
    * @return 爬虫定义视图
     */
    @GetMapping("/detail")
    @Operation(summary = "按编码查询爬虫定义详情")
    public ReturnResult<SpiderDefinitionVO> detail(@RequestParam String spiderCode) {
        SpiderDefinition entity = definitionService.lambdaQuery()
                .eq(SpiderDefinition::getCode, spiderCode).one();
        return ReturnResult.ok(SpiderConverter.toDefinitionVO(entity));
    }

    /**
    * 保存爬虫定义（新增或更新）。
    *
    * @param vo 爬虫定义视图
    * @return 保存后的爬虫定义
     */
    @PostMapping("/save")
    @Operation(summary = "保存爬虫定义")
    public ReturnResult<SpiderDefinitionVO> save(@RequestBody SpiderDefinitionVO vo) {
        SpiderDefinition entity = SpiderConverter.toDefinition(vo);
        if (entity.getId() == null) {
            definitionService.save(entity);
        } else {
            definitionService.updateById(entity);
        }
        return ReturnResult.ok(SpiderConverter.toDefinitionVO(
                definitionService.getById(entity.getId())));
    }

    /**
    * 按编码删除爬虫定义。
    *
    * @param spiderCode 爬虫编码
    * @return 是否成功
     */
    @DeleteMapping("/delete")
    @Operation(summary = "按编码删除爬虫定义")
    public ReturnResult<Boolean> delete(@RequestParam String spiderCode) {
        List<SpiderDefinition> list = definitionService.lambdaQuery()
                .eq(SpiderDefinition::getCode, spiderCode).list();
        if (list.isEmpty()) {
            return ReturnResult.ok(false);
        }
        return ReturnResult.ok(definitionService.removeById(list.get(0).getId()));
    }

    /**
    * 启停切换。
    *
    * @param spiderCode   爬虫编码
    * @param spiderStatus 目标状态（1 启用 / 0 禁用）
    * @return 是否成功
     */
    @PostMapping("/status")
    @Operation(summary = "启用/禁用爬虫定义")
    public ReturnResult<Boolean> status(@RequestParam String spiderCode,
                                        @RequestParam Integer spiderStatus) {
        List<SpiderDefinition> list = definitionService.lambdaQuery()
                .eq(SpiderDefinition::getCode, spiderCode).list();
        if (list.isEmpty()) {
            return ReturnResult.ok(false);
        }
        SpiderDefinition update = new SpiderDefinition();
        update.setId(list.get(0).getId());
        update.setEnabled(spiderStatus);
        return ReturnResult.ok(definitionService.updateById(update));
    }

    /**
    * 查询全部爬虫定义。
    *
    * @return 爬虫定义列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部爬虫定义")
    public ReturnResult<List<SpiderDefinitionVO>> list() {
        return ReturnResult.ok(definitionService.list().stream()
                .map(SpiderConverter::toDefinitionVO).toList());
    }

    /**
    * 查询全部已启用爬虫定义（供调度器使用）。
    *
    * @return 已启用爬虫定义列表
     */
    @GetMapping("/list-enabled")
    @Operation(summary = "查询全部已启用爬虫定义")
    public ReturnResult<List<SpiderDefinitionVO>> listEnabled() {
        return ReturnResult.ok(definitionService.listEnabled().stream()
                .map(SpiderConverter::toDefinitionVO).toList());
    }

    /**
    * 按 标识 查询爬虫定义。
    *
    * @param id 主键 标识
    * @return 爬虫定义
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询爬虫定义")
    public ReturnResult<SpiderDefinitionVO> get(@PathVariable Long id) {
        return ReturnResult.ok(SpiderConverter.toDefinitionVO(definitionService.getById(id)));
    }

    /**
    * 新增爬虫定义。
    *
    * @param entity 爬虫定义
    * @return 是否成功
     */
    @PostMapping
    @Operation(summary = "新增爬虫定义")
    public ReturnResult<Boolean> saveEntity(@RequestBody SpiderDefinition entity) {
        return ReturnResult.ok(definitionService.save(entity));
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
        return ReturnResult.ok(definitionService.updateById(entity));
    }

    /**
    * 删除爬虫定义。
    *
    * @param id 主键 标识
    * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除爬虫定义")
    public ReturnResult<Boolean> remove(@PathVariable Long id) {
        return ReturnResult.ok(definitionService.removeById(id));
    }
}