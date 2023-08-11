package com.chua.starter.config.server.pojo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

/**
 * String
 *
 * @author CH
 * @since 2023/8/11 13:06
 */
@SuppressWarnings("ALL")
@Repository
public interface ConfigurationMappingRepository extends
        PagingAndSortingRepository<ConfigurationMapping, String>,
        CrudRepository<ConfigurationMapping, String>,
        JpaRepository<ConfigurationMapping, String> {

}
