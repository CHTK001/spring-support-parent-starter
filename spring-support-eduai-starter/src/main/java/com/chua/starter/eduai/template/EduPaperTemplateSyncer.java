package com.chua.starter.eduai.template;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.eduai.model.EduPaperTemplate;
import com.chua.starter.eduai.mapper.EduPaperTemplateMapper;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSourceRequest;
import com.chua.starter.template.support.service.SourceSyncer;
import com.chua.starter.template.support.service.TemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组卷模板 → 统一模板库（template_info）同步器
 *
 * <p>把 {@code edu_paper_template} 中启用的组卷模板接入统一模板库，供 {@code pages/template}
 * 集中展示。来源端负责增删改，同步器每 5 分钟（或页面「同步」按钮）全量修复一次。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
public class EduPaperTemplateSyncer implements SourceSyncer {

    /** 来源标识 */
    public static final String SOURCE = "paper";

    /** 库中一次查询上限 */
    private static final int PAGE_SIZE = 500;

    private final EduPaperTemplateMapper paperTemplateMapper;
    private final TemplateService templateService;

    public EduPaperTemplateSyncer(EduPaperTemplateMapper paperTemplateMapper,
                                  TemplateService templateService) {
        this.paperTemplateMapper = paperTemplateMapper;
        this.templateService = templateService;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public int sync() {
        List<EduPaperTemplate> templates = getSourceTemplates();
        Set<String> sourceIds = new HashSet<>(templates.size());
        int result = 0;
        for (EduPaperTemplate template : templates) {
            try {
                register(template);
                result++;
                sourceIds.add(String.valueOf(template.getTemplateId()));
            } catch (Exception e) {
                log.warn("[EduPaperTemplateSyncer] 同步模板失败: id={}", template.getTemplateId(), e);
            }
        }
        // 清理库中已不在来源的冗余行（来源端已停用），保持来源关系一致
        cleanUpTemplates(sourceIds);
        log.info("[EduPaperTemplateSyncer] 同步完成，总数: {}", result);
        return result;
    }

    /**
     * 注册：将组卷模板行映射到统一模板库
     *
     * @param template 来源模板行
     * @return 注册后的模板信息
     */
    @Override
    public TemplateInfo register(TemplateSourceRequest request) {
        return templateService.registerTemplate(request);
    }

    /**
     * 注册：将组卷模板行映射到统一模板库（来源行重载方法）
     *
     * @param template 来源模板行
     * @return 注册后的模板信息
     */
    public TemplateInfo register(EduPaperTemplate template) {
        return templateService.registerTemplate(request(template));
    }

    /**
     * 清理：库中没有对应来源行的模板（来源端已停用）
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
                    log.info("[EduPaperTemplateSyncer] 清理来源失效模板: id={}", row.getTemplateInfoId());
                    removed++;
                } catch (Exception e) {
                    log.warn("[EduPaperTemplateSyncer] 清理模板失败: id={}", row.getTemplateInfoId(), e);
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
    private List<EduPaperTemplate> getSourceTemplates() {
        return paperTemplateMapper.selectList(
                Wrappers.<EduPaperTemplate>lambdaQuery()
                        .eq(EduPaperTemplate::getTemplateStatus, 1)
                        .orderByDesc(EduPaperTemplate::getTemplateId));
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
     * @return 注册请求
     */
    private static TemplateSourceRequest request(EduPaperTemplate template) {
        return new TemplateSourceRequest(
            SOURCE,
            String.valueOf(template.getTemplateId()),
            // 组卷模板在来源侧没有独立的模板编码，三方模板 ID 就是来源行 ID
            null,
            template.getOwnerUserId(),
            template.getTemplateName(),
            template.getTemplateStyle(),
            "json",
            "document",
            template.getTemplateStatus() != null && template.getTemplateStatus() == 1
                ? "online" : "offline",
            template.getTemplateStyle(),
            content(template));
    }

    /**
     * 组卷内容：JSON 描述（大标题风格/页眉页脚/纸张规格/纸张方向）
     *
     * @param template 模板行
     * @return 内容 JSON
     */
    private static String content(EduPaperTemplate template) {
        try {
            return new ObjectMapper().writeValueAsString(
                    Map.of(
                            "templateStyle", template.getTemplateStyle(),
                            "templateHeader", template.getTemplateHeader(),
                            "templateFooter", template.getTemplateFooter(),
                            "templatePageSize", template.getTemplatePageSize(),
                            "templateOrientation", template.getTemplateOrientation()));
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
