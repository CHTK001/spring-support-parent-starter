package com.chua.starter.sso.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.entity.system.SysUser;
import com.chua.starter.entity.system.SysUserThirdParty;
import com.chua.starter.sso.mapper.SysUserThirdPartyMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 小程序已验证身份与本地账号的原子关联；令牌只能在本事务提交后签发。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class MiniAppIdentityService {

    /** 微信小程序的绑定类型。 */
    private static final String TYPE = "wx_mini_app";
    /** 本模块的三方绑定持久层。 */
    private final SysUserThirdPartyMapper bindingMapper;
    /** 本地账号契约。 */
    private final SsoLoginBridge bridge;

    /**
     * 将服务端校验过的 openid 与手机号关联；拒绝改绑和重复身份。
     * @param openid 微信平台身份
     * @param phone 微信平台验证的手机号
     * @param request 注册请求
     * @return 已完成绑定的本地账号
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public SysUser resolve(String openid, String phone, HttpServletRequest request) {
        if (openid == null || openid.isBlank()) {
            throw new IllegalArgumentException("微信身份缺失");
        }
        var identities = bindingMapper.selectList(Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserThirdPartyType, TYPE)
                .eq(SysUserThirdParty::getSysUserThirdPartyUid, openid).last("FOR UPDATE"));
        if (identities.size() > 1) {
            throw new IllegalStateException("微信身份关联异常，请联系管理员");
        }
        SysUser user = bridge.resolveVerifiedPhone(phone, request);
        SysUserThirdParty identity = identities.isEmpty() ? null : identities.getFirst();
        if (identity != null && !Objects.equals(identity.getSysUserId(), user.getSysUserId())) {
            throw new IllegalStateException("微信身份与手机号所属账号不一致，请先处理绑定");
        }
        var userBindings = bindingMapper.selectList(Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserThirdPartyType, TYPE)
                .eq(SysUserThirdParty::getSysUserId, user.getSysUserId()).last("FOR UPDATE"));
        if (userBindings.stream().anyMatch(binding -> !Objects.equals(openid, binding.getSysUserThirdPartyUid()))) {
            throw new IllegalStateException("该账号已关联其他微信身份，请先处理绑定");
        }
        if (identity == null) {
            identity = new SysUserThirdParty();
            identity.setSysUserId(user.getSysUserId());
            identity.setSysUserThirdPartyType(TYPE);
            identity.setSysUserThirdPartyUid(openid);
            identity.setSysUserThirdPartyName("微信小程序");
            identity.setSysUserThirdPartyValue(phone);
            identity.setCreateTime(LocalDateTime.now());
            identity.setUpdateTime(LocalDateTime.now());
            if (bindingMapper.insert(identity) != 1) {
                throw new IllegalStateException("微信身份关联失败");
            }
        }
        return user;
    }
}
