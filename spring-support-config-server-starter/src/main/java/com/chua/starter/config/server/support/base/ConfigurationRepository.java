package com.chua.starter.config.server.support.base;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Map;

/**
 * 配置中心统一jpa
 * @author CH
 */
public interface ConfigurationRepository<T> extends
        PagingAndSortingRepository<T, Integer>,
        CrudRepository<T, Integer>,
        JpaSpecificationExecutor<T>,
        JpaRepository<T, Integer> {

    /**
     * 数据解析
     *
     * @param applicationName   应用
     * @param data            数据
     */
    void analysis(String applicationName, Map<String, Object> data);

    /**
     * 获取订阅
     * @param subscribe 订阅
     * @param profile 环境
     * @return 结果
     */
    String getSubscribe(String subscribe, String profile);

    /**
     * 注销数据
     * @param applicationName 应用
     */
    void unregister(String applicationName);
}
