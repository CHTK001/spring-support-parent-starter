package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import org.springframework.stereotype.Service;

/**
 * 全局设置服务接口的最小占位实现。
 *
 * <p>接口已定义但原实现类缺失（重构遗留），此处提供空实现使应用可独立启动。
 * 若需启用全局设置管理，请替换为完整实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class AiGlobalSettingServiceImpl implements AiGlobalSettingService {

    @Override
    public AiGlobalSettingDTO get() {
        return null;
    }

    @Override
    public AiGlobalSettingDTO update(AiGlobalSettingDTO setting) {
        return setting;
    }
}
