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

    /** 身份 映射器 */
    private final MsServerIdentityMapper identityMapper;
    /** 主机身份关联 映射器 */
    private final MsServerHostIdentityMapper hostIdentityMapper;
    /** 凭据加解密器 */
    private final CredentialCipher cipher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param identity identity
     * @return 创建的结果
     */
    public ReturnResult<MsServerIdentity> create(MsServerIdentity identity) {
        if (identity.getMsServerIdentityName() == null || identity.getMsServerIdentityName().isEmpty()) {
            return ReturnResult.error("身份名称不能为空");
        }
        String authType = identity.getMsServerIdentityAuthType();
        if (authType == null || authType.isBlank()) {
            return ReturnResult.error("认证类型不能为空");
        }
 // 特定类型保留额外校验；其他类型（令牌/API_键/OAuth2/Cookie/账户/自定义）自由入库
        if ("PUBLIC_KEY".equals(authType)) {
            if (identity.getMsServerIdentityKeyId() == null) {
                return ReturnResult.error("公钥认证必须引用密钥库条目");
            }
        } else if ("MAILBOX".equals(authType)) {
            String email = identity.getMsServerIdentityUsername();
            if (email == null || !email.contains("@")) {
                return ReturnResult.error("邮箱账号必须提供邮箱地址(用户名)");
            }
            if (identity.getMsServerIdentityPassword() == null || identity.getMsServerIdentityPassword().isEmpty()) {
                return ReturnResult.error("邮箱账号必须提供 IMAP 授权码(密码)");
            }
            String source = identity.getMsServerIdentityEmailSource();
            if ("TEMP".equals(source)) {
                String provider = identity.getMsServerIdentityProvider();
                if (provider == null || provider.isBlank()) {
                    return ReturnResult.error("临时邮箱必须指定服务商(provider)");
                }
            }
        }
        if (identity.getMsServerIdentityPassword() != null && !identity.getMsServerIdentityPassword().isEmpty()) {
            identity.setMsServerIdentityPassword(cipher.encrypt(identity.getMsServerIdentityPassword()));
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
        if (identity.getMsServerIdentityPassword() != null) {
            exists.setMsServerIdentityPassword(identity.getMsServerIdentityPassword().isEmpty()
                    ? "" : cipher.encrypt(identity.getMsServerIdentityPassword()));
        }
        exists.setMsServerIdentityName(identity.getMsServerIdentityName() == null
                ? exists.getMsServerIdentityName() : identity.getMsServerIdentityName());
        exists.setMsServerIdentityAuthType(identity.getMsServerIdentityAuthType() == null
                ? exists.getMsServerIdentityAuthType() : identity.getMsServerIdentityAuthType());
        exists.setMsServerIdentityUsername(identity.getMsServerIdentityUsername() == null
                ? exists.getMsServerIdentityUsername() : identity.getMsServerIdentityUsername());
        exists.setMsServerIdentityKeyId(identity.getMsServerIdentityKeyId() == null
                ? exists.getMsServerIdentityKeyId() : identity.getMsServerIdentityKeyId());
        exists.setMsServerIdentityEmailSource(identity.getMsServerIdentityEmailSource() == null
                ? exists.getMsServerIdentityEmailSource() : identity.getMsServerIdentityEmailSource());
        exists.setMsServerIdentityProvider(identity.getMsServerIdentityProvider() == null
                ? exists.getMsServerIdentityProvider() : identity.getMsServerIdentityProvider());
        exists.setMsServerIdentityImapHost(identity.getMsServerIdentityImapHost() == null
                ? exists.getMsServerIdentityImapHost() : identity.getMsServerIdentityImapHost());
        exists.setMsServerIdentityImapPort(identity.getMsServerIdentityImapPort() == null
                ? exists.getMsServerIdentityImapPort() : identity.getMsServerIdentityImapPort());
        exists.setMsServerIdentityRemark(identity.getMsServerIdentityRemark() == null
                ? exists.getMsServerIdentityRemark() : identity.getMsServerIdentityRemark());
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
                .eq(MsServerHostIdentity::getMsServerHostIdentityIdentityId, id));
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
        identity.setMsServerIdentityPassword(null);
        return identity;
    }
}
