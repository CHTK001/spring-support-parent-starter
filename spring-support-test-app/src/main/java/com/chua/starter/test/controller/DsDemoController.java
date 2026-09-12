package com.chua.starter.test.controller;

import com.chua.starter.test.mapper.DsRegionMapper;
import com.chua.starter.test.service.DsSwitchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
   * 多数据源（@DS）演示控制器：暴露数据源路由查询接口，供接口级（mockmvc）测试驱动。
 *
 * <p>{@code /api/ds/region} 无 {@code @DS} → 默认数据源（master 主库）；
 * {@code /api/ds/region/ds1|ds2} → Service 级 {@code @DS} 路由；
 * {@code /api/ds/region/mapper/ds1|ds2} → mapper 级 {@code @DS} 路由。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@RestController
@RequestMapping("/api/ds")
public class DsDemoController {

    private final DsSwitchService dsSwitchService; // dsswitch服务
    private final DsRegionMapper dsRegionMapper; // dsregion映射器

    /**
     * dsdemo控制器。
     * @param dsSwitchService dsswitch服务
     * @param dsRegionMapper dsregion映射器
     */
    public DsDemoController(DsSwitchService dsSwitchService, DsRegionMapper dsRegionMapper) {
        this.dsSwitchService = dsSwitchService;
        this.dsRegionMapper = dsRegionMapper;
    }

    /**
     * 无 @DS：默认数据源（master 主库）
     *
     * @return 默认region的结果
     */
    @GetMapping("/region")
    public Map<String, Object> defaultRegion() {
        return Map.of("data", dsSwitchService.defaultRegion());
    }

    /**
     * 服务 级 @DS：路由到 ds1
     *
     * @return region从ds1的结果
     */
    @GetMapping("/region/ds1")
    public Map<String, Object> regionFromDs1() {
        return Map.of("data", dsSwitchService.regionFromDs1());
    }

    /**
     * 服务 级 @DS：路由到 ds2
     *
     * @return region从ds2的结果
     */
    @GetMapping("/region/ds2")
    public Map<String, Object> regionFromDs2() {
        return Map.of("data", dsSwitchService.regionFromDs2());
    }

    /**
     * 映射器 级 @DS：路由到 ds1
     *
     * @return 映射器regionds1的结果
     */
    @GetMapping("/region/mapper/ds1")
    public Map<String, Object> mapperRegionDs1() {
        return Map.of("data", dsRegionMapper.queryRegionDs1());
    }

    /**
     * 映射器 级 @DS：路由到 ds2
     *
     * @return 映射器regionds2的结果
     */
    @GetMapping("/region/mapper/ds2")
    public Map<String, Object> mapperRegionDs2() {
        return Map.of("data", dsRegionMapper.queryRegionDs2());
    }
}
