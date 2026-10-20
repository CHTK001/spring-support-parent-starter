package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.mapper.DeviceProviderCredentialMapper;
import com.chua.starter.device.support.security.DeviceSecretCodec;
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

    /** 密钥编解码器：SK 落库加密、建连时解密 */
    private final DeviceSecretCodec secretCodec;

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
    @Transactional
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
        // SK 落库加密；空值保持为空（SDK 直连无 SK）
        credential.setDeviceCredentialAppSecret(
                secretCodec.encrypt(credential.getDeviceCredentialAppSecret()));
        credentialMapper.insert(credential);
        log.info("新增提供商凭据: vendor={}, platform={}, name={}",
                credential.getDeviceCredentialVendor(),
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialName());
        return credential;
    }

    @Override
    @Transactional
    public DeviceProviderCredential update(String id, DeviceProviderCredential credential) {
        DeviceProviderCredential existing = credentialMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        normalize(credential);
        assertNameFree(credential, id);
        if (Boolean.TRUE.equals(credential.getDeviceCredentialIsDefault())) {
            clearVendorDefault(credential.getDeviceCredentialVendor(), id);
        }
        // 编辑时前端回传空串或掩码表示“不修改”，保持原 SK
        String incomingSecret = credential.getDeviceCredentialAppSecret();
        if (!StringUtils.hasText(incomingSecret) || secretCodec.isMasked(incomingSecret)) {
            credential.setDeviceCredentialAppSecret(null);
        } else {
            credential.setDeviceCredentialAppSecret(secretCodec.encrypt(incomingSecret));
        }
        credential.setDeviceCredentialId(id);
        // 创建时间不可改，避免覆盖历史
        credential.setDeviceCredentialCreatedAt(null);
        credential.setDeviceCredentialUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(credential);
        log.info("更新提供商凭据: id={}, vendor={}, name={}",
                id, credential.getDeviceCredentialVendor(), credential.getDeviceCredentialName());
        return credentialMapper.selectById(id);
    }

    @Override
    @Transactional
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
        DeviceProviderCredential credential =
                credentialMapper.selectList(wrapper).stream().findFirst().orElse(null);
        if (credential != null) {
            // 建连需要明文 SK；响应侧由 WRITE_ONLY 保证不会外泄
            credential.setDeviceCredentialAppSecret(
                    secretCodec.decrypt(credential.getDeviceCredentialAppSecret()));
        }
        return credential;
    }

    @Override
    public DeviceProviderCredential resolveById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        DeviceProviderCredential credential = credentialMapper.selectById(id);
        if (credential != null) {
            credential.setDeviceCredentialAppSecret(
                    secretCodec.decrypt(credential.getDeviceCredentialAppSecret()));
        }
        return credential;
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

    /** 补齐默认值并做基本校验 */
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
    }

    /** 同厂商同平台下凭据名称唯一 */
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

    private long countVendor(String vendor) {
        LambdaQueryWrapper<DeviceProviderCredential> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProviderCredential::getDeviceCredentialVendor, vendor);
        return credentialMapper.selectCount(wrapper);
    }

    /** 清除该厂商的默认标记（排除自身），保证每厂商至多一套默认 */
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

    /** 默认套被删后，把该厂商最早的一套启用凭据提为默认 */
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
    }
}