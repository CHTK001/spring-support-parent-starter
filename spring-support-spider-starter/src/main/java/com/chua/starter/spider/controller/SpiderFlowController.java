package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.service.SpiderFlowService;
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

/**
 * 流程编排管理接口。
 *
 * <p>提供流程编排的读写，前端 ReFlow 保存/加载节点与连线 JSON，路径前缀
 * {@code /spider-api/spider/flows}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "流程编排管理")
@RestController
@RequestMapping("${spider.path:/spider-api/spider}/flows")
public class SpiderFlowController {

    /**
     * 流程编排服务
     */
    private final SpiderFlowService flowService;

    /**
     * 构造方法。
     *
     * @param flowService 流程编排服务
     */
    public SpiderFlowController(SpiderFlowService flowService) {
        this.flowService = flowService;
    }

    /**
     * 按定义 ID 查询流程编排。
     *
     * @param definitionId 爬虫定义 ID
     * @return 流程编排
     */
    @GetMapping("/by-definition/{definitionId}")
    @Operation(summary = "按定义 ID 查询流程编排")
    public ReturnResult<SpiderFlow> getByDefinition(@PathVariable Long definitionId) {
        return ReturnResult.ok(flowService.getOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SpiderFlow>lambdaQuery()
                        .eq(SpiderFlow::getDefinitionId, definitionId)));
    }

    /**
     * 新增流程编排。
     *
     * @param entity 流程编排
     * @return 是否成功
     */
    @PostMapping
    @Operation(summary = "新增流程编排")
    public ReturnResult<Boolean> save(@RequestBody SpiderFlow entity) {
        return ReturnResult.ok(flowService.save(entity));
    }

    /**
     * 更新流程编排。
     *
     * @param entity 流程编排
     * @return 是否成功
     */
    @PutMapping
    @Operation(summary = "更新流程编排")
    public ReturnResult<Boolean> update(@RequestBody SpiderFlow entity) {
        return ReturnResult.ok(flowService.updateById(entity));
    }

    /**
     * 删除流程编排。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除流程编排")
    public ReturnResult<Boolean> remove(@PathVariable Long id) {
        return ReturnResult.ok(flowService.removeById(id));
    }
}