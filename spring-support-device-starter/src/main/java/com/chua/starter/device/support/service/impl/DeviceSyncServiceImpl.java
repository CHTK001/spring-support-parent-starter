package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.provider.dahua.DahuaProvider;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.VendorDevice;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.mapper.DeviceOrganizationMapper;
import com.chua.starter.device.support.mapper.DevicePersonMapper;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商设备目录同步实现：按 vendor + 序列号幂等 upsert。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncServiceImpl implements DeviceSyncService {

    private final List<DeviceProvider> providers;
    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceService deviceService;
    private final DeviceProviderCredentialService credentialService;
    private final DeviceOrganizationMapper deviceOrganizationMapper;
    private final DevicePersonMapper devicePersonMapper;

    @Override
    public Map<String, Integer> syncDevicesByCredential(String credentialId) {
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            return result(0, 0, 0);
        }
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            return result(0, 0, 0);
        }
        DeviceSession session = null;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        try {
            // 优先走统一 SDK 门面（官方 SDK / 官方 OpenAPI 由实现类决定）；
            // 门面未覆盖的平台回落到原有会话实现。
            List<VendorDevice> remoteDevices = fetchDevicesBySdk(provider, job);
            for (VendorDevice remote : remoteDevices) {
                try {
                    if (upsertByCredential(provider, remote, credentialId, job.credential())) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warn("[Device][Sync] 凭据 {} 同步设备 {} 失败: {}",
                            credentialId, remote.externalId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 设备目录同步失败: {}", credentialId, e.getMessage());
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result(created, updated, skipped);
    }

    @Override
    /**
     * 按凭据同步组织树。
     *
     * @param credentialId 凭据 ID
     * @return created / updated / skipped
     */
    public Map<String, Integer> syncOrganizationsByCredential(String credentialId) {
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            return result(0, 0, 0);
        }
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            return result(0, 0, 0);
        }
        DeviceSession session = null;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        try {
            session = provider.connect(job.connectInfo());
            for (OrganizationNode org : provider.fetchOrganizations(session)) {
                try {
                    if (upsertOrganization(provider, org)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warn("[Device][Sync] 凭据 {} 同步组织 {} 失败: {}",
                            credentialId, org.orgId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 组织同步失败: {}", credentialId, e.getMessage());
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result(created, updated, skipped);
    }

    @Override
    /**
     * 按凭据同步人员列表（与 syncDevicesByCredential 平齐的维度） <p>人员与组织表解耦，单建 {@code device_person}（按 vendor + 厂商 side 人员标识 幂等入库；行政归属挂接厂商 side 组织标识），避免 API 人员结构换表时耦合到 org-person 关联表。</p>
     * @param credentialId 字符串
     * @return 返回值
    */
    public Map<String, Integer> syncPersonsByCredential(String credentialId) {
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            return result(0, 0, 0);
        }
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            return result(0, 0, 0);
        }
        DeviceSession session = null;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        try {
            session = provider.connect(job.connectInfo());
            for (DevicePerson remote : provider.fetchPersons(session)) {
                try {
                    if (upsertPerson(provider, remote, credentialId)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warn("[Device][Sync] 凭据 {} 同步人员 {} 失败: {}",
                            credentialId, remote.externalId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 人员同步失败: {}", credentialId, e.getMessage());
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result(created, updated, skipped);
    }

    /**
     * 拉设备目录：优先统一 SDK 门面，未覆盖平台回落原会话实现。
     *
     * @param provider 厂商 Provider
     * @param job      凭据任务
     * @return 设备条目
     */
    private List<VendorDevice> fetchDevicesBySdk(DeviceProvider provider, CredentialJob job) {
        com.chua.starter.device.provider.sdk.DevicePlatformSdk sdk =
                com.chua.starter.device.provider.sdk.DevicePlatformSdks.of(job.connectInfo());
        if (sdk != null) {
            log.info("[Device][Sync] 走统一 SDK 门面: platform={}, officialSdk={}",
                    sdk.platform(), sdk.officialSdk());
            return sdk.listDevices();
        }
        DeviceSession session = provider.connect(job.connectInfo());
        try {
            return provider.fetchDevices(session);
        } finally {
            session.close();
        }
    }

    /**
     * 按凭据 ID 解析：缺失 / 停用的凭据返回 {@code null}
     *
     * @param credentialId 凭据 ID
     * @return 建连依赖；不存在/已停用时返回 {@code null}
     */
    private CredentialJob resolveCredential(String credentialId) {
        DeviceProviderCredential credential = credentialService.resolveById(credentialId);
        if (credential == null) {
            return null;
        }
        if (Boolean.FALSE.equals(credential.getDeviceCredentialEnabled())) {
            return null;
        }
        return new CredentialJob(credential, credentialConnectInfo(credential));
    }

    /**
     * 凭据 → 建立云平台会话所用的连接信息
     *
     * @param credential 凭据
     * @return 连接信息（authType 为平台侧）
     */
    private DeviceConnectInfo credentialConnectInfo(DeviceProviderCredential credential) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (StringUtils.hasText(credential.getDeviceCredentialBaseUrl())) {
            extra.put("baseUrl", credential.getDeviceCredentialBaseUrl());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProductCode())) {
            extra.put("productCode", credential.getDeviceCredentialProductCode());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProjectId())) {
            extra.put("projectId", credential.getDeviceCredentialProjectId());
        }
        // 凭据 ID 打入 extra，会话持有者、设备回写、采集向下保持一致
        extra.put("credentialId", credential.getDeviceCredentialId());
        return new DeviceConnectInfo(
                credential.getDeviceCredentialVendor(),
                "PLATFORM",
                null, 0, null, null,
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialAppKey(),
                credential.getDeviceCredentialAppSecret(),
                null, extra);
    }

    /**
     * 按设备台账 + 凭据 ID upsert 设备
     *
     * <p>按 vendor + 序列号（平台序列号）幂等入库；设备台账行回写
     * {@code deviceInfoCredentialId}，使在线检测/视频/云台等方向知道该设备由哪套凭据维系。</p>
     *
     * @param provider      厂商提供者
     * @param remote        厂商设备条目
     * @param credentialId  凭据 ID
     * @param credential    凭据
     * @return true 新增，false 更新
     */
    private boolean upsertByCredential(DeviceProvider provider, VendorDevice remote,
                                       String credentialId, DeviceProviderCredential credential) {
        String serial = StringUtils.hasText(remote.serial()) ? remote.serial() : remote.externalId();
        DeviceInfo existing = deviceInfoMapper.selectByVendorAndSerial(provider.getVendor(), serial);
        if (existing != null) {
            existing.setDeviceInfoName(remote.name());
            existing.setDeviceInfoModel(remote.model());
            existing.setDeviceInfoCategory(StringUtils.hasText(remote.category()) ? remote.category() : "camera");
            existing.setDeviceInfoSerial(serial);
            existing.setDeviceInfoLatitude(remote.latitude());
            existing.setDeviceInfoLongitude(remote.longitude());
            existing.setDeviceInfoAddress(remote.address());
            existing.setDeviceInfoChannelCount(remote.channelCount());
            existing.setDeviceInfoOnline(Boolean.TRUE.equals(remote.online()));
            existing.setDeviceInfoLastOnlineAt(Boolean.TRUE.equals(remote.online()) ? LocalDateTime.now() : null);
            if (StringUtils.hasText(remote.externalId())) {
                // 远端幂等键回写台账，推送/解绑方向才能定位到同一台设备
                existing.setDeviceInfoExternalId(remote.externalId());
            }
            existing.setDeviceInfoUpdatedAt(LocalDateTime.now());
            // 回写：该台账行由哪套凭据维系
            existing.setDeviceInfoCredentialId(credentialId);
            deviceInfoMapper.updateById(existing);
            return false;
        }

        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceInfoName(remote.name());
        request.setDeviceInfoCode(serial);
        request.setDeviceInfoVendor(provider.getVendor());
        request.setDeviceInfoModel(remote.model());
        request.setDeviceInfoCategory(StringUtils.hasText(remote.category()) ? remote.category() : "camera");
        request.setDeviceInfoSerial(serial);
        request.setDeviceSerial(serial);
        request.setDeviceInfoIp(remote.ip());
        request.setDeviceInfoPort(remote.port());
        request.setDeviceInfoLatitude(remote.latitude());
        request.setDeviceInfoLongitude(remote.longitude());
        request.setDeviceInfoAddress(remote.address());
        request.setDeviceInfoChannelCount(remote.channelCount());
        request.setDeviceInfoSource("vendor");
        request.setDeviceInfoStatus("normal");
        request.setAuthType("PLATFORM");
        request.setPlatformType(credential.getDeviceCredentialPlatformType());
        request.setDeviceInfoCredentialId(credentialId);
        // 同步只落台账，不在同步线程里建立厂商会话；在线状态由定时检测校准
        request.setConnect(false);
        DeviceInfo created = deviceService.addDevice(request);
        boolean dirty = false;
        if (StringUtils.hasText(remote.externalId())) {
            created.setDeviceInfoExternalId(remote.externalId());
            dirty = true;
        }
        if (Boolean.TRUE.equals(remote.online())) {
            created.setDeviceInfoOnline(true);
            created.setDeviceInfoLastOnlineAt(LocalDateTime.now());
            dirty = true;
        }
        if (dirty) {
            deviceInfoMapper.updateById(created);
        }
        return true;
    }

    /**
     * 按 vendor + 厂商 side 组织标识 upsert 组织
     *
     * @param provider 厂商提供者
     * @param node     厂商侧组织节点
     * @return true 新增，false 更新
     */
    private boolean upsertOrganization(DeviceProvider provider, OrganizationNode node) {
        String orgId = provider.getVendor() + ":" + node.orgId();
        String parentId = node.parentId() == null ? null : provider.getVendor() + ":" + node.parentId();
        DeviceOrganization existing = selectOrganization(provider.getVendor(), node.orgId());
        if (existing != null) {
            existing.setDeviceOrganizationName(node.orgName());
            existing.setDeviceOrganizationParentId(parentId);
            existing.setDeviceOrganizationUpdatedAt(LocalDateTime.now());
            deviceOrganizationMapper.updateById(existing);
            return false;
        }
        DeviceOrganization org = new DeviceOrganization();
        org.setDeviceOrganizationId(orgId);
        org.setDeviceOrganizationName(node.orgName());
        org.setDeviceOrganizationVendor(provider.getVendor());
        org.setDeviceOrganizationExternalId(node.orgId());
        org.setDeviceOrganizationParentId(parentId);
        org.setDeviceOrganizationUpdatedAt(LocalDateTime.now());
        deviceOrganizationMapper.insert(org);
        return true;
    }

    /**
     * 按 vendor + 厂商 side 组织标识查已有组织
     * @param externalId 字符串
     * @param vendor 字符串
     * @return 返回值
    */
    private DeviceOrganization selectOrganization(String vendor, String externalId) {
        LambdaQueryWrapper<DeviceOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceOrganization::getDeviceOrganizationVendor, vendor)
                .eq(DeviceOrganization::getDeviceOrganizationExternalId, externalId);
        return deviceOrganizationMapper.selectOne(wrapper);
    }

    /**
     * 按 vendor + 厂商 side 人员标识 upsert 人员
     *
     * @param provider     厂商提供者
     * @param remote       厂商侧人员条目（SPI record）
     * @param credentialId 凭据 ID
     * @return true 新增，false 更新
     */
    private boolean upsertPerson(DeviceProvider provider, DevicePerson remote, String credentialId) {
        String personId = provider.getVendor() + ":" + remote.externalId();
        LambdaQueryWrapper<com.chua.starter.device.support.entity.DevicePerson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.chua.starter.device.support.entity.DevicePerson::getDevicePersonVendor, provider.getVendor())
                .eq(com.chua.starter.device.support.entity.DevicePerson::getDevicePersonExternalId, remote.externalId());
        com.chua.starter.device.support.entity.DevicePerson existing = devicePersonMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setDevicePersonName(remote.name());
            existing.setDevicePersonTitle(remote.title());
            existing.setDevicePersonPhone(remote.phone());
            existing.setDevicePersonEmail(remote.email());
            existing.setDevicePersonOrgId(remote.orgId());
            existing.setDevicePersonUpdatedAt(LocalDateTime.now());
            devicePersonMapper.updateById(existing);
            return false;
        }
        com.chua.starter.device.support.entity.DevicePerson person = new com.chua.starter.device.support.entity.DevicePerson();
        person.setDevicePersonId(personId);
        person.setDevicePersonName(remote.name());
        person.setDevicePersonTitle(remote.title());
        person.setDevicePersonPhone(remote.phone());
        person.setDevicePersonEmail(remote.email());
        person.setDevicePersonVendor(provider.getVendor());
        person.setDevicePersonExternalId(remote.externalId());
        person.setDevicePersonOrgId(remote.orgId());
        person.setDevicePersonUpdatedAt(LocalDateTime.now());
        devicePersonMapper.insert(person);
        return true;
    }

    /**
     * 关联映射统计词
     * @param created 整数
     * @param skipped 整数
     * @param updated 整数
     * @return 返回值
    */
    private Map<String, Integer> result(int created, int updated, int skipped) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 按凭据建连的准结果（凭据 + 为建连拼凑的连接信息）
     */
    private static record CredentialJob(DeviceProviderCredential credential,
                                        DeviceConnectInfo connectInfo) {
    }

    /**
     * 按厂商同步设备（拉取方向）。
     *
     * @param vendor 厂商标识；为空时同步全部已注册厂商
     * @return created / updated / skipped 统计
     */
    @Override
    public Map<String, Integer> syncDevices(String vendor) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (DeviceProvider provider : providers) {
            if (StringUtils.hasText(vendor) && !vendor.equals(provider.getVendor())) {
                continue;
            }
            List<VendorDevice> remoteDevices;
            try {
                remoteDevices = provider.fetchDevices();
            } catch (Exception e) {
                log.warn("[Device][Sync] 厂商 {} 拉取设备失败: {}", provider.getVendor(), e.getMessage());
                continue;
            }
            for (VendorDevice remote : remoteDevices) {
                if (remote == null || !StringUtils.hasText(remote.externalId())) {
                    skipped++;
                    continue;
                }
                String serial = StringUtils.hasText(remote.serial()) ? remote.serial() : remote.externalId();
                try {
                    if (upsert(provider, remote, serial)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    skipped++;
                    log.warn("[Device][Sync] 同步设备 {} 失败: {}", remote.externalId(), e.getMessage());
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        log.info("[Device][Sync] vendor={} 完成: {}", vendor, result);
        return result;
    }

    /**
     * upsert
     * @param provider 参数
     * @param remote 参数
     * @param serial 字符串
     * @return true 表示新增，false 表示更新
    */
    private boolean upsert(DeviceProvider provider, VendorDevice remote, String serial) {
        DeviceInfo existing = deviceInfoMapper.selectByVendorAndSerial(provider.getVendor(), serial);
        if (existing != null) {
            existing.setDeviceInfoName(remote.name());
            existing.setDeviceInfoModel(remote.model());
            existing.setDeviceInfoCategory(remote.category());
            existing.setDeviceInfoSerial(serial);
            existing.setDeviceInfoLatitude(remote.latitude());
            existing.setDeviceInfoLongitude(remote.longitude());
            existing.setDeviceInfoAddress(remote.address());
            existing.setDeviceInfoChannelCount(remote.channelCount());
            existing.setDeviceInfoOnline(Boolean.TRUE.equals(remote.online()));
            if (Boolean.TRUE.equals(remote.online())) {
                existing.setDeviceInfoLastOnlineAt(LocalDateTime.now());
            }
            if (StringUtils.hasText(remote.externalId())) {
                existing.setDeviceInfoExternalId(remote.externalId());
            }
            existing.setDeviceInfoUpdatedAt(LocalDateTime.now());
            deviceInfoMapper.updateById(existing);
            return false;
        }

        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceInfoName(remote.name());
        request.setDeviceInfoCode(serial);
        request.setDeviceInfoVendor(provider.getVendor());
        request.setDeviceInfoModel(remote.model());
        request.setDeviceInfoCategory(StringUtils.hasText(remote.category()) ? remote.category() : "camera");
        request.setDeviceInfoSerial(serial);
        request.setDeviceSerial(serial);
        request.setDeviceInfoIp(remote.ip());
        request.setDeviceInfoPort(remote.port());
        request.setDeviceInfoLatitude(remote.latitude());
        request.setDeviceInfoLongitude(remote.longitude());
        request.setDeviceInfoAddress(remote.address());
        request.setDeviceInfoChannelCount(remote.channelCount());
        request.setDeviceInfoSource("vendor");
        request.setDeviceInfoStatus("normal");
        request.setAuthType("PLATFORM");
        if (!provider.descriptor().platforms().isEmpty()) {
            request.setPlatformType(provider.descriptor().platforms().get(0).code());
        }
        // 同步只落台账，不在同步线程里建立厂商会话；在线状态由定时检测校准
        request.setConnect(false);
        DeviceInfo created = deviceService.addDevice(request);
        boolean dirty = false;
        if (StringUtils.hasText(remote.externalId())) {
            created.setDeviceInfoExternalId(remote.externalId());
            dirty = true;
        }
        if (Boolean.TRUE.equals(remote.online())) {
            created.setDeviceInfoOnline(true);
            created.setDeviceInfoLastOnlineAt(LocalDateTime.now());
            dirty = true;
        }
        if (dirty) {
            deviceInfoMapper.updateById(created);
        }
        return true;
    }

    @Override
    public boolean pushDevice(DeviceInfo device) {
        if (device == null || !StringUtils.hasText(device.getDeviceInfoId())) {
            return false;
        }
        DeviceProvider provider = findProvider(device.getDeviceInfoVendor());
        if (provider == null) {
            log.warn("[Device][Push] 未注册厂商 Provider: vendor={}", device.getDeviceInfoVendor());
            return false;
        }
        VendorDevice remote = toRemoteDevice(device);
        boolean ok;
        try {
            if (provider instanceof DahuaProvider dahuaProvider) {
                DeviceProviderCredential credential = resolveDeviceCredential(device);
                if (credential == null) {
                    return false;
                }
                boolean exists = remoteExists(device);
                ok = dahuaProvider.upsertRemote(deviceConnectInfo(device, credential), remote, exists);
                if (ok && !exists) {
                    // 绑定成功后补记远端标识，后续推送走改名、删除走解绑
                    persistExternalId(device, remote.externalId());
                }
            } else {
                ok = provider.upsertRemote(remote);
            }
        } catch (Exception e) {
            log.warn("[Device][Push] 厂商 {} 写回设备失败: {}, message={}",
                    provider.getVendor(), device.getDeviceInfoId(), e.getMessage());
            return false;
        }
        log.info("[Device][Push] 厂商 {} 写回设备: id={}, externalId={}, result={}",
                provider.getVendor(), device.getDeviceInfoId(), device.getDeviceInfoExternalId(), ok);
        return ok;
    }

    @Override
    public boolean removeRemote(DeviceInfo device) {
        if (device == null) {
            return false;
        }
        // 与推送方向同口径：externalId 缺失时用序列号兜底
        String externalId = StringUtils.hasText(device.getDeviceInfoExternalId())
                ? device.getDeviceInfoExternalId()
                : device.getDeviceInfoSerial();
        if (!StringUtils.hasText(externalId)) {
            return false;
        }
        DeviceProvider provider = findProvider(device.getDeviceInfoVendor());
        if (provider == null) {
            log.warn("[Device][Push] 删除远端设备失败：未注册厂商 Provider: vendor={}", device.getDeviceInfoVendor());
            return false;
        }
        boolean ok;
        try {
            if (provider instanceof DahuaProvider dahuaProvider) {
                DeviceProviderCredential credential = resolveDeviceCredential(device);
                if (credential == null) {
                    return false;
                }
                ok = dahuaProvider.removeRemote(deviceConnectInfo(device, credential), externalId);
            } else {
                ok = provider.removeRemote(externalId);
            }
        } catch (Exception e) {
            log.warn("[Device][Push] 厂商 {} 删除设备失败: id={}, message={}",
                    provider.getVendor(), device.getDeviceInfoId(), e.getMessage());
            return false;
        }
        log.info("[Device][Push] 厂商 {} 删除远端设备: id={}, externalId={}, result={}",
                provider.getVendor(), device.getDeviceInfoId(), externalId, ok);
        return ok;
    }

    /**
     * 按厂商标识取已注册 Provider
     *
     * @param vendor 厂商标识
     * @return Provider；未注册返回 {@code null}
     */
    private DeviceProvider findProvider(String vendor) {
        if (!StringUtils.hasText(vendor)) {
            return null;
        }
        return providers.stream()
                .filter(p -> vendor.equalsIgnoreCase(p.getVendor()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析设备写回所用的凭据：优先显式绑定，其次厂商默认套。
     *
     * <p>凭据必须启用、厂商一致且配置了平台类型，否则云平台写接口无法建连。</p>
     *
     * @param device 本地设备
     * @return 可用凭据；不可用时返回 {@code null}
     */
    private DeviceProviderCredential resolveDeviceCredential(DeviceInfo device) {
        DeviceProviderCredential credential;
        if (StringUtils.hasText(device.getDeviceInfoCredentialId())) {
            credential = credentialService.resolveById(device.getDeviceInfoCredentialId());
            if (credential == null) {
                log.warn("[Device][Push] 设备绑定的凭据不存在: deviceId={}, credentialId={}",
                        device.getDeviceInfoId(), device.getDeviceInfoCredentialId());
                return null;
            }
        } else {
            credential = credentialService.resolveFor(device.getDeviceInfoVendor(), null);
            if (credential == null) {
                log.warn("[Device][Push] 厂商无可用凭据: deviceId={}, vendor={}",
                        device.getDeviceInfoId(), device.getDeviceInfoVendor());
                return null;
            }
        }
        if (Boolean.FALSE.equals(credential.getDeviceCredentialEnabled())) {
            log.warn("[Device][Push] 设备绑定凭据已停用: deviceId={}, credentialId={}",
                    device.getDeviceInfoId(), credential.getDeviceCredentialId());
            return null;
        }
        if (!StringUtils.hasText(device.getDeviceInfoVendor())
                || !device.getDeviceInfoVendor().equalsIgnoreCase(credential.getDeviceCredentialVendor())) {
            log.warn("[Device][Push] 设备厂商与凭据厂商不一致: deviceId={}, vendor={}, credentialVendor={}",
                    device.getDeviceInfoId(), device.getDeviceInfoVendor(),
                    credential.getDeviceCredentialVendor());
            return null;
        }
        if (!StringUtils.hasText(credential.getDeviceCredentialPlatformType())) {
            log.warn("[Device][Push] 凭据未配置平台类型，无法写回: deviceId={}, credentialId={}",
                    device.getDeviceInfoId(), credential.getDeviceCredentialId());
            return null;
        }
        return credential;
    }

    /**
     * 设备台账 + 凭据 → 写回用连接信息。
     *
     * <p>设备密码同时写入 {@code code}/{@code devCode}/{@code devicePassword}：乐橙绑定用
     * {@code code}，云睿新增用 {@code devCode}，Provider 亦兼容 {@code devicePassword}。</p>
     *
     * @param device     本地设备
     * @param credential 凭据
     * @return 连接信息
     */
    private DeviceConnectInfo deviceConnectInfo(DeviceInfo device, DeviceProviderCredential credential) {
        DeviceConnectInfo base = credentialConnectInfo(credential);
        Map<String, Object> extra = new LinkedHashMap<>(
                base.extra() == null ? Map.of() : base.extra());
        String password = device.getDeviceInfoPassword();
        if (StringUtils.hasText(password)) {
            extra.put("code", password);
            extra.put("devCode", password);
            extra.put("devicePassword", password);
        }
        String serial = StringUtils.hasText(device.getDeviceInfoSerial())
                ? device.getDeviceInfoSerial()
                : device.getDeviceInfoExternalId();
        return new DeviceConnectInfo(
                base.vendor(), base.authType(), base.ip(), base.port(), base.username(), base.password(),
                base.platformType(), base.appKey(), base.appSecret(), serial, extra);
    }

    /**
     * 平台侧是否已存在该设备。
     *
     * <p>厂商同步来的台账标记为 {@code vendor}，无需 externalId 也可判定为已存在；
     * 手工录入设备只有回写 externalId 后才视为已绑定。</p>
     *
     * @param device 本地设备
     * @return true 表示已有远端设备
     */
    private boolean remoteExists(DeviceInfo device) {
        return StringUtils.hasText(device.getDeviceInfoExternalId())
                || "vendor".equalsIgnoreCase(device.getDeviceInfoSource());
    }

    /**
     * 写回成功后持久化远端标识。
     *
     * <p>只更新 externalId 与更新时间，避免把内存中的密码等字段整行覆盖。</p>
     *
     * @param device     本地设备
     * @param externalId 远端设备标识
     */
    private void persistExternalId(DeviceInfo device, String externalId) {
        if (!StringUtils.hasText(externalId)) {
            return;
        }
        try {
            DeviceInfo patch = new DeviceInfo();
            patch.setDeviceInfoId(device.getDeviceInfoId());
            patch.setDeviceInfoExternalId(externalId);
            patch.setDeviceInfoUpdatedAt(LocalDateTime.now());
            deviceInfoMapper.updateById(patch);
            device.setDeviceInfoExternalId(externalId);
        } catch (RuntimeException e) {
            log.warn("[Device][Push] 设备已写回但回写 externalId 失败: id={}, message={}",
                    device.getDeviceInfoId(), e.getMessage());
        }
    }

    /**
     * 本地台账设备 → 厂商侧传输模型（写回用）。
     *
     * <p>externalId 缺省时用序列号兜底，与拉取方向的幂等键口径保持一致。</p>
     *
     * @param device 本地设备
     * @return 厂商设备条目
     */
    private VendorDevice toRemoteDevice(DeviceInfo device) {
        String externalId = StringUtils.hasText(device.getDeviceInfoExternalId())
                ? device.getDeviceInfoExternalId()
                : device.getDeviceInfoSerial();
        return new VendorDevice(
                externalId,
                device.getDeviceInfoName(),
                device.getDeviceInfoModel(),
                device.getDeviceInfoCategory(),
                device.getDeviceInfoSerial(),
                device.getDeviceInfoIp(),
                device.getDeviceInfoPort(),
                device.getDeviceInfoAddress(),
                device.getDeviceInfoLatitude(),
                device.getDeviceInfoLongitude(),
                device.getDeviceInfoChannelCount(),
                device.getDeviceInfoOnline());
    }
}
