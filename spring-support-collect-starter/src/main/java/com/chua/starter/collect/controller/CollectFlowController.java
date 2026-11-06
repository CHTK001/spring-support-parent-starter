package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectFlowSaveDTO;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.service.CollectFlowService;
import com.chua.starter.collect.vo.CollectFlowVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
 * 采集流程接口。
 *
 * <p>流程定义即前端设计器保存的 DSL，列表不返回 DSL 正文，只有详情返回。
 * 校验失败由全局异常处理转成 400 与中文提示，控制器不再重复包装。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-流程")
@RestController
@RequestMapping("/v2/collect/flow")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectFlowController {

    /**
     * 流程服务。
     */
    private final CollectFlowService flowService;

    /**
     * 流程分页列表。
     *
     * @param query     分页参数
     * @param condition 查询条件
     * @return 流程分页
     */
    @Operation(summary = "流程分页列表")
    @GetMapping("/page")
    public ReturnPageResult<CollectFlowVO> page(@ParameterObject Query<CollectFlow> query,
                                                @ParameterObject CollectFlow condition) {
        return PageResultUtils.ok(flowService.pageFlows(query.createPage(), condition));
    }

    /**
     * 流程精简列表。
     *
     * @param keyword 名称或编码模糊词
     * @return 流程列表
     */
    @Operation(summary = "流程精简列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectFlowVO>> list(@Parameter(description = "名称或编码") String keyword) {
        return ReturnResult.ok(flowService.listFlows(keyword));
    }

    /**
     * 流程详情，含流程图 DSL。
     *
     * @param flowId 流程ID
     * @return 流程详情
     */
    @Operation(summary = "流程详情")
    @GetMapping("/detail")
    public ReturnResult<CollectFlowVO> detail(@Parameter(description = "流程ID") Integer flowId) {
        return ReturnResult.ok(flowService.detail(flowId));
    }

    /**
     * 新增流程。
     *
     * @param dto 保存入参
     * @return 流程出参
     */
    @Operation(summary = "新增流程")
    @PostMapping("/save")
    public ReturnResult<CollectFlowVO> save(@RequestBody CollectFlowSaveDTO dto) {
        return ReturnResult.ok(flowService.addFlow(dto));
    }

    /**
     * 修改流程。
     *
     * @param dto 保存入参
     * @return 流程出参
     */
    @Operation(summary = "修改流程")
    @PutMapping("/update")
    public ReturnResult<CollectFlowVO> update(@RequestBody CollectFlowSaveDTO dto) {
        return ReturnResult.ok(flowService.editFlow(dto));
    }

    /**
     * 启停流程调度。
     *
     * @param flowId 流程ID
     * @param status 0 停用 1 启用
     * @return 流程出参
     */
    @Operation(summary = "启停流程调度")
    @PutMapping("/status")
    public ReturnResult<CollectFlowVO> status(@RequestParam Integer flowId, @RequestParam Integer status) {
        return ReturnResult.ok(flowService.changeStatus(flowId, status));
    }

    /**
     * 复制流程。
     *
     * @param flowId 流程ID
     * @return 副本出参
     */
    @Operation(summary = "复制流程")
    @PostMapping("/copy")
    public ReturnResult<CollectFlowVO> copy(@RequestParam Integer flowId) {
        return ReturnResult.ok(flowService.copyFlow(flowId));
    }

    /**
     * 删除流程。
     *
     * @param flowId 流程ID
     * @return 是否成功
     */
    @Operation(summary = "删除流程")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer flowId) {
        return ReturnResult.ok(flowService.deleteFlow(flowId));
    }

    /**
     * 只校验流程图不落库，供设计器保存前检查。
     *
     * @param dsl 流程图
     * @return 校验通过的提示
     */
    @Operation(summary = "校验流程图")
    @PostMapping("/validate")
    public ReturnResult<String> validate(@RequestBody CollectDslDTO dsl) {
        flowService.normalizeDsl(dsl);
        return ReturnResult.ok("流程图校验通过");
    }
}
