package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceTestView;
import com.chua.starter.panel.model.PanelDriverUploadView;
import com.chua.starter.panel.model.PanelDatasourceView;
import com.chua.starter.panel.service.PanelDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Panel 数据源控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/source")
@Tag(name = "Panel Datasource", description = "Panel 数据源管理接口")
public class PanelDatasourceController {

    private final PanelDatasourceService panelDatasourceService;

    /**
     * 查询全部数据源。
     *
     * @return 数据源列表
     */
    @Operation(summary = "查询数据源列表")
    @GetMapping
    public ReturnResult<List<PanelDatasourceView>> listAll() {
        return ReturnResult.ok(panelDatasourceService.listAll());
    }

    /**
     * 保存数据源。
     *
     * @param request 数据源参数
     * @return 保存后的数据源
     */
    @Operation(summary = "保存数据源")
    @PostMapping
    public ReturnResult<PanelDatasourceView> save(@Parameter(description = "数据源保存参数") @RequestBody PanelDatasourceRequest request) {
        return ReturnResult.ok(panelDatasourceService.save(request));
    }

    /**
     * 测试数据源连通性。
     *
     * @param request 数据源参数
     * @return 测试结果
     */
    @Operation(summary = "测试数据源")
    @PostMapping("/test")
    public ReturnResult<PanelDatasourceTestView> test(@Parameter(description = "数据源测试参数") @RequestBody PanelDatasourceRequest request) {
        return ReturnResult.ok(panelDatasourceService.test(request));
    }

    /**
     * 上传数据源驱动文件。
     *
     * @param panelDialectType 方言类型
     * @param file 驱动文件
     * @return 上传结果
     */
    @Operation(summary = "上传驱动")
    @PostMapping(value = "/driver/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<PanelDriverUploadView> uploadDriver(
            @Parameter(description = "方言类型") @RequestParam(required = false) String panelDialectType,
            @Parameter(description = "驱动文件") @RequestParam("file") MultipartFile file) {
        return ReturnResult.ok(panelDatasourceService.uploadDriver(panelDialectType, file));
    }

    /**
     * 删除指定数据源。
     *
     * @param panelSourceId 数据源主键
     * @return 删除结果
     */
    @Operation(summary = "删除数据源")
    @DeleteMapping("/{panelSourceId}")
    public ReturnResult<Boolean> delete(@Parameter(description = "数据源主键") @PathVariable String panelSourceId) {
        panelDatasourceService.delete(panelSourceId);
        return ReturnResult.ok(Boolean.TRUE);
    }
}
