package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingFileStorage;
import com.chua.starter.proxy.support.mapper.SystemServerSettingFileStorageMapper;
import com.chua.starter.proxy.support.service.server.SystemServerSettingFileStorageService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingFileStorageServiceImpl extends ServiceImpl<SystemServerSettingFileStorageMapper, SystemServerSettingFileStorage>
        implements SystemServerSettingFileStorageService {

    /**
     * AES加密算法
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * AES加密密钥（实际项目中应该从配置文件或环境变量中获取）
     */
    private static final String SECRET_KEY = "MySecretKey12345"; // 16字节密钥

    private final SystemServerSettingService systemServerSettingService;

    /**
     * 重写save方法，在保存前加密敏感数据
     */
    @Override
    public boolean save(SystemServerSettingFileStorage entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.save(entity);
    }

    /**
     * 重写updateById方法，在更新前加密敏感数据
     */
    @Override
    public boolean updateById(SystemServerSettingFileStorage entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.updateById(entity);
    }

    /**
     * 重写getById方法，在查询后解密敏感数据
     */
    @Override
    public SystemServerSettingFileStorage getById(Serializable id) {
        SystemServerSettingFileStorage entity = super.getById(id);
        if (entity != null) {
            decryptSensitiveData(entity);
        }
        return entity;
    }

    /**
     * 重写list方法，在查询后解密敏感数据
     */
    @Override
    public List<SystemServerSettingFileStorage> list() {
        List<SystemServerSettingFileStorage> entities = super.list();
        if (entities != null) {
            entities.forEach(this::decryptSensitiveData);
        }
        return entities;
    }


    /**
     * 加密SystemServerSettingFileStorage中的敏感数据
     *
     * @param entity 文件存储配置对象
     */
    private void encryptSensitiveData(SystemServerSettingFileStorage entity) {
        if (entity == null) {
            return;
        }
        
        // 加密AccessKey
        if (entity.getFileStorageAccessKey() != null && !entity.getFileStorageAccessKey().isEmpty()) {
            entity.setFileStorageAccessKey(encrypt(entity.getFileStorageAccessKey()));
        }
        
        // 加密SecretKey
        if (entity.getFileStorageSecretKey() != null && !entity.getFileStorageSecretKey().isEmpty()) {
            entity.setFileStorageSecretKey(encrypt(entity.getFileStorageSecretKey()));
        }
    }

    /**
     * 解密SystemServerSettingFileStorage中的敏感数据
     *
     * @param entity 文件存储配置对象
     */
    private void decryptSensitiveData(SystemServerSettingFileStorage entity) {
        if (entity == null) {
            return;
        }
        
        // 解密AccessKey
        if (entity.getFileStorageAccessKey() != null && !entity.getFileStorageAccessKey().isEmpty()) {
            entity.setFileStorageAccessKey(decrypt(entity.getFileStorageAccessKey()));
        }
        
        // 解密SecretKey
        if (entity.getFileStorageSecretKey() != null && !entity.getFileStorageSecretKey().isEmpty()) {
            entity.setFileStorageSecretKey(decrypt(entity.getFileStorageSecretKey()));
        }
    }

    /**
     * AES加密
     *
     * @param data 待加密数据
     * @return 加密后的数据
     */
    private String encrypt(String data) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            log.error("加密失败", e);
            return data; // 加密失败时返回原数据
        }
    }

    /**
     * AES解密
     *
     * @param encryptedData 待解密数据
     * @return 解密后的数据
     */
    private String decrypt(String encryptedData) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            return encryptedData; // 解密失败时返回原数据
        }
    }

    @Override
    public List<SystemServerSettingFileStorage> listByServerId(Integer serverId) {
        List<SystemServerSettingFileStorage> entities = lambdaQuery().eq(SystemServerSettingFileStorage::getFileStorageServerId, serverId).list();
        if (entities != null) {
            entities.forEach(this::decryptSensitiveData);
        }
        return entities;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> replaceAllForServer(Integer serverId, List<SystemServerSettingFileStorage> configs) {
        try {
            // 先删除旧配置
            lambdaUpdate().eq(SystemServerSettingFileStorage::getFileStorageServerId, serverId).remove();
            // 批量插入新配置
            if (configs != null && !configs.isEmpty()) {
                for (SystemServerSettingFileStorage cfg : configs) {
                    cfg.setFileStorageServerId(serverId);
                }
                saveBatch(configs);
            }
            // 保存后热应用
            systemServerSettingService.applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存文件存储配置失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> saveOne(SystemServerSettingFileStorage config) {
        try {
            saveOrUpdate(config);
            systemServerSettingService.applyConfigToRunningServer(config.getFileStorageServerId());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}






