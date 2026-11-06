package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectVariable;
import com.chua.starter.collect.service.CollectVariableService;
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
 * 全局变量接口。
 *
 * <p>加密变量只回掩码，回传掩码表示不修改原值。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-全局变量")
@RestController
@RequestMapping("/v2/collect/variable")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectVariableController {

    /**
     * 变量服务。
     */
    private final CollectVariableService variableService;

    /**
     * 变量列表，密文值脱敏。
     *
     * @return 变量列表
     */
    @Operation(summary = "变量列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectVariable>> list() {
        return ReturnResult.ok(variableService.listMasked());
    }

    /**
     * 新增变量。
     *
     * @param variable 变量
     * @return 保存后的变量
     */
    @Operation(summary = "新增变量")
    @PostMapping("/save")
    public ReturnResult<CollectVariable> save(@RequestBody CollectVariable variable) {
        return ReturnResult.ok(variableService.addVariable(variable));
    }

    /**
     * 修改变量。
     *
     * @param variable 变量
     * @return 是否成功
     */
    @Operation(summary = "修改变量")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody CollectVariable variable) {
        return ReturnResult.ok(variableService.editVariable(variable));
    }

    /**
     * 删除变量。
     *
     * @param variableId 变量ID
     * @return 是否成功
     */
    @Operation(summary = "删除变量")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer variableId) {
        return ReturnResult.ok(variableService.deleteVariable(variableId));
    }
}
