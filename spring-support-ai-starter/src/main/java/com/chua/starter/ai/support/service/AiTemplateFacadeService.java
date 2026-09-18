package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiTemplate;

import java.util.List;

/**
* AI 模板门面服务接口。
*
* @since 4.0.0.42
* @author CH
 */
public interface AiTemplateFacadeService {

    /**
    * 按条件查询模板列表。
    *
    * @param moduleId     模块 标识
    * @param templateType 模板类型
    * @param category     分类
    * @param name         名称
    * @param remark       备注
    * @return 模板列表
    */
    List<SysAiTemplate> list(Integer moduleId, String templateType, String category, String name, String remark);

    /**
    * 新增模板。
    *
    * @param template 模板实体
    * @return 保存后的模板
    */
    SysAiTemplate save(SysAiTemplate template);

    /**
    * 更新模板。
    *
    * @param template 模板实体
    * @return 是否成功
    */
    boolean update(SysAiTemplate template);

    /**
    * 按 标识 删除模板。
    *
    * @param id 模板 标识
    * @return 是否成功
    */
    boolean deleteById(Integer id);
}
