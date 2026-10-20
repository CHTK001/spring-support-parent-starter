package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.mapper.DeviceOrganizationMapper;
import com.chua.starter.device.support.model.DevicePageQuery;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.model.DeviceStats;
import com.chua.starter.device.support.security.DeviceSecretCodec;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceStatusService;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.OrganizationNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final DeviceProviderCredentialService credentialService;

    /**
     * 密钥编解码器（密码落库加密、建连解密）
     */
    private final DeviceSecretCodec secretCodec;

    /**
     * 在线状态服务（统一落库与事件发布）
     */
    private final DeviceStatusService statusService;

    @Override
    public DeviceInfo addDevice(DeviceConnectInfo info) {
        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceInfoVendor(info.vendor());
        request.setDeviceInfoIp(info.ip());
        request.setDeviceInfoPort(info.port());
        request.setDeviceInfoUsername(info.username());
        request.setDeviceInfoPassword(info.password());
        request.setDeviceInfoSerial(info.deviceSerial());
        request.setDeviceSerial(info.deviceSerial());
        request.setAuthType(info.authType());
        request.setPlatformType(info.platformType());
        request.setAppKey(info.appKey());
        request.setAppSecret(info.appSecret());
        return addDevice(request);
    }

    @Override
    public DeviceInfo addDevice(DeviceSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("设备信息不能为空");
        }
        if (!StringUtils.hasText(request.getDeviceInfoVendor())) {
            throw new IllegalArgumentException("厂商不能为空");
        }
        DeviceConnectInfo connectInfo = enrichWithCredential(
                request.toConnectInfo(), request.getDeviceInfoCredentialId());
        DeviceProvider provider = providers.stream()
                .filter(p -> p.support(connectInfo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的设备厂商: " + request.getDeviceInfoVendor()));

        boolean needConnect = !Boolean.FALSE.equals(request.getConnect());
        DeviceSession session = null;
        if (needConnect) {
            session = provider.connect(connectInfo);
        }

        DeviceInfo device = request.toEntity();
        device.setDeviceInfoId(newId());
        if (!StringUtils.hasText(device.getDeviceInfoName())) {
            device.setDeviceInfoName(defaultName(device));
        }
        if (!StringUtils.hasText(device.getDeviceInfoSource())) {
            device.setDeviceInfoSource("local");
        }
        if (!StringUtils.hasText(device.getDeviceInfoStatus())) {
            device.setDeviceInfoStatus("normal");
        }
        if (!StringUtils.hasText(device.getDeviceInfoSerial())) {
            device.setDeviceInfoSerial(request.getDeviceSerial());
        }
        // 密码加密落库；建连使用请求中的明文，二者不混用
        device.setDeviceInfoPassword(secretCodec.encrypt(request.getDeviceInfoPassword()));
        boolean online = session != null;
        device.setDeviceInfoOnline(online);
        device.setDeviceInfoLastOnlineAt(online ? LocalDateTime.now() : null);
        device.setDeviceInfoCreatedAt(LocalDateTime.now());
        device.setDeviceInfoUpdatedAt(LocalDateTime.now());

        try {
            deviceInfoMapper.insert(device);
        } catch (RuntimeException e) {
            if (session != null) {
                session.close();
            }
            throw e;
        }
        if (session != null) {
            sessionManager.register(device.getDeviceInfoId(), session);
        }
        log.info("设备添加成功: id={}, vendor={}, name={}, ip={}", device.getDeviceInfoId(),
                device.getDeviceInfoVendor(), device.getDeviceInfoName(), device.getDeviceInfoIp());
        return device;
    }

    @Override
    public void updateDevice(String id, DeviceInfo info) {
        if (info == null) {
            return;
        }
        // 服务端托管字段不允许通过通用更新覆盖
        info.setDeviceInfoId(id);
        info.setDeviceInfoOnline(null);
        info.setDeviceInfoCreatedAt(null);
        info.setDeviceInfoLastOnlineAt(null);
        info.setDeviceInfoLastOfflineAt(null);
        info.setDeviceInfoPassword(encodeIncomingSecret(info.getDeviceInfoPassword()));
        info.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(info);
    }

    @Override
    public void updateDevice(String id, DeviceSaveRequest request) {
        DeviceInfo existing = deviceInfoMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        DeviceInfo update = request.toEntity();
        update.setDeviceInfoId(id);
        update.setDeviceInfoPassword(encodeIncomingSecret(request.getDeviceInfoPassword()));
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备更新成功: id={}", id);
    }

    @Override
    public PageResult<DeviceInfo> pageDevices(DevicePageQuery query) {
        DevicePageQuery q = (query == null ? new DevicePageQuery() : query).normalize();
        Page<DeviceInfo> page = new Page<>(q.getPageNo(), q.getPageSize());
        // 多条件分页 SQL 统一放 XML（resources/mapper/device/DeviceInfoMapper.xml）
        deviceInfoMapper.selectDevicePage(page, q);
        return PageResult.<DeviceInfo>builder()
                .pageNo((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .totalPages((int) page.getPages())
                .total(page.getTotal())
                .data(page.getRecords())
                .build();
    }

    @Override
    public DeviceStats stats(String orgId, String vendor) {
        return deviceInfoMapper.selectDeviceStats(orgId, vendor);
    }

    @Override
    public void deleteDevice(String id) {
        sessionManager.unregister(id);
        deviceInfoMapper.deleteById(id);
        log.info("设备删除成功: id={}", id);
    }

    @Override
    public DeviceInfo getDevice(String id) {
        return deviceInfoMapper.selectById(id);
    }

    @Override
    public List<DeviceInfo> listDevices(String orgId) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orgId)) {
            wrapper.eq(DeviceInfo::getDeviceInfoOrgId, orgId);
        }
        wrapper.orderByDesc(DeviceInfo::getDeviceInfoCreatedAt);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceInfo> listByCategory(String category) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getDeviceInfoCategory, category);
        return deviceInfoMapper.selectList(wrapper);
    }

    @Override
    public void syncOrganization() {
        for (DeviceProvider provider : providers) {
            try {
                List<OrganizationNode> nodes = provider.fetchOrganizations();
                if (nodes == null || nodes.isEmpty()) {
                    log.info("组织同步: vendor={} 无组织节点", provider.getVendor());
                    continue;
                }
                // 去重并按 orgId 建索引，便于计算父子路径
                Map<String, OrganizationNode> byId = new LinkedHashMap<>();
                for (OrganizationNode node : nodes) {
                    if (node != null && StringUtils.hasText(node.orgId())) {
                        byId.putIfAbsent(node.orgId(), node);
                    }
                }
                int index = 0;
                for (OrganizationNode node : byId.values()) {
                    upsertOrganization(provider.getVendor(), node, byId, index++);
                }
                log.info("组织同步: vendor={}, count={}", provider.getVendor(), byId.size());
            } catch (Exception e) {
                log.error("组织同步失败: vendor={}", provider.getVendor(), e);
            }
        }
    }

    @Override
    public List<DeviceOrganization> getOrganizationTree(String vendor) {
        LambdaQueryWrapper<DeviceOrganization> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(vendor)) {
            wrapper.eq(DeviceOrganization::getDeviceOrganizationVendor, vendor);
        }
        // 历史数据没有 deleted 列，NULL 视为正常
        wrapper.and(w -> w.isNull(DeviceOrganization::getDeviceOrganizationDeleted)
                .or().eq(DeviceOrganization::getDeviceOrganizationDeleted, false));
        wrapper.orderByAsc(DeviceOrganization::getDeviceOrganizationSort);
        return deviceOrganizationMapper.selectList(wrapper);
    }

    @Override
    public void updateOfflineThreshold(String id, int threshold) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        DeviceInfo update = new DeviceInfo();
        update.setDeviceInfoId(id);
        update.setDeviceInfoOfflineThreshold(threshold);
        update.setDeviceInfoUpdatedAt(LocalDateTime.now());
        deviceInfoMapper.updateById(update);
        log.info("设备离线检测阈值更新: id={}, threshold={}秒", id, threshold);
    }

    @Override
    public void reconnect(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        sessionManager.unregister(id);
        DeviceProvider provider = providers.stream()
                .filter(p -> p.getVendor().equalsIgnoreCase(device.getDeviceInfoVendor()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("设备重连失败，无可用 Provider: " + id));
        boolean sdkMode = StringUtils.hasText(device.getDeviceInfoIp());
        String authType = sdkMode ? "SDK" : "PLATFORM";
        String platformType = sdkMode ? null : provider.descriptor().platforms().stream()
                .findFirst().map(platform -> platform.code()).orElse(null);
        DeviceConnectInfo info = new DeviceConnectInfo(
                device.getDeviceInfoVendor(),
                authType,
                device.getDeviceInfoIp(),
                device.getDeviceInfoPort(),
                device.getDeviceInfoUsername(),
                secretCodec.decrypt(device.getDeviceInfoPassword()),
                platformType,
                null,
                null,
                device.getDeviceInfoSerial(),
                null
        );
        DeviceConnectInfo connectInfo = enrichWithCredential(info, device.getDeviceInfoCredentialId());
        DeviceSession session;
        try {
            session = providers.stream()
                    .filter(p -> p.support(connectInfo))
                    .findFirst()
                    .map(p -> p.connect(connectInfo))
                    .orElseThrow(() -> new IllegalStateException(
                            "设备重连失败，无可用 Provider: " + id));
        } catch (RuntimeException e) {
            statusService.updateOnline(device, false, true);
            throw e;
        }
        sessionManager.register(id, session);
        statusService.updateOnline(device, true, true);
        log.info("设备重连成功: id={}, ip={}", id, device.getDeviceInfoIp());
    }

    @Override
    public void refreshStatus(String id) {
        DeviceInfo device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        DeviceSession session = sessionManager.getSession(id);
        boolean online = false;
        if (session != null) {
            try {
                online = session.isOnline() && session.testConnection();
            } catch (Exception e) {
                log.debug("设备状态探测失败: id={}, message={}", id, e.getMessage());
            }
        }
        statusService.updateOnline(device, online, true);
        if (!online) {
            sessionManager.unregister(id);
        }
        log.debug("设备状态刷新: id={}, online={}", id, online);
    }

    /**
     * 用已登记的提供商凭据补齐连接信息中缺失的云平台参数。
     *
     * <p>优先使用设备显式绑定的凭据（credentialId）；未绑定时按 (vendor, platformType)
     * 取该厂商默认套。调用方已显式给出的值优先，不覆盖。</p>
     *
     * @param info         原始连接信息
     * @param credentialId 设备绑定的凭据 ID，可为空
     * @return 补齐后的连接信息
     */
    private DeviceConnectInfo enrichWithCredential(DeviceConnectInfo info, String credentialId) {
        if (info == null) {
            return null;
        }
        String vendor = info.vendor();
        if (vendor == null || vendor.isBlank()) {
            return info;
        }
        // authType 缺省时按平台模式处理：SDK 直连不需要 AK/SK，凭据缺失不应阻断
        if (info.authType() == null && info.appKey() != null && !info.appKey().isBlank()) {
            return info;
        }
        DeviceProviderCredential credential = StringUtils.hasText(credentialId)
                ? credentialService.resolveById(credentialId)
                : credentialService.resolveFor(vendor, info.platformType());
        if (credential == null) {
            log.debug("未登记 {} 的提供商凭据，按现有连接信息建连", vendor);
            return info;
        }
        String appKey = firstNonBlank(info.appKey(), credential.getDeviceCredentialAppKey());
        String appSecret = firstNonBlank(info.appSecret(), credential.getDeviceCredentialAppSecret());
        String platformType = firstNonBlank(info.platformType(), credential.getDeviceCredentialPlatformType());
        String authType = info.authType();
        if (authType == null && appKey != null && !appKey.isBlank()) {
            authType = "PLATFORM";
        }
        log.debug("建连补齐提供商凭据: vendor={}, platform={}, credential={}",
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
                info.extra()
        );
    }

    /**
     * 组织 upsert：存在则更新名称/父级/路径，不存在则插入。
     *
     * @param vendor 厂商
     * @param node   厂商组织节点
     * @param byId   本次同步的节点索引
     * @param sort   排序号
     */
    private void upsertOrganization(String vendor, OrganizationNode node,
                                    Map<String, OrganizationNode> byId, int sort) {
        String id = vendor + ":" + node.orgId();
        DeviceOrganization org = deviceOrganizationMapper.selectById(id);
        boolean insert = org == null;
        if (insert) {
            org = new DeviceOrganization();
            org.setDeviceOrganizationId(id);
            org.setDeviceOrganizationVendor(vendor);
            org.setDeviceOrganizationExternalId(node.orgId());
        }
        org.setDeviceOrganizationName(node.orgName());
        org.setDeviceOrganizationParentId(
                StringUtils.hasText(node.parentId()) ? vendor + ":" + node.parentId() : null);
        org.setDeviceOrganizationPath(resolvePath(node, byId));
        org.setDeviceOrganizationSort(sort);
        org.setDeviceOrganizationDeleted(false);
        org.setDeviceOrganizationUpdatedAt(LocalDateTime.now());
        if (insert) {
            deviceOrganizationMapper.insert(org);
        } else {
            deviceOrganizationMapper.updateById(org);
        }
    }

    /**
     * 计算组织路径（厂商侧 orgId 用 / 连接）
     *
     * @param node 当前节点
     * @param byId 本次同步的节点索引
     * @return 路径
     */
    private String resolvePath(OrganizationNode node, Map<String, OrganizationNode> byId) {
        LinkedList<String> ids = new LinkedList<>();
        Set<String> guard = new HashSet<>();
        OrganizationNode current = node;
        while (current != null && StringUtils.hasText(current.orgId()) && guard.add(current.orgId())) {
            ids.addFirst(current.orgId());
            current = StringUtils.hasText(current.parentId()) ? byId.get(current.parentId()) : null;
        }
        return String.join("/", ids);
    }

    /**
     * 生成 32 位主键（表字段为 VARCHAR(32)，不能直接用带连字符的 UUID）
     *
     * @return 32 位 ID
     */
    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 默认设备名称
     *
     * @param device 设备
     * @return 名称
     */
    private static String defaultName(DeviceInfo device) {
        String suffix = firstNonBlank(device.getDeviceInfoCode(), device.getDeviceInfoSerial(),
                device.getDeviceInfoIp(), device.getDeviceInfoId());
        return firstNonBlank(device.getDeviceInfoVendor(), "设备") + "-" + suffix;
    }

    /**
     * 编码前端提交的密码：空值或掩码表示“不修改”，返回 null 让 MyBatis-Plus 跳过该列
     *
     * @param incoming 前端提交的密码
     * @return 密文或 null
     */
    private String encodeIncomingSecret(String incoming) {
        if (!StringUtils.hasText(incoming) || secretCodec.isMasked(incoming)) {
            return null;
        }
        return secretCodec.encrypt(incoming);
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

    @Override
    public boolean testConnection(DeviceConnectInfo info) {
        DeviceSession session = providers.stream()
                .filter(p -> p.support(info))
                .findFirst()
                .map(p -> {
                    try {
                        return p.connect(enrichWithCredential(info, null));
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
