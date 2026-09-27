package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型别名双向解析服务。
 * 入站：业务请求带别名 → 转真实 model id 再调用/落库。
 * 出站：历史/会话/用量返回 → 真实 id 转别名展示。
 * 系统设置类接口仍返回真实 id。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelAliasService {

    private final SysAiProviderModelMapper sysAiProviderModelMapper;

    /** 模型别名到真实模型标识的映射。 */
    private final Map<String, String> aliasToReal = new ConcurrentHashMap<>();
    /** 真实模型标识到模型别名的映射。 */
    private final Map<String, String> realToAlias = new ConcurrentHashMap<>();

    /**
     * 初始化模型别名映射。
     */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重新加载模型别名映射。
     */
    public void reload() {
        aliasToReal.clear();
        realToAlias.clear();
        try {
            LambdaQueryWrapper<SysAiProviderModel> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(SysAiProviderModel::getAlias)
                   .ne(SysAiProviderModel::getAlias, "");
            List<SysAiProviderModel> list = sysAiProviderModelMapper.selectList(wrapper);
            for (SysAiProviderModel m : list) {
                if (m.getModel() != null && m.getAlias() != null && !m.getAlias().isBlank()) {
                    aliasToReal.put(m.getAlias(), m.getModel());
                    realToAlias.put(m.getModel(), m.getAlias());
                }
            }
            log.info("[ModelAlias] 加载别名映射: {} 条", realToAlias.size());
        } catch (Exception e) {
            log.warn("[ModelAlias] 加载别名失败: {}", e.getMessage());
        }
    }

    /**
     * 入站：把用户传入的模型标识解析为真实 model id。
     * 支持格式：
     *   - "alias" → 查别名表返回真实 id
     *   - "providerId:modelName" → 保留冒号格式，内部 modelName 若命中别名则替换
     *   - 其它格式：原样返回
     *
     * @param modelId 用户传入的模型标识，允许为 null 或空字符串
     * @return 解析后的真实模型标识
     */
    public String resolveInbound(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return modelId;
        }
        // 先查纯别名
        String real = aliasToReal.get(modelId);
        if (real != null) {
            return real;
        }
        // 处理 providerId:modelName 格式
        if (modelId.contains(":")) {
            int idx = modelId.indexOf(':');
            String prefix = modelId.substring(0, idx + 1);
            String suffix = modelId.substring(idx + 1);
            String realSuffix = aliasToReal.get(suffix);
            if (realSuffix != null) {
                return prefix + realSuffix;
            }
        }
        return modelId;
    }

    /**
     * 出站：把真实 model id 转成别名展示。
     * 无别名或空值时返回真实模型标识，不抛出异常。
     *
     * @param realModelId 真实模型标识，允许为 null 或空字符串
     * @return 对应的模型别名；无别名时返回真实模型标识
     */
    public String resolveOutbound(String realModelId) {
        if (realModelId == null || realModelId.isBlank()) {
            return realModelId;
        }
        String alias = realToAlias.get(realModelId);
        return alias != null ? alias : realModelId;
    }
}
