package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduPaperTemplateMapper;
import com.chua.starter.eduai.model.EduPaperTemplate;
import com.chua.starter.eduai.model.dto.PaperTemplateSaveDTO;
import com.chua.starter.eduai.service.IEduPaperTemplateService;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 组卷模板服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class EduPaperTemplateServiceImpl extends ServiceImpl<EduPaperTemplateMapper, EduPaperTemplate>
        implements IEduPaperTemplateService {

    /**
     * 默认大标题风格
     */
    private static final String DEFAULT_TEMPLATE_STYLE = "classic";

    /**
     * 默认纸张规格
     */
    private static final String DEFAULT_PAGE_SIZE = "A4";

    /**
     * 默认纸张方向
     */
    private static final String DEFAULT_ORIENTATION = "PORTRAIT";

    /**
     * 默认页码
     */
    private static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页大小
     */
    private static final int DEFAULT_PAGE_SIZE_VALUE = 10;

    @Override
    /**
     * Page查询
     */
    public PageResult<EduPaperTemplate> pageQuery(String name, String style, Integer status,
                                                  Integer pageNum, Integer pageSize) {
        long current = pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE_VALUE : pageSize;
        Page<EduPaperTemplate> page = new Page<>(current, size);
        IPage<EduPaperTemplate> result = baseMapper.pageSelect(page, name, style, status, EduUserContext.ownerScope());
        return PageResult.<EduPaperTemplate>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    /**
     * 获取组卷模板详情
     */
    public EduPaperTemplate getTemplate(Long templateId) {
        EduPaperTemplate template = baseMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("组卷模板不存在");
        }
        EduUserContext.checkReadable(template.getOwnerUserId());
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增组卷模板
     *
     * @param dto dto
     * @return 新增组卷模板的结果
     */
    public Long createTemplate(PaperTemplateSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.templateName())) {
            throw new BusinessException("模板名称不能为空");
        }
        EduPaperTemplate entity = new EduPaperTemplate();
        entity.setTemplateName(dto.templateName());
        entity.setTemplateStyle(StringUtils.hasText(dto.templateStyle())
                ? dto.templateStyle() : DEFAULT_TEMPLATE_STYLE);
        entity.setTemplateHeader(dto.templateHeader());
        entity.setTemplateFooter(dto.templateFooter());
        entity.setTemplatePageSize(StringUtils.hasText(dto.templatePageSize())
                ? dto.templatePageSize() : DEFAULT_PAGE_SIZE);
        entity.setTemplateOrientation(StringUtils.hasText(dto.templateOrientation())
                ? dto.templateOrientation() : DEFAULT_ORIENTATION);
        entity.setTemplateStatus(dto.templateStatus() != null ? dto.templateStatus() : 1);
        entity.setOwnerUserId(EduUserContext.requireUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(entity);
        return entity.getTemplateId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新组卷模板
     *
     * @param templateId templateid
     * @param dto dto
     * @return 更新组卷模板的结果
     */
    public boolean updateTemplate(Long templateId, PaperTemplateSaveDTO dto) {
        EduPaperTemplate exists = baseMapper.selectById(templateId);
        if (exists == null) {
            throw new BusinessException("组卷模板不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        if (dto == null) {
            return false;
        }
        if (StringUtils.hasText(dto.templateName())) {
            exists.setTemplateName(dto.templateName());
        }
        if (StringUtils.hasText(dto.templateStyle())) {
            exists.setTemplateStyle(dto.templateStyle());
        }
        if (dto.templateHeader() != null) {
            exists.setTemplateHeader(dto.templateHeader());
        }
        if (dto.templateFooter() != null) {
            exists.setTemplateFooter(dto.templateFooter());
        }
        if (StringUtils.hasText(dto.templatePageSize())) {
            exists.setTemplatePageSize(dto.templatePageSize());
        }
        if (StringUtils.hasText(dto.templateOrientation())) {
            exists.setTemplateOrientation(dto.templateOrientation());
        }
        if (dto.templateStatus() != null) {
            exists.setTemplateStatus(dto.templateStatus());
        }
        exists.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(exists) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除组卷模板
     *
     * @param templateId templateid
     * @return 删除组卷模板的结果
     */
    public boolean deleteTemplate(Long templateId) {
        EduPaperTemplate exists = baseMapper.selectById(templateId);
        if (exists == null) {
            return false;
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        return baseMapper.deleteById(templateId) > 0;
    }
}
