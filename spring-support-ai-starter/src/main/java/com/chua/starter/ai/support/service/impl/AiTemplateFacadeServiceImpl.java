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
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class AiTemplateFacadeServiceImpl implements AiTemplateFacadeService {

    /**
     * SYSAI模板映射器
    */
    private final SysAiTemplateMapper sysAiTemplateMapper;

    /**
     * 根据条件查询AI模板列表。
     *
     * @param moduleId    模块标识（可选）
     * @param templateType 模板类型（可选）
     * @param category    分类（可选）
     * @param name        模板名称模糊匹配（可选）
     * @param remark      备注模糊匹配（可选）
     * @return 启用的模板列表，按排序字段升序、创建时间降序排列
     */
    @Override
    public List<SysAiTemplate> list(Integer moduleId, String templateType, String category, String name, String remark) {
        return sysAiTemplateMapper.selectList(
                Wrappers.lambdaQuery(SysAiTemplate.class)
                        .eq(moduleId != null, SysAiTemplate::getSysAiModuleId, moduleId)
                        .eq(StringUtils.isNotBlank(templateType), SysAiTemplate::getType, templateType)
                        .eq(StringUtils.isNotBlank(category), SysAiTemplate::getCategory, category)
                        .likeRight(StringUtils.isNotBlank(name), SysAiTemplate::getName, name)
                        .likeRight(StringUtils.isNotBlank(remark), SysAiTemplate::getRemark, remark)
                        .eq(SysAiTemplate::getStatus, 1)
                        .orderByAsc(SysAiTemplate::getSort)
                        .orderByDesc(SysAiTemplate::getCreateTime)
        );
    }

    /**
     * 保存新AI模板，自动初始化标识为空、排序为0、状态为1及时间戳。
     *
     * @param template 待保存的模板对象
     * @return 保存后的模板对象（含生成的ID）
     */
    @Override
    public SysAiTemplate save(SysAiTemplate template) {
        template.setId(null);
        template.setSort(template.getSort() == null ? 0 : template.getSort());
        template.setStatus(template.getStatus() == null ? 1 : template.getStatus());
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        sysAiTemplateMapper.insert(template);
        return template;
    }

    /**
     * 根据主键更新AI模板，仅更新非空字段。
     *
     * @param template 待更新的模板对象
     * @return 是否更新成功
     */
    @Override
    public boolean update(SysAiTemplate template) {
        template.setUpdateTime(LocalDateTime.now());
        return sysAiTemplateMapper.updateById(template) > 0;
    }

    /**
     * 根据主键删除AI模板。
     *
     * @param templateId 模板标识
     * @return 是否删除成功
     */
    @Override
    public boolean deleteById(Integer templateId) {
        return templateId != null && sysAiTemplateMapper.deleteById(templateId) > 0;
    }
}
