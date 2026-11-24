package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.provider.dahua.DahuaProvider;
import com.chua.starter.device.provider.hikvision.HikvisionProvider;
import com.chua.starter.device.provider.uniview.UniviewProvider;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.DevicePerson;
import com.chua.starter.device.spi.model.OrganizationNode;
import com.chua.starter.device.spi.model.VendorDevice;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceOrganization;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.entity.DeviceSyncOutbox;
import com.chua.starter.device.support.entity.DeviceSyncRecord;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.mapper.DeviceOrganizationMapper;
import com.chua.starter.device.support.mapper.DevicePersonMapper;
import com.chua.starter.device.support.mapper.DeviceSyncOutboxMapper;
import com.chua.starter.device.support.mapper.DeviceSyncRecordMapper;
import com.chua.starter.device.support.model.DeviceSaveRequest;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final DeviceSyncRecordMapper syncRecordMapper;
    private final DeviceSyncOutboxMapper syncOutboxMapper;

    /** outbox 载荷快照序列化（含时间字段，需 JSR-310 模块） */
    private static final ObjectMapper PAYLOAD_JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public Map<String, Integer> syncDevicesByCredential(String credentialId) {
        DeviceSyncRecord record = startRecord(credentialId, null, "pull", "devices");
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            finishRecord(record, 0, 0, 0, "凭据不存在或已停用");
            return result(0, 0, 0);
        }
        record.setDeviceSyncRecordVendor(job.credential().getDeviceCredentialVendor());
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            finishRecord(record, 0, 0, 0, "未注册厂商 Provider: vendor=" + job.credential().getDeviceCredentialVendor());
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
            finishRecord(record, created, updated, skipped, null);
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 设备目录同步失败: {}", credentialId, e.getMessage());
            finishRecord(record, created, updated, skipped, "设备目录同步失败: " + e.getMessage());
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
        DeviceSyncRecord record = startRecord(credentialId, null, "pull", "organizations");
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            finishRecord(record, 0, 0, 0, "凭据不存在或已停用");
            return result(0, 0, 0);
        }
        record.setDeviceSyncRecordVendor(job.credential().getDeviceCredentialVendor());
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            finishRecord(record, 0, 0, 0, "未注册厂商 Provider: vendor=" + job.credential().getDeviceCredentialVendor());
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
            finishRecord(record, created, updated, skipped, null);
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 组织同步失败: {}", credentialId, e.getMessage());
            finishRecord(record, created, updated, skipped, "组织同步失败: " + e.getMessage());
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
        DeviceSyncRecord record = startRecord(credentialId, null, "pull", "persons");
        CredentialJob job = resolveCredential(credentialId);
        if (job == null) {
            log.warn("[Device][Sync] 凭据不存在或已停用: credentialId={}", credentialId);
            finishRecord(record, 0, 0, 0, "凭据不存在或已停用");
            return result(0, 0, 0);
        }
        record.setDeviceSyncRecordVendor(job.credential().getDeviceCredentialVendor());
        DeviceProvider provider = findProvider(job.credential().getDeviceCredentialVendor());
        if (provider == null) {
            log.warn("[Device][Sync] 未注册厂商 Provider: vendor={}", job.credential().getDeviceCredentialVendor());
            finishRecord(record, 0, 0, 0, "未注册厂商 Provider: vendor=" + job.credential().getDeviceCredentialVendor());
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
            finishRecord(record, created, updated, skipped, null);
        } catch (Exception e) {
            log.error("[Device][Sync] 凭据 {} 人员同步失败: {}", credentialId, e.getMessage());
            finishRecord(record, created, updated, skipped, "人员同步失败: " + e.getMessage());
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
        if (StringUtils.hasText(credential.getDeviceCredentialGroupNo())) {
            extra.put("groupNo", credential.getDeviceCredentialGroupNo());
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
            existing.setUpdateTime(LocalDateTime.now());
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
            existing.setUpdateTime(LocalDateTime.now());
            deviceOrganizationMapper.updateById(existing);
            return false;
        }
        DeviceOrganization org = new DeviceOrganization();
        org.setDeviceOrganizationId(orgId);
        org.setDeviceOrganizationName(node.orgName());
        org.setDeviceOrganizationVendor(provider.getVendor());
        org.setDeviceOrganizationExternalId(node.orgId());
        org.setDeviceOrganizationParentId(parentId);
        org.setUpdateTime(LocalDateTime.now());
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
            existing.setUpdateTime(LocalDateTime.now());
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
        person.setUpdateTime(LocalDateTime.now());
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
        DeviceSyncRecord record = startRecord(StringUtils.hasText(vendor) ? vendor : "all", vendor, "pull", "devices");
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
        finishRecord(record, created, updated, skipped, null);
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
            existing.setUpdateTime(LocalDateTime.now());
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
        DeviceSyncRecord record = startRecord(device.getDeviceInfoId(), device.getDeviceInfoVendor(), "push", "device");
        DeviceProvider provider = findProvider(device.getDeviceInfoVendor());
        if (provider == null) {
            log.warn("[Device][Push] 未注册厂商 Provider: vendor={}", device.getDeviceInfoVendor());
            finishRecord(record, 0, 0, 1, "未注册厂商 Provider: vendor=" + device.getDeviceInfoVendor());
            enqueueOutbox(device, "update", "未注册厂商 Provider: vendor=" + device.getDeviceInfoVendor());
            return false;
        }
        VendorDevice remote = toRemoteDevice(device);
        boolean exists = remoteExists(device);
        boolean ok;
        try {
            if (provider instanceof DahuaProvider || provider instanceof HikvisionProvider
                    || provider instanceof UniviewProvider) {
                DeviceProviderCredential credential = resolveDeviceCredential(device);
                if (credential == null) {
                    finishRecord(record, 0, 0, 1, "凭据不可用，无法写回");
                    enqueueOutbox(device, "update", "凭据不可用，无法写回");
                    return false;
                }
                DeviceConnectInfo connectInfo = deviceConnectInfo(device, credential);
                if (provider instanceof DahuaProvider dahuaProvider) {
                    ok = dahuaProvider.upsertRemote(connectInfo, remote, exists);
                } else if (provider instanceof HikvisionProvider hikvisionProvider) {
                    ok = hikvisionProvider.upsertRemote(connectInfo, remote, exists);
                } else if (provider instanceof UniviewProvider univiewProvider) {
                    ok = univiewProvider.upsertRemote(connectInfo, remote, exists);
                } else {
                    ok = false;
                }
                if (ok && !exists) {
                    // 首次写入成功后补记远端标识，后续推送走改名、删除走删除接口
                    persistExternalId(device, remote.externalId());
                }
            } else {
                ok = provider.upsertRemote(remote);
            }
        } catch (Exception e) {
            log.warn("[Device][Push] 厂商 {} 写回设备失败: {}, message={}",
                    provider.getVendor(), device.getDeviceInfoId(), e.getMessage());
            finishRecord(record, 0, 0, 1, "写回异常: " + e.getMessage());
            enqueueOutbox(device, "update", "写回异常: " + e.getMessage());
            return false;
        }
        log.info("[Device][Push] 厂商 {} 写回设备: id={}, externalId={}, result={}",
                provider.getVendor(), device.getDeviceInfoId(), device.getDeviceInfoExternalId(), ok);
        if (ok) {
            finishRecord(record, exists ? 0 : 1, exists ? 1 : 0, 0, null);
        } else {
            finishRecord(record, 0, 0, 1, "厂商写回未成功");
            enqueueOutbox(device, "update", "厂商写回未成功");
        }
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
        DeviceSyncRecord record = startRecord(device.getDeviceInfoId(), device.getDeviceInfoVendor(),
                "push", "device-delete");
        DeviceProvider provider = findProvider(device.getDeviceInfoVendor());
        if (provider == null) {
            log.warn("[Device][Push] 删除远端设备失败：未注册厂商 Provider: vendor={}", device.getDeviceInfoVendor());
            finishRecord(record, 0, 0, 1, "未注册厂商 Provider: vendor=" + device.getDeviceInfoVendor());
            enqueueOutbox(device, "delete", "未注册厂商 Provider: vendor=" + device.getDeviceInfoVendor());
            return false;
        }
        boolean ok;
        try {
            if (provider instanceof DahuaProvider || provider instanceof HikvisionProvider
                    || provider instanceof UniviewProvider) {
                DeviceProviderCredential credential = resolveDeviceCredential(device);
                if (credential == null) {
                    finishRecord(record, 0, 0, 1, "凭据不可用，无法删除远端");
                    enqueueOutbox(device, "delete", "凭据不可用，无法删除远端");
                    return false;
                }
                DeviceConnectInfo connectInfo = deviceConnectInfo(device, credential);
                if (provider instanceof DahuaProvider dahuaProvider) {
                    ok = dahuaProvider.removeRemote(connectInfo, externalId);
                } else if (provider instanceof HikvisionProvider hikvisionProvider) {
                    ok = hikvisionProvider.removeRemote(connectInfo, externalId);
                } else if (provider instanceof UniviewProvider univiewProvider) {
                    ok = univiewProvider.removeRemote(connectInfo, externalId);
                } else {
                    ok = false;
                }
            } else {
                ok = provider.removeRemote(externalId);
            }
        } catch (Exception e) {
            log.warn("[Device][Push] 厂商 {} 删除设备失败: id={}, message={}",
                    provider.getVendor(), device.getDeviceInfoId(), e.getMessage());
            finishRecord(record, 0, 0, 1, "删除远端异常: " + e.getMessage());
            enqueueOutbox(device, "delete", "删除远端异常: " + e.getMessage());
            return false;
        }
        log.info("[Device][Push] 厂商 {} 删除远端设备: id={}, externalId={}, result={}",
                provider.getVendor(), device.getDeviceInfoId(), externalId, ok);
        if (ok) {
            finishRecord(record, 0, 0, 1, 0, null);
        } else {
            finishRecord(record, 0, 0, 1, "厂商删除远端未成功");
            enqueueOutbox(device, "delete", "厂商删除远端未成功");
        }
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
     * <p>设备密码同时写入 {@code code}/{@code devCode}/{@code devicePassword}/{@code validateCode}：
     * 乐橙绑定用 {@code code}，云睿新增用 {@code devCode}，海康萤石/云眸新增用
     * {@code validateCode}，Provider 亦兼容 {@code devicePassword}。</p>
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
            extra.put("validateCode", password);
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
            patch.setUpdateTime(LocalDateTime.now());
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

    // ==================== 同步记录 / 推送队列（device_sync_record / device_sync_outbox） ====================

    /**
     * 落一条 running 状态的同步记录。
     *
     * <p>记录失败只告警不抛出，避免记录问题阻断同步主流程。</p>
     *
     * @param taskId 任务维度（凭据 ID / 厂商 / 设备 ID）
     * @param vendor 厂商标识，可空（凭据维度解析后回填）
     * @param mode   pull / push
     * @param phase  devices / organizations / persons / device / device-delete
     * @return 已入库的记录；落库失败返回 {@code null}
     */
    private DeviceSyncRecord startRecord(String taskId, String vendor, String mode, String phase) {
        try {
            DeviceSyncRecord record = new DeviceSyncRecord();
            record.setDeviceSyncRecordTaskId(taskId);
            record.setDeviceSyncRecordVendor(vendor);
            record.setDeviceSyncRecordMode(mode);
            record.setDeviceSyncRecordPhase(phase);
            record.setDeviceSyncRecordStatus("running");
            record.setDeviceSyncRecordStartedAt(LocalDateTime.now());
            syncRecordMapper.insert(record);
            return record;
        } catch (Exception e) {
            log.warn("[Device][Sync] 同步记录落库失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 回写同步记录的计数与结果状态（拉取方向）。
     *
     * @param record  开始时落库的记录
     * @param added   新增数
     * @param updated 更新数
     * @param skipped 失败/跳过数
     * @param message 失败原因；成功时为 {@code null}
     */
    private void finishRecord(DeviceSyncRecord record, int added, int updated, int skipped, String message) {
        finishRecord(record, added, updated, 0, skipped, message);
    }

    /**
     * 回写同步记录的计数与结果状态（含删除数，写回方向使用）。
     *
     * @param record  开始时落库的记录
     * @param added   新增数
     * @param updated 更新数
     * @param removed 删除数
     * @param failed  失败数
     * @param message 失败原因；成功时为 {@code null}
     */
    private void finishRecord(DeviceSyncRecord record, int added, int updated, int removed, int failed, String message) {
        if (record == null) {
            return;
        }
        try {
            record.setDeviceSyncRecordTotal(added + updated + removed + failed);
            record.setDeviceSyncRecordAdded(added);
            record.setDeviceSyncRecordUpdated(updated);
            record.setDeviceSyncRecordRemoved(removed);
            record.setDeviceSyncRecordFailed(failed);
            record.setDeviceSyncRecordStatus(statusOf(added, updated, removed, failed, message));
            record.setDeviceSyncRecordMessage(message);
            record.setDeviceSyncRecordFinishedAt(LocalDateTime.now());
            syncRecordMapper.updateById(record);
        } catch (Exception e) {
            log.warn("[Device][Sync] 同步记录回写失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 按计数与说明推导结果状态：有失败计数 = failed / partial；
     * 无失败计数但带失败说明（凭据失效、Provider 未注册、拉取异常等整段未跑通）= failed；否则 success。
     *
     * @param added   新增数
     * @param updated 更新数
     * @param removed 删除数
     * @param failed  失败数
     * @param message 结果说明；失败原因非空
     * @return success / partial / failed
     */
    private static String statusOf(int added, int updated, int removed, int failed, String message) {
        if (failed > 0) {
            return (added + updated + removed) > 0 ? "partial" : "failed";
        }
        return StringUtils.hasText(message) ? "failed" : "success";
    }

    /**
     * 写回失败入队（outbox），供「同步记录」视图重试。
     *
     * <p>同设备同动作已有 pending 行时只更新失败原因与时间，避免重复失败堆爆队列。</p>
     *
     * @param device  本地设备
     * @param action  update / delete
     * @param message 失败原因
     */
    private void enqueueOutbox(DeviceInfo device, String action, String message) {
        try {
            DeviceSyncOutbox pending = syncOutboxMapper.selectOne(new LambdaQueryWrapper<DeviceSyncOutbox>()
                    .eq(DeviceSyncOutbox::getDeviceSyncOutboxDeviceId, device.getDeviceInfoId())
                    .eq(DeviceSyncOutbox::getDeviceSyncOutboxAction, action)
                    .eq(DeviceSyncOutbox::getDeviceSyncOutboxStatus, "pending")
                    .orderByDesc(DeviceSyncOutbox::getDeviceSyncOutboxCreatedAt)
                    .last("limit 1"));
            if (pending != null) {
                pending.setDeviceSyncOutboxMessage(message);
                pending.setDeviceSyncOutboxNextAt(LocalDateTime.now());
                syncOutboxMapper.updateById(pending);
                return;
            }
            DeviceSyncOutbox outbox = new DeviceSyncOutbox();
            outbox.setDeviceSyncOutboxDeviceId(device.getDeviceInfoId());
            outbox.setDeviceSyncOutboxVendor(device.getDeviceInfoVendor());
            outbox.setDeviceSyncOutboxAction(action);
            outbox.setDeviceSyncOutboxPayload(writePayload(device));
            outbox.setDeviceSyncOutboxStatus("pending");
            outbox.setDeviceSyncOutboxRetry(0);
            outbox.setDeviceSyncOutboxNextAt(LocalDateTime.now());
            outbox.setDeviceSyncOutboxMessage(message);
            outbox.setDeviceSyncOutboxCreatedAt(LocalDateTime.now());
            syncOutboxMapper.insert(outbox);
        } catch (Exception e) {
            log.warn("[Device][Outbox] 写回失败入队异常: deviceId={}, message={}",
                    device.getDeviceInfoId(), e.getMessage());
        }
    }

    /**
     * outbox 载荷快照：只取写回所需字段（不含密码等敏感列），排障用。
     *
     * @param device 本地设备
     * @return JSON 快照；序列化失败退化为设备 ID
     */
    private String writePayload(DeviceInfo device) {
        try {
            return PAYLOAD_JSON.writeValueAsString(toRemoteDevice(device));
        } catch (Exception e) {
            return String.valueOf(device.getDeviceInfoId());
        }
    }

    /**
     * 批量把本地设备写回到厂商侧（推送方向）。
     *
     * <p>按 ID 逐台调用 {@link #pushDevice(DeviceInfo)}，单台失败不中断其余设备，
     * 失败设备已进入推送队列（outbox）可重试。</p>
     *
     * @param ids 设备 ID 列表
     * @return total / success / failed 汇总与逐台结果 items
     */
    @Override
    public Map<String, Object> pushDevices(List<String> ids) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int success = 0;
        int failed = 0;
        if (ids != null) {
            for (String id : ids) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                DeviceInfo device = deviceService.getDevice(id);
                if (device == null) {
                    item.put("result", false);
                    item.put("message", "设备不存在");
                    failed++;
                } else {
                    item.put("name", device.getDeviceInfoName());
                    boolean ok = pushDevice(device);
                    item.put("result", ok);
                    item.put("message", ok ? "写回成功" : "写回失败（已入重试队列）");
                    if (ok) {
                        success++;
                    } else {
                        failed++;
                    }
                }
                items.add(item);
            }
        }
        summary.put("items", items);
        summary.put("total", items.size());
        summary.put("success", success);
        summary.put("failed", failed);
        return summary;
    }
}
