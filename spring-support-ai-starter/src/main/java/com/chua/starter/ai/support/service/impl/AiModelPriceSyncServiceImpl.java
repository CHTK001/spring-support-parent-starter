package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiModelSetting;
import com.chua.starter.ai.support.mapper.SysAiModelSettingMapper;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiModelPriceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* AI 模型价格同步服务实现。
* <p>
* 从 对话客户端 注册中心获取模型列表，同步到 sys_AI_模型_setting 表。
*
* @author CH
* @since 4.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelPriceSyncServiceImpl implements AiModelPriceSyncService {

    /** SYSAI模型设置映射器 */
    private final SysAiModelSettingMapper sysAiModelSettingMapper;
    /** 对话客户端注册表 */
    private final ChatClientRegistry chatClientRegistry;

    @Override
    /** 同步prices */
    public void syncPrices() {
        log.info("[AiModelPriceSync] 开始同步模型价格");
        try {
            var models = chatClientRegistry.getModels();
            if (models.isEmpty()) {
                log.info("[AiModelPriceSync] 无注册模型，跳过价格同步");
                return;
            }

            int synced = 0;
            for (String modelName : models) {
                try {
                    List<SysAiModelSetting> settings = sysAiModelSettingMapper.selectList(
                            Wrappers.<SysAiModelSetting>lambdaQuery()
                                    .eq(SysAiModelSetting::getCode, modelName));
                    if (settings.isEmpty()) {
                        continue;
                    }
                    // 更新模型名称
                    for (SysAiModelSetting setting : settings) {
                        setting.setName(modelName);
                        sysAiModelSettingMapper.updateById(setting);
                    }
                    synced++;
                } catch (Exception e) {
                    log.warn("[AiModelPriceSync] 同步模型 {} 价格失败: {}", modelName, e.getMessage());
                }
            }
            log.info("[AiModelPriceSync] 价格同步完成，共同步 {} 个模型", synced);
        } catch (Exception e) {
            log.error("[AiModelPriceSync] 价格同步异常", e);
        }
    }

    @Override
    /** 同步模型列表 */
    public void syncModelList() {
        log.info("[AiModelPriceSync] 开始同步模型列表");
        try {
            var models = chatClientRegistry.getModels();
            log.info("[AiModelPriceSync] 当前注册中心模型列表 (共 {} 个): {}", models.size(), models);

            int synced = 0;
            for (String modelName : models) {
                try {
                    List<SysAiModelSetting> existing = sysAiModelSettingMapper.selectList(
                            Wrappers.<SysAiModelSetting>lambdaQuery()
                                    .eq(SysAiModelSetting::getCode, modelName));

                    if (existing.isEmpty()) {
                        SysAiModelSetting setting = new SysAiModelSetting();
                        setting.setCode(modelName);
                        setting.setName(modelName);
                        setting.setProvider("");
                        setting.setStatus(1);
                        setting.setSort(synced);
                        sysAiModelSettingMapper.insert(setting);
                        synced++;
                    }
                } catch (Exception e) {
                    log.warn("[AiModelPriceSync] 同步模型 {} 失败: {}", modelName, e.getMessage());
                }
            }
            log.info("[AiModelPriceSync] 模型列表同步完成，新增 {} 个模型", synced);
        } catch (Exception e) {
            log.error("[AiModelPriceSync] 模型列表同步异常", e);
        }
    }

    @Override
    /** 同步prices */
    public void syncPrices(String code) {
        if (code == null || code.isBlank()) {
            /**
    * 同步prices。
    */
            syncPrices();
            return;
        }
        log.info("[AiModelPriceSync] 同步指定模型价格: code={}", code);
        try {
            var client = chatClientRegistry.getByModel(code);
            if (client == null) {
                log.warn("[AiModelPriceSync] 模型 {} 未在注册中心找到", code);
                return;
            }
            List<SysAiModelSetting> settings = sysAiModelSettingMapper.selectList(
                    Wrappers.<SysAiModelSetting>lambdaQuery()
                            .eq(SysAiModelSetting::getCode, code));
            for (SysAiModelSetting setting : settings) {
                setting.setName(code);
                sysAiModelSettingMapper.updateById(setting);
            }
            log.info("[AiModelPriceSync] 模型 {} 价格同步完成", code);
        } catch (Exception e) {
            log.error("[AiModelPriceSync] 同步模型 {} 价格失败", code, e);
        }
    }
}
