package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.spi.annotations.Spi;

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
*              ┌───────────────┼───────────────┐
*              ▼               ▼               ▼
* ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
* │   UserInfoService │ │   UserResult     │ │  LoginAuthResult │
* │   (SPI接口)       │ │  (用户认证结果)   │ │  (登录认证结果)   │
* └──────────────────┘ └──────────────────┘ └──────────────────┘
*                              │
*              ┌───────────────┼───────────────┐
*              ▼               ▼               ▼
* ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
* │   checkLogin()    │ │   getUsername()  │ │   getUserResume() │
* │   upgrade()       │ │   getUserId()    │ │   getToken()      │
* └──────────────────┘ └──────────────────┘ └──────────────────┘
* </pre>
*
* @since 1.0.0
* @author CH
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
    * @return 用户认证结果
     */
    UserResume checkLogin(String principal, String credential, String address, Object ext);

    /**
    * 用户升级
    * <p>将基础用户信息升级为完整的认证结果</p>
    *
    * @param userResult 基础用户信息
    * @return 升级后的用户认证结果
     */
    UserResume upgrade(UserResult userResult);
}
