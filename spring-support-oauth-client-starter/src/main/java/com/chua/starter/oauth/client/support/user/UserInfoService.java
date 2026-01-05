package com.chua.starter.oauth.client.support.user;


import com.chua.common.support.annotations.Spi;

/**
 * 用户信息服务接口
 * <p>提供用户认证和信息查询功能</p>
 * <p>使用 SPI 机制，通过 @Extension 注解标注实现类</p>
 *
 * <h2>架构关系图</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                      OAuth 认证架构                              │
 * └─────────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    LoginCheck (登录检测)                        │
 * │  ┌──────────────────────────────────────────────────────────┐  │
 * │  │  doLogin() - 登录验证                                     │  │
 * │  │  getUserInfo() - 获取用户信息                             │  │
 * │  └──────────────────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────────────────┘
 *                              │
 *                              │ SPI机制获取实现
 *                              ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │              UserInfoService (用户信息服务接口)                │
 * │  ┌──────────────────────────────────────────────────────────┐  │
 * │  │  checkLogin() - 检验用户凭证                              │  │
 * │  │  upgrade() - 升级/刷新用户信息                            │  │
 * │  └──────────────────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────────────────┘
 *                              │
 *                    ┌─────────┴─────────┐
 *                    │                    │
 *                    ▼                    ▼
 * ┌──────────────────────────┐  ┌──────────────────────────┐
 * │ AbstractUserLoginService │  │ TemporaryTokenLoginService│
 * │   (抽象基类)              │  │   (临时令牌登录)          │
 * └──────────────────────────┘  └──────────────────────────┘
 *           │                              │
 *           │                              │
 *     ┌─────┴─────┐                       │
 *     │           │                       │
 *     ▼           ▼                       │
 * ┌─────────┐ ┌──────────────┐          │
 * │UserLogin│ │TenantUser    │          │
 * │Service  │ │LoginService  │          │
 * │(标准登录)│ │(多租户登录)   │          │
 * └─────────┘ └──────────────┘          │
 *     │           │                      │
 *     └───────────┴──────────────────────┘
 *                    │
 *                    ▼
 *         ┌──────────────────────┐
 *         │   UserResume         │
 *         │   (用户信息结果)      │
 *         └──────────────────────┘
 * </pre>
 *
 * <h2>类关系图</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    UserInfoService                         │
 * │                    (接口)                                   │
 * │  + checkLogin(principal, credential, address, ext)         │
 * │  + upgrade(userResult)                                      │
 * └─────────────────────────────────────────────────────────────┘
 *                            ▲
 *                            │ implements
 *                            │
 *         ┌──────────────────┴──────────────────┐
 *         │                                      │
 *         │                                      │
 * ┌───────────────────────┐      ┌──────────────────────────┐
 * │AbstractUserLoginService│      │TemporaryTokenLoginService│
 * │   (抽象基类)            │      │   (临时令牌登录)          │
 * │                       │      │                          │
 * │ - sysUserMapper       │      │ + checkLogin()           │
 * │ - sysDeptMapper       │      │ + upgrade()              │
 * │ - stringRedisTemplate │      └──────────────────────────┘
 * │                       │
 * │ + loginInfo()         │
 * │ + getRole()           │
 * │ + getPermission()     │
 * │ + checkLogin()        │
 * │ + upgrade()           │
 * └───────────────────────┘
 *         ▲
 *         │ extends
 *         │
 * ┌───────┴────────┐
 * │                │
 * ▼                ▼
 * ┌─────────────┐ ┌──────────────────┐
 * │UserLogin    │ │TenantUserLogin   │
 * │Service      │ │Service            │
 * │(标准登录)    │ │(多租户登录)        │
 * │             │ │                  │
 * │+generateUser│ │+generateUserIds()│
 * │Ids()        │ │                  │
 * └─────────────┘ └──────────────────┘
 * </pre>
 *
 * <h2>调用流程</h2>
 * <pre>
 * 1. 客户端请求认证
 *    │
 *    ▼
 * 2. AuthorizationHandler (认证处理器)
 *    │  - AppKeyAuthorizationHandler
 *    │  - TokenAuthorizationHandler
 *    │  - LoginAuthorizationHandler
 *    │
 *    ▼
 * 3. LoginCheck.doLogin()
 *    │  - 检查账户锁定状态
 *    │  - 通过SPI获取UserInfoService实现
 *    │
 *    ▼
 * 4. UserInfoService.checkLogin()
 *    │  - AbstractUserLoginService.checkLogin()
 *    │    - 验证用户凭证
 *    │    - 获取用户角色和权限
 *    │    - 构建UserResume对象
 *    │
 *    ▼
 * 5. TokenResolver.createToken()
 *    │  - 生成访问令牌
 *    │  - 存储到Redis
 *    │
 *    ▼
 * 6. 返回LoginResult
 *    │  - 包含Token和UserResume
 * </pre>
 *
 * <h2>SPI实现类</h2>
 * <pre>
 * @Spi("WEB") - AbstractUserLoginService及其子类
 *   ├─ UserLoginService (@Spi("WEB"))
 *   ├─ TenantUserLoginService (@Spi("WEB"))
 *   ├─ GiteeLoginService (@Spi("GITEE"))
 *   ├─ GithubLoginService (@Spi("GITHUB"))
 *   ├─ WechatEnterpriseLoginService (@Spi("WECHAT_ENTERPRISE"))
 *   ├─ WeixinMiniLoginService (@Spi("WECHAT_MINI"))
 *   └─ DingtalkLoginService (@Spi("DINGTALK"))
 *
 * @Spi("TEMP") - TemporaryTokenLoginService
 * </pre>
 *
 * @author CH
 * @since 1.0.0
 */
@Spi("WEB")
public interface UserInfoService {
    /**
     * 检验用户凭证是否合法
     * <p>根据不同的登录类型，凭证参数可能代表不同的含义：</p>
     * <ul>
     *   <li>用户名密码登录: principal=用户名, credential=密码</li>
     *   <li>令牌登录: principal=令牌, credential=null</li>
     *   <li>第三方登录: principal=第三方ID, credential=第三方访问令牌</li>
     * </ul>
     *
     * @param principal  用户标识（用户名/手机号/邮箱/令牌等）
     * @param credential 用户凭证（密码/令牌/证书等）
     * @param address    客户端地址
     * @param ext        扩展参数，可包含额外的登录信息
     * @return 用户结果，若认证失败则message字段包含错误信息
     */
    UserResume checkLogin(String principal, String credential, String address, Object ext);

    /**
     * 升级/刷新用户信息
     * <p>根据已有的用户信息获取最新的完整用户数据</p>
     *
     * @param userResult 已有的用户信息
     * @return 更新后的用户信息
     */
    UserResume upgrade(UserResult userResult);
}
