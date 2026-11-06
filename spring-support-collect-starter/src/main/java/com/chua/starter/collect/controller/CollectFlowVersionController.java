package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectFlowVersion;
import com.chua.starter.collect.service.CollectFlowVersionService;
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
import java.util.Map;

/**
 * 流程版本接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-流程版本")
@RestController
@RequestMapping("/v2/collect/flow-version")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectFlowVersionController {

    private final CollectFlowVersionService versionService;

    @Operation(summary = "查询版本历史")
    @GetMapping("/list")
    public ReturnResult<List<CollectFlowVersion>> list(@RequestParam Integer flowId) {
        return ReturnResult.ok(versionService.listByFlow(flowId));
    }

    @Operation(summary = "保存版本快照")
    @PostMapping("/save")
    public ReturnResult<Integer> save(@RequestBody Map<String, Object> body) {
        Integer flowId = asInt(body.get("flowId"));
        String flowDsl = body.get("flowDsl") == null ? null : String.valueOf(body.get("flowDsl"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        int version = versionService.saveVersion(flowId, flowDsl, remark);
        return ReturnResult.ok(version);
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback")
    public ReturnResult<Boolean> rollback(@RequestParam Integer flowId,
                                          @RequestParam Integer versionNo) {
        return ReturnResult.ok(versionService.rollback(flowId, versionNo));
    }

    @Operation(summary = "对比两个版本")
    @GetMapping("/diff")
    public ReturnResult<Map<String, Object>> diff(@RequestParam Integer flowId,
                                                  @RequestParam Integer v1,
                                                  @RequestParam Integer v2) {
        List<CollectFlowVersion> all = versionService.listByFlow(flowId);
        String dsl1 = all.stream().filter(v -> v.getVersionNo().equals(v1)).findFirst()
                .map(CollectFlowVersion::getFlowDsl).orElse("");
        String dsl2 = all.stream().filter(v -> v.getVersionNo().equals(v2)).findFirst()
                .map(CollectFlowVersion::getFlowDsl).orElse("");
        return ReturnResult.ok(Map.of("v1", v1, "dsl1", dsl1, "v2", v2, "dsl2", dsl2));
    }

    private Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value));
    }
}