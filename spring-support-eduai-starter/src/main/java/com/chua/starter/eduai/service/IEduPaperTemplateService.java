package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduPaperTemplate;
import com.chua.starter.eduai.model.dto.PaperTemplateSaveDTO;

/**
 * 组卷模板服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduPaperTemplateService extends IService<EduPaperTemplate> {

    /**
     * 分页查询组卷模板（非管理员仅返回本人归属行与系统预置行）。
     *
     * @param name     模板名称，模糊匹配，可为空
     * @param style    大标题风格，精确匹配，可为空
     * @param status   状态，可为空
     * @param pageNum  页码，为空或小于 1 时取 1
     * @param pageSize 每页大小，为空或小于 1 时取 10
     * @return 分页结果
     */
    PageResult<EduPaperTemplate> pageQuery(String name, String style, Integer status,
                                           Integer pageNum, Integer pageSize);

    /**
     * 获取组卷模板详情（校验读权限）。
     *
     * @param templateId 模板标识
     * @return 模板详情
     */
    EduPaperTemplate getTemplate(Long templateId);

    /**
     * 新增组卷模板（归属取当前登录用户）。
     *
     * @param dto 模板数据
     * @return 新增的模板标识
     */
    Long createTemplate(PaperTemplateSaveDTO dto);

    /**
     * 更新组卷模板（先校验写权限，归属保持不变）。
     *
     * @param templateId 模板标识
     * @param dto        更新内容，字段为空表示不修改
     * @return 是否成功
     */
    boolean updateTemplate(Long templateId, PaperTemplateSaveDTO dto);

    /**
     * 删除组卷模板（先校验写权限）。
     *
     * @param templateId 模板标识
     * @return 是否成功
     */
    boolean deleteTemplate(Long templateId);
}
