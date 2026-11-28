package com.chua.starter.sso.service;

import com.chua.starter.entity.system.SysUser;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.sso.pojo.UserInfoVO;
import com.chua.starter.sso.support.ThirdPartyPendingBindStore;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SSO 登录桥接 SPI
 *
 * <p>把「本地账号 / 令牌 / 用户信息」这些与宿主强绑定的能力抽象成桥：
 * sso 模块只做第三方登录编排（授权码、绑定、解绑、换票、令牌签发），
 * 宿主（如 spring-api-support-common-system-starter）实现本接口，
 * 把密码校验、本地令牌签发、用户信息组装等接进来。</p>
 *
 * <p>依赖方向：system → sso（宿主实现 sso 的桥）。sso 不反向依赖宿主业务。</p>
 *
 * @since 2026/11/24
 * @author CH
 */
public interface SsoLoginBridge {

    /**
     * 按用户名查本地账号
     *
     * @param username 用户名
     * @return 用户；不存在返回 {@code null}
     */
    SysUser findByUsername(String username);

    /**
     * 按主键查本地账号
     *
     * @param userId 用户 ID
     * @return 用户；不存在返回 {@code null}
     */
    SysUser getById(Integer userId);

    /**
     * 校验本地账号口令（与密码登录同口径：账号状态检查 + 口令匹配）
     *
     * <p>账号被禁用或口令不匹配均返回 {@code false}，
     * 调用方统一提示「账号或密码错误」。</p>
     *
     * @param user        本地用户
     * @param rawPassword 明文口令
     * @return 校验通过返回 {@code true}
     */
    boolean verifyPassword(SysUser user, String rawPassword);

    /**
     * 更新本地账号（三方绑定后回填头像 / 邮箱等空位字段）
     *
     * @param user 本地用户
     */
    void updateUser(SysUser user);

    /**
     * 签发本地登录令牌（与密码登录同源）
     *
     * @param user 本地用户
     * @return 登录认证结果
     */
    LoginAuthResult issueLocalToken(SysUser user);

    /**
     * 组装登录用户信息
     *
     * @param userResume 认证用户快照
     * @return 用户信息
     */
    UserInfoVO buildUserInfo(UserResume userResume);

    /**
     * 按令牌读取登录用户信息
     *
     * @param token 访问令牌
     * @return 用户信息
     */
    UserInfoVO getUserLoginInfo(String token);

    /**
     * 回调链路按用户 ID 查账号（原 SysLoginCallbackService#getById 语义）
     *
     * @param userId 用户 ID（字符串形态，回调参数原样透传）
     * @return 用户；不存在返回 {@code null}
     */
    SysUser getCallbackUser(String userId);

    /**
     * 回调链路更新账号（原 SysLoginCallbackService#updateById 语义）
     *
     * @param user 本地用户
     */
    void updateCallbackUser(SysUser user);

    /**
     * 三方登录自动注册本地账号（仅开启自动注册的渠道回调时调用）
     *
     * <p>宿主应在同一事务内完成两件事：
     * ① 创建本地账号：用户名直接取三方唯一标识（GitHub 内部数字 id，不加前缀），
     * 口令置随机不可用值（该账号只能经三方登录，无法用密码登录）；
     * ② 落第三方绑定关系（{@code sys_user_third_party}），保证「账号存在 ⇒ 绑定存在」。
     * 用户名与既有本地账号撞名时追加短后缀并记日志，不中断登录。</p>
     *
     * @param pending 三方身份快照
     * @param request HTTP 请求（取注册 IP 等环境信息）
     * @return 创建成功的本地用户；注册失败返回 {@code null}（调用方回落到待绑定流程）
     */
    SysUser registerThirdPartyUser(ThirdPartyPendingBindStore.PendingBind pending, HttpServletRequest request);
}
