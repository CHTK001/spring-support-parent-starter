// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.support.DatasourceTypeInfoSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 类型专属控制器基类。
 *
 * <p>数据源中心的台账 CRUD 与通用连通性测试仍由 {@link DatasourceController} 承担；
 * 各类型控制器只负责「该类型才有的能力」，避免所有类型的分支堆在一个控制器里。</p>
 *
 * <p>子类只需给出自己负责的类型常量，其余（类型校验、测速、专属信息探测）复用这里的实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据源类型能力", description = "按类型划分的专属能力")
@RequiredArgsConstructor
public abstract class DatasourceTypeController {

    /**
     * 数据源服务
     */
    protected final DatasourceService datasourceService;

    /**
     * 本控制器负责的数据源类型。
     *
     * @return 类型标识，小写
     */
    protected abstract String type();

    /**
     * 类型专属连通性测试。
     *
     * <p>先校验数据源确属本类型，避免用 redis 控制器去测一个 mysql 数据源；
     * 测速与落库复用通用实现，保证延迟口径一致。</p>
     *
     * @param id 数据源 ID
     * @return 测试结果
     */
    @Operation(summary = "测试该类型数据源连接")
    public ReturnResult<DataSourceTestVO> test(Long id) {
        ReturnResult<DataSourceVO> found = datasourceService.get(id);
        if (!found.isOk() || found.getData() == null) {
            return ReturnResult.error(found.getMsg() == null ? "数据源不存在" : found.getMsg());
        }
        if (!type().equalsIgnoreCase(found.getData().type())) {
            return ReturnResult.error("数据源类型不匹配，期望 " + type() + "，实际 " + found.getData().type());
        }
        return datasourceService.test(id);
    }

    /**
     * 该类型的运行时信息（版本、模式、状态行等）。
     *
     * @param id 数据源 ID
     * @return 键值信息；类型不支持专属探针时返回空 Map
     */
    @Operation(summary = "读取该类型数据源的运行时信息")
    public ReturnResult<Map<String, String>> info(Long id) {
        ReturnResult<DataSourceVO> found = datasourceService.get(id);
        if (!found.isOk() || found.getData() == null) {
            return ReturnResult.error(found.getMsg() == null ? "数据源不存在" : found.getMsg());
        }
        DataSourceVO ds = found.getData();
        if (!type().equalsIgnoreCase(ds.type())) {
            return ReturnResult.error("数据源类型不匹配，期望 " + type() + "，实际 " + ds.type());
        }
        int port = ds.port() == null ? DatasourceHubConstants.DS_DEFAULT_PORTS.getOrDefault(type(), 0) : ds.port();
        return ReturnResult.ok(DatasourceTypeInfoSupport.read(type(), ds.host(), port, 2000));
    }
}