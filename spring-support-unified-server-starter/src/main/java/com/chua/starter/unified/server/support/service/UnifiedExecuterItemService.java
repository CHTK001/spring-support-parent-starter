package com.chua.starter.unified.server.support.service;

import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.pojo.ActuatorQuery;

import java.io.Serializable;
import java.util.List;

/**
 * 统一执行器项目服务
 *
 * @author CH
 */
public interface UnifiedExecuterItemService extends NotifyService<UnifiedExecuterItem>{


    /**
     * 获取全部
     *
     * @return {@link List}<{@link UnifiedExecuterItem}>
     */
    List<UnifiedExecuterItem> getAll();

    /**
     * 获取oshi
     *
     * @param dataId 数据id
     * @return {@link JsonObject}
     */
    JsonObject getOshi(String dataId);
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
     * @param unifiedExecuterItemId 统一执行器子项id
     */
    void remove(Serializable unifiedExecuterItemId);
    /**
     *删除
     *
     * @param unifiedExecuterId 统一执行器id
     */
    void removeExecuterId(Serializable unifiedExecuterId);

    /**
     * 通过ID查询数据
     *
     * @param unifiedExecuterItemId id
     * @return UnifiedExecuterItem
     */
    UnifiedExecuterItem get(Serializable unifiedExecuterItemId);

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

    /**
     * 获取执行器查询
     *
     * @param dataId 数据id
     * @return {@link ActuatorQuery}
     */
    ActuatorQuery getActuatorQuery(String dataId);

    /**
     * 收到进程
     * 获取进程
     *
     * @param dataId   数据id
     * @param status   状态
     * @param keyword  关键字
     * @param page     分页
     * @param pageSize 分页大小
     * @return {@link JsonObject}
     */
    Page<JsonObject> getProcess(String dataId, String status, String keyword, Integer page, Integer pageSize);
}
