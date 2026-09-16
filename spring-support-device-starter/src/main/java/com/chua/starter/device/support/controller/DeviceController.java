package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.support.util.ExcelExportUtil;
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

    /**
    * 提供商配置服务
     */
    private final DeviceProviderConfigService providerConfigService;

    /**
    * 添加设备
    *
    * @param info 连接信息
    * @return 设备信息
     */
    @PostMapping("/add")
    public DeviceInfo addDevice(@RequestBody DeviceConnectInfo info) {
        return deviceService.addDevice(info);
    }

    /**
    * 更新设备
    *
    * @param id   设备 标识
    * @param info 设备信息
     */
    @PutMapping("/{id}")
    public void updateDevice(@PathVariable String id, @RequestBody DeviceInfo info) {
        deviceService.updateDevice(id, info);
    }

    /**
    * 删除设备
    *
    * @param id 设备 标识
     */
    @DeleteMapping("/{id}")
    public void deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
    }

    /**
    * 获取设备详情
    *
    * @param id 设备 标识
    * @return 设备信息
     */
    @GetMapping("/{id}")
    public DeviceInfo getDevice(@PathVariable String id) {
        return deviceService.getDevice(id);
    }

    /**
    * 查询设备列表
    *
    * @param orgId 组织 标识，可选
    * @return 设备列表
    * @param false false
     */
    @GetMapping("/list")
    public List<DeviceInfo> listDevices(@RequestParam(required = false) String orgId) {
        return deviceService.listDevices(orgId);
    }

    /**
    * 按大类查询设备
    *
    * @param category 大类
    * @return 设备列表
     */
    @GetMapping("/category/{category}")
    public List<DeviceInfo> listByCategory(@PathVariable String category) {
        return deviceService.listByCategory(category);
    }

    /**
    * 同步组织树
     */
    @PostMapping("/org/sync")
    public void syncOrg() {
        deviceService.syncOrganization();
    }

    /**
    * 获取组织树
    *
    * @param vendor 厂商，可选
    * @return 组织列表
    * @param false false
     */
    @GetMapping("/org/tree")
    public List<DeviceOrganization> getOrgTree(@RequestParam(required = false) String vendor) {
        return deviceService.getOrganizationTree(vendor);
    }

    /**
    * 导出设备列表为 Excel
    *
    * @param orgId 组织 标识，可选
    * @param response HTTP 响应
    * @throws Exception 导出异常
    * @param false false
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
    public Map<String, Integer> listProviderConfigs() {
        return providerConfigService.getThresholds();
    }

    /**
    * 更新指定厂商的离线检测阈值（秒）
    *
    * @param vendor    厂商标识
    * @param threshold 阈值（秒）
     */
    @PutMapping("/provider-config/{vendor}")
    public void updateProviderConfig(@PathVariable String vendor, @RequestParam int threshold) {
        providerConfigService.updateThreshold(vendor, threshold);
    }

    /**
    * 更新指定设备的离线检测阈值（秒）
    *
    * @param id        设备 标识
    * @param threshold 阈值（秒）
     */
    @PutMapping("/{id}/offline-threshold")
    public void updateOfflineThreshold(@PathVariable String id, @RequestParam int threshold) {
        deviceService.updateOfflineThreshold(id, threshold);
    }

    /**
    * 重新连接设备
    *
    * @param id 设备 标识
     */
    @PostMapping("/{id}/reconnect")
    public void reconnect(@PathVariable String id) {
        deviceService.reconnect(id);
    }

    /**
    * 刷新设备在线状态
    *
    * @param id 设备 标识
     */
    @PostMapping("/{id}/refresh-status")
    public void refreshStatus(@PathVariable String id) {
        deviceService.refreshStatus(id);
    }

    /**
    * 测试连接
    *
    * @param info 设备连接信息
    * @return 是否可达
     */
    @PostMapping("/test-connection")
    public boolean testConnection(@RequestBody DeviceConnectInfo info) {
        return deviceService.testConnection(info);
    }
}