package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiSessionGroup;

import java.util.List;

/**
 * 会话分组 Service
 *
 * @author CH
 */
public interface SysAiSessionGroupService {

    /**
     * 查询所有分组
     * @return 结果列表，无数据时为空列表
     */
    List<SysAiSessionGroup> list();

    /**
     * 创建分组
     * @param name 名称，不允许为 null
     * @return SysAi会话分组 对象
     */
    SysAiSessionGroup create(String name);

    /**
     * 更新分组
     * @param id ID，不允许为 null
     * @param name 名称，不允许为 null
     * @return SysAi会话分组 对象
     */
    SysAiSessionGroup update(Long id, String name);

    /**
     * 删除分组
     * @param id ID，不允许为 null
     */
    void delete(Long id);

    /**
     * 批量删除分组
     * @param ids 分组 ID 列表，不允许为 null
     */
    void deleteBatch(List<Long> ids);

    /**
     * 排序
     * @param ids 方法入参 ids
     */
    void reorder(List<Long> ids);
}
