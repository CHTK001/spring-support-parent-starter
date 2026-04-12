package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.JdbcCatalogNode;
import com.chua.starter.panel.model.JdbcConnectionMetadata;
import com.chua.starter.panel.model.JdbcQueryResult;
import com.chua.starter.panel.model.JdbcTableStructure;
import com.chua.starter.panel.model.PanelAiSqlRequest;
import com.chua.starter.panel.model.PanelCapabilitySnapshot;
import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionDescriptor;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.model.PanelRemarkRequest;
import com.chua.starter.panel.model.PanelRemarkView;
import com.chua.starter.panel.model.PanelSqlTemplateRequest;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.PanelAiService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.service.PanelDocumentService;
import com.chua.starter.panel.service.PanelRemarkService;
import com.chua.starter.panel.service.PanelSqlTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JDBC 面板控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/jdbc")
public class PanelJdbcController {

    private final PanelConnectionService panelConnectionService;
    private final JdbcPanelService jdbcPanelService;
    private final PanelDocumentService panelDocumentService;
    private final PanelAiService panelAiService;
    private final PanelRemarkService panelRemarkService;
    private final PanelSqlTemplateService panelSqlTemplateService;

    @PostMapping("/connection/open")
    public ReturnResult<PanelConnectionHandle> openConnection(@RequestBody PanelConnectionDefinition definition) {
        return ReturnResult.ok(panelConnectionService.open(definition));
    }

    @GetMapping("/connection/cache")
    public ReturnResult<List<PanelConnectionDescriptor>> cachedConnections() {
        return ReturnResult.ok(panelConnectionService.listCachedConnections());
    }

    @DeleteMapping("/connection/{connectionId}")
    public ReturnResult<Boolean> closeConnection(@PathVariable String connectionId) {
        panelConnectionService.evict(connectionId);
        return ReturnResult.ok(Boolean.TRUE);
    }

    @GetMapping("/{connectionId}/catalog")
    public ReturnResult<List<JdbcCatalogNode>> catalogTree(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.listCatalogTree(connectionId));
    }

    @GetMapping("/{connectionId}/search")
    public ReturnResult<List<JdbcCatalogNode>> search(
            @PathVariable String connectionId,
            @RequestParam String keyword) {
        return ReturnResult.ok(jdbcPanelService.search(connectionId, keyword));
    }

    @GetMapping("/{connectionId}/structure")
    public ReturnResult<JdbcTableStructure> tableStructure(
            @PathVariable String connectionId,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String tableName) {
        return ReturnResult.ok(jdbcPanelService.tableStructure(connectionId, catalog, schema, tableName));
    }

    @GetMapping("/{connectionId}/metadata")
    public ReturnResult<JdbcConnectionMetadata> metadata(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.metadata(connectionId));
    }

    @GetMapping("/{connectionId}/remark")
    public ReturnResult<List<PanelRemarkView>> remarks(@PathVariable String connectionId) {
        return ReturnResult.ok(panelRemarkService.listByConnectionId(connectionId));
    }

    @PostMapping("/{connectionId}/remark")
    public ReturnResult<PanelRemarkView> saveRemark(
            @PathVariable String connectionId,
            @RequestBody PanelRemarkRequest request) {
        request.setPanelConnectionId(connectionId);
        return ReturnResult.ok(panelRemarkService.saveRemark(request));
    }

    @PostMapping("/{connectionId}/sql/template")
    public ReturnResult<String> sqlTemplate(
            @PathVariable String connectionId,
            @RequestBody PanelSqlTemplateRequest request) {
        request.setPanelConnectionId(connectionId);
        return ReturnResult.ok(panelSqlTemplateService.generateTemplate(connectionId, request));
    }

    @GetMapping("/{connectionId}/capabilities")
    public ReturnResult<PanelCapabilitySnapshot> capabilities(@PathVariable String connectionId) {
        return ReturnResult.ok(jdbcPanelService.capabilities(connectionId));
    }

    @GetMapping("/{connectionId}/document")
    public ReturnResult<String> tableDocument(
            @PathVariable String connectionId,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String tableName) {
        return ReturnResult.ok(panelDocumentService.buildJdbcTableDocument(connectionId, catalog, schema, tableName));
    }

    @GetMapping("/{connectionId}/ai/structure")
    public ReturnResult<String> explainStructure(
            @PathVariable String connectionId,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String tableName) {
        return ReturnResult.ok(panelAiService.explainJdbcStructure(connectionId, catalog, schema, tableName));
    }

    @PostMapping("/{connectionId}/ai/sql")
    public ReturnResult<String> explainSql(
            @PathVariable String connectionId,
            @RequestBody String sql) {
        return ReturnResult.ok(panelAiService.explainSql(connectionId, sql));
    }

    @PostMapping("/{connectionId}/ai/sql/generate")
    public ReturnResult<String> generateSql(
            @PathVariable String connectionId,
            @RequestBody PanelAiSqlRequest request) {
        return ReturnResult.ok(panelAiService.generateSql(connectionId, request));
    }

    @PostMapping("/{connectionId}/execute")
    public ReturnResult<JdbcQueryResult> execute(
            @PathVariable String connectionId,
            @RequestBody String sql) {
        return ReturnResult.ok(jdbcPanelService.execute(connectionId, sql));
    }
}
