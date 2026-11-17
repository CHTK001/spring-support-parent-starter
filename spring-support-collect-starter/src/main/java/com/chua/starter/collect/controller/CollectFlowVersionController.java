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

    /**
     * 查询版本历史。
     *
     * @param flowId 流程 ID
     * @return 版本历史列表
     */
    @Operation(summary = "查询版本历史")
    @GetMapping("/list")
    public ReturnResult<List<CollectFlowVersion>> list(@RequestParam Integer flowId) {
        return ReturnResult.ok(versionService.listByFlow(flowId));
    }

    /**
     * 保存版本快照。
     *
     * @param body 请求体（flowId / flowDsl / remark）
     * @return 新版本号
     */
    @Operation(summary = "保存版本快照")
    @PostMapping("/save")
    public ReturnResult<Integer> save(@RequestBody Map<String, Object> body) {
        Integer flowId = asInt(body.get("flowId"));
        String flowDsl = body.get("flowDsl") == null ? null : String.valueOf(body.get("flowDsl"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        int version = versionService.saveVersion(flowId, flowDsl, remark);
        return ReturnResult.ok(version);
    }

    /**
     * 回滚到指定版本。
     *
     * @param flowId    流程 ID
     * @param versionNo 目标版本号
     * @return 是否成功
     */
    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback")
    public ReturnResult<Boolean> rollback(@RequestParam Integer flowId,
                                          @RequestParam Integer versionNo) {
        return ReturnResult.ok(versionService.rollback(flowId, versionNo));
    }

    /**
     * 对比两个版本。
     *
     * @param flowId 流程 ID
     * @param v1     版本一
     * @param v2     版本二
     * @return 两版本 DSL 对比结果
     */
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

    /**
     * 请求值转 int（null 返回 null）。
     *
     * @param value 请求值
     * @return int 值（null 时为 null）
     */
    private Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value));
    }
}