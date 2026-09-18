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
    */
    List<SysAiSessionGroup> list();

    /**
    * 创建分组
    */
    SysAiSessionGroup create(String name);

    /**
    * 更新分组
    */
    SysAiSessionGroup update(Long id, String name);

    /**
    * 删除分组
    */
    void delete(Long id);

    /**
    * 排序
    */
    void reorder(List<Long> ids);
}
