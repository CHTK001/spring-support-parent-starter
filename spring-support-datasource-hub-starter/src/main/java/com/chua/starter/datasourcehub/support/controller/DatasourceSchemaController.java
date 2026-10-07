package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.FieldMappingVO;
import com.chua.starter.datasourcehub.support.model.SchemaColumnVO;
import com.chua.starter.datasourcehub.support.model.SchemaTableVO;
import com.chua.starter.datasourcehub.support.model.TableDdlVO;
import com.chua.starter.datasourcehub.support.model.dto.FieldMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.TableDdlDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceSchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源结构与 DDL 接口：库表浏览、建表、字段映射。
 *
 * <p>这些能力归数据源模块：采集的入库节点、监控的库表浏览都调这里，
 * 不再各自实现一份表结构读取与建表逻辑。</p>
 *
 * <p>路径说明：{@code /tables}、{@code /columns} 与台账的 {@code /{id}} 同级，
 * Spring 的字面量路径优先于变量路径，不会互相抢占。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据源中心-结构与DDL", description = "库表浏览、建表与字段映射")
@RestController
@RequestMapping(DatasourceHubConstants.API_PREFIX)
@RequiredArgsConstructor
public class DatasourceSchemaController {

    /**
     * 数据源结构服务。
     */
    private final DatasourceSchemaService schemaService;

    /**
     * 数据源表清单（含视图）。
     *
     * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
     * @return 表名清单
     */
    @Operation(summary = "数据源表清单")
    @GetMapping("/tables")
    public ReturnResult<List<String>> tables(@RequestParam(required = false) String datasource) {
        return ReturnResult.ok(schemaService.listTables(datasource));
    }

    /**
     * 数据源表清单（含表备注与类型）。
     *
     * <p>老 {@code /tables} 只给表名串（保留兼容）；前端三联选要显示中文备注时调这里，
     * 返回「表名 + 表备注 + TABLE/VIEW」，label/subtitle 直接渲染。</p>
     *
     * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
     * @return 表元数据
     */
    @Operation(summary = "数据源表清单（含备注）")
    @GetMapping("/table-infos")
    public ReturnResult<List<SchemaTableVO>> tableInfos(@RequestParam(required = false) String datasource) {
        return ReturnResult.ok(schemaService.listTableInfos(datasource));
    }

    /**
     * 表列元数据。
     *
     * @param datasource 数据源引用
     * @param table      表名
     * @return 列元数据
     */
    @Operation(summary = "数据源表列元数据")
    @GetMapping("/columns")
    public ReturnResult<List<SchemaColumnVO>> columns(@RequestParam(required = false) String datasource,
                                                      @RequestParam String table) {
        return ReturnResult.ok(schemaService.listColumns(datasource, table));
    }

    /**
     * 表是否存在。
     *
     * @param datasource 数据源引用
     * @param table      表名
     * @return true=存在
     */
    @Operation(summary = "判断表是否存在")
    @GetMapping("/table-exists")
    public ReturnResult<Boolean> tableExists(@RequestParam(required = false) String datasource,
                                             @RequestParam String table) {
        return ReturnResult.ok(schemaService.tableExists(datasource, table));
    }

    /**
     * 预览建表 / 补列 DDL。
     *
     * @param request 建表请求
     * @return 预览结果
     */
    @Operation(summary = "预览建表DDL")
    @PostMapping("/ddl/preview")
    public ReturnResult<TableDdlVO> preview(@RequestBody TableDdlDTO request) {
        return ReturnResult.ok(schemaService.previewDdl(request));
    }

    /**
     * 执行建表 / 补列。
     *
     * @param request 建表请求
     * @return 执行结果
     */
    @Operation(summary = "执行建表DDL")
    @PostMapping("/ddl/execute")
    public ReturnResult<TableDdlVO> execute(@RequestBody TableDdlDTO request) {
        return ReturnResult.ok(schemaService.executeDdl(request));
    }

    /**
     * 解析字段映射。
     *
     * @param request 映射请求
     * @return 每个来源字段的映射结论
     */
    @Operation(summary = "解析字段映射")
    @PostMapping("/mapping/resolve")
    public ReturnResult<List<FieldMappingVO>> resolve(@RequestBody FieldMappingDTO request) {
        return ReturnResult.ok(schemaService.resolveMapping(request));
    }
}
