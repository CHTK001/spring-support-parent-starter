package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.model.DevicePageQuery;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.model.DeviceStats;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.support.util.ExcelExportUtil;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 设备管理 控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    /**
     * 设备服务
     */
    private final DeviceService deviceService;
    private final DeviceSyncService deviceSyncService;

    /**
     * 提供商配置服务
     */
    private final DeviceProviderConfigService providerConfigService;

    /**
     * 添加设备
     *
     * @param request 连接信息 + 资产字段
     * @return 设备信息
     */
    @PostMapping("/add")
    public ReturnResult<DeviceInfo> addDevice(@RequestBody DeviceSaveRequest request) {
        return ReturnResult.ok(deviceService.addDevice(request));
    }

    /**
     * 更新设备
     *
     * @param id      设备 标识
     * @param request 连接信息 + 资产字段
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ReturnResult<Void> updateDevice(@PathVariable String id, @RequestBody DeviceSaveRequest request) {
        deviceService.updateDevice(id, request);
        return ReturnResult.ok();
    }

    /**
     * 删除设备
     *
     * @param id 设备 标识
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
        return ReturnResult.ok();
    }

    /**
     * 获取设备详情
     *
     * @param id 设备 标识
     * @return 设备信息
     */
    @GetMapping("/{id}")
    public ReturnResult<DeviceInfo> getDevice(@PathVariable String id) {
        return ReturnResult.ok(deviceService.getDevice(id));
    }

    /**
     * 查询设备列表
     *
     * @param orgId 组织 标识，可空
     * @return 设备列表
     */
    @GetMapping("/list")
    public ReturnResult<List<DeviceInfo>> listDevices(@RequestParam(required = false) String orgId) {
        return ReturnResult.ok(deviceService.listDevices(orgId));
    }

    /**
     * 分页查询设备
     * <p>列表页使用，支持关键字、组织、厂商、分类、在线状态、负责人、标签与排序。</p>
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    public ReturnPageResult<DeviceInfo> pageDevices(DevicePageQuery query) {
        return ReturnPageResult.ok(deviceService.pageDevices(query));
    }

    /**
     * 设备台账统计（列表页顶部卡片）
     *
     * @param orgId  组织 ID，可空
     * @param vendor 厂商，可空
     * @return 统计结果
     */
    @GetMapping("/stats")
    public ReturnResult<DeviceStats> stats(@RequestParam(required = false) String orgId,
                                           @RequestParam(required = false) String vendor) {
        return ReturnResult.ok(deviceService.stats(orgId, vendor));
    }

    /**
     * 按大类查询设备
     *
     * @param category 大类
     * @return 设备列表
     */
    @GetMapping("/category/{category}")
    public ReturnResult<List<DeviceInfo>> listByCategory(@PathVariable String category) {
        return ReturnResult.ok(deviceService.listByCategory(category));
    }

    /**
     * 同步组织树
     *
     * @return 操作结果
     */
    @PostMapping("/org/sync")
    public ReturnResult<Void> syncOrg() {
        deviceService.syncOrganization();
        return ReturnResult.ok();
    }

    /**
     * 同步厂商设备目录（按 vendor + 序列号幂等 upsert）。
     *
     * @param vendor 厂商标识，可空（空则同步全部厂商）
     * @return created / updated / skipped 统计
     */
    @PostMapping("/sync")
    public ReturnResult<Map<String, Integer>> syncDevices(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(deviceSyncService.syncDevices(vendor));
    }

    /**
     * 获取组织树
     *
     * @param vendor 厂商，可空
     * @return 组织列表
     */
    @GetMapping("/org/tree")
    public ReturnResult<List<DeviceOrganization>> getOrgTree(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(deviceService.getOrganizationTree(vendor));
    }

    /**
     * 导出设备列表为 Excel
     *
     * @param orgId 组织 标识，可空
     * @param response HTTP 响应
     * @throws Exception 导出异常
     */
    @GetMapping("/export")
    public void exportDevices(@RequestParam(required = false) String orgId, HttpServletResponse response) throws Exception {
        List<DeviceInfo> devices = deviceService.listDevices(orgId);
        ExcelExportUtil.exportDevices(response, devices);
    }

    /**
     * 获取所有厂商的离线检测阈值配置
     *
     * @return 厂商标识 -> 阈值（秒）
     */
    @GetMapping("/provider-config")
    public ReturnResult<Map<String, Integer>> listProviderConfigs() {
        return ReturnResult.ok(providerConfigService.getThresholds());
    }

    /**
     * 更新指定厂商的离线检测阈值（秒）
     *
     * @param vendor    厂商标识
     * @param threshold 阈值（秒）
     * @return 操作结果
     */
    @PutMapping("/provider-config/{vendor}")
    public ReturnResult<Void> updateProviderConfig(@PathVariable String vendor, @RequestParam int threshold) {
        providerConfigService.updateThreshold(vendor, threshold);
        return ReturnResult.ok();
    }

    /**
     * 更新指定设备的离线检测阈值（秒）
     *
     * @param id        设备 标识
     * @param threshold 阈值（秒）
     * @return 操作结果
     */
    @PutMapping("/{id}/offline-threshold")
    public ReturnResult<Void> updateOfflineThreshold(@PathVariable String id, @RequestParam int threshold) {
        deviceService.updateOfflineThreshold(id, threshold);
        return ReturnResult.ok();
    }

    /**
     * 重新连接设备
     *
     * @param id 设备 标识
     * @return 操作结果
     */
    @PostMapping("/{id}/reconnect")
    public ReturnResult<Void> reconnect(@PathVariable String id) {
        deviceService.reconnect(id);
        return ReturnResult.ok();
    }

    /**
     * 刷新设备在线状态
     *
     * @param id 设备 标识
     * @return 操作结果
     */
    @PostMapping("/{id}/refresh-status")
    public ReturnResult<Void> refreshStatus(@PathVariable String id) {
        deviceService.refreshStatus(id);
        return ReturnResult.ok();
    }

    /**
     * 测试连接
     *
     * @param info 设备连接信息
     * @return 是否可达
     */
    @PostMapping("/test-connection")
    public ReturnResult<Boolean> testConnection(@RequestBody DeviceConnectInfo info) {
        return ReturnResult.ok(deviceService.testConnection(info));
    }
}
