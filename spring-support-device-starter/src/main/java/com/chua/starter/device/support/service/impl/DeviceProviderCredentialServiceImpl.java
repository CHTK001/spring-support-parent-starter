// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.mapper.DeviceProviderCredentialMapper;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供商接入凭据服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProviderCredentialServiceImpl implements DeviceProviderCredentialService {

    /** 代码内置的兜底阈值（厂商无默认套时使用），同时作为默认套的初始值 */
    private static final Map<String, Integer> FALLBACK_THRESHOLDS = Map.of(
            "hikvision", 60,
            "dahua", 60,
            "ezviz", 120,
            "isup", 90
    );

    /** 未显式配置时的阈值 */
    private static final int DEFAULT_THRESHOLD = 60;

    /** 提供商接入凭据 映射器 */
    private final DeviceProviderCredentialMapper credentialMapper;

    @Override
    public List<DeviceProviderCredential> list(String vendor) {
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(vendor)) {
            wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, vendor);
        }
        wrapper.orderByAsc(DeviceProviderCredential::getDeviceCredentialVendor)
                .orderByAsc(DeviceProviderCredential::getDeviceCredentialPlatformType)
                .orderByAsc(DeviceProviderCredential::getDeviceCredentialName);
        return credentialMapper.selectList(wrapper);
    }

    @Override
    public DeviceProviderCredential get(String id) {
        return id == null ? null : credentialMapper.selectById(id);
    }

    @Override
    public DeviceProviderCredential resolveById(String id) {
        return get(id);
    }

    @Override
    @Transactional
    /**
     * 创建
     * @param credential 参数
     * @return 返回值
    */
    public DeviceProviderCredential create(DeviceProviderCredential credential) {
        normalize(credential);
        assertNameFree(credential, null);
        // 厂商下第一套凭据自动成为默认套，避免厂商有凭据却取不到阈值
        if (Boolean.TRUE.equals(credential.getDeviceCredentialIsDefault())) {
            clearVendorDefault(credential.getDeviceCredentialVendor(), null);
        } else if (countVendor(credential.getDeviceCredentialVendor()) == 0) {
            credential.setDeviceCredentialIsDefault(true);
        }
        credential.setDeviceCredentialCreatedAt(LocalDateTime.now());
        credential.setDeviceCredentialUpdatedAt(LocalDateTime.now());
        credentialMapper.insert(credential);
        log.info("新增提供商凭据: vendor={}, platform={}, name={}",
                credential.getDeviceCredentialVendor(),
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialName());
        return credential;
    }

    @Override
    @Transactional
    /**
     * 更新
     * @param credential 参数
     * @param id 字符串
     * @return 返回值
    */
    public DeviceProviderCredential update(String id, DeviceProviderCredential credential) {
        DeviceProviderCredential existing = credentialMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        if (credential == null) {
            throw new IllegalArgumentException("待更新内容不能为空");
        }
        // 合并而不是整对象覆盖：编辑表单不带默认套标记时，不能把原有默认态抹掉
        DeviceProviderCredential update = new DeviceProviderCredential();
        update.setDeviceCredentialId(id);
        update.setDeviceCredentialVendor(firstText(credential.getDeviceCredentialVendor(),
                existing.getDeviceCredentialVendor()));
        update.setDeviceCredentialPlatformType(credential.getDeviceCredentialPlatformType() != null
                ? credential.getDeviceCredentialPlatformType()
                : existing.getDeviceCredentialPlatformType());
        update.setDeviceCredentialName(firstText(credential.getDeviceCredentialName(),
                existing.getDeviceCredentialName()));
        update.setDeviceCredentialAppKey(credential.getDeviceCredentialAppKey() != null
                ? credential.getDeviceCredentialAppKey()
                : existing.getDeviceCredentialAppKey());
        // 留空表示不改 SK，避免编辑其它字段时把密钥洗掉
        update.setDeviceCredentialAppSecret(firstText(credential.getDeviceCredentialAppSecret(),
                existing.getDeviceCredentialAppSecret()));
        update.setDeviceCredentialProductCode(credential.getDeviceCredentialProductCode() != null
                ? credential.getDeviceCredentialProductCode()
                : existing.getDeviceCredentialProductCode());
        update.setDeviceCredentialProjectId(credential.getDeviceCredentialProjectId() != null
                ? credential.getDeviceCredentialProjectId()
                : existing.getDeviceCredentialProjectId());
        update.setDeviceCredentialBaseUrl(credential.getDeviceCredentialBaseUrl() != null
                ? credential.getDeviceCredentialBaseUrl()
                : existing.getDeviceCredentialBaseUrl());
        update.setDeviceCredentialOfflineThreshold(credential.getDeviceCredentialOfflineThreshold() != null
                ? credential.getDeviceCredentialOfflineThreshold()
                : existing.getDeviceCredentialOfflineThreshold());
        update.setDeviceCredentialIsDefault(credential.getDeviceCredentialIsDefault() != null
                ? credential.getDeviceCredentialIsDefault()
                : existing.getDeviceCredentialIsDefault());
        update.setDeviceCredentialEnabled(credential.getDeviceCredentialEnabled() != null
                ? credential.getDeviceCredentialEnabled()
                : existing.getDeviceCredentialEnabled());
        update.setDeviceCredentialRemark(credential.getDeviceCredentialRemark() != null
                ? credential.getDeviceCredentialRemark()
                : existing.getDeviceCredentialRemark());
        // 凭据级高级配置（默认全关）：与现有字段的合并方式一致，空代表不改
        update.setDeviceCredentialOfflineDetectEnabled(credential.getDeviceCredentialOfflineDetectEnabled() != null
                ? credential.getDeviceCredentialOfflineDetectEnabled()
                : existing.getDeviceCredentialOfflineDetectEnabled());
        update.setDeviceCredentialOfflineDetectInterval(credential.getDeviceCredentialOfflineDetectInterval() != null
                ? credential.getDeviceCredentialOfflineDetectInterval()
                : existing.getDeviceCredentialOfflineDetectInterval());
        update.setDeviceCredentialEventPollEnabled(credential.getDeviceCredentialEventPollEnabled() != null
                ? credential.getDeviceCredentialEventPollEnabled()
                : existing.getDeviceCredentialEventPollEnabled());
        update.setDeviceCredentialEventPollInterval(credential.getDeviceCredentialEventPollInterval() != null
                ? credential.getDeviceCredentialEventPollInterval()
                : existing.getDeviceCredentialEventPollInterval());
        update.setDeviceCredentialDeviceSyncEnabled(credential.getDeviceCredentialDeviceSyncEnabled() != null
                ? credential.getDeviceCredentialDeviceSyncEnabled()
                : existing.getDeviceCredentialDeviceSyncEnabled());
        update.setDeviceCredentialDeviceSyncInterval(credential.getDeviceCredentialDeviceSyncInterval() != null
                ? credential.getDeviceCredentialDeviceSyncInterval()
                : existing.getDeviceCredentialDeviceSyncInterval());
        update.setDeviceCredentialOrgSyncEnabled(credential.getDeviceCredentialOrgSyncEnabled() != null
                ? credential.getDeviceCredentialOrgSyncEnabled()
                : existing.getDeviceCredentialOrgSyncEnabled());
        update.setDeviceCredentialOrgSyncInterval(credential.getDeviceCredentialOrgSyncInterval() != null
                ? credential.getDeviceCredentialOrgSyncInterval()
                : existing.getDeviceCredentialOrgSyncInterval());

        normalize(update);
        assertNameFree(update, id);
        if (Boolean.TRUE.equals(update.getDeviceCredentialIsDefault())) {
            clearVendorDefault(update.getDeviceCredentialVendor(), id);
        }
        update.setDeviceCredentialUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(update);
        log.info("更新提供商凭据: id={}, vendor={}, name={}, enabled={}",
                id, update.getDeviceCredentialVendor(), update.getDeviceCredentialName(),
                update.getDeviceCredentialEnabled());
        return credentialMapper.selectById(id);
    }

    @Override
    @Transactional
    /**
     * update Enabled
     * @param enabled 布尔值
     * @param id 字符串
     * @return 返回值
    */
    public DeviceProviderCredential updateEnabled(String id, boolean enabled) {
        DeviceProviderCredential existing = credentialMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        DeviceProviderCredential update = new DeviceProviderCredential();
        update.setDeviceCredentialId(id);
        update.setDeviceCredentialEnabled(enabled);
        update.setDeviceCredentialUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(update);
        log.info("切换提供商凭据启停: id={}, enabled={}", id, enabled);
        return credentialMapper.selectById(id);
    }

    @Override
    @Transactional
    /**
     * 删除
     * @param id 字符串
     * @return 返回值
    */
    public void delete(String id) {
        DeviceProviderCredential existing = credentialMapper.selectById(id);
        if (existing == null) {
            return;
        }
        credentialMapper.deleteById(id);
        // 删掉的若正是默认套，把该厂商剩余启用套里最早的一套提为默认
        if (Boolean.TRUE.equals(existing.getDeviceCredentialIsDefault())) {
            promoteFallbackDefault(existing.getDeviceCredentialVendor());
        }
        log.info("删除提供商凭据: id={}, vendor={}, name={}",
                id, existing.getDeviceCredentialVendor(), existing.getDeviceCredentialName());
    }

    @Override
    public DeviceProviderCredential resolveFor(String vendor, String platformType) {
        if (!StringUtils.hasText(vendor)) {
            return null;
        }
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, vendor)
                .eq(DeviceProviderCredential::getDeviceCredentialEnabled, true);
        if (StringUtils.hasText(platformType)) {
            wrapper.eq(DeviceProviderCredential::getDeviceCredentialPlatformType, platformType);
        }
        // 默认套优先；否则取排序最靠前的一套，保证建连确定性
        wrapper.orderByDesc(DeviceProviderCredential::getDeviceCredentialIsDefault)
                .orderByAsc(DeviceProviderCredential::getDeviceCredentialCreatedAt);
        return credentialMapper.selectList(wrapper).stream().findFirst().orElse(null);
    }

    @Override
    public Map<String, Integer> vendorThresholds() {
        Map<String, Integer> thresholds = new LinkedHashMap<>(FALLBACK_THRESHOLDS);
        for (DeviceProviderCredential c : list(null)) {
            Integer threshold = c.getDeviceCredentialOfflineThreshold();
            if (threshold == null || threshold <= 0) {
                continue;
            }
            String vendor = c.getDeviceCredentialVendor();
            // 非默认套不覆盖厂商阈值；厂商无默认套时才用它兜底
            if (Boolean.TRUE.equals(c.getDeviceCredentialIsDefault())
                    || !thresholds.containsKey(vendor)) {
                thresholds.put(vendor, threshold);
            }
        }
        return thresholds;
    }

    /**
     * 取第一个非空白文本；候选为空时回退到原值
     * @param candidate 字符串
     * @param fallback 字符串
     * @return 返回值
    */
    private static String firstText(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate : fallback;
    }

    /**
     * normalize
     * @param c 参数
     * @return 返回值
    */
    private void normalize(DeviceProviderCredential c) {
        if (c == null) {
            throw new IllegalArgumentException("凭据内容为空");
        }
        if (!StringUtils.hasText(c.getDeviceCredentialVendor())) {
            throw new IllegalArgumentException("厂商标识不能为空");
        }
        if (!StringUtils.hasText(c.getDeviceCredentialName())) {
            throw new IllegalArgumentException("凭据名称不能为空");
        }
        c.setDeviceCredentialVendor(c.getDeviceCredentialVendor().trim().toLowerCase());
        c.setDeviceCredentialName(c.getDeviceCredentialName().trim());
        if (!StringUtils.hasText(c.getDeviceCredentialPlatformType())) {
            // SDK 直连无平台类型，归一为空串便于唯一性比较与后续查询
            c.setDeviceCredentialPlatformType("");
        }
        if (c.getDeviceCredentialOfflineThreshold() == null || c.getDeviceCredentialOfflineThreshold() <= 0) {
            c.setDeviceCredentialOfflineThreshold(FALLBACK_THRESHOLDS.getOrDefault(
                    c.getDeviceCredentialVendor(), DEFAULT_THRESHOLD));
        }
        if (c.getDeviceCredentialEnabled() == null) {
            c.setDeviceCredentialEnabled(true);
        }
        if (c.getDeviceCredentialIsDefault() == null) {
            c.setDeviceCredentialIsDefault(false);
        }
        if (c.getDeviceCredentialOfflineDetectEnabled() == null) {
            c.setDeviceCredentialOfflineDetectEnabled(false);
        }
        if (c.getDeviceCredentialEventPollEnabled() == null) {
            c.setDeviceCredentialEventPollEnabled(false);
        }
        if (c.getDeviceCredentialDeviceSyncEnabled() == null) {
            c.setDeviceCredentialDeviceSyncEnabled(false);
        }
        if (c.getDeviceCredentialOrgSyncEnabled() == null) {
            c.setDeviceCredentialOrgSyncEnabled(false);
        }
    }

    /**
     * assert Name Free
     * @param c 参数
     * @param selfId 字符串
     * @return 返回值
    */
    private void assertNameFree(DeviceProviderCredential c, String selfId) {
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, c.getDeviceCredentialVendor())
                .eq(DeviceProviderCredential::getDeviceCredentialPlatformType, c.getDeviceCredentialPlatformType())
                .eq(DeviceProviderCredential::getDeviceCredentialName, c.getDeviceCredentialName());
        if (selfId != null) {
            wrapper.ne(DeviceProviderCredential::getDeviceCredentialId, selfId);
        }
        if (credentialMapper.selectCount(wrapper) > 0) {
            throw new IllegalStateException(
                    "该厂商/平台下已存在同名凭据：" + c.getDeviceCredentialName());
        }
    }

    /**
     * count Vendor
     * @param vendor 字符串
     * @return 返回值
    */
    private long countVendor(String vendor) {
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, vendor);
        return credentialMapper.selectCount(wrapper);
    }

    /**
     * clear Vendor Default
     * @param keepId 字符串
     * @param vendor 字符串
     * @return 返回值
    */
    private void clearVendorDefault(String vendor, String keepId) {
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, vendor)
                .eq(DeviceProviderCredential::getDeviceCredentialIsDefault, true);
        if (keepId != null) {
            wrapper.ne(DeviceProviderCredential::getDeviceCredentialId, keepId);
        }
        for (DeviceProviderCredential c : credentialMapper.selectList(wrapper)) {
            c.setDeviceCredentialIsDefault(false);
            c.setDeviceCredentialUpdatedAt(LocalDateTime.now());
            credentialMapper.updateById(c);
        }
    }

    /**
     * promote Fallback Default
     * @param vendor 字符串
     * @return 返回值
    */
    private void promoteFallbackDefault(String vendor) {
        List<DeviceProviderCredential> rest = list(vendor);
        DeviceProviderCredential next = rest.stream()
                .filter(c -> Boolean.TRUE.equals(c.getDeviceCredentialEnabled()))
                .findFirst()
                .orElse(null);
        if (next != null) {
            next.setDeviceCredentialIsDefault(true);
            next.setDeviceCredentialUpdatedAt(LocalDateTime.now());
            credentialMapper.updateById(next);
        }
    }}