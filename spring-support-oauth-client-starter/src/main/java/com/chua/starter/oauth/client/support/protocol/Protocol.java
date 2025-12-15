package com.chua.starter.oauth.client.support.protocol;

import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.Cookie;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 认证协议接口
 * <p>定义了与 OAuth 认证相关的核心操作，包括：</p>
 * <ul>
 *     <li>令牌验证与刷新</li>
 *     <li>用户登录与登出</li>
 *     <li>临时令牌管理</li>
 *     <li>在线状态查询</li>
 * </ul>
 *
 * @author CH
 * @since 2024/12/11
 * @version 4.0.0.34
 */
public interface Protocol {

    /**
     * 验证认证信息
     * <p>根据 Cookie 或 Token 验证用户身份</p>
     *
     * @param cookie      请求中的 Cookie 数组
     * @param token       认证令牌
     * @param subProtocol 子协议名称
     * @return 认证信息对象
     */
    AuthenticationInformation approve(Cookie[] cookie, String token, String subProtocol);

    /**
     * 应用密钥认证
     * <p>根据 AppKey 和 AppSecret 进行应用级别的身份验证</p>
     *
     * @param appKeySecret 应用密钥对
     * @return 认证信息对象
     */
    AuthenticationInformation authentication(AppKeySecret appKeySecret);

    /**
     * 升级/刷新令牌
     * <p>使用刷新令牌获取新的访问令牌</p>
     *
     * @param cookie       请求中的 Cookie 数组
     * @param token        当前访问令牌
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken);

    /**
     * 获取访问令牌
     * <p>根据用户凭据进行登录认证，获取访问令牌</p>
     *
     * @param username 用户名
     * @param password 密码
     * @param authType 认证类型
     * @param ext      扩展参数
     * @return 登录认证结果
     */
    LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext);

    /**
     * 用户登出
     * <p>终止用户会话，清除认证信息</p>
     *
     * @param uid        用户唯一标识
     * @param logoutType 登出类型
     * @param userResult 用户信息
     * @return 登出处理结果
     */
    LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult);

    /**
     * 创建临时令牌
     * <p>基于源令牌创建一个短期有效的临时令牌，适用于临时授权场景</p>
     *
     * @param sourceToken 源令牌
     * @param ext         扩展参数（如 expireTime、allowedUrls 等）
     * @return 临时令牌结果
     */
    default LoginAuthResult createTemporaryToken(String sourceToken, Map<String, Object> ext) {
        return new LoginAuthResult(501, "当前协议不支持临时令牌功能");
    }

    /**
     * 查询用户在线状态
     * <p>获取指定用户的在线状态信息，用于单点登录或多端控制</p>
     *
     * @param uid 用户唯一标识
     * @return 在线状态信息
     */
    default OnlineStatus getOnlineStatus(String uid) {
        return OnlineStatus.defaultStatus();
    }

    /**
     * 获取在线用户列表
     * <p>获取所有在线用户信息，支持分页和筛选</p>
     *
     * @param query 查询参数
     * @return 在线用户列表结果
     */
    default OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        return OnlineUserResult.empty();
    }

    /**
     * 在线用户查询参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OnlineUserQuery {
        /**
         * 用户名（模糊匹配）
         */
        private String username;
        /**
         * IP地址（模糊匹配）
         */
        private String ip;
        /**
         * 页码（从1开始）
         */
        private int page = 1;
        /**
         * 每页大小
         */
        private int size = 20;
    }

    /**
     * 在线用户列表返回结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OnlineUserResult {
        /**
         * 在线用户列表
         */
        private java.util.List<OnlineUserInfo> users;
        /**
         * 总数量
         */
        private int total;
        /**
         * 当前页码
         */
        private int page;
        /**
         * 每页大小
         */
        private int size;

        public static OnlineUserResult empty() {
            return new OnlineUserResult(java.util.Collections.emptyList(), 0, 1, 20);
        }
    }

    /**
     * 在线用户信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OnlineUserInfo {
        /**
         * 用户ID
         */
        private String userId;
        /**
         * 用户名
         */
        private String username;
        /**
         * 昵称
         */
        private String nickname;
        /**
         * 登录IP
         */
        private String loginIp;
        /**
         * 登录地址
         */
        private String loginAddress;
        /**
         * 浏览器
         */
        private String browser;
        /**
         * 操作系统
         */
        private String os;
        /**
         * 登录时间（时间戳）
         */
        private Long loginTime;
        /**
         * Token
         */
        private String token;
        /**
         * 登录类型
         */
        private String loginType;
    }

    /**
     * 用户在线状态信息
     * <p>包含当前在线数、最大允许在线数、在线模式等信息</p>
     *
     * @author CH
     * @since 2024/12/11
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OnlineStatus {

        /**
         * 不限制在线数量的标识值
         */
        private static final int UNLIMITED = -1;

        /**
         * 当前在线数量
         */
        private int onlineCount = 0;

        /**
         * 最大允许在线数量
         * <p>-1 表示不限制</p>
         */
        private int maxOnlineCount = UNLIMITED;

        /**
         * 在线模式
         * <ul>
         *     <li>SINGLE - 单点登录，同时只允许一个终端</li>
         *     <li>MULTIPLE - 多端登录，不限制终端数量</li>
         *     <li>LIMIT - 限制模式，最多允许指定数量的终端</li>
         * </ul>
         */
        private String onlineMode = "MULTIPLE";

        /**
         * 创建默认状态
         *
         * @return 默认在线状态
         */
        public static OnlineStatus defaultStatus() {
            return new OnlineStatus();
        }

        /**
         * 判断是否已达到在线上限
         *
         * @return true-已达上限，false-未达上限
         */
        public boolean isReachedLimit() {
            return maxOnlineCount > 0 && onlineCount >= maxOnlineCount;
        }

        /**
         * 获取剩余可登录数量
         *
         * @return 剩余可登录数量，-1 表示不限制
         */
        public int getRemainingSlots() {
            if (maxOnlineCount == UNLIMITED) {
                return UNLIMITED;
            }
            return Math.max(0, maxOnlineCount - onlineCount);
        }

        /**
         * 判断是否为单点登录模式
         *
         * @return true-单点登录模式
         */
        public boolean isSingleMode() {
            return "SINGLE".equalsIgnoreCase(onlineMode);
        }

        /**
         * 判断是否为多端登录模式
         *
         * @return true-多端登录模式
         */
        public boolean isMultipleMode() {
            return "MULTIPLE".equalsIgnoreCase(onlineMode);
        }

        /**
         * 判断是否为限制模式
         *
         * @return true-限制模式
         */
        public boolean isLimitMode() {
            return "LIMIT".equalsIgnoreCase(onlineMode);
        }
    }
}
