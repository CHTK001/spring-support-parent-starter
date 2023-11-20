package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;

import java.io.Serializable;
import java.util.List;

public interface UnifiedExecuterItemService extends IService<UnifiedExecuterItem>{


    /**
     * 获取全部
     *
     * @return {@link List}<{@link UnifiedExecuterItem}>
     */
    List<UnifiedExecuterItem> getAll();

    /**
     * 查找项目
     *
     * @param unifiedConfigProfile 统一配置文件
     * @return {@link List}<{@link UnifiedExecuterItem}>
     */
    List<UnifiedExecuterItem> findItem(String unifiedConfigProfile);

    /**
     *删除
     *
     * @param unifiedExecuterId 统一执行器id
     */
    void remove(Serializable unifiedExecuterId);

    /**
     * 保存或更新
     *
     * @param unifiedExecuterItem 统一执行器项目
     * @param unifiedExecuter     统一执行器
     */
    void saveOrUpdate(UnifiedExecuterItem unifiedExecuterItem, UnifiedExecuter unifiedExecuter);

    /**
     * 检查心脏
     *
     * @param request 请求
     */
    void checkHeart(BootRequest request);
}
