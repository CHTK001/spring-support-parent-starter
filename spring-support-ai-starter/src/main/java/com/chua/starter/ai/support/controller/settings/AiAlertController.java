package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiAlertDTO;
import com.chua.starter.ai.support.service.settings.AiAlertService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
* @since 2026-08-03
* @author CH
 */
@RestController
@RequestMapping("/v2/alerts")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 告警规则")
public class AiAlertController {

    /**
    * 告警服务
     */
    private final AiAlertService alertService;

    /**
    * 查询所有告警规则。
    *
    * @return 告警 DTO 列表
     */
    @Permission("admin")
    @Operation(summary = "查询所有告警规则")
    @GetMapping
    public List<AiAlertDTO> list() {
        return alertService.list();
    }

    /**
    * 新建告警规则。
    *
    * @param alert 告警 DTO
    * @return 创建后的告警 DTO
     */
    @Permission("admin")
    @Operation(summary = "新建告警规则")
    @PostMapping
    public AiAlertDTO create(@RequestBody AiAlertDTO alert) {
        return alertService.create(alert);
    }

    /**
    * 更新告警规则。
    *
    * @param alertId 告警主键
    * @param alert   告警 DTO
    * @return 更新后的告警 DTO
    * @param ID" 标识"
     */
    @Permission("admin")
    @Operation(summary = "更新告警规则")
    @PutMapping("/{alertId}")
    public AiAlertDTO update(@Parameter(description = "告警主键 ID") @PathVariable Long alertId,
                             @RequestBody AiAlertDTO alert) {
        return alertService.update(alertId, alert);
    }

    /**
    * 删除告警规则（逻辑删除）。
    *
    * @param alertId 告警主键
    * @param ID" 标识"
    * @param ID" 标识"
    * @param ID" ID"
     */
    @Permission("admin")
    @Operation(summary = "删除告警规则")
    @DeleteMapping("/{alertId}")
    public void delete(@Parameter(description = "告警主键 ID") @PathVariable Long alertId) {
        alertService.delete(alertId);
    }

    /**
    * 切换告警启用状态。
    *
    * @param alertId 告警主键
    * @param enabled 是否启用
    * @return 更新后的告警 DTO
    * @param ID" 标识"
     */
    @Permission("admin")
    @Operation(summary = "切换告警启用状态")
    @PutMapping("/{alertId}/toggle")
    public AiAlertDTO toggle(@Parameter(description = "告警主键 ID") @PathVariable Long alertId,
                             @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return alertService.toggle(alertId, enabled);
    }
}
