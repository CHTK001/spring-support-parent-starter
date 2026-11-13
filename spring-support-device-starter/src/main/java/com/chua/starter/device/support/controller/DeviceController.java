package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.model.DeviceImportResult;
import com.chua.starter.device.support.model.DevicePageQuery;
import com.chua.starter.device.support.model.DeviceStats;
import com.chua.starter.device.support.service.DeviceImportService;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.support.util.DeviceExcelImportUtil;
import com.chua.starter.device.support.util.ExcelExportUtil;
import com.chua.starter.mybatis.entity.PageResult;
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
import org.springframework.web.multipart.MultipartFile;

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
     * 台账导入服务
     */
    private final DeviceImportService importService;

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
     * 启用 / 停用设备
     *
     * <p>只改设备状态：停用后后台所有定时采集（在线探测 / 事件轮询 / 门禁抽取）跳过该设备，
     * 已建立的会话立即注销；地址、凭据、组织、资产等原有配置不受影响。</p>
     *
     * @param id     设备 标识
     * @param status 目标状态：{@code normal} 启用 / {@code disabled} 停用
     * @return 变更后的设备信息
     */
    @PutMapping("/{id}/status")
    public DeviceInfo toggleStatus(@PathVariable String id, @RequestParam String status) {
        return deviceService.toggleStatus(id, status);
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
     * 分页查询设备。
     *
     * <p>查询条件直接绑定 {@link DevicePageQuery}，支持关键字、厂商、分类、在线状态、
     * 资产状态、组织、分组（groupId / ungrouped）与排序。</p>
     *
     * @param query 分页与筛选条件
     * @return 设备分页结果
     */
    @GetMapping("/page")
    public PageResult<DeviceInfo> pageDevices(DevicePageQuery query) {
        return deviceService.pageDevices(query);
    }

    /**
     * 设备统计（总数 / 在线 / 离线 / 未定位）。
     *
     * @param orgId  组织 ID，可选
     * @param vendor 厂商，可选
     * @return 统计结果
     */
    @GetMapping("/stats")
    public DeviceStats stats(@RequestParam(required = false) String orgId,
                             @RequestParam(required = false) String vendor) {
        return deviceService.stats(orgId, vendor);
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
     * 导出设备台账为 Excel。
     *
     * <p>筛选条件与 {@code /page} 完全一致（关键字、厂商、分类、在线状态、组织/分组/设备集合），
     * 保证导出内容与列表「所见即所得」。{@code orgIds} / {@code groupIds} / {@code ids}
     * 用于把左树选中的组织（含子孙）、分组（含子分组）、单台设备带过来。</p>
     *
     * @param query    筛选条件
     * @param response HTTP 响应
     * @throws Exception 导出异常
     * @param false false
     */
    @GetMapping("/export")
    public void exportDevices(DevicePageQuery query, HttpServletResponse response) throws Exception {
        List<DeviceInfo> devices = deviceService.listDevices(query);
        ExcelExportUtil.exportDevices(response, devices);
    }

    /**
     * 导出设备台账为 Excel（POST 版）。
     *
     * <p>条件里含组织/分组/设备 ID 集合时用 POST，避免 GET 查询串过长；
     * 筛选语义与 {@link #exportDevices(DevicePageQuery, HttpServletResponse)} 完全一致。</p>
     *
     * @param query    筛选条件
     * @param response HTTP 响应
     * @throws Exception 导出异常
     */
    @PostMapping("/export")
    public void exportDevicesPost(@RequestBody(required = false) DevicePageQuery query,
                                  HttpServletResponse response) throws Exception {
        List<DeviceInfo> devices = deviceService.listDevices(query);
        ExcelExportUtil.exportDevices(response, devices);
    }

    /**
     * 下载台账导入模板（Excel）。
     *
     * @param response HTTP 响应
     * @throws Exception 写出失败
     */
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) throws Exception {
        DeviceExcelImportUtil.writeTemplate(response);
    }

    /**
     * 导入台账（multipart）。
     *
     * <p>前端先用 {@code dryRun=true} 看逐行预览，确认后按 {@code dryRun=false} 写库。</p>
     *
     * @param file   Excel 文件
     * @param mode   create / update / upsert
     * @param dryRun true 只校验不写库
     * @return 逐行导入结果
     * @throws Exception 读取文件失败
     */
    @PostMapping("/import")
    public DeviceImportResult importDevices(@RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "create") String mode,
                                            @RequestParam(defaultValue = "true") boolean dryRun) throws Exception {
        return importService.importDevices(file, mode, dryRun);
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
