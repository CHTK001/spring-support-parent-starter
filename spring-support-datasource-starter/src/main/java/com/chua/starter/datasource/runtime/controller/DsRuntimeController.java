// [P3C A20 豁免] A20/A30/A31: 日志/SQL 字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasource.runtime.calcite.CalciteFederationManager;
import com.chua.starter.datasource.runtime.materialized.DsMaterializedManagementService;
import com.chua.starter.datasource.runtime.model.DsCalciteFederation;
import com.chua.starter.datasource.runtime.model.DsShardingRule;
import com.chua.starter.datasource.runtime.model.FederationMember;
import com.chua.starter.datasource.runtime.partition.DsPartitionManagementService;
import com.chua.starter.datasource.runtime.sharding.ShardingPhysicalTableService;
import com.chua.starter.datasource.runtime.sharding.ShardingRuleManager;
import com.chua.starter.datasource.runtime.store.DsRuntimeConfigStore;
import com.chua.starter.datasource.runtime.support.DsConnectionTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final DsConnectionTestService connectionTestService;
    private final DsMaterializedManagementService materializedManagementService;
    private final DsPartitionManagementService partitionManagementService;
    private final ObjectMapper objectMapper;

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
            // 概览下钻明细：生效联邦成员 schema 与生效分片逻辑表（去重）
            result.put("activeFederationSchemas", federationManager.activeFederations().stream()
                    .map(DsCalciteFederation::getMemberList)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .map(FederationMember::getSchemaName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.toList()));
            result.put("activeRuleTables", shardingRuleManager.activeRules().stream()
                    .map(DsShardingRule::getLogicTable)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.toList()));
            // 物理化 / 分区能力计数（只读盘点，失败降级不计入，不阻断概览）
            try {
                result.put("materializedRoutes", materializedManagementService.routes().size());
                result.put("materializedCaches", materializedManagementService.caches().size());
            } catch (Exception e) {
                log.debug("[ds-runtime] 物理化盘点失败（概览降级）: {}", e.getMessage());
            }
            try {
                Map<String, Object> partition = partitionManagementService.tables();
                result.put("partitionSources", partition.get("total"));
                long partitionTables = 0;
                if (partition.get("tables") instanceof List<?> rows) {
                    for (Object row : rows) {
                        if (row instanceof Map<?, ?> table && table.get("partitionTables") instanceof List<?> list) {
                            partitionTables += list.size();
                        }
                    }
                }
                result.put("partitionTables", partitionTables);
            } catch (Exception e) {
                log.debug("[ds-runtime] 分区盘点失败（概览降级）: {}", e.getMessage());
            }
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

    /**
     * 启用联邦的成员健康检查（逐成员真实建连 + SELECT 1，结果不含密码）。
     */
    @GetMapping("/calcite/health")
    public ReturnResult<List<Map<String, Object>>> federationHealth() {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (DsCalciteFederation federation : store.findEnabledFederations()) {
                List<FederationMember> members = federation.getMemberList() == null
                        ? List.of() : federation.getMemberList();
                for (FederationMember member : members) {
                    Map<String, Object> test = connectionTestService.test(member.getUrl(), member.getUsername(),
                            member.getPassword(), member.getDriverClassName());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("federationId", federation.getId());
                    row.put("federationSchema", federation.getSchemaName());
                    row.put("member", member.getSchemaName());
                    row.put("url", member.getUrl());
                    row.put("ok", test.get("ok"));
                    row.put("latencyMs", test.get("latencyMs"));
                    row.put("server", test.get("server"));
                    row.put("version", test.get("version"));
                    row.put("message", test.get("message"));
                    rows.add(row);
                }
            }
            return ReturnResult.ok(rows);
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
     * 补建模板辅助生成：按首个分库现有物理表结构生成逻辑表建表模板 DDL（只读，不执行）。
     */
    @PostMapping("/tables/generate-template")
    public ReturnResult<Map<String, Object>> generateTemplate(@RequestBody Map<String, Object> body) {
        try {
            DsShardingRule rule = requireRule(longValue(body == null ? null : body.get("ruleId")));
            return ReturnResult.ok(physicalTableService.generateTemplate(rule));
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

    // ==================== 连接测试 / 路由预览 ====================

    /**
     * 连接测试（联邦成员 / 分库成员保存前预检，不落库）。
     */
    @PostMapping("/test-connection")
    public ReturnResult<Map<String, Object>> testConnection(@RequestBody Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ReturnResult.ok(connectionTestService.test(
                    params.get("url"), params.get("username"), params.get("password"), params.get("driverClassName")));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 分片路由预览（按分片值推算目标分库与物理表，不执行 SQL）。
     *
     * <p>body 传 <code>ruleId</code> 按已保存规则预览，或传 <code>rule</code>（规则草稿对象）
     * 在保存前预览编辑中的规则；二者都没有返回参数错误。</p>
     */
    @PostMapping("/sharding/route-preview")
    public ReturnResult<Map<String, Object>> routePreview(@RequestBody Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        try {
            DsShardingRule rule;
            Long ruleId = longValue(params.get("ruleId"));
            if (ruleId != null) {
                rule = requireRule(ruleId);
            } else if (params.get("rule") instanceof Map) {
                rule = objectMapper.convertValue(params.get("rule"), DsShardingRule.class);
            } else {
                return ReturnResult.error("缺少 ruleId 或 rule 草稿");
            }
            return ReturnResult.ok(shardingRuleManager.routePreview(rule, strValue(params.get("value"))));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    // ==================== 配置导入导出 ====================

    /**
     * 导出全部联邦与分片规则配置（管理端配置备份语义：成员 JSON 按库内原样，含密码，请妥善保管导出文件）。
     */
    @GetMapping("/config/export")
    public ReturnResult<Map<String, Object>> exportConfig() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exportedAt", System.currentTimeMillis());
            result.put("federations", store.findAllFederations());
            result.put("rules", store.findAllShardingRules());
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 导入联邦与分片规则配置（按自然键 upsert：联邦 = schemaName，规则 = logicTable；
     * 命中已有行回填 id/version 走乐观锁更新，导入后分别刷新联邦与分片运行时）。
     */
    @PostMapping("/config/import")
    public ReturnResult<Map<String, Object>> importConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        try {
            int federationCount = 0;
            int ruleCount = 0;
            if (params.get("federations") instanceof List<?> federations) {
                for (Object item : federations) {
                    DsCalciteFederation federation = objectMapper.convertValue(item, DsCalciteFederation.class);
                    DsCalciteFederation existing = store.findFederationBySchemaName(federation.getSchemaName());
                    if (existing != null) {
                        federation.setId(existing.getId());
                        federation.setVersion(existing.getVersion());
                    }
                    store.saveFederation(federation);
                    federationCount++;
                }
            }
            if (params.get("rules") instanceof List<?> rules) {
                for (Object item : rules) {
                    DsShardingRule rule = objectMapper.convertValue(item, DsShardingRule.class);
                    DsShardingRule existing = store.findShardingRuleByLogicTable(rule.getLogicTable());
                    if (existing != null) {
                        rule.setId(existing.getId());
                        rule.setVersion(existing.getVersion());
                    }
                    store.saveShardingRule(rule);
                    ruleCount++;
                }
            }
            // 导入后分别刷新运行时；单侧刷新失败不影响另一侧，结果中如实带回
            String federationRefresh;
            String shardingRefresh;
            try {
                federationManager.refresh();
                federationRefresh = federationManager.lastRefreshResult();
            } catch (Exception e) {
                federationRefresh = "刷新失败: " + e.getMessage();
            }
            try {
                shardingRuleManager.refresh();
                shardingRefresh = shardingRuleManager.lastRefreshResult();
            } catch (Exception e) {
                shardingRefresh = "刷新失败: " + e.getMessage();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("federations", federationCount);
            result.put("rules", ruleCount);
            result.put("federationRefresh", federationRefresh);
            result.put("shardingRefresh", shardingRefresh);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.warn("[ds-runtime] 配置导入失败: {}", e.getMessage());
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
