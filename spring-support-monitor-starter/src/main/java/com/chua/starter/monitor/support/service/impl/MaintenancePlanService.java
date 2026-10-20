package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.entity.MsMaintenancePlan;
import com.chua.starter.monitor.support.mapper.MsMaintenancePlanMapper;
import com.chua.starter.monitor.support.model.dto.MaintenancePlanDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 维护计划服务：台账的增删改查。
 *
 * <p>表先由 Spring SQL 初始化脚本创建；这里再用 PostConstruct 自愈一次，兼容
 * 生产部署使用外部 application.yml、未把新脚本纳入 schema-locations 的旧库。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenancePlanService {

    /**
     * 默认创建状态
     */
    private static final String DEFAULT_STATUS = "PENDING";

    /**
     * 计划状态白名单；后续流程需要新状态时只改这里与前端字典。
     */
    private static final Set<String> STATUSES = Set.of("PENDING", "RUNNING", "COMPLETED", "CANCELLED");

    /**
     * 维护计划映射器
     */
    private final MsMaintenancePlanMapper planMapper;

    /**
     * 平台默认数据源（用于表结构自愈）
     */
    private final DatasourceService datasourceService;

    /**
     * 启动时确保维护计划表可用。
     */
    @PostConstruct
    public void ensureSchema() {
        try (Connection connection = datasourceService.connection(null);
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
            log.info("[maintenance-plan] ms_maintenance_plan 表已就绪");
        } catch (Exception e) {
            log.warn("[maintenance-plan] 初始化 ms_maintenance_plan 表失败: {}", e.getMessage());
        }
    }

    /**
     * 列出全部维护计划。
     *
     * @return 维护计划列表，按主键升序
     */
    public ReturnResult<List<MsMaintenancePlan>> list() {
        try {
            List<MsMaintenancePlan> plans = planMapper.selectList(
                    new LambdaQueryWrapper<MsMaintenancePlan>().orderByAsc(MsMaintenancePlan::getId));
            return ReturnResult.ok(plans);
        } catch (Exception e) {
            log.error("[maintenance-plan] 查询维护计划列表失败", e);
            return ReturnResult.error("查询维护计划失败: " + e.getMessage());
        }
    }

    /**
     * 新建维护计划。
     *
     * @param request 入参
     * @return 新建后的计划
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsMaintenancePlan> create(MaintenancePlanDTO request) {
        try {
            validate(request, null);
            MsMaintenancePlan plan = new MsMaintenancePlan();
            apply(plan, request);
            planMapper.insert(plan);
            return ReturnResult.ok(planMapper.selectById(plan.getId()));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[maintenance-plan] 新建维护计划失败", e);
            return ReturnResult.error("新建维护计划失败: " + e.getMessage());
        }
    }

    /**
     * 修改维护计划。
     *
     * @param id      计划 ID
     * @param request 入参
     * @return 修改后的计划
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsMaintenancePlan> update(Long id, MaintenancePlanDTO request) {
        try {
            MsMaintenancePlan plan = requirePlan(id);
            validate(request, plan);
            apply(plan, request);
            planMapper.updateById(plan);
            return ReturnResult.ok(planMapper.selectById(id));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[maintenance-plan] 修改维护计划失败, id={}", id, e);
            return ReturnResult.error("修改维护计划失败: " + e.getMessage());
        }
    }

    /**
     * 删除维护计划。
     *
     * @param id 计划 ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> delete(Long id) {
        try {
            requirePlan(id);
            planMapper.deleteById(id);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[maintenance-plan] 删除维护计划失败, id={}", id, e);
            return ReturnResult.error("删除维护计划失败: " + e.getMessage());
        }
    }

    /**
     * 按 ID 加载计划，不存在时抛出业务异常。
     *
     * @param id 计划 ID
     * @return 计划
     */
    private MsMaintenancePlan requirePlan(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("计划 ID 不合法");
        }
        MsMaintenancePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("维护计划不存在: " + id);
        }
        return plan;
    }

    /**
     * 校验新增 / 修改入参。
     *
     * @param request 入参
     * @param old     已有计划，修改时用于保留原状态；新增传 null
     */
    private void validate(MaintenancePlanDTO request, MsMaintenancePlan old) {
        if (request == null) {
            throw new IllegalArgumentException("维护计划参数不能为空");
        }
        if (request.planName() == null || request.planName().trim().isBlank()) {
            throw new IllegalArgumentException("计划名称不能为空");
        }
        if (request.planName().trim().length() > 128) {
            throw new IllegalArgumentException("计划名称不能超过 128 个字符");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        String status = request.status();
        if (status != null && !status.isBlank() && !STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不支持的维护计划状态: " + status);
        }
    }

    /**
     * 把入参写入实体。
     *
     * @param plan    目标实体
     * @param request 入参
     */
    private void apply(MsMaintenancePlan plan, MaintenancePlanDTO request) {
        plan.setPlanName(request.planName().trim());
        String type = request.planType() == null || request.planType().isBlank()
                ? "MAINTENANCE" : request.planType().trim();
        plan.setPlanType(type);
        plan.setStartTime(request.startTime());
        plan.setEndTime(request.endTime());
        plan.setTargetHostIds(normalizeHostIds(request.targetHostIds()));
        plan.setStatus(request.status() == null || request.status().isBlank()
                ? DEFAULT_STATUS : request.status().trim().toUpperCase(Locale.ROOT));
        plan.setDescription(request.description());
    }

    /**
     * 归一化目标主机 ID JSON；非法 JSON 不展开，由前端再次编辑时校验。
     *
     * @param raw 原始 JSON
     * @return 归一化字符串，空数组返回 "[]"
     */
    private String normalizeHostIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return "[]";
        }
        String trim = raw.trim();
        if (trim.equals("[]")) {
            return trim;
        }
        return trim;
    }

    /**
     * 建表 SQL；与 db/init/V2.0__init_all_monitor.sql 中的 22 号表保持一致。
     */
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS `ms_maintenance_plan` (
                `id`                                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                `ms_maintenance_plan_name`           VARCHAR(128) NOT NULL COMMENT '计划名称',
                `ms_maintenance_plan_type`           VARCHAR(32)  NOT NULL DEFAULT 'MAINTENANCE' COMMENT '计划类型 MAINTENANCE/UPGRADE/BACKUP/REBOOT',
                `ms_maintenance_plan_start_time`     DATETIME     NOT NULL COMMENT '开始时间',
                `ms_maintenance_plan_end_time`       DATETIME     NOT NULL COMMENT '结束时间',
                `ms_maintenance_plan_target_host_ids` VARCHAR(1024) DEFAULT NULL COMMENT '目标主机 ID JSON 数组',
                `ms_maintenance_plan_status`         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '计划状态 PENDING/RUNNING/COMPLETED/CANCELLED',
                `ms_maintenance_plan_description`    VARCHAR(512) DEFAULT NULL COMMENT '计划说明',
                `create_name`                        VARCHAR(64)  DEFAULT NULL COMMENT '创建人姓名',
                `create_by`                          INT          DEFAULT NULL COMMENT '创建人',
                `create_time`                        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                `update_name`                        VARCHAR(64)  DEFAULT NULL COMMENT '更新人姓名',
                `update_by`                          INT          DEFAULT NULL COMMENT '更新人',
                `update_time`                        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                PRIMARY KEY (`id`),
                KEY `idx_ms_maintenance_plan_status` (`ms_maintenance_plan_status`),
                KEY `idx_ms_maintenance_plan_window` (`ms_maintenance_plan_start_time`, `ms_maintenance_plan_end_time`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维护计划'
            """;
}