package com.chua.starter.message.support.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.message.support.entity.SysMessageProvider;
import com.chua.starter.message.support.entity.SysMessageTemplate;
import com.chua.starter.message.support.mapper.SysMessageProviderMapper;
import com.chua.starter.message.support.mapper.SysMessageTemplateMapper;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSourceRequest;
import com.chua.starter.template.support.service.SourceSyncer;
import com.chua.starter.template.support.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消息模板 → 统一模板库（template_info）同步器
 *
 * <p>把 {@code sys_message_template} 与 {@code sys_message_provider} 中的数据接入
 * 统一模板库，供 {@code pages/template} 集中展示。来源端负责增删改，同步器每 5 分钟
 * （或页面「同步」按钮）全量修复一次，确保库中行的来源关系与来源端一致。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
public class MessageTemplateSyncer implements SourceSyncer {

    /** 来源标识 */
    public static final String SOURCE = "message";

    /** 短信 */
    private static final String SOURCE_CHIP = "sms";

    /** 库中一次查询上限（来源数据不可控） */
    private static final int PAGE_SIZE = 500;

    private final SysMessageTemplateMapper templateMapper;
    private final SysMessageProviderMapper providerMapper;
    private final TemplateService templateService;

    public MessageTemplateSyncer(SysMessageTemplateMapper templateMapper,
                                 SysMessageProviderMapper providerMapper,
                                 TemplateService templateService) {
        this.templateMapper = templateMapper;
        this.providerMapper = providerMapper;
        this.templateService = templateService;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public int sync() {
        List<SysMessageTemplate> templates = getSourceTemplates();
        Set<String> sourceIds = new HashSet<>(templates.size());
        int result = 0;
        for (SysMessageTemplate template : templates) {
            try {
                register(template);
                result++;
                sourceIds.add(String.valueOf(template.getSysMessageTemplateId()));
            } catch (Exception e) {
                log.warn("[MessageTemplateSyncer] 同步模板失败: id={}",
                        template.getSysMessageTemplateId(), e);
            }
        }
        // 清理库中已不在来源的冗余行（软删除），保持来源关系一致
        cleanUpTemplates(sourceIds);
        log.info("[MessageTemplateSyncer] 同步完成，总数: {}", result);
        return result;
    }

    /**
     * 注册：将消息模板行映射到统一模板库
     *
     * @param template 源模板行
     * @return 注册后的模板信息
     */
    @Override
    public TemplateInfo register(TemplateSourceRequest request) {
        return templateService.registerTemplate(request);
    }

    /**
     * 注册：将消息模板行映射到统一模板库（来源行重载方法）
     *
     * @param template 来源模板行
     * @return 注册后的模板信息
     */
    public TemplateInfo register(SysMessageTemplate template) {
        SysMessageProvider provider = providerMapper.selectById(template.getSysMessageProviderId());
        return templateService.registerTemplate(request(template, provider));
    }

    /**
     * 清理：库中没有对应来源行的模板（来源端已删除）
     *
     * @param sourceIds 来源端已存在模板 ID 集合
     * @return 清理数
     */
    private int cleanUpTemplates(Set<String> sourceIds) {
        int removed = 0;
        List<TemplateInfo> rows = templateService.listTemplates(query(SOURCE));
        for (TemplateInfo row : rows) {
            if (!sourceIds.contains(row.getTemplateInfoSourceId())) {
                try {
                    templateService.removeTemplate(row.getTemplateInfoId());
                    log.info("[MessageTemplateSyncer] 清理来源失效模板: id={}", row.getTemplateInfoId());
                    removed++;
                } catch (Exception e) {
                    log.warn("[MessageTemplateSyncer] 清理模板失败: id={}", row.getTemplateInfoId(), e);
                }
            }
        }
        return removed;
    }

    /**
     * 来源排序 + 分页查询条件
     *
     * @return 来源全量模板行，id 倒序
     */
    private List<SysMessageTemplate> getSourceTemplates() {
        return templateMapper.selectList(
                Wrappers.<SysMessageTemplate>lambdaQuery()
                        .select(SysMessageTemplate::getSysMessageTemplateId,
                                SysMessageTemplate::getSysMessageTemplateDelete)
                        .eq(SysMessageTemplate::getSysMessageTemplateDelete, 0)
                        .orderByDesc(SysMessageTemplate::getSysMessageTemplateId));
    }

    /**
     * 统一模板库查询条件（按来源）
     *
     * @param source 来源标识
     * @return 查询条件
     */
    private static TemplatePageQuery query(String source) {
        TemplatePageQuery query = new TemplatePageQuery();
        query.setSource(source);
        query.setSize(PAGE_SIZE);
        return query;
    }

    /**
     * 模板行 → 来源注册请求
     *
     * @param template 模板行
     * @param provider 提供商（可空的参考值）
     * @return 注册请求
     */
    private static TemplateSourceRequest request(SysMessageTemplate template, SysMessageProvider provider) {
        TemplateSourceRequest request = new TemplateSourceRequest();
        request.setSource(SOURCE);
        request.setSourceId(String.valueOf(template.getSysMessageTemplateId()));
        request.setProviderId(provider == null ? null : String.valueOf(provider.getSysMessageProviderId()));
        request.setName(template.getSysMessageTemplateName());
        request.setSubtitle(template.getSysMessageTemplateCode());
        request.setLanguage("html");
        request.setType(mapType(template.getSysMessageTemplateType()));
        request.setStatus(template.getSysMessageTemplateStatus() != null && template.getSysMessageTemplateStatus() == 1
                ? "online" : "offline");
        request.setRemark(template.getSysMessageTemplateRemark());
        request.setContent(template.getSysMessageTemplateContent());
        return request;
    }

    /**
     * 消息类型 → 统一模板类型
     *
     * @param type 消息类型：sms / email / wechat / other
     * @return 模板类型
     */
    private static String mapType(String type) {
        if (type == null) {
            return SOURCE_CHIP;
        }
        switch (type.trim().toLowerCase()) {
            case "sms":
                return SOURCE_CHIP;
            case "email":
            case "smtp":
            case "mail":
                return "email";
            default:
                return SOURCE_CHIP;
        }
    }
}
