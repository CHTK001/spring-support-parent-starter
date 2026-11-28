package com.chua.starter.sso.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.entity.system.SysUserThirdParty;

import java.util.List;

/**
 * 用户第三方账号绑定服务
 *
 * <p>提供 Gitee、微信小程序、GitHub 等第三方平台账号与本地用户绑定的管理能力。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysUserThirdPartyService extends IService<SysUserThirdParty> {

    /**
     * 查询用户绑定的所有第三方账号。
     *
     * @param userId 用户主键
     * @return 第三方账号列表
     */
    List<SysUserThirdParty> listByUserId(Integer userId);

    /**
     * 按类型查询用户的第三方账号。
     *
     * @param userId 用户主键
     * @param type   平台类型 (gitee/wechat-mp/GitHub/...)
     * @return 该类型下的绑定列表
     */
    List<SysUserThirdParty> listByUserIdAndType(Integer userId, String type);

    /**
     * 根据第三方 uid + 类型查找绑定记录。
     *
     * @param type 平台类型
     * @param uid  第三方 uid
     * @return SysUserThirdParty 或 空
     */
    SysUserThirdParty getByThirdPartyUid(String type, String uid);

    /**
     * 绑定第三方账号到本地用户(已存在则更新名称)。
     *
     * @param record 绑定记录
     * @return 保存后的对象
     */
    SysUserThirdParty bind(SysUserThirdParty record);

    /**
     * 解绑指定主键。
     *
     * @param id 主键
     * @return 是否成功
     */
    boolean unbind(Integer id);

    /**
     * 解绑用户所有第三方账号。
     *
     * @param userId 用户主键
     * @return 解绑条数
     */
    int unbindAll(Integer userId);

    /**
     * 更新 令牌 / 值 字段(用于刷新第三方 access_令牌)。
     *
     * @param id       主键
     * @param newValue 新 令牌 或 值
     * @return 是否成功
     */
    boolean updateValue(Integer id, String newValue);
}
