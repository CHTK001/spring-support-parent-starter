package com.chua.starter.config.server.support.base;

import com.chua.starter.config.server.support.protocol.ProtocolServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Map;
import java.util.Set;

/**
 * 配置中心统一jpa
 *
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
     * @param applicationName 应用
     * @param data            数据
     */
    void analysis(String applicationName, Map<String, Object> data);

    /**
     * 获取订阅
     *
     * @param subscribe 订阅
     * @param profile   环境
     * @return 结果
     */
    String getSubscribe(String subscribe, String profile);

    /**
     * 注销数据
     *
     * @param applicationName 应用
     */
    void unregister(String applicationName);

    /**
     * 查询数据
     *
     * @param profile  环境
     * @param pageable 分页
     * @return 结果
     */
    Page<?> findAll(String profile, Pageable pageable);

    /**
     * 获取环境
     *
     * @return 环境
     */
    Set<String> profile();

    /**
     * 获取应用
     *
     * @return 环境
     */
    Set<String> applications();

    /**
     * 通知
     *
     * @param protocolServer 协议
     * @param configId       ID
     * @param configValue    值
     * @param disable        是否禁用
     * @param o              o
     */
    void notifyConfig(ProtocolServer protocolServer, Integer configId, String configValue, Integer disable, Object o);
}
