package com.chua.starter.test.service;

import com.chua.starter.datasource.annotation.DS;
import com.chua.starter.test.mapper.DsRegionMapper;
import org.springframework.stereotype.Service;

/**
   * 多数据源切换演示（{@code @DS}，服务 级）。
 *
 * <p>{@code @DS("ds1")}/{@code @DS("ds2")} 标注在 Service 方法上，方法内经 mapper
 * 发起的查询会路由到对应数据源；无 {@code @DS} 的方法走默认数据源。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Service
public class DsSwitchService {

    private final DsRegionMapper regionMapper; // region映射器

    /**
     * dsswitch服务。
     * @param regionMapper region映射器
     */
    public DsSwitchService(DsRegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    /**
     * 无 @DS：默认数据源
     *
     * @return 默认region的结果
     */
    public String defaultRegion() {
        return regionMapper.queryRegion();
    }

    /**
     * 服务 级 @DS：方法内所有 映射器 查询路由到 ds1
     *
     * @return region从ds1的结果
     */
    @DS("ds1")
    public String regionFromDs1() {
        return regionMapper.queryRegion();
    }

    /**
     * 服务 级 @DS：方法内所有 映射器 查询路由到 ds2
     *
     * @return region从ds2的结果
     */
    @DS("ds2")
    public String regionFromDs2() {
        return regionMapper.queryRegion();
    }
}
