package com.chua.starter.test.mapper;

import com.chua.starter.datasource.annotation.DS;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 数据访问层（映射器）：基于 {@link JdbcTemplate} 的简单 去中心化自治组织。
 *
 * <p>{@code @DS} 既可标注在 Service 方法上，也可直接标注在 mapper 方法上——本类演示
 * 映射器 级数据源切换（{@code queryRegionDs1}/{@code queryRegionDs2}）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Repository
public class DsRegionMapper {

    private final JdbcTemplate jdbcTemplate; // jdbctemplate

    /**
     * dsregion映射器。
     * @param jdbcTemplate jdbctemplate
     */
    public DsRegionMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 无 @DS：使用当前线程路由的数据源（由 服务 层 @DS 决定，缺省为默认数据源）
     *
     * @return 查询region的结果
     */
    public String queryRegion() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }

    /**
     * 映射器 级 @DS：直接切换到 ds1 数据源查询
     *
     * @return 查询regionds1的结果
     */
    @DS("ds1")
    public String queryRegionDs1() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }

    /**
     * 映射器 级 @DS：直接切换到 ds2 数据源查询
     *
     * @return 查询regionds2的结果
     */
    @DS("ds2")
    public String queryRegionDs2() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }
}
