package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiProviderDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型供应商服务接口的最小占位实现。
 *
 * <p>接口已定义但原实现类缺失（重构遗留），此处提供空实现使应用可独立启动。
 * 若需启用供应商管理，请替换为完整实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class AiProviderServiceImpl implements AiProviderService {

    @Override
    public List<AiProviderDTO> list() {
        return List.of();
    }

    @Override
    public AiProviderDTO create(AiProviderDTO provider) {
        return provider;
    }

    @Override
    public AiProviderDTO update(Long providerId, AiProviderDTO provider) {
        return provider;
    }

    @Override
    public void delete(Long providerId) {
        // 空实现
    }

    @Override
    public AiProviderDTO toggle(Long providerId, Boolean enabled) {
        return new AiProviderDTO();
    }

    @Override
    public AiProviderDTO activate(Long providerId) {
        return new AiProviderDTO();
    }
}
