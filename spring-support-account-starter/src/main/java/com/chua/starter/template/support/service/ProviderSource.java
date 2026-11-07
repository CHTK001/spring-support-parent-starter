package com.chua.starter.template.support.service;

import com.chua.starter.template.support.model.TemplateProviderInfo;
import com.chua.starter.template.support.model.TemplateProviderSyncResult;
import com.chua.starter.template.support.model.TemplateSourceDetail;

import java.util.Collections;
import java.util.List;

/**
 * 来源提供商（SPI）
 *
 * <p>模板库本身不认识短信、邮件、组卷这些业务来源，它只认识「提供商」。
 * 但提供商与其模板的真实数据在各自的业务模块里（如消息模块的
 * {@code sys_message_provider}）。为避免 template-starter 反向依赖业务模块
 * （业务模块本来就依赖 template-starter 注册 {@link SourceSyncer}，反向依赖会成环），
 * 这里定义一条单向 SPI：业务模块实现本接口，把自身提供商上报给模板库。</p>
 *
 * <p>上报的提供商在 {@code pages/template} 左栏与库内提供商一起展示，
 * 其增删改仍回到来源模块处理，模板库只读。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
public interface ProviderSource {

    /**
     * 来源标识，与 {@link SourceSyncer#source()} 保持一致（message / paper …）
     *
     * @return 来源标识
     */
    String source();

    /**
     * 上报本来源下全部提供商
     *
     * <p>返回的 {@code id} 必须与 {@link SourceSyncer} 写入
     * {@code template_info.template_info_provider} 的值一致，否则左栏筛选不到模板。</p>
     *
     * @return 提供商列表，无数据时返回空集合
     */
    default List<TemplateProviderInfo> providers() {
        return Collections.emptyList();
    }

    /**
     * 按来源行取回模板详情（含推送 / 渲染所需参数）
     *
     * <p>统一模板库只保存来源行的「影子」信息（名称 / 类型 / 状态 / 内容副本）。
     * 需要完整信息（归属账号、推送渠道、内容类型、占位符）时由来源模块回答，
     * 这样模板库这边就有一条「按模板 ID 取模板信息」的通路，而不必反向依赖业务模块。</p>
     *
     * @param providerId 提供商 ID（模板归属账号，可为空）
     * @param sourceId   来源行 ID（如短信模板的 sys_message_template_id）
     * @return 模板详情，来源模块不认识该行时返回 {@code null}
     */
    default TemplateSourceDetail detail(String providerId, String sourceId) {
        return null;
    }

    /**
     * 同步某个提供商的模板
     *
     * <p>默认不处理；实现方应从远端拉取该提供商的模板，再交给
     * {@link SourceSyncer} 注册进统一模板库。</p>
     *
     * @param providerId 提供商 ID
     * @return 新增 / 更新 / 失败数
     */
    default TemplateProviderSyncResult sync(String providerId) {
        return new TemplateProviderSyncResult();
    }
}
