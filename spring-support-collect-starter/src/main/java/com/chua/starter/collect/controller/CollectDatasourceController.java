package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectDatasource;
import com.chua.starter.collect.service.CollectDatasourceService;
import com.chua.starter.collect.support.CollectJdbcSupport;
import com.chua.starter.collect.vo.CollectColumnVO;
import com.chua.starter.collect.vo.CollectDatasourceVO;
import com.chua.starter.collect.vo.CollectOptionVO;
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
 * 采集数据源接口。
 *
 * <p>列表与详情永不回传密码，只回是否已设置；连接测试允许用未保存的配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-数据源")
@RestController
@RequestMapping("/v2/collect/datasource")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectDatasourceController {

    /**
     * 数据源服务。
     */
    private final CollectDatasourceService datasourceService;

    /**
     * 数据源连接与元数据读取。
     */
    private final CollectJdbcSupport jdbcSupport;

    /**
     * 数据源列表。
     *
     * @return 数据源列表
     */
    @Operation(summary = "数据源列表")
    @GetMapping("/list")
    public ReturnResult<List<CollectDatasourceVO>> list() {
        return ReturnResult.ok(datasourceService.listDatasources());
    }

    /**
     * 数据源下拉选项，首项为应用主库。
     *
     * @return 选项列表
     */
    @Operation(summary = "数据源下拉选项")
    @GetMapping("/options")
    public ReturnResult<List<CollectOptionVO>> options() {
        return ReturnResult.ok(datasourceService.options());
    }

    /**
     * 新增数据源。
     *
     * @param datasource 数据源
     * @return 保存后的数据源
     */
    @Operation(summary = "新增数据源")
    @PostMapping("/save")
    public ReturnResult<CollectDatasourceVO> save(@RequestBody CollectDatasource datasource) {
        return ReturnResult.ok(datasourceService.addDatasource(datasource));
    }

    /**
     * 修改数据源，密码留掩码表示不改。
     *
     * @param datasource 数据源
     * @return 是否成功
     */
    @Operation(summary = "修改数据源")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody CollectDatasource datasource) {
        return ReturnResult.ok(datasourceService.editDatasource(datasource));
    }

    /**
     * 删除数据源，被流程引用时拒绝。
     *
     * @param datasourceId 数据源ID
     * @return 是否成功
     */
    @Operation(summary = "删除数据源")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer datasourceId) {
        return ReturnResult.ok(datasourceService.deleteDatasource(datasourceId));
    }

    /**
     * 测试连接，返回数据库产品信息。
     *
     * @param datasource 数据源
     * @return 数据库产品信息
     */
    @Operation(summary = "测试连接")
    @PostMapping("/test")
    public ReturnResult<String> test(@RequestBody CollectDatasource datasource) {
        return ReturnResult.ok(datasourceService.testConnection(datasource));
    }

    /**
     * 列出数据源里的表与视图，供入库节点选择目标表。
     *
     * @param datasource 数据源引用，留空或 {@code app} 表示应用主库
     * @return 表名列表
     */
    @Operation(summary = "数据源表清单")
    @GetMapping("/tables")
    public ReturnResult<List<String>> tables(@RequestParam(required = false) String datasource) {
        return ReturnResult.ok(jdbcSupport.listTables(datasource));
    }

    /**
     * 列出指定表的列元数据，供入库节点配置字段映射。
     *
     * @param datasource 数据源引用，留空或 {@code app} 表示应用主库
     * @param table      表名
     * @return 列元数据
     */
    @Operation(summary = "数据源表列元数据")
    @GetMapping("/columns")
    public ReturnResult<List<CollectColumnVO>> columns(@RequestParam(required = false) String datasource,
                                                       @RequestParam String table) {
        return ReturnResult.ok(jdbcSupport.listColumns(datasource, table));
    }
}
