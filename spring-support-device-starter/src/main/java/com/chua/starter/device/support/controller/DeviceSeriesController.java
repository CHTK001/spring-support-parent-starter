package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.timeseries.DeviceSeriesCategory;
import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 设备时序存储接口（传感器 / 门禁 / 车闸共用）。
 *
 * <p>「传感器记录」页用 {@code /series/engine} 展示当前存储类型；其余端点用于
 * 运维核对与保留期清理，不再提供独立的「存储浏览」菜单 —— 存储只是记录落点。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@RestController
@RequestMapping("/api/device/series")
@RequiredArgsConstructor
public class DeviceSeriesController {

    /**
     * 时序引擎
     */
    private final DeviceTimeSeriesEngine engine;

    /**
     * 时序配置
     */
    private final DeviceTimeSeriesProperties properties;

    /**
     * 当前引擎信息（页面顶部的「存储：bean / influxdb / redis」徽标与原因提示）。
     *
     * @return 引擎信息
     */
    @GetMapping("/engine")
    public Map<String, Object> engineInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("engine", engine.engine());
        info.put("available", engine.isAvailable());
        info.put("reason", engine.unavailableReason());
        info.put("retentionDays", properties.getRetentionDays());
        info.put("defaultLimit", DeviceSeriesQuery.DEFAULT_LIMIT);
        info.put("maxLimit", DeviceSeriesQuery.MAX_LIMIT);
        return info;
    }

    /**
     * 时序分类清单（每个分类的 key / value 口径）。
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceSeriesCategory category : DeviceSeriesCategory.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", category.code());
            row.put("label", category.label());
            row.put("description", category.description());
            result.add(row);
        }
        return result;
    }

    /**
     * 查询时序点。
     *
     * <p>门禁记录页 / 车闸记录页要改成读时序存储时走这里；不单独暴露「存储浏览」菜单，
     * 存储只是这些记录的落点。</p>
     *
     * @param category 分类编码（metric / access / vehicle），必填
     * @param deviceId 设备 ID，可空
     * @param key      序列键，可空
     * @param start    起始时间，可空
     * @param end      结束时间，可空
     * @param limit    条数，默认 500，上限 5000
     * @param order    排序：desc（默认）/ asc
     * @return 时序点
     */
    @GetMapping
    public List<DeviceSeriesPoint> list(@RequestParam String category,
                                        @RequestParam(required = false) String deviceId,
                                        @RequestParam(required = false) String key,
                                        @RequestParam(required = false) String start,
                                        @RequestParam(required = false) String end,
                                        @RequestParam(defaultValue = "0") int limit,
                                        @RequestParam(defaultValue = "desc") String order) {
        return engine.query(queryOf(category, deviceId, key, start, end, limit, order));
    }

    /**
     * 门禁通行记录（时序口径）。
     *
     * <p>门禁记录页与车闸记录页都读这里：记录落点是时序存储，
     * 换引擎（bean / influxdb / redis）后页面接口不变。</p>
     *
     * @param deviceId 设备 ID，可空
     * @param start    起始时间，可空
     * @param end      结束时间，可空
     * @param limit    条数，默认 500，上限 5000
     * @param order    排序：desc（默认）/ asc
     * @return 时序点（key=卡号或人员，value=事件类型，extra 带门点/方向/抓拍）
     */
    @GetMapping("/access")
    public List<DeviceSeriesPoint> access(@RequestParam(required = false) String deviceId,
                                          @RequestParam(required = false) String start,
                                          @RequestParam(required = false) String end,
                                          @RequestParam(defaultValue = "0") int limit,
                                          @RequestParam(defaultValue = "desc") String order) {
        return engine.query(queryOf(DeviceSeriesCategory.ACCESS.code(), deviceId, null, start, end, limit, order));
    }

    /**
     * 车闸过车记录（时序口径）。
     *
     * @param deviceId 设备 ID，可空
     * @param start    起始时间，可空
     * @param end      结束时间，可空
     * @param limit    条数，默认 500，上限 5000
     * @param order    排序：desc（默认）/ asc
     * @return 时序点（key=车牌号，value=进出方向，extra 带车闸/车型/抓拍）
     */
    @GetMapping("/vehicle")
    public List<DeviceSeriesPoint> vehicle(@RequestParam(required = false) String deviceId,
                                           @RequestParam(required = false) String start,
                                           @RequestParam(required = false) String end,
                                           @RequestParam(defaultValue = "0") int limit,
                                           @RequestParam(defaultValue = "desc") String order) {
        return engine.query(queryOf(DeviceSeriesCategory.VEHICLE.code(), deviceId, null, start, end, limit, order));
    }

    /**
     * 按时序条件计数。
     *
     * @param category 分类编码，必填
     * @param deviceId 设备 ID，可空
     * @param key      序列键，可空
     * @param start    起始时间，可空
     * @param end      结束时间，可空
     * @return 条数
     */
    @GetMapping("/count")
    public long count(@RequestParam String category,
                      @RequestParam(required = false) String deviceId,
                      @RequestParam(required = false) String key,
                      @RequestParam(required = false) String start,
                      @RequestParam(required = false) String end) {
        return engine.count(queryOf(category, deviceId, key, start, end, 1, "desc"));
    }

    /**
     * 按保留期清理时序点。
     *
     * @param category 分类编码，空表示全部分类
     * @param before   删除该时间之前的点；空则按 retention-days 推算
     * @return 删除条数；-1 表示后端无法精确统计（已执行）
     */
    @DeleteMapping
    public ReturnResult<Integer> purge(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) String before) {
        LocalDateTime time = before == null || before.isBlank()
                ? LocalDateTime.now().minusDays(Math.max(properties.getRetentionDays(), 1))
                : DeviceSeriesQuery.parseTime(before);
        String code = category == null || category.isBlank() ? null : categoryOf(category);
        return ReturnResult.ok(engine.purge(code, time));
    }

    /**
     * 组装并校验查询条件。
     *
     * @param category 分类
     * @param deviceId 设备
     * @param key      序列键
     * @param start    起始时间
     * @param end      结束时间
     * @param limit    条数
     * @param order    排序
     * @return 查询条件
     */
    private static DeviceSeriesQuery queryOf(String category, String deviceId, String key,
                                             String start, String end, int limit, String order) {
        DeviceSeriesQuery query = new DeviceSeriesQuery();
        query.setCategory(categoryOf(category));
        query.setDeviceId(deviceId);
        query.setKey(key);
        query.setFrom(DeviceSeriesQuery.parseTime(start));
        query.setTo(DeviceSeriesQuery.parseTime(end));
        query.setLimit(limit);
        query.setAscending("asc".equalsIgnoreCase(order == null ? "" : order.trim()));
        return query;
    }

    /**
     * 校验分类编码（拼错时明确报错，而不是静默返回空列表）。
     *
     * @param category 分类编码
     * @return 归一化后的编码
     */
    private static String categoryOf(String category) {
        DeviceSeriesCategory parsed = DeviceSeriesCategory.of(category);
        if (parsed == null) {
            throw new IllegalArgumentException("不支持的时序分类: " + category
                    + "（可选 " + String.join(" / ", codes()) + "）");
        }
        return parsed.code().toLowerCase(Locale.ROOT);
    }

    /**
     * 全部分类编码。
     *
     * @return 编码列表
     */
    private static List<String> codes() {
        List<String> codes = new ArrayList<>();
        for (DeviceSeriesCategory category : DeviceSeriesCategory.values()) {
            codes.add(category.code());
        }
        return codes;
    }
}
