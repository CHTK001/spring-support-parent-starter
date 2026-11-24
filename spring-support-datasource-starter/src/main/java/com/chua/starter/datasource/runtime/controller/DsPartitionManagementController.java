// [P3C A20 豁免] A20/A30/A31: 日志/SQL 字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasource.runtime.partition.DsPartitionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分区路由运行期管理接口（/ms/ds-runtime/partition/**）。
 *
 * <p>只读暴露分区路由配置与实际分区表；日期范围预览为纯静态推算 +
 * 元数据存在性核对，不执行任何 DDL/DML。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/ms/ds-runtime/partition")
@RequiredArgsConstructor
public class DsPartitionManagementController {

    private final DsPartitionManagementService managementService;

    /**
     * 分区配置与实际分区表盘点（pay_partition_config 优先，回退 information_schema）。
     */
    @GetMapping("/tables")
    public ReturnResult<Map<String, Object>> tables() {
        try {
            return ReturnResult.ok(managementService.tables());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 按日期范围推算分区表名并核对存在性（不执行 DDL/DML）。
     *
     * <p>body 传 <code>prefix</code> / <code>granularity</code>（MONTH|DAY）/
     * <code>start</code> / <code>end</code>（yyyy-MM-dd）。</p>
     */
    @PostMapping("/preview")
    public ReturnResult<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        try {
            return ReturnResult.ok(managementService.preview(body));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}
