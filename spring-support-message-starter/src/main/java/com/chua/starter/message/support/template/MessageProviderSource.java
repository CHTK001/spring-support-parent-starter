package com.chua.starter.message.support.template;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.message.support.entity.SysMessageProvider;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import com.chua.starter.message.support.mapper.SysMessageProviderMapper;
import com.chua.starter.message.support.mapper.SysMessageTemplateMapper;
import com.chua.starter.template.support.model.TemplateProviderInfo;
import com.chua.starter.template.support.model.TemplateProviderSyncResult;
import com.chua.starter.template.support.model.TemplateSourceDetail;
import com.chua.starter.template.support.service.ProviderSource;
import com.chua.starter.template.support.util.TemplateContentParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 消息提供商 → 模板库左栏提供商轨
 *
 * <p>把 {@code sys_message_provider} 中的短信 / 邮件服务商上报给模板库，
 * 使 {@code pages/template} 的左栏能直接看到「阿里云短信」「腾讯云邮件」等提供商，
 * 并按其 {@code providerId} 过滤到对应的模板。增删改仍在消息模块进行，
 * 模板库只读展示。</p>
 *
 * <p>上报的 {@code id} 与 {@link MessageTemplateSyncer} 写入
 * {@code template_info.template_info_provider} 的值一致
 * （都是 {@code sys_message_provider_id} 的字符串形式），保证左栏筛选能命中模板。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Slf4j
@Component
public class MessageProviderSource implements ProviderSource {

    /** 来源标识，与 {@link MessageTemplateSyncer#SOURCE} 一致 */
    public static final String SOURCE = MessageTemplateSyncer.SOURCE;

    private final SysMessageProviderMapper providerMapper;

    /** 消息模板表：按来源行 ID 取回模板详情（三方模板 ID / 内容 / 类型） */
    private final SysMessageTemplateMapper templateMapper;

    public MessageProviderSource(SysMessageProviderMapper providerMapper,
                                 SysMessageTemplateMapper templateMapper) {
        this.providerMapper = providerMapper;
        this.templateMapper = templateMapper;
    }

    /**
     * 按来源行取回消息模板详情
     *
     * <p>字段按 {@code MessageRequest}（发送一条模板消息要什么）对齐：
     * {@code sourceCode} → templateId、{@code type} → contentType、
     * {@code content} → content、{@code params} → templateParams 的键；
     * {@code channel} 与发送时同一套解析规则（厂家优先，其次提供商类型）。</p>
     *
     * @param providerId 提供商 ID（可为空，为空时取模板自身记录的提供商）
     * @param sourceId   来源行 ID（sys_message_template_id）
     * @return 模板详情，取不到返回 {@code null}
     */
    @Override
    public TemplateSourceDetail detail(String providerId, String sourceId) {
        Integer templateRowId = parseId(sourceId);
        if (templateRowId == null) {
            return null;
        }
        SysMessageTemplate template = templateMapper.selectById(templateRowId);
        if (template == null) {
            return null;
        }
        Integer providerRowId = parseId(providerId);
        if (providerRowId == null) {
            providerRowId = template.getSysMessageProviderId();
        }
        SysMessageProvider provider = providerRowId == null ? null : providerMapper.selectById(providerRowId);
        String content = template.getSysMessageTemplateContent();
        return new TemplateSourceDetail(
                null,
                template.getSysMessageTemplateName(),
                SOURCE,
                true,
                String.valueOf(template.getSysMessageTemplateId()),
                template.getSysMessageTemplateCode(),
                provider == null ? providerId : String.valueOf(provider.getSysMessageProviderId()),
                provider == null ? null : provider.getSysMessageProviderName(),
                resolveChannel(provider),
                template.getSysMessageTemplateType(),
                content,
                TemplateContentParams.of(content),
                template.getSysMessageTemplateRemark());
    }

    /**
     * 解析渠道标识
     *
     * <p>与消息模块发送时的规则保持一致：优先厂家编码，其次提供商类型
     * （见 {@code SysMessageServiceImpl#resolveChannel}）。</p>
     *
     * @param provider 消息提供商
     * @return 渠道标识；无提供商时返回 {@code null}
     */
    private static String resolveChannel(SysMessageProvider provider) {
        if (provider == null) {
            return null;
        }
        String vender = provider.getSysMessageProviderVender();
        return vender != null && !vender.trim().isEmpty() ? vender : provider.getSysMessageProviderType();
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<TemplateProviderInfo> providers() {
        List<SysMessageProvider> providers = providerMapper.selectList(
                Wrappers.<SysMessageProvider>lambdaQuery()
                        .eq(SysMessageProvider::getSysMessageProviderDelete, 0)
                        .orderByAsc(SysMessageProvider::getSysMessageProviderSort));
        List<TemplateProviderInfo> result = new ArrayList<>(providers.size());
        for (SysMessageProvider provider : providers) {
            result.add(toInfo(provider));
        }
        return result;
    }

    @Override
    public TemplateProviderSyncResult sync(String providerId) {
        TemplateProviderSyncResult result = new TemplateProviderSyncResult();
        Integer id = parseId(providerId);
        if (id == null) {
            log.warn("[MessageProviderSource] 提供商 ID 非法: {}", providerId);
            result.setFailed(1);
            return result;
        }
        SysMessageProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            log.warn("[MessageProviderSource] 提供商不存在: {}", providerId);
            result.setFailed(1);
            return result;
        }
        // 远端模板拉取由消息模块的渠道实现负责，此处只登记同步意图：
        // 模板重新注册进统一库由 MessageTemplateSyncer 全量修复。
        result.setUpdated(0);
        log.info("[MessageProviderSource] 提供商 {} 同步请求已受理", providerId);
        return result;
    }

    /**
     * 消息提供商实体 → 模板库提供商模型
     *
     * @param provider 消息提供商
     * @return 模板库提供商模型
     */
    private static TemplateProviderInfo toInfo(SysMessageProvider provider) {
        TemplateProviderInfo info = new TemplateProviderInfo();
        info.setId(String.valueOf(provider.getSysMessageProviderId()));
        info.setName(provider.getSysMessageProviderName());
        info.setVendor(toVendor(provider.getSysMessageProviderVender()));
        // 凭据只在来源模块可见，上报到模板库时置空，避免在另一个系统里被读到
        info.setAk(null);
        info.setSk(null);
        info.setEndpoint(provider.getSysMessageProviderEndpoint());
        info.setTemplateTypes(channelTypes(provider.getSysMessageProviderType()));
        // 短信 / 邮件模板是内容型模板，不走 hiprint 拖拽设计
        info.setCustomDesign(false);
        info.setStatus(Integer.valueOf(1).equals(provider.getSysMessageProviderStatus()) ? "online" : "offline");
        info.setSource(SOURCE);
        info.setOwner(provider.getSysMessageProviderRemark());
        info.setRemark(provider.getSysMessageProviderRemark());
        return info;
    }

    /**
     * 厂家标识归一：消息模块用自由文本，模板库只认三种
     *
     * @param vender 厂家标识
     * @return aliyun / tencent / self
     */
    private static String toVendor(String vender) {
        if (vender == null) {
            return "self";
        }
        String lower = vender.trim().toLowerCase();
        if (lower.contains("ali")) {
            return "aliyun";
        }
        if (lower.contains("tencent") || lower.contains("qq")) {
            return "tencent";
        }
        return "self";
    }

    /**
     * 渠道类型 → 模板类型列表
     *
     * <p>{@code sys_message_provider_type} 的语义是渠道（EMAIL / SMS / WECHAT …），
     * 因此这里直接按渠道映射；认不出时按「短信 + 邮件」双能力上报，
     * 与 {@link MessageTemplateSyncer} 的 mapType 保持同一套判定。</p>
     *
     * @param type 渠道类型：EMAIL / SMS / WECHAT …
     * @return 模板类型列表
     */
    private static List<String> channelTypes(String type) {
        if (type == null) {
            return new ArrayList<>(Arrays.asList("sms", "email"));
        }
        switch (type.trim().toUpperCase()) {
            case "EMAIL":
            case "SMTP":
            case "MAIL":
                return new ArrayList<>(Arrays.asList("email"));
            case "SMS":
                return new ArrayList<>(Arrays.asList("sms"));
            default:
                return new ArrayList<>(Arrays.asList("sms", "email"));
        }
    }

    /**
     * 提供商 ID 解析
     *
     * @param providerId 字符串形式的提供商 ID
     * @return 数字 ID，非法返回 {@code null}
     */
    private static Integer parseId(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(providerId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
