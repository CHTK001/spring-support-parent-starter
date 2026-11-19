package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.service.ServerIdentityService;
import com.chua.starter.server.support.util.CredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 登录身份服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
@RequiredArgsConstructor
public class ServerIdentityServiceImpl implements ServerIdentityService {

    /**
     * 身份 映射器
    */
    private final MsServerIdentityMapper identityMapper;
    /**
     * 主机身份关联 映射器
    */
    private final MsServerHostIdentityMapper hostIdentityMapper;
    /**
     * 凭据加解密器
    */
    private final CredentialCipher cipher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param identity identity
     * @return 创建的结果
     */
    public ReturnResult<MsServerIdentity> create(MsServerIdentity identity) {
        if (identity.getServerIdentityName() == null || identity.getServerIdentityName().isEmpty()) {
            return ReturnResult.error("身份名称不能为空");
        }
        String authType = identity.getServerIdentityAuthType();
        if (authType == null || authType.isBlank()) {
            return ReturnResult.error("认证类型不能为空");
        }
 // 特定类型保留额外校验；其他类型（令牌/API_键/OAuth2/Cookie/账户/自定义）自由入库
        if ("PUBLIC_KEY".equals(authType)) {
            if (identity.getServerIdentityKeyId() == null) {
                return ReturnResult.error("公钥认证必须引用密钥库条目");
            }
        } else if ("MAILBOX".equals(authType)) {
            String email = identity.getServerIdentityUsername();
            if (email == null || !email.contains("@")) {
                return ReturnResult.error("邮箱账号必须提供邮箱地址(用户名)");
            }
            if (identity.getServerIdentityPassword() == null || identity.getServerIdentityPassword().isEmpty()) {
                return ReturnResult.error("邮箱账号必须提供 IMAP 授权码(密码)");
            }
            String source = identity.getServerIdentityEmailSource();
            if ("TEMP".equals(source)) {
                String provider = identity.getServerIdentityProvider();
                if (provider == null || provider.isBlank()) {
                    return ReturnResult.error("临时邮箱必须指定服务商(provider)");
                }
            }
        }
        if (identity.getServerIdentityPassword() != null && !identity.getServerIdentityPassword().isEmpty()) {
            identity.setServerIdentityPassword(cipher.encrypt(identity.getServerIdentityPassword()));
        }
        identityMapper.insert(identity);
        return ReturnResult.ok(mask(identityMapper.selectById(identity.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新。
     * @param id 标识
     * @param identity identity
     * @return 更新的结果
     */
    public ReturnResult<MsServerIdentity> update(Long id, MsServerIdentity identity) {
        MsServerIdentity exists = identityMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("身份不存在: " + id);
        }
        if (identity.getServerIdentityPassword() != null) {
            exists.setServerIdentityPassword(identity.getServerIdentityPassword().isEmpty()
                    ? "" : cipher.encrypt(identity.getServerIdentityPassword()));
        }
        exists.setServerIdentityName(identity.getServerIdentityName() == null
                ? exists.getServerIdentityName() : identity.getServerIdentityName());
        exists.setServerIdentityAuthType(identity.getServerIdentityAuthType() == null
                ? exists.getServerIdentityAuthType() : identity.getServerIdentityAuthType());
        exists.setServerIdentityUsername(identity.getServerIdentityUsername() == null
                ? exists.getServerIdentityUsername() : identity.getServerIdentityUsername());
        exists.setServerIdentityKeyId(identity.getServerIdentityKeyId() == null
                ? exists.getServerIdentityKeyId() : identity.getServerIdentityKeyId());
        exists.setServerIdentityEmailSource(identity.getServerIdentityEmailSource() == null
                ? exists.getServerIdentityEmailSource() : identity.getServerIdentityEmailSource());
        exists.setServerIdentityProvider(identity.getServerIdentityProvider() == null
                ? exists.getServerIdentityProvider() : identity.getServerIdentityProvider());
        exists.setServerIdentityImapHost(identity.getServerIdentityImapHost() == null
                ? exists.getServerIdentityImapHost() : identity.getServerIdentityImapHost());
        exists.setServerIdentityImapPort(identity.getServerIdentityImapPort() == null
                ? exists.getServerIdentityImapPort() : identity.getServerIdentityImapPort());
        exists.setServerIdentityRemark(identity.getServerIdentityRemark() == null
                ? exists.getServerIdentityRemark() : identity.getServerIdentityRemark());
        identityMapper.updateById(exists);
        return ReturnResult.ok(mask(identityMapper.selectById(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        hostIdentityMapper.delete(new LambdaQueryWrapper<MsServerHostIdentity>()
                .eq(MsServerHostIdentity::getServerHostIdentityIdentityId, id));
        identityMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsServerIdentity>> list() {
        List<MsServerIdentity> identities = identityMapper.selectList(
                new LambdaQueryWrapper<MsServerIdentity>().orderByDesc(MsServerIdentity::getId));
        identities.forEach(this::mask);
        return ReturnResult.list(identities);
    }

    /**
     * 脱敏：清除密码明文
     *
     * @param identity 身份
     * @return 同一对象（已脱敏）
     */
    private MsServerIdentity mask(MsServerIdentity identity) {
        identity.setServerIdentityPassword(null);
        return identity;
    }
}
