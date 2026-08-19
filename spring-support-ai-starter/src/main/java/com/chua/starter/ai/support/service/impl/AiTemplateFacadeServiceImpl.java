package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiTemplate;
import com.chua.starter.ai.support.mapper.SysAiTemplateMapper;
import com.chua.starter.ai.support.service.AiTemplateFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模板门面服务实现。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class AiTemplateFacadeServiceImpl implements AiTemplateFacadeService {

    /** SYSAI模板mapper */
    private final SysAiTemplateMapper sysAiTemplateMapper;

    @Override
    public List<SysAiTemplate> list(Integer moduleId, String templateType, String category, String name, String remark) {
        return sysAiTemplateMapper.selectList(
                Wrappers.lambdaQuery(SysAiTemplate.class)
                        .eq(moduleId != null, SysAiTemplate::getSysAiModuleId, moduleId)
                        .eq(StringUtils.isNotBlank(templateType), SysAiTemplate::getSysAiTemplateType, templateType)
                        .eq(StringUtils.isNotBlank(category), SysAiTemplate::getSysAiTemplateCategory, category)
                        .likeRight(StringUtils.isNotBlank(name), SysAiTemplate::getSysAiTemplateName, name)
                        .likeRight(StringUtils.isNotBlank(remark), SysAiTemplate::getSysAiTemplateRemark, remark)
                        .eq(SysAiTemplate::getSysAiTemplateStatus, 1)
                        .orderByAsc(SysAiTemplate::getSysAiTemplateSort)
                        .orderByDesc(SysAiTemplate::getCreateTime)
        );
    }

    @Override
    public SysAiTemplate save(SysAiTemplate template) {
        template.setSysAiTemplateId(null);
        template.setSysAiTemplateSort(template.getSysAiTemplateSort() == null ? 0 : template.getSysAiTemplateSort());
        template.setSysAiTemplateStatus(template.getSysAiTemplateStatus() == null ? 1 : template.getSysAiTemplateStatus());
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        sysAiTemplateMapper.insert(template);
        return template;
    }

    @Override
    public boolean update(SysAiTemplate template) {
        template.setUpdateTime(LocalDateTime.now());
        return sysAiTemplateMapper.updateById(template) > 0;
    }

    @Override
    public boolean deleteById(Integer templateId) {
        return templateId != null && sysAiTemplateMapper.deleteById(templateId) > 0;
    }
}
