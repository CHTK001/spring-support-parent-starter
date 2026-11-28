package com.chua.starter.sso.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.entity.system.SysWechatUser;
import com.chua.starter.sso.mapper.SysWechatUserMapper;
import com.chua.starter.sso.service.SysWechatUserService;
import org.springframework.stereotype.Service;

/**
 * 微信用户与系统用户关联服务实现。
 */
@Service
public class SysWechatUserServiceImpl extends ServiceImpl<SysWechatUserMapper, SysWechatUser>
        implements SysWechatUserService {

    @Override
    public SysWechatUser getByOpenId(String openId) {
        if (openId == null) {
            return null;
        }
        return getOne(Wrappers.<SysWechatUser>lambdaQuery()
                .eq(SysWechatUser::getSysWechatOpenId, openId));
    }

    @Override
    public void saveOrUpdateForUser(Integer sysUserId, String openId) {
        if (sysUserId == null || openId == null || openId.isEmpty()) {
            return;
        }
        SysWechatUser wechatUser = getByOpenId(openId);
        if (wechatUser == null) {
            SysWechatUser created = new SysWechatUser();
            created.setSysUserId(sysUserId);
            created.setSysWechatOpenId(openId);
            save(created);
            return;
        }
        if (!sysUserId.equals(wechatUser.getSysUserId())) {
            wechatUser.setSysUserId(sysUserId);
            updateById(wechatUser);
        }
    }
}
