package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.entity.SysAiModule;

import java.util.List;

/**
 * AI 模型分组服务——管理模型分组及分组-模型关联。
 * <p>
 * 每个分组关联多个模型，AppKey/Token 通过绑定分组来控制可访问的模型范围。
 *
 * @author CH
 */
public interface SysAiGroupService {

    /**
     * 列出所有未删除的分组。
     *
     * @return 分组列表
     */
    List<SysAiGroup> list();

    /**
     * 保存分组。
     *
     * @param group 分组实体
     * @return 保存后的分组
     */
    SysAiGroup save(SysAiGroup group);

    /**
     * 更新分组。
     *
     * @param group 分组实体
     * @return 是否成功
     */
    boolean update(SysAiGroup group);

    /**
     * 逻辑删除分组。
     *
     * @param groupId 分组 ID
     * @return 是否成功
     */
    boolean deleteById(Integer groupId);

    /**
     * 为分组添加模型关联。
     *
     * @param groupId   分组 ID
     * @param moduleIds 模型 ID 列表
     */
    void addModels(Integer groupId, List<Integer> moduleIds);

    /**
     * 从分组移除模型关联。
     *
     * @param groupId   分组 ID
     * @param moduleIds 模型 ID 列表
     */
    void removeModels(Integer groupId, List<Integer> moduleIds);

    /**
     * 获取分组关联的模型列表。
     *
     * @param groupId 分组 ID
     * @return 模型列表
     */
    List<SysAiModule> getGroupModels(Integer groupId);

    /**
     * 获取分组关联的模型 ID 列表。
     *
     * @param groupId 分组 ID
     * @return 模型 ID 列表
     */
    List<Integer> getGroupModelIds(Integer groupId);

    /**
     * 获取模型所属的分组列表。
     *
     * @param moduleId 模型 ID
     * @return 分组列表
     */
    List<SysAiGroup> getModuleGroups(Integer moduleId);

    /**
     * 替换分组下的全部模型关联（先删后插）。
     *
     * @param groupId   分组 ID
     * @param moduleIds 新的模型 ID 列表
     */
    void replaceModels(Integer groupId, List<Integer> moduleIds);
}
