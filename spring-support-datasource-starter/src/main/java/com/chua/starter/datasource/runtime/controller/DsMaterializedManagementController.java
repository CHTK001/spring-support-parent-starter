// [P3C A20 豁免] A20/A30/A31: 日志/描述字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasource.runtime.materialized.DsMaterializedManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SQL 物理化运行期管理接口（/ms/ds-runtime/materialized/**）。
 *
 * <p>只读暴露物理化路由的配置、注解声明点与缓存数据源；刷新接口如实返回
 * 当前实现的能力边界（写后刷新 no-op，不具备主动重建副本能力）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/ms/ds-runtime/materialized")
@RequiredArgsConstructor
public class DsMaterializedManagementController {

    private final DsMaterializedManagementService managementService;

    /**
     * 物理化配置快照（宿主未启用时 enabled=false）。
     */
    @GetMapping("/config")
    public ReturnResult<Map<String, Object>> config() {
        try {
            return ReturnResult.ok(managementService.config());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 容器中带 @MaterializedRoute / @MaterializedRefresh 的声明点清单。
     */
    @GetMapping("/routes")
    public ReturnResult<List<Map<String, Object>>> routes() {
        try {
            return ReturnResult.ok(managementService.routes());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 按前缀命中的物化缓存数据源清单。
     */
    @GetMapping("/caches")
    public ReturnResult<List<Map<String, Object>>> caches() {
        try {
            return ReturnResult.ok(managementService.caches());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 触发物化副本刷新（当前实现不具备该能力，如实返回 supported=false）。
     */
    @PostMapping("/refresh")
    public ReturnResult<Map<String, Object>> refresh() {
        try {
            return ReturnResult.ok(managementService.refresh());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}
