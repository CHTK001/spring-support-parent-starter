package com.chua.starter.config.server.pojo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TConfigurationDistributeInfo
 *
 * @author CH
 * @since 2022/8/1 13:06
 */
@SuppressWarnings("ALL")
@Repository
public interface ConfigurationDistributeInfoRepository extends
        PagingAndSortingRepository<ConfigurationDistributeInfo, Integer>,
        CrudRepository<ConfigurationDistributeInfo, Integer>,
        JpaRepository<ConfigurationDistributeInfo, Integer> {
    /**
     * 查询数据
     * @param configId 配置ID
     * @param configItem 标识
     * @return 结果
     */
    @Query(value = "select t.* from CONFIGURATION_DISTRIBUTE_INFO t where t.config_id in (?1) and t.config_item = ?2", nativeQuery = true)
    List<ConfigurationDistributeInfo> queryById(List<Integer> configId, String configItem);

    /**
     * 查询数据
     * @param configItem 标识
     * @return 结果
     */
    @Query(value = "select t.* from CONFIGURATION_DISTRIBUTE_INFO t where t.config_item = ?2", nativeQuery = true)
    List<ConfigurationDistributeInfo> queryById(String configItem);

    /**
     * 删除数据
     * @param configItem item
     */
    @Modifying
    @Transactional
    @Query(" DELETE FROM CONFIGURATION_DISTRIBUTE_INFO WHERE config_item = ?1")
    void deleteByConfigItem(String configItem);
}
