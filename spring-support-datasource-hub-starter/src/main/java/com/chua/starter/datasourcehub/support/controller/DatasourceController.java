package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.SysLog;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
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

/**
 * 数据源中心接口：数据源台账 CRUD、类型枚举与连通性测试。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据源中心", description = "数据源台账与连通性")
@RestController
@RequestMapping(DatasourceHubConstants.API_PREFIX)
@RequiredArgsConstructor
public class DatasourceController {

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 数据源列表。
     *
     * @return 数据源列表
     */
    @Operation(summary = "获取所有数据源")
    @GetMapping({"", "/list"})
    public ReturnResult<List<DataSourceVO>> list() {
        return datasourceService.list();
    }

    /**
     * 数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @Operation(summary = "获取数据源详情")
    @GetMapping("/{id}")
    public ReturnResult<DataSourceVO> get(@PathVariable Long id) {
        return datasourceService.get(id);
    }

    /**
     * 新增数据源。
     *
     * @param data 数据源配置
     * @return 新增后的数据源
     */
    @Operation(summary = "新增数据源")
    @PostMapping
    public ReturnResult<DataSourceVO> create(@RequestBody DataSourceDTO data) {
        return datasourceService.create(data);
    }

    /**
     * 更新数据源。
     *
     * @param id   数据源 ID
     * @param data 数据源配置
     * @return 更新后的数据源
     */
    @SysLog(name = "更新数据源")
    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    public ReturnResult<DataSourceVO> update(@PathVariable Long id, @RequestBody DataSourceDTO data) {
        return datasourceService.update(id, data);
    }

    /**
     * 删除数据源。
     *
     * @param id 数据源 ID
     * @return 删除结果
     */
    @SysLog(name = "删除数据源")
    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return datasourceService.delete(id);
    }

    /**
     * 置顶 / 取消置顶数据源。
     *
     * <p>置顶是台账上的用户偏好：置顶项在左栏列表与全量接口里排在前面，
     * 不影响连通性判定与监控指标。幂等操作，重复置顶同一数据源不报错。</p>
     *
     * @param id  数据源 ID
     * @param top true 置顶、false 取消置顶
     * @return 更新后的数据源
     */
    @SysLog(name = "置顶/取消置顶数据源")
    @Operation(summary = "置顶/取消置顶数据源")
    @PostMapping("/{id}/top")
    public ReturnResult<DataSourceVO> toggleTop(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean top) {
        return datasourceService.toggleTop(id, top);
    }

    /**
     * 测试数据源连通性。
     *
     * @param id 数据源 ID
     * @return 测试结果
     */
    @SysLog(name = "测试数据源连接")
    @Operation(summary = "测试数据源连接")
    @PostMapping("/{id}/test")
    public ReturnResult<DataSourceTestVO> test(@PathVariable Long id) {
        return datasourceService.test(id);
    }

    /**
     * 支持的数据源类型。
     *
     * @return 类型列表
     */
    @Operation(summary = "获取支持的数据源类型")
    @GetMapping("/types")
    public ReturnResult<List<String>> types() {
        return ReturnResult.ok(datasourceService.supportedTypes());
    }
}