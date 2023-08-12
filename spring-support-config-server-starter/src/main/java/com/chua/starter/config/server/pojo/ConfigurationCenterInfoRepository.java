package com.chua.starter.config.server.pojo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * TConfigurationCenterInfo
 *
 * @author CH
 * @since 2022/8/1 13:06
 */
@SuppressWarnings("ALL")
@Repository
public interface ConfigurationCenterInfoRepository extends
        PagingAndSortingRepository<ConfigurationCenterInfo, Integer>,
        CrudRepository<ConfigurationCenterInfo, Integer>,
        JpaRepository<ConfigurationCenterInfo, Integer> {
    /**
     * 删除数据
     *
     * @param binderName 名称
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM CONFIGURATION_CENTER_INFO WHERE config_item = ?1", nativeQuery = false)
    void delete(String binderName);

    /**
     * 更新數據
     *
     * @param name       字段
     * @param binderName itemName
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE CONFIGURATION_CENTER_INFO SET config_value = ?2 WHERE config_profile = ?3 AND config_item = ?4 AND config_name = ?1", nativeQuery = false)
    void update(String name, String value, String profile, String binderName);

    /**
     * 更新數據
     *
     * @param configValue 值
     * @param disable     是否可用
     * @param configId    ID
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE CONFIGURATION_CENTER_INFO  SET config_value = ?1, disable = ?2 WHERE config_id = ?3", nativeQuery = false)
    void update(String configValue, Integer disable, Integer configId);

    /**
     * 查询binder
     *
     * @param configItem 标识
     * @return 配置
     */
    @Query(value = "SELECT t.* FROM CONFIGURATION_CENTER_INFO t  WHERE config_item = ?1 AND (config_name = 'binder-port' OR config_name = 'binder-client')", nativeQuery = true)
    List<ConfigurationCenterInfo> findBinder(String configItem);

    /**
     * 查询binder
     *
     * @param binderName 标识
     * @param profile    环境
     * @return 配置
     */
    @Query(value = "SELECT t.* FROM CONFIGURATION_CENTER_INFO t WHERE disable = 0 AND config_profile = ?2 and (config_item = ?1 or config_id in (select config_id from CONFIGURATION_DISTRIBUTE_INFO  where config_item = ?1))", nativeQuery = true)
    List<ConfigurationCenterInfo> list(String binderName, String profile);
    /**
     * 查询binder
     *
     * @param configId 标识
     * @return 配置
     */
    @Query(value = "SELECT distinct t.* FROM CONFIGURATION_CENTER_INFO t " +
            "WHERE t.config_item in (select config_item from CONFIGURATION_DISTRIBUTE_INFO  where config_id = ?1) " +
            "or t.config_item in (SELECT config_item FROM CONFIGURATION_CENTER_INFO WHERE config_id = ?1) and t.config_name = 'binder-port' or t.config_name = 'binder-client'", nativeQuery = true)
    List<ConfigurationCenterInfo> listByConfigId(Integer configId);

    /**
     * 环境
     * @return 环境
     */
    @Query(value = "SELECT config_profile FROM CONFIGURATION_CENTER_INFO GROUP BY config_profile ", nativeQuery = true)
    Set<String> profile();
}
