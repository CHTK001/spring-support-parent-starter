// [P3C A20 豁免] A20/A30/A31: 日志/SQL 字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasource.runtime.calcite.CalciteFederationManager;
import com.chua.starter.datasource.runtime.model.DsCalciteFederation;
import com.chua.starter.datasource.runtime.model.DsShardingRule;
import com.chua.starter.datasource.runtime.sharding.ShardingPhysicalTableService;
import com.chua.starter.datasource.runtime.sharding.ShardingRuleManager;
import com.chua.starter.datasource.runtime.store.DsRuntimeConfigStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源运行时管理接口（/ms/ds-runtime/**）。
 *
 * <p>提供 Calcite 联邦与分库分表规则的运行期 CRUD、即时刷新（不重启生效）、
 * SQL 试跑与分片物理表受限 DDL（补建 / 追加列）的预览与执行。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/ms/ds-runtime")
@RequiredArgsConstructor
public class DsRuntimeController {

    private final CalciteFederationManager federationManager;
    private final ShardingRuleManager shardingRuleManager;
    private final ShardingPhysicalTableService physicalTableService;
    private final DsRuntimeConfigStore store;

    // ==================== 概览 ====================

    /**
     * 运行时概览：启用配置数、生效成员/规则与最近刷新状态。
     */
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            List<DsCalciteFederation> federations = store.findAllFederations();
            List<DsShardingRule> rules = store.findAllShardingRules();
            result.put("federationTotal", federations.size());
            result.put("federationEnabled", federations.stream().filter(f -> f.getEnabled() != null && f.getEnabled() == 1).count());
            result.put("shardingTotal", rules.size());
            result.put("shardingEnabled", rules.stream().filter(r -> r.getEnabled() != null && r.getEnabled() == 1).count());
            result.put("calciteLastRefreshAt", federationManager.lastRefreshAt());
            result.put("calciteLastRefreshResult", federationManager.lastRefreshResult());
            result.put("shardingLastRefreshAt", shardingRuleManager.lastRefreshAt());
            result.put("shardingLastRefreshResult", shardingRuleManager.lastRefreshResult());
            result.put("activeCalciteMembers", federationManager.activeMemberCount());
            result.put("activeShardingTables", shardingRuleManager.activeRules().size());
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    // ==================== Calcite 联邦 ====================

    /**
     * 联邦配置清单（含停用）。
     */
    @GetMapping("/calcite")
    public ReturnResult<List<DsCalciteFederation>> listFederations() {
        try {
            return ReturnResult.ok(store.findAllFederations());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 保存联邦配置并立即刷新（运行期生效，不重启）。
     */
    @PostMapping("/calcite")
    public ReturnResult<DsCalciteFederation> saveFederation(@RequestBody DsCalciteFederation federation) {
        try {
            return ReturnResult.ok(federationManager.saveAndRefresh(federation));
        } catch (Exception e) {
            log.warn("[ds-runtime] 保存联邦配置失败: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除联邦配置并立即刷新。
     */
    @DeleteMapping("/calcite/{id}")
    public ReturnResult<Void> deleteFederation(@PathVariable("id") Long id) {
        try {
            federationManager.deleteAndRefresh(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 手动刷新全部联邦配置。
     */
    @PostMapping("/calcite/refresh")
    public ReturnResult<Map<String, Object>> refreshFederations() {
        try {
            List<String> schemas = federationManager.refresh();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("schemas", schemas);
            result.put("refreshAt", federationManager.lastRefreshAt());
            result.put("message", federationManager.lastRefreshResult());
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 联邦 SQL 试跑（仅 SELECT/WITH/SHOW/EXPLAIN）。
     */
    @PostMapping("/calcite/query")
    public ReturnResult<Map<String, Object>> queryFederation(@RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(federationManager.query(body == null ? null : body.get("sql")));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    // ==================== 分库分表 ====================

    /**
     * 分片规则清单（含停用）。
     */
    @GetMapping("/sharding")
    public ReturnResult<List<DsShardingRule>> listShardingRules() {
        try {
            return ReturnResult.ok(store.findAllShardingRules());
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 保存分片规则并立即刷新（运行期生效，不重启）。
     */
    @PostMapping("/sharding")
    public ReturnResult<DsShardingRule> saveShardingRule(@RequestBody DsShardingRule rule) {
        try {
            return ReturnResult.ok(shardingRuleManager.saveAndRefresh(rule));
        } catch (Exception e) {
            log.warn("[ds-runtime] 保存分片规则失败: {}", e.getMessage());
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除分片规则并立即刷新。
     */
    @DeleteMapping("/sharding/{id}")
    public ReturnResult<Void> deleteShardingRule(@PathVariable("id") Long id) {
        try {
            shardingRuleManager.deleteAndRefresh(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 手动刷新全部分片规则。
     */
    @PostMapping("/sharding/refresh")
    public ReturnResult<Map<String, Object>> refreshSharding() {
        try {
            List<String> tables = shardingRuleManager.refresh();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("logicTables", tables);
            result.put("refreshAt", shardingRuleManager.lastRefreshAt());
            result.put("message", shardingRuleManager.lastRefreshResult());
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 分片 SQL 试跑（SELECT 类）。
     */
    @PostMapping("/sharding/query")
    public ReturnResult<Map<String, Object>> querySharding(@RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(shardingRuleManager.query(body == null ? null : body.get("sql")));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    // ==================== 分片物理表（受限 DDL） ====================

    /**
     * 查看某规则在各分库中已存在的物理表。
     */
    @GetMapping("/tables/{ruleId}")
    public ReturnResult<Map<String, List<String>>> listPhysicalTables(@PathVariable("ruleId") Long ruleId) {
        try {
            DsShardingRule rule = requireRule(ruleId);
            return ReturnResult.ok(physicalTableService.listPhysicalTables(rule));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 补建分片表 DDL 预览（不执行）。
     */
    @PostMapping("/tables/preview-create")
    public ReturnResult<List<Map<String, Object>>> previewCreate(@RequestBody Map<String, Object> body) {
        try {
            DsShardingRule rule = requireRule(longValue(body.get("ruleId")));
            return ReturnResult.ok(physicalTableService.previewCreate(rule, strValue(body.get("sampleDdl"))));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 执行补建分片表（幂等 CREATE TABLE IF NOT EXISTS）。
     */
    @PostMapping("/tables/apply-create")
    public ReturnResult<List<Map<String, Object>>> applyCreate(@RequestBody Map<String, Object> body) {
        try {
            DsShardingRule rule = requireRule(longValue(body.get("ruleId")));
            return ReturnResult.ok(physicalTableService.applyCreate(rule, strValue(body.get("sampleDdl"))));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 追加列 DDL 预览（不执行；已存在同名列标记 skipped）。
     */
    @PostMapping("/tables/preview-add-column")
    public ReturnResult<List<Map<String, Object>>> previewAddColumn(@RequestBody Map<String, Object> body) {
        try {
            DsShardingRule rule = requireRule(longValue(body.get("ruleId")));
            return ReturnResult.ok(physicalTableService.previewAddColumn(rule, strValue(body.get("columnDdl"))));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 执行追加列（已存在同名列自动跳过）。
     */
    @PostMapping("/tables/apply-add-column")
    public ReturnResult<List<Map<String, Object>>> applyAddColumn(@RequestBody Map<String, Object> body) {
        try {
            DsShardingRule rule = requireRule(longValue(body.get("ruleId")));
            return ReturnResult.ok(physicalTableService.applyAddColumn(rule, strValue(body.get("columnDdl"))));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    private DsShardingRule requireRule(Long ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId 不能为空");
        }
        DsShardingRule rule = store.findShardingRuleById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("分片规则不存在: " + ruleId);
        }
        return rule;
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static String strValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
