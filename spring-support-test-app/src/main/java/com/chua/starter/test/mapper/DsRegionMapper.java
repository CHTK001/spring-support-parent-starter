package com.chua.starter.test.mapper;

import com.chua.starter.datasource.annotation.DS;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 数据访问层（mapper）：基于 {@link JdbcTemplate} 的简单 DAO。
 *
 * <p>{@code @DS} 既可标注在 Service 方法上，也可直接标注在 mapper 方法上——本类演示
 * mapper 级数据源切换（{@code queryRegionDs1}/{@code queryRegionDs2}）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Repository
public class DsRegionMapper {

    private final JdbcTemplate jdbcTemplate;

    public DsRegionMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 无 @DS：使用当前线程路由的数据源（由 Service 层 @DS 决定，缺省为默认数据源） */
    public String queryRegion() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }

    /** mapper 级 @DS：直接切换到 ds1 数据源查询 */
    @DS("ds1")
    public String queryRegionDs1() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }

    /** mapper 级 @DS：直接切换到 ds2 数据源查询 */
    @DS("ds2")
    public String queryRegionDs2() {
        return jdbcTemplate.queryForObject("select data from region", String.class);
    }
}
