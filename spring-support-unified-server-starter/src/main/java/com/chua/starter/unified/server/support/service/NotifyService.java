package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;

/**
 * 通知服务
 *
 * @author CH
 * @since 2023/11/20
 */
public interface NotifyService<T> extends IService<T> {

    /**
     * 通知
     *
     * @param t 统一配置
     * @return {@link Boolean}
     */
    Boolean notifyConfig(T t);

    /**
     * 通知客户端
     *
     * @param unifiedExecuterItem 统一执行器项目
     * @param t                   t
     * @return {@link Boolean}
     */
    Boolean notifyClient(UnifiedExecuterItem unifiedExecuterItem, T t);
    /**
     * 保存或更新配置
     *
     * @param t t
     * @return {@link ResultData}<{@link Boolean}>
     */
    ResultData<Boolean> saveOrUpdateConfig(T t);
}
