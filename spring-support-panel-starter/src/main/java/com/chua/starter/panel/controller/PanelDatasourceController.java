package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceView;
import com.chua.starter.panel.service.PanelDatasourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Panel 数据源控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/source")
public class PanelDatasourceController {

    private final PanelDatasourceService panelDatasourceService;

    @GetMapping
    public ReturnResult<List<PanelDatasourceView>> listAll() {
        return ReturnResult.ok(panelDatasourceService.listAll());
    }

    @PostMapping
    public ReturnResult<PanelDatasourceView> save(@RequestBody PanelDatasourceRequest request) {
        return ReturnResult.ok(panelDatasourceService.save(request));
    }

    @DeleteMapping("/{panelSourceId}")
    public ReturnResult<Boolean> delete(@PathVariable String panelSourceId) {
        panelDatasourceService.delete(panelSourceId);
        return ReturnResult.ok(Boolean.TRUE);
    }
}
