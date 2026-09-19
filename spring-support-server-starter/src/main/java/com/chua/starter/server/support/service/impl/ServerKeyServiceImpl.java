package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.entity.MsServerKey;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerKeyMapper;
import com.chua.starter.server.support.service.ServerKeyService;
import com.chua.starter.server.support.util.CredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

/**
 * SSH 密钥库服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
@RequiredArgsConstructor
public class ServerKeyServiceImpl implements ServerKeyService {

    /**
     * 密钥 映射器
    */
    private final MsServerKeyMapper keyMapper;
    /**
     * 身份 映射器
    */
    private final MsServerIdentityMapper identityMapper;
    /**
     * 凭据加解密器
    */
    private final CredentialCipher cipher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param key 键
     * @return 创建的结果
     */
    public ReturnResult<MsServerKey> create(MsServerKey key) {
        if (key.getMsServerKeyName() == null || key.getMsServerKeyName().isEmpty()) {
            return ReturnResult.error("密钥名称不能为空");
        }
        if (key.getMsServerKeyPrivateKey() != null && !key.getMsServerKeyPrivateKey().isEmpty()) {
            key.setMsServerKeyPrivateKey(cipher.encrypt(key.getMsServerKeyPrivateKey()));
        }
        if (key.getMsServerKeyPassword() != null && !key.getMsServerKeyPassword().isEmpty()) {
            key.setMsServerKeyPassword(cipher.encrypt(key.getMsServerKeyPassword()));
        }
        keyMapper.insert(key);
        return ReturnResult.ok(mask(keyMapper.selectById(key.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新。
     * @param id 标识
     * @param key 键
     * @return 更新的结果
     */
    public ReturnResult<MsServerKey> update(Long id, MsServerKey key) {
        MsServerKey exists = keyMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("密钥不存在: " + id);
        }
 // 空串表示清空；空 表示保持不变；非空则加密覆盖
        if (key.getMsServerKeyPrivateKey() != null) {
            exists.setMsServerKeyPrivateKey(key.getMsServerKeyPrivateKey().isEmpty()
                    ? "" : cipher.encrypt(key.getMsServerKeyPrivateKey()));
        }
        if (key.getMsServerKeyPassword() != null) {
            exists.setMsServerKeyPassword(key.getMsServerKeyPassword().isEmpty()
                    ? "" : cipher.encrypt(key.getMsServerKeyPassword()));
        }
        exists.setMsServerKeyName(key.getMsServerKeyName() == null ? exists.getMsServerKeyName() : key.getMsServerKeyName());
        exists.setMsServerKeyType(key.getMsServerKeyType() == null ? exists.getMsServerKeyType() : key.getMsServerKeyType());
        exists.setMsServerKeyPublicKey(key.getMsServerKeyPublicKey() == null ? exists.getMsServerKeyPublicKey() : key.getMsServerKeyPublicKey());
        exists.setMsServerKeyRemark(key.getMsServerKeyRemark() == null ? exists.getMsServerKeyRemark() : key.getMsServerKeyRemark());
        keyMapper.updateById(exists);
        return ReturnResult.ok(mask(keyMapper.selectById(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        Long refCount = identityMapper.selectCount(
                new LambdaQueryWrapper<MsServerIdentity>().eq(MsServerIdentity::getMsServerIdentityKeyId, id));
        if (refCount != null && refCount > 0) {
            return ReturnResult.error("密钥被登录身份引用，禁止删除");
        }
        keyMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsServerKey>> list() {
        List<MsServerKey> keys = keyMapper.selectList(
                new LambdaQueryWrapper<MsServerKey>().orderByDesc(MsServerKey::getId));
        keys.forEach(this::mask);
        return ReturnResult.list(keys);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerKey> generate(String name, String type, String password, String remark) {
        if (name == null || name.isBlank()) {
            return ReturnResult.error("密钥名称不能为空");
        }
        String keyType = type == null || type.isBlank() ? "RSA" : type.trim().toUpperCase();
        try {
            java.security.KeyPairGenerator generator;
            switch (keyType) {
                case "ED25519" -> generator = java.security.KeyPairGenerator.getInstance("Ed25519");
                case "ECDSA" -> generator = java.security.KeyPairGenerator.getInstance("EC");
                default -> {
                    keyType = "RSA";
                    generator = java.security.KeyPairGenerator.getInstance("RSA");
                    generator.initialize(4096);
                }
            }
            java.security.KeyPair pair = generator.generateKeyPair();
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

            MsServerKey key = new MsServerKey();
            key.setMsServerKeyName(name.trim());
            key.setMsServerKeyType(keyType);
            key.setMsServerKeyPublicKey(publicKey);
            key.setMsServerKeyPrivateKey(cipher.encrypt(privateKey));
            if (password != null && !password.isBlank()) {
                key.setMsServerKeyPassword(cipher.encrypt(password));
            }
            key.setMsServerKeyRemark(remark);
            keyMapper.insert(key);
            return ReturnResult.ok(mask(keyMapper.selectById(key.getId())));
        } catch (Exception e) {
            return ReturnResult.error("生成密钥失败: " + e.getMessage());
        }
    }

    /**
     * 脱敏：清除私钥与口令
     *
     * @param key 密钥
     * @return 同一对象（已脱敏）
     */
    private MsServerKey mask(MsServerKey key) {
        key.setMsServerKeyPrivateKey(null);
        key.setMsServerKeyPassword(null);
        return key;
    }
}
