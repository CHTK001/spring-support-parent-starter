package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.dto.CollectFieldSaveDTO;
import com.chua.starter.collect.entity.CollectField;
import com.chua.starter.collect.service.CollectFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集结果字段接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-结果字段")
@RestController
@RequestMapping("/v2/collect/field")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectFieldController {

    /**
     * 字段服务。
     */
    private final CollectFieldService fieldService;

    /**
     * 某流程节点的字段定义。
     *
     * @param flowId 流程ID
     * @param nodeId 节点ID，空表示流程级字段
     * @return 字段列表
     */
    @Operation(summary = "字段定义列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectField>> list(@RequestParam Integer flowId,
                                                 @RequestParam(required = false) String nodeId) {
        return ReturnResult.ok(fieldService.listFields(flowId, nodeId));
    }

    /**
     * 整体保存字段定义。
     *
     * @param dto 保存入参
     * @return 是否成功
     */
    @Operation(summary = "保存字段定义")
    @PostMapping("/replace")
    public ReturnResult<Boolean> replace(@RequestBody CollectFieldSaveDTO dto) {
        return ReturnResult.ok(fieldService.replaceFields(dto.collectFlowId(),
                dto.collectNodeId(), dto.fields()));
    }
}
