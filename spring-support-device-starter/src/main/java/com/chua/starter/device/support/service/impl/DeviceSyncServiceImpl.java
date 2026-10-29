package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
            session = provider.connect(job.connectInfo());
            for (VendorDevice remote : provider.fetchDevices(session)) {
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
     * 按凭据同步组织树（与 syncDevicesByCredential 平齐的维度）
     *
     * <p>厂商侧组织以 {@code vendor + 厂商 side 组织标识} 为在键幂等入库（与
     * 设备列表的 {@code vendor + 序列号} 同口径），父节点依据 {@code desc()} 再挂接。
     * 平台组织间字段取反/改名时只影响本节课索命，活动页拉动 */
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
     * 按凭据同步人员列表（与 syncDevicesByCredential 平齐的维度）
     *
     * <p>人员与组织表解耦，单建 {@code device_person}（按 vendor + 厂商 side 人员标识
     * 幂等入库；行政归属挂接厂商 side 组织标识），避免 API 人员结构换表时耦合到
     * org-person 关联表。</p>
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
     * <p>appKey / appSecret / platformType 直取凭据本身；baseUrl 覆盖到 extra 里，
     * 产品/项目等厂商侧隔离字段同样打入 extra，避免会话重建时遗漏参数。</p>
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
        if (Boolean.TRUE.equals(remote.online())) {
            created.setDeviceInfoOnline(true);
            created.setDeviceInfoLastOnlineAt(LocalDateTime.now());
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
        if (Boolean.TRUE.equals(remote.online())) {
            created.setDeviceInfoOnline(true);
            created.setDeviceInfoLastOnlineAt(LocalDateTime.now());
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
            ok = provider.upsertRemote(remote);
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
        if (device == null || !StringUtils.hasText(device.getDeviceInfoExternalId())) {
            return false;
        }
        DeviceProvider provider = findProvider(device.getDeviceInfoVendor());
        if (provider == null) {
            log.warn("[Device][Push] 删除远端设备失败：未注册厂商 Provider: vendor={}", device.getDeviceInfoVendor());
            return false;
        }
        boolean ok;
        try {
            ok = provider.removeRemote(device.getDeviceInfoExternalId());
        } catch (Exception e) {
            log.warn("[Device][Push] 厂商 {} 删除设备失败: id={}, message={}",
                    provider.getVendor(), device.getDeviceInfoId(), e.getMessage());
            return false;
        }
        log.info("[Device][Push] 厂商 {} 删除远端设备: id={}, externalId={}, result={}",
                provider.getVendor(), device.getDeviceInfoId(), device.getDeviceInfoExternalId(), ok);
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
