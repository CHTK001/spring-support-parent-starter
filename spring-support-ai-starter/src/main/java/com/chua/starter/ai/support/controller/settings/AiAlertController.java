package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.settings.AiAlert;
import com.chua.starter.ai.support.service.settings.AiAlertService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
 * 告警规则控制器，提供规则的增删改查与启停切换。
 *
 * <p>所有写接口均受 {@code @Permission("admin")} 保护。</p>
 *
 * @author CH
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/v2/ai/alerts")
@RequiredArgsConstructor
@Api(tags = "AI 设置 - 告警规则")
public class AiAlertController {

    /**
     * 告警服务
     */
    private final AiAlertService alertService;

    /**
     * 查询所有告警规则。
     *
     * @return 告警列表
     */
    @Permission("admin")
    @ApiOperation("查询所有告警规则")
    @GetMapping
    public List<AiAlert> list() {
        return alertService.list();
    }

    /**
     * 新建告警规则。
     *
     * @param alert 告警实体
     * @return 创建后的告警
     */
    @Permission("admin")
    @ApiOperation("新建告警规则")
    @PostMapping
    public AiAlert create(@RequestBody AiAlert alert) {
        return alertService.create(alert);
    }

    /**
     * 更新告警规则。
     *
     * @param alertId 告警主键
     * @param alert   告警实体
     * @return 更新后的告警
     */
    @Permission("admin")
    @ApiOperation("更新告警规则")
    @PutMapping("/{alertId}")
    public AiAlert update(@ApiParam("告警主键 ID") @PathVariable Long alertId,
                          @RequestBody AiAlert alert) {
        return alertService.update(alertId, alert);
    }

    /**
     * 删除告警规则（逻辑删除）。
     *
     * @param alertId 告警主键
     */
    @Permission("admin")
    @ApiOperation("删除告警规则")
    @DeleteMapping("/{alertId}")
    public void delete(@ApiParam("告警主键 ID") @PathVariable Long alertId) {
        alertService.delete(alertId);
    }

    /**
     * 切换告警启用状态。
     *
     * @param alertId 告警主键
     * @param enabled 是否启用
     * @return 更新后的告警
     */
    @Permission("admin")
    @ApiOperation("切换告警启用状态")
    @PutMapping("/{alertId}/toggle")
    public AiAlert toggle(@ApiParam("告警主键 ID") @PathVariable Long alertId,
                          @ApiParam("是否启用") @RequestParam Boolean enabled) {
        return alertService.toggle(alertId, enabled);
    }
}