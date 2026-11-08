package com.chua.starter.httpman.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.httpman.entity.MsHttpmanCollection;
import com.chua.starter.httpman.entity.MsHttpmanHistory;
import com.chua.starter.httpman.entity.MsHttpmanRequest;
import com.chua.starter.httpman.entity.MsHttpmanVariable;
import com.chua.starter.httpman.model.dto.HttpmanHistoryQuery;
import com.chua.starter.httpman.model.dto.HttpmanRequestSave;
import com.chua.starter.httpman.model.dto.HttpmanVariableSave;
import com.chua.starter.httpman.service.HttpmanService;
import com.chua.starter.mybatis.entity.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

/**
 * 接口调试控制台 - REST 入口
 * <p>与前端约定前缀 {@code /ms/httpman}，通过 {@code HttpmanService} 管理集合/请求/历史/变量，
 * 以及由 {@code execution} 转入代理执行。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "HttpMan 接口调试控制台", description = "集合/请求/历史/变量管理与代理执行")
@RestController
@RequestMapping("/ms/httpman")
@RequiredArgsConstructor
public class HttpmanController {

    private final HttpmanService httpmanService;

    // ==================== 集合 ====================

    @Operation(summary = "集合树")
    @GetMapping("/collections")
    public ReturnResult<List<MsHttpmanCollection>> collections() {
        return ReturnResult.ok(httpmanService.listCollections());
    }

    @Operation(summary = "新增集合")
    @PostMapping("/collections")
    public ReturnResult<MsHttpmanCollection> addCollection(@RequestBody MsHttpmanCollection collection) {
        return ReturnResult.ok(httpmanService.createCollection(collection));
    }

    @Operation(summary = "修改集合")
    @PutMapping("/collections/{id}")
    public ReturnResult<MsHttpmanCollection> updateCollection(@RequestBody MsHttpmanCollection collection) {
        return ReturnResult.ok(httpmanService.updateCollection(collection));
    }

    @Operation(summary = "删除集合")
    @DeleteMapping("/collections/{id}")
    public ReturnResult<Void> deleteCollection(@PathVariable String id) {
        httpmanService.deleteCollection(id);
        return ReturnResult.ok();
    }

    // ==================== 请求 ====================

    @Operation(summary = "请求列表")
    @GetMapping("/requests")
    public ReturnResult<List<MsHttpmanRequest>> requests(@RequestParam(required = false) String collectionId,
                                                          @RequestParam(required = false) String parentId) {
        return ReturnResult.ok(httpmanService.listRequests(collectionId, parentId));
    }

    @Operation(summary = "新增请求")
    @PostMapping("/requests")
    public ReturnResult<MsHttpmanRequest> addRequest(@RequestBody HttpmanRequestSave save) {
        return ReturnResult.ok(httpmanService.saveRequest(save));
    }

    @Operation(summary = "修改请求")
    @PutMapping("/requests/{id}")
    public ReturnResult<MsHttpmanRequest> updateRequest(@PathVariable String id, @RequestBody HttpmanRequestSave save) {
        return ReturnResult.ok(httpmanService.updateRequest(id, save));
    }

    @Operation(summary = "请求详情")
    @GetMapping("/requests/{id}")
    public ReturnResult<MsHttpmanRequest> requestDetail(@PathVariable String id) {
        return ReturnResult.ok(httpmanService.getRequest(id));
    }

    @Operation(summary = "删除请求")
    @DeleteMapping("/requests/{id}")
    public ReturnResult<Void> deleteRequest(@PathVariable String id) {
        httpmanService.deleteRequest(id);
        return ReturnResult.ok();
    }

    // ==================== 执行 ====================

    @Operation(summary = "执行请求", description = "执行一条已存储的请求并记录运行历史")
    @PostMapping("/requests/{id}/execute")
    public ReturnResult<Map<String, Object>> execute(@PathVariable String id,
                                                      @RequestBody(required = false) MsHttpmanRequest override) {
        MsHttpmanRequest request = httpmanService.getRequest(id);
        if (request == null) {
            return ReturnResult.error("请求不存在: " + id);
        }
        if (override != null) {
            // 前端选项：临时覆盖本次执行的地址/正文（不落库）
            request.setRequestUrl(override.getRequestUrl());
            request.setRequestBody(override.getRequestBody());
            request.setRequestBodyType(override.getRequestBodyType());
            request.setRequestMethod(override.getRequestMethod());
        }
        return httpmanService.executeAndRecord(request, request.getRequestCollectionId());
    }

    @Operation(summary = "执行请求（不记录历史）")
    @PostMapping("/requests/{id}/test")
    public ReturnResult<Map<String, Object>> test(@PathVariable String id) {
        MsHttpmanRequest request = httpmanService.getRequest(id);
        if (request == null) {
            return ReturnResult.error("请求不存在: " + id);
        }
        return httpmanService.execute(request);
    }

    // ==================== 历史 ====================

    @Operation(summary = "历史分页")
    @GetMapping("/history")
    public ReturnResult<PageResult<Map<String, Object>>> history(HttpmanHistoryQuery query) {
        return ReturnResult.ok(httpmanService.pageHistory(query));
    }

    @Operation(summary = "历史详情")
    @GetMapping("/history/{id}")
    public ReturnResult<Map<String, Object>> historyDetail(@PathVariable String id) {
        return ReturnResult.ok(httpmanService.getHistory(id));
    }

    @Operation(summary = "删除历史")
    @DeleteMapping("/history/{id}")
    public ReturnResult<Void> deleteHistory(@PathVariable String id) {
        httpmanService.deleteHistory(id);
        return ReturnResult.ok();
    }

    @Operation(summary = "清理历史")
    @PostMapping("/history/clean")
    public ReturnResult<Void> cleanHistory(@RequestParam(defaultValue = "30") int retentionDays) {
        httpmanService.cleanHistory(retentionDays);
        return ReturnResult.ok();
    }

    // ==================== 变量 ====================

    @Operation(summary = "变量列表")
    @GetMapping("/variables")
    public ReturnResult<List<MsHttpmanVariable>> variables(@RequestParam(required = false) String scope,
                                                           @RequestParam(required = false) String collectionId) {
        return ReturnResult.ok(httpmanService.listVariables(scope, collectionId));
    }

    @Operation(summary = "新增变量")
    @PostMapping("/variables")
    public ReturnResult<MsHttpmanVariable> addVariable(@RequestBody HttpmanVariableSave save) {
        MsHttpmanVariable variable = new MsHttpmanVariable();
        variable.setVariableScope(save.getScope());
        variable.setVariableCollectionId(save.getCollectionId());
        variable.setVariableName(save.getName());
        variable.setVariableValue(save.getValue());
        variable.setVariableRemark(save.getRemark());
        return ReturnResult.ok(httpmanService.createVariable(variable));
    }

    @Operation(summary = "修改变量")
    @PutMapping("/variables/{id}")
    public ReturnResult<MsHttpmanVariable> updateVariable(@PathVariable String id, @RequestBody HttpmanVariableSave save) {
        MsHttpmanVariable variable = new MsHttpmanVariable();
        variable.setVariableId(id);
        variable.setVariableScope(save.getScope());
        variable.setVariableCollectionId(save.getCollectionId());
        variable.setVariableName(save.getName());
        variable.setVariableValue(save.getValue());
        variable.setVariableRemark(save.getRemark());
        return ReturnResult.ok(httpmanService.updateVariable(variable));
    }

    @Operation(summary = "删除变量")
    @DeleteMapping("/variables/{id}")
    public ReturnResult<Void> deleteVariable(@PathVariable String id) {
        httpmanService.deleteVariable(id);
        return ReturnResult.ok();
    }
}
