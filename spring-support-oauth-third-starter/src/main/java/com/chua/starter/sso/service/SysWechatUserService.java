package com.chua.starter.sso.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.entity.system.SysWechatUser;

/**
 * 微信用户与系统用户关联服务。
 */
public interface SysWechatUserService extends IService<SysWechatUser> {

    /**
     * 按微信 OpenID 查询关联记录。
     *
     * @param openId 微信 OpenID
     * @return 关联记录或空
     */
    SysWechatUser getByOpenId(String openId);

    /**
     * 保存关联关系；已存在记录仅在系统用户 ID 变化时更新。
     *
     * @param sysUserId 系统用户 ID
     * @param openId 微信 OpenID
     */
    void saveOrUpdateForUser(Integer sysUserId, String openId);
}
