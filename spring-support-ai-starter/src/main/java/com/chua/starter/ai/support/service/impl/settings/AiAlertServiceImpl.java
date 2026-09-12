package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.settings.SysAiAlert;
import com.chua.starter.ai.support.mapper.settings.AiAlertMapper;
import com.chua.starter.ai.support.model.AiAlertDTO;
import com.chua.starter.ai.support.service.settings.AiAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警规则服务实现类
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAlertServiceImpl implements AiAlertService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
      * 告警 映射器
     */
    private final AiAlertMapper alertMapper;

    /**
     * 查询所有告警规则。
     *
     * @return 告警 DTO 列表
     */
    @Override
    public List<AiAlertDTO> list() {
        return alertMapper.selectList(
                        new LambdaQueryWrapper<SysAiAlert>()
                                .orderByAsc(SysAiAlert::getAlertCreateTime))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 新建告警规则。
     *
     * @param alert 告警 DTO
     * @return 创建后的告警 DTO
     */
    @Override
    @Transactional
    public AiAlertDTO create(AiAlertDTO alert) {
        SysAiAlert entity = toEntity(alert);
        LocalDateTime now = LocalDateTime.now();
        entity.setAlertCreateTime(now);
        entity.setAlertUpdateTime(now);
        if (entity.getAlertEnabled() == null) {
            entity.setAlertEnabled(true);
        }
        if (entity.getAlertDeleted() == null) {
            entity.setAlertDeleted(0);
        }
        alertMapper.insert(entity);
        log.debug("[SysAiAlert] 新建成功: id={}, name={}", entity.getAlertId(), entity.getAlertName());
        return toDto(entity);
    }

    /**
     * 更新告警规则。
     *
     * @param alertId 告警主键
     * @param alert   告警 DTO
     * @return 更新后的告警 DTO
     */
    @Override
    @Transactional
    public AiAlertDTO update(Long alertId, AiAlertDTO alert) {
        SysAiAlert entity = toEntity(alert);
        entity.setAlertId(alertId);
        entity.setAlertUpdateTime(LocalDateTime.now());
        alertMapper.updateById(entity);
        return toDto(alertMapper.selectById(alertId));
    }

    /**
     * 删除告警规则（逻辑删除）。
     *
     * @param alertId 告警主键
     */
    @Override
    @Transactional
    public void delete(Long alertId) {
        alertMapper.deleteById(alertId);
        log.debug("[SysAiAlert] 删除成功: id={}", alertId);
    }

    /**
     * 切换告警启用状态。
     *
     * @param alertId 告警主键
     * @param enabled 是否启用
     * @return 更新后的告警 DTO
     */
    @Override
    @Transactional
    public AiAlertDTO toggle(Long alertId, Boolean enabled) {
        LambdaUpdateWrapper<SysAiAlert> wrapper = new LambdaUpdateWrapper<SysAiAlert>()
                .eq(SysAiAlert::getAlertId, alertId)
                .set(SysAiAlert::getAlertEnabled, enabled)
                .set(SysAiAlert::getAlertUpdateTime, LocalDateTime.now());
        alertMapper.update(null, wrapper);
        return toDto(alertMapper.selectById(alertId));
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 告警实体
     * @return 告警 DTO
     */
    private AiAlertDTO toDto(SysAiAlert entity) {
        if (entity == null) {
            return null;
        }
        AiAlertDTO dto = new AiAlertDTO();
        dto.setId(entity.getAlertId());
        dto.setName(entity.getAlertName());
        dto.setAppKeyId(entity.getAlertAppKeyId());
        dto.setCondition(entity.getAlertCondition());
        dto.setThreshold(entity.getAlertThreshold());
        dto.setChannels(entity.getAlertChannels());
        dto.setEnabled(entity.getAlertEnabled());
        dto.setCreatedAt(entity.getAlertCreateTime() != null ? entity.getAlertCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto 告警 DTO
     * @return 告警实体
     */
    private SysAiAlert toEntity(AiAlertDTO dto) {
        SysAiAlert entity = new SysAiAlert();
        entity.setAlertName(dto.getName());
        entity.setAlertAppKeyId(dto.getAppKeyId());
        entity.setAlertCondition(dto.getCondition());
        entity.setAlertThreshold(dto.getThreshold());
        entity.setAlertChannels(dto.getChannels());
        entity.setAlertEnabled(dto.getEnabled());
        return entity;
    }
}
