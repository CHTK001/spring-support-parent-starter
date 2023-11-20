package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;

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
     * 保存或更新配置
     *
     * @param t t
     * @return {@link ResultData}<{@link Boolean}>
     */
    ResultData<Boolean> saveOrUpdateConfig(T t);
}
