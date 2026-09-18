package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiAlertDTO;

import java.util.List;

/**
* 告警规则服务接口
*
* @since 2026-08-03
* @author CH
 */
public interface AiAlertService {

    /**
    * 查询所有告警规则。
    *
    * @return 告警 DTO 列表
    */
    List<AiAlertDTO> list();

    /**
    * 新建告警规则。
    *
    * @param alert 告警 DTO
    * @return 创建后的告警 DTO
    */
    AiAlertDTO create(AiAlertDTO alert);

    /**
    * 更新告警规则。
    *
    * @param alertId 告警主键
    * @param alert   告警 DTO
    * @return 更新后的告警 DTO
    */
    AiAlertDTO update(Long alertId, AiAlertDTO alert);

    /**
    * 删除告警规则（逻辑删除）。
    *
    * @param alertId 告警主键
    */
    void delete(Long alertId);

    /**
    * 切换告警启用状态。
    *
    * @param alertId 告警主键
    * @param enabled 是否启用
    * @return 更新后的告警 DTO
    */
    AiAlertDTO toggle(Long alertId, Boolean enabled);
}
