package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.mapper.DeviceOrganizationMapper;
import com.chua.starter.device.support.model.DevicePageQuery;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.support.model.DeviceStats;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 设备服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    /**
     * 设备信息 映射器
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备组织 映射器
     */
    private final DeviceOrganizationMapper deviceOrganizationMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 所有可用的设备 提供者（由 SPI 自动注入）
     */
    private final List<DeviceProvider> providers;

    /**
     * 提供商接入凭据服务（补齐 AK/SK 等云平台参数）
     */
    private final com.chua.starter.device.support.service.DeviceProviderCredentialService credentialService;

    @Override
    /**
     * 添加Device
     * @param info 参数
     * @return 返回值
    */
    public DeviceInfo addDevice(DeviceConnectInfo info) {
        // 平台接入只传提供商凭据 ID，AK/SK 在服务端补齐，前端不再散落密钥
        DeviceConnectInfo connectInfo = enrichWithCredential(info);
        DeviceProvider provider = providers.stream()
                .filter(p -> p.support(connectInfo))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的设备厂商"));

        DeviceSession session = provider.connect(connectInfo);

        Map<String, Object> extra = connectInfo.extra() == null ? Map.of() : connectInfo.extra();
        LocalDateTime now = LocalDateTime.now();
        DeviceInfo device = new DeviceInfo();
        device.setDeviceInfoId(UUID.randomUUID().toString());
        device.setDeviceInfoName(firstNonBlank(str(extra.get("deviceInfoName")),
                connectInfo.deviceSerial(), connectInfo.ip()));
        device.setDeviceInfoVendor(connectInfo.vendor());
        device.setDeviceInfoCategory(str(extra.get("deviceInfoCategory")));
        device.setDeviceInfoSubCategory(str(extra.get("deviceInfoSubCategory")));
        device.setDeviceInfoIp(connectInfo.ip());
        device.setDeviceInfoPort(connectInfo.port());
        device.setDeviceInfoUsername(connectInfo.username());
        device.setDeviceInfoPassword(connectInfo.password());
        device.setDeviceInfoSerial(firstNonBlank(connectInfo.deviceSerial(), str(extra.get("deviceInfoSerial"))));
        device.setDeviceInfoLatitude(decimal(extra.get("deviceInfoLatitude")));
        device.setDeviceInfoLongitude(decimal(extra.get("deviceInfoLongitude")));
        device.setDeviceInfoAddress(str(extra.get("deviceInfoAddress")));
        device.setDeviceInfoChannelCount(0);
        device.setDeviceInfoOnline(Boolean.TRUE);
        device.setDeviceInfoCreatedAt(now);
        device.setDeviceInfoUpdatedAt(now);
        deviceInfoMapper.insert(device);

        sessionManager.register(device.getDeviceInfoId(), session);
        log.info("设备添加成功: vendor={}, ip={}, name={}",
                connectInfo.vendor(), connectInfo.ip(), device.getDeviceInfoName());
        return device;
    }

    @Override
    /**
     * 添加设备（含资产字段）并连接：请求对象直存资产字段
     * @param request 参数
     * @return 返回值
    */
    public DeviceInfo addDevice(DeviceSaveRequest request) {
        DeviceConnectInfo connectInfo = new DeviceConnectInfo(
                request.getDeviceInfoVendor(), "SDK",
                request.getDeviceInfoIp(), request.getDeviceInfoPort(),
                request.getDeviceInfoUsername(), request.getDeviceInfoPassword(),
                null, null, null, request.getDeviceInfoSerial(),
                Map.of());
        DeviceInfo device = addDevice(connectInfo);
        applySaveRequest(device, request);
        deviceInfoMapper.updateById(device);
        return device;
    }

    /**
     * 把请求对象的非空资产字段覆盖到设备实体。
     * @param device 参数
     * @param request 参数
     * @return 返回值
    */
    private void applySaveRequest(DeviceInfo device, DeviceSaveRequest request) {
        if (StringUtils.hasText(request.getDeviceInfoName())) {
            device.setDeviceInfoName(request.getDeviceInfoName());
        }
        if (StringUtils.hasText(request.getDeviceInfoCode())) {
            device.setDeviceInfoCode(request.getDeviceInfoCode());
        }
        if (StringUtils.hasText(request.getDeviceInfoOrgId())) {
            device.setDeviceInfoOrgId(request.getDeviceInfoOrgId());
        }
        if (StringUtils.hasText(request.getDeviceInfoModel())) {
            device.setDeviceInfoModel(request.getDeviceInfoModel());
        }
        if (StringUtils.hasText(request.getDeviceInfoCategory())) {
            device.setDeviceInfoCategory(request.getDeviceInfoCategory());
        }
        if (StringUtils.hasText(request.getDeviceInfoSubCategory())) {
            device.setDeviceInfoSubCategory(request.getDeviceInfoSubCategory());
        }
        if (StringUtils.hasText(request.getDeviceInfoAddress())) {
            device.setDeviceInfoAddress(request.getDeviceInfoAddress());
        }
        if (request.getDeviceInfoLatitude() != null) {
            device.setDeviceInfoLatitude(request.getDeviceInfoLatitude());
        }
        if (request.getDeviceInfoLongitude() != null) {
            device.setDeviceInfoLongitude(request.getDeviceInfoLongitude());
        }
        if (request.getDeviceInfoChannelCount() != null) {
            device.setDeviceInfoChannelCount(request.getDeviceInfoChannelCount());
        }
        if (request.getDeviceInfoOfflineThreshold() != null) {
            device.setDeviceInfoOfflineThreshold(request.getDeviceInfoOfflineThreshold());
        }
        if (StringUtils.hasText(request.getDeviceInfoCredentialId())) {
            device.setDeviceInfoCredentialId(request.getDeviceInfoCredentialId());
        }
        if (StringUtils.hasText(request.getDeviceInfoOwnerId())) {
            device.setDeviceInfoOwnerId(request.getDeviceInfoOwnerId());
        }
        device.setDeviceInfoUpdatedAt(LocalDateTime.now());
    }

    @Override
    /**
     * 更新Device
     * @param id 字符串
     * @param info 参数
     * @return 返回值
    */
    public void updateDevice(String id, DeviceInfo info) {
        info.setDeviceInfoId(id);
        deviceInfoMapper.updateById(info);
    }

    @Override
    /**
     * 更新设备（含资产字段）：密码为空或为掩码时不修改
     * @param id 字符串
     * @param request 参数
     * @return 返回值
    */
    public void updateDevice(String id, DeviceSaveRequest request) {
        DeviceInfo existing = deviceInfoMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("设备不存在: " + id);
        }
        applySaveRequest(existing, request);
        if (StringUtils.hasText(request.getDeviceInfoPassword())
                && !request.getDeviceInfoPassword().contains("*")) {
            existing.setDeviceInfoPassword(request.getDeviceInfoPassword());
        }
        deviceInfoMapper.updateById(existing);
    }

    @Override
    /**
     * 删除Device
     * @param id 字符串
     * @return 返回值
    */
    public void deleteDevice(String id) {
        deviceInfoMapper.deleteById(id);
        sessionManager.unregister(id);
    }

    @Override
    /**
     * 获取Device
     * @param id 字符串
     * @return 返回值
    */
    public DeviceInfo getDevice(String id) {
        return deviceInfoMapper.selectById(id);
    }

    @Override
    /**
     * 列表devices
     * @param orgId 字符串
     * @return 返回值
    */
    public List<DeviceInfo> listDevices(String orgId) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orgId)) {
            wrapper.eq(DeviceInfo::getDeviceInfoOrgId, orgId);
        }
        wrapper.orderByDesc(DeviceInfo::getDeviceInfoCreatedAt);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    public PageResult<DeviceInfo> pageDevices(DevicePageQuery query) {
        DevicePageQuery q = query == null ? new DevicePageQuery() : query.normalize();
        Page<DeviceInfo> page = new Page<>(q.getPageNo(), q.getPageSize());
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getOrgId())) {
            wrapper.eq(DeviceInfo::getDeviceInfoOrgId, q.getOrgId());
        }
        if (StringUtils.hasText(q.getVendor())) {
            wrapper.eq(DeviceInfo::getDeviceInfoVendor, q.getVendor());
        }
        if (StringUtils.hasText(q.getCategory())) {
            wrapper.eq(DeviceInfo::getDeviceInfoCategory, q.getCategory());
        }
        if (StringUtils.hasText(q.getSubCategory())) {
            wrapper.eq(DeviceInfo::getDeviceInfoSubCategory, q.getSubCategory());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(DeviceInfo::getDeviceInfoStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getOwnerId())) {
            wrapper.eq(DeviceInfo::getDeviceInfoOwnerId, q.getOwnerId());
        }
        if (q.getOnline() != null) {
            wrapper.eq(DeviceInfo::getDeviceInfoOnline, q.getOnline());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like(DeviceInfo::getDeviceInfoName, kw)
                    .or().like(DeviceInfo::getDeviceInfoIp, kw)
                    .or().like(DeviceInfo::getDeviceInfoSerial, kw)
                    .or().like(DeviceInfo::getDeviceInfoModel, kw)
                    .or().like(DeviceInfo::getDeviceInfoCode, kw));
        }
        // 分组筛选：一台设备可属于多个分组，这里用关联表 EXISTS 过滤
        if (StringUtils.hasText(q.getGroupId())) {
            wrapper.apply("EXISTS (SELECT 1 FROM device_group_device gd "
                    + "WHERE gd.device_id = device_info.device_info_id AND gd.group_id = {0})", q.getGroupId());
        } else if (Boolean.TRUE.equals(q.getUngrouped())) {
            wrapper.apply("NOT EXISTS (SELECT 1 FROM device_group_device gd "
                    + "WHERE gd.device_id = device_info.device_info_id)");
        }
        SFunction<DeviceInfo, ?> sort = switch (q.getSortBy() == null ? "" : q.getSortBy()) {
            case "name" -> DeviceInfo::getDeviceInfoName;
            case "createdAt" -> DeviceInfo::getDeviceInfoCreatedAt;
            case "lastOnlineAt" -> DeviceInfo::getDeviceInfoLastOnlineAt;
            default -> DeviceInfo::getDeviceInfoUpdatedAt;
        };
        wrapper.orderBy(true, "asc".equalsIgnoreCase(q.getSortOrder()), sort);
        deviceInfoMapper.selectPage(page, wrapper);
        return PageResult.copy(page);
    }

    @Override
    /**
     * 设备台账统计 <p>统计范围与列表的组织/厂商过滤一致，不受关键字与在线过滤影响（见 DeviceStats 说明）。</p>
     * @param orgId 字符串
     * @param vendor 字符串
     * @return 返回值
    */
    public DeviceStats stats(String orgId, String vendor) {
        LambdaQueryWrapper<DeviceInfo> base = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orgId)) {
            base.eq(DeviceInfo::getDeviceInfoOrgId, orgId);
        }
        if (StringUtils.hasText(vendor)) {
            base.eq(DeviceInfo::getDeviceInfoVendor, vendor);
        }
        long total = deviceInfoMapper.selectCount(base);
        long online = deviceInfoMapper.selectCount(
                base.clone().eq(DeviceInfo::getDeviceInfoOnline, true));
        long noLocation = deviceInfoMapper.selectCount(
                base.clone().and(w -> w.isNull(DeviceInfo::getDeviceInfoLatitude)
                        .or().isNull(DeviceInfo::getDeviceInfoLongitude)));
        return new DeviceStats(total, online, total - online, noLocation);
    }

    @Override
    /**
     * 分页查询设备
    */
    public PageResult<DeviceInfo> pageDevices(long pageNo, long pageSize, String orgId,
                                              String keyword, String category, Boolean online) {
        Page<DeviceInfo> page = new Page<>(Math.max(1, pageNo), Math.max(1, pageSize));
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orgId)) {
            wrapper.eq(DeviceInfo::getDeviceInfoOrgId, orgId);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(DeviceInfo::getDeviceInfoCategory, category);
        }
        if (online != null) {
            wrapper.eq(DeviceInfo::getDeviceInfoOnline, online);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(DeviceInfo::getDeviceInfoName, kw)
                    .or().like(DeviceInfo::getDeviceInfoIp, kw)
                    .or().like(DeviceInfo::getDeviceInfoSerial, kw));
        }
        wrapper.orderByDesc(DeviceInfo::getDeviceInfoCreatedAt);
        deviceInfoMapper.selectPage(page, wrapper);
        PageResult<DeviceInfo> result = PageResult.copy(page);
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    /**
     * 列表by分类
     * @param category 字符串
     * @return 返回值
    */
    public List<DeviceInfo> listByCategory(String category) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getDeviceInfoCategory, category);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    /**
     * 同步组织
    */
    public void syncOrganization() {
        for (DeviceProvider provider : providers) {
            try {
                List<com.chua.starter.device.spi.model.OrganizationNode> nodes = provider.fetchOrganizations();
                if (nodes == null || nodes.isEmpty()) {
                    log.info("组织同步: vendor={} 无组织节点", provider.getVendor());
                    continue;
                }
                for (com.chua.starter.device.spi.model.OrganizationNode node : nodes) {
                    DeviceOrganization org = new DeviceOrganization();
                    org.setDeviceOrganizationId(provider.getVendor() + ":" + node.orgId());
                    org.setDeviceOrganizationName(node.orgName());
                    org.setDeviceOrganizationVendor(provider.getVendor());
                    org.setDeviceOrganizationParentId(
                            node.parentId() == null ? null : provider.getVendor() + ":" + node.parentId());
                    deviceOrganizationMapper.insert(org);
                }
                log.info("组织同步: vendor={}, count={}", provider.getVendor(), nodes.size());
            } catch (Exception e) {
                log.error("组织同步失败: vendor={}", provider.getVendor(), e);
            }
        }
    }

    @Override
    /**
     * 获取组织树
     * @param vendor 字符串
     * @return 返回值
    */
    public List<DeviceOrganization> getOrganizationTree(String vendor) {
        LambdaQueryWrapper<DeviceOrganization> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(vendor)) {
            wrapper.eq(DeviceOrganization::getDeviceOrganizationVendor, vendor);
        }
        return deviceOrganizationMapper.selectList(wrapper);
    }

    @Override
    /**
     * 更新offline阈值
     * @param id 字符串
     * @param threshold 整数
     * @return 返回值
    */
    public void updateOfflineThreshold(String id, int threshold) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            /**
             * runtime异常。
             * @param id 标识
             * @return runtime异常的结果
             */
            throw new RuntimeException("设备不存在: " + id);
        }
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOfflineThreshold(threshold);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备离线检测阈值更新: id={}, threshold={}秒", id, threshold);
    }

    @Override
    /**
     * Reconnect
     * @param id 字符串
     * @return 返回值
    */
    public void reconnect(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            /**
             * runtime异常。
             * @param id 标识
             * @return runtime异常的结果
             */
            throw new RuntimeException("设备不存在: " + id);
        }
        // 关闭旧会话
        sessionManager.unregister(id);
        // 按设备现有信息重建连接
        DeviceConnectInfo info = new DeviceConnectInfo(
                device.getDeviceInfoVendor(),
                null,
                device.getDeviceInfoIp(),
                device.getDeviceInfoPort(),
                device.getDeviceInfoUsername(),
                device.getDeviceInfoPassword(),
                null,
                null,
                null,
                null,
                null
        );
        final DeviceConnectInfo connectInfo = enrichWithCredential(info);
        DeviceSession session = providers.stream()
                .filter(p -> p.support(connectInfo))
                .findFirst()
                .map(p -> p.connect(connectInfo))
                .orElse(null);
        if (session == null) {
            /**
             * runtime异常。
             * @param id 标识
             * @return runtime异常的结果
             */
            throw new RuntimeException("设备重连失败，无可用 Provider: " + id);
        }
        sessionManager.register(id, session);
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOnline(Boolean.TRUE);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备重连成功: id={}, ip={}", id, device.getDeviceInfoIp());
    }

    @Override
    /**
     * refresh状态
     * @param id 字符串
     * @return 返回值
    */
    public void refreshStatus(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            /**
             * runtime异常。
             * @param id 标识
             * @return runtime异常的结果
             */
            throw new RuntimeException("设备不存在: " + id);
        }
        DeviceSession session = sessionManager.getSession(id);
        boolean online = session != null && session.isOnline() && session.testConnection();
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOnline(online);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.debug("设备状态刷新: id={}, online={}", id, online);
    }

    /**
     * 用已登记的提供商凭据补齐连接信息中缺失的云平台参数。
     *
     * <p>调用方（如 reconnect）按设备台账构造 {@link DeviceConnectInfo}，其中
     * platformType/appKey/appSecret 一律为 null——云平台会话据此必然认证失败。
     * 这里按 (vendor, platformType) 从 {@code device_provider_credential} 取该厂商默认套
     * 补齐；调用方已显式给出的值优先，不覆盖。</p>
     *
     * @param info 原始连接信息
     * @return 补齐后的连接信息
     */
    private DeviceConnectInfo enrichWithCredential(DeviceConnectInfo info) {
        if (info == null) {
            return null;
        }
        // SDK 直连走设备自身 IP + 账号密码，不使用平台凭据
        if ("SDK".equalsIgnoreCase(info.authType())) {
            return info;
        }
        String vendor = info.vendor();
        if (vendor == null || vendor.isBlank()) {
            return info;
        }
        // authType 缺省且已显式给出 AK/SK 时保持调用方参数，不再覆盖
        if (info.authType() == null && info.appKey() != null && !info.appKey().isBlank()) {
            return info;
        }
        Map<String, Object> extra = info.extra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(info.extra());
        // 前端设备表单只传所选凭据 ID，这里优先按 ID 精确取；读不到再按厂商 + 平台回退
        DeviceProviderCredential credential = null;
        Object credentialId = extra.get("credentialId");
        if (credentialId != null && StringUtils.hasText(String.valueOf(credentialId))) {
            credential = credentialService.get(String.valueOf(credentialId));
        }
        if (credential == null) {
            credential = credentialService.resolveFor(vendor, info.platformType());
        }
        if (credential == null) {
            log.debug("未登记 {} 的提供商凭据，按现有连接信息建连", vendor);
            return info;
        }
        if (Boolean.FALSE.equals(credential.getDeviceCredentialEnabled())) {
            throw new IllegalStateException("提供商凭据已停用: " + credential.getDeviceCredentialName());
        }
        String appKey = firstNonBlank(info.appKey(), credential.getDeviceCredentialAppKey());
        String appSecret = firstNonBlank(info.appSecret(), credential.getDeviceCredentialAppSecret());
        String platformType = firstNonBlank(info.platformType(), credential.getDeviceCredentialPlatformType());
        // 有 AK/SK 却没有 authType 时补 PLATFORM，否则 Provider 会走 SDK 直连分支
        String authType = info.authType();
        if (authType == null && appKey != null && !appKey.isBlank()) {
            authType = "PLATFORM";
        }
        if (StringUtils.hasText(credential.getDeviceCredentialBaseUrl())) {
            extra.put("baseUrl", credential.getDeviceCredentialBaseUrl());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProductCode())) {
            extra.put("productCode", credential.getDeviceCredentialProductCode());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProjectId())) {
            extra.put("projectId", credential.getDeviceCredentialProjectId());
        }
        extra.put("credentialId", credential.getDeviceCredentialId());
        log.info("建连补齐提供商凭据: vendor={}, platform={}, credential={}",
                vendor, platformType, credential.getDeviceCredentialName());
        return new DeviceConnectInfo(
                vendor,
                authType,
                info.ip(),
                info.port(),
                info.username(),
                info.password(),
                platformType,
                appKey,
                appSecret,
                info.deviceSerial(),
                extra
        );
    }

    /**
     * 取第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值；全为空白时返回 {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 任意对象转字符串；空白按 {@code null} 处理
     * @param value 对象
     * @return 返回值
    */
    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 任意对象转经纬度；非法值返回 {@code null}
     * @param value 对象
     * @return 返回值
    */
    private static BigDecimal decimal(Object value) {
        String text = str(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    /**
     * 测试connection
     * @param info 参数
     * @return 返回值
    */
    public boolean testConnection(DeviceConnectInfo info) {
        DeviceSession session = providers.stream()
                .filter(p -> p.support(info))
                .findFirst()
                .map(p -> {
                    try {
                        return p.connect(enrichWithCredential(info));
                    } catch (Exception e) {
                        log.warn("设备连接失败: {}", e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
        if (session == null) {
            return false;
        }
        try {
            return session.testConnection();
        } finally {
            session.close();
        }
    }

}

