package com.chua.starter.ai.support.service.impl.settings;

import com.chua.starter.ai.support.entity.settings.AiGlobalSetting;
import com.chua.starter.ai.support.mapper.settings.AiGlobalSettingMapper;
import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import com.chua.starter.ai.support.service.settings.AiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 全局设置服务实现类（单条记录 id=1）
 *
 * @author CH
 * @since 2026-08-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGlobalSettingServiceImpl implements AiGlobalSettingService {

    /**
     * 全局设置单条记录主键
     */
    private static final Long SETTING_ID = 1L;

    /**
     * 全局设置 Mapper
     */
    private final AiGlobalSettingMapper settingMapper;

    /**
     * 获取全局设置，不存在时返回空 DTO。
     *
     * @return 全局设置 DTO
     */
    @Override
    public AiGlobalSettingDTO get() {
        AiGlobalSetting entity = settingMapper.selectById(SETTING_ID);
        if (entity == null) {
            return new AiGlobalSettingDTO();
        }
        return toDto(entity);
    }

    /**
     * 更新全局设置，无记录时新建。
     *
     * @param setting 全局设置 DTO
     * @return 更新后的全局设置 DTO
     */
    @Override
    @Transactional
    public AiGlobalSettingDTO update(AiGlobalSettingDTO setting) {
        AiGlobalSetting entity = toEntity(setting);
        AiGlobalSetting exist = settingMapper.selectById(SETTING_ID);
        if (exist == null) {
            entity.setSettingId(SETTING_ID);
            entity.setSettingUpdateTime(LocalDateTime.now());
            settingMapper.insert(entity);
            log.debug("[AiGlobalSetting] 初始化配置成功");
        } else {
            entity.setSettingId(SETTING_ID);
            entity.setSettingUpdateTime(LocalDateTime.now());
            settingMapper.updateById(entity);
            log.debug("[AiGlobalSetting] 更新配置成功");
        }
        return toDto(settingMapper.selectById(SETTING_ID));
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 全局设置实体
     * @return 全局设置 DTO
     */
    private AiGlobalSettingDTO toDto(AiGlobalSetting entity) {
        AiGlobalSettingDTO dto = new AiGlobalSettingDTO();
        dto.setChatClientEnabled(entity.getSettingChatClientEnabled());
        dto.setEnableUsageAnalysis(entity.getSettingEnableUsageAnalysis());
        dto.setUsageReportInterval(entity.getSettingUsageIntervalSec());
        dto.setDefaultModel(entity.getSettingDefaultModel());
        dto.setTemperature(entity.getSettingTemperature());
        dto.setMaxTokens(entity.getSettingMaxTokens());
        dto.setTimeout(entity.getSettingTimeout());
        dto.setEnableCache(entity.getSettingEnableCache());
        dto.setStream(entity.getSettingStream());
        return dto;
    }

    /**
     * DTO 转实体，空值保留不覆盖。
     *
     * @param dto 全局设置 DTO
     * @return 全局设置实体
     */
    private AiGlobalSetting toEntity(AiGlobalSettingDTO dto) {
        AiGlobalSetting entity = new AiGlobalSetting();
        AiGlobalSetting exist = settingMapper.selectById(SETTING_ID);
        if (exist != null) {
            entity.setSettingId(SETTING_ID);
            if (dto.getChatClientEnabled() != null) {
                entity.setSettingChatClientEnabled(dto.getChatClientEnabled());
            }
            if (dto.getEnableUsageAnalysis() != null) {
                entity.setSettingEnableUsageAnalysis(dto.getEnableUsageAnalysis());
            }
            if (dto.getUsageReportInterval() != null) {
                entity.setSettingUsageIntervalSec(dto.getUsageReportInterval());
            }
            if (dto.getDefaultModel() != null) {
                entity.setSettingDefaultModel(dto.getDefaultModel());
            }
            if (dto.getTemperature() != null) {
                entity.setSettingTemperature(dto.getTemperature());
            }
            if (dto.getMaxTokens() != null) {
                entity.setSettingMaxTokens(dto.getMaxTokens());
            }
            if (dto.getTimeout() != null) {
                entity.setSettingTimeout(dto.getTimeout());
            }
            if (dto.getEnableCache() != null) {
                entity.setSettingEnableCache(dto.getEnableCache());
            }
            if (dto.getStream() != null) {
                entity.setSettingStream(dto.getStream());
            }
            return entity;
        }
        entity.setSettingChatClientEnabled(dto.getChatClientEnabled());
        entity.setSettingEnableUsageAnalysis(dto.getEnableUsageAnalysis());
        entity.setSettingUsageIntervalSec(dto.getUsageReportInterval());
        entity.setSettingDefaultModel(dto.getDefaultModel());
        entity.setSettingTemperature(dto.getTemperature());
        entity.setSettingMaxTokens(dto.getMaxTokens());
        entity.setSettingTimeout(dto.getTimeout());
        entity.setSettingEnableCache(dto.getEnableCache());
        entity.setSettingStream(dto.getStream());
        return entity;
    }
}
