package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.google.common.base.Strings;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_ASTERISK;

/**
 * 用户认证信息基类
 * <p>
 * 存储用户认证后的完整信息，包含用户基本信息、权限角色、在线控制配置等。
 * 作为认证系统的核心数据载体，在登录、鉴权、刷新token等场景中使用。
 * </p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>存储用户身份标识（uid、userId、userCode）</li>
 *   <li>存储用户权限和角色信息</li>
 *   <li>支持多租户（tenantId）</li>
 *   <li>支持第三方登录（openId、unionId）</li>
 *   <li>支持个性化在线控制配置（优先级高于全局配置）</li>
 * </ul>
 *
 * <h3>配置优先级：</h3>
 * <p>用户级别配置 > 全局配置，包括：在线模式、最大在线数、Token过期时间等</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2022/7/23 8:48
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@Hidden
@Schema(description = "用户认证信息")
public class UserResume implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数（用于创建错误消息）
     *
     * @param message 错误消息
     */
    public UserResume(String message) {
        this.message = message;
    }

    // ==================== 身份标识 ====================

    /**
     * 用户唯一标识
     * <p>由用户名和登录类型生成的MD5值，用于在Redis中标识用户的所有Token</p>
     * <p>格式：MD5(username + loginType)，长度128位</p>
     */
    @Schema(description = "用户唯一标识（MD5）")
    private String uid;

    /**
     * 用户编码
     * <p>用于API认证的加密编码，包含uid和加密的userId</p>
     * <p>格式：{uid}:{加密的userId}</p>
     */
    @Schema(description = "用户编码（用于API认证）")
    private String userCode;

    /**
     * 用户ID
     * <p>数据库中的用户主键ID</p>
     */
    @Schema(description = "用户ID（数据库主键）")
    private String userId;

    /**
     * 用户名/账号
     */
    @Schema(description = "用户名/账号")
    private String username;

    // ==================== 第三方登录标识 ====================

    /**
     * UnionID
     * <p>同一用户在同一开放平台下的唯一标识</p>
     */
    @Schema(description = "UnionID")
    private String unionId;

    /**
     * OpenID
     * <p>用户在某个应用下的唯一标识</p>
     */
    @Schema(description = "OpenID")
    private String openId;

    /**
     * 应用ID
     * <p>第三方应用的唯一标识</p>
     */
    @Schema(description = "应用ID")
    private String appId;

    // ==================== 登录信息 ====================

    /**
     * 登录类型
     * <p>对应 AuthType 枚举值：WEB、TOKEN、WECHAT、TENANT</p>
     */
    @Schema(description = "登录类型", example = "WEB")
    private String loginType;

    /**
     * 登录次数
     */
    @Schema(description = "累计登录次数")
    private int loginCnt;

    /**
     * 客户端IP地址
     */
    @Schema(description = "客户端IP地址")
    private String address;

    /**
     * 最后一次登录IP
     */
    @Schema(description = "最后一次登录IP")
    private String lastIp;

    // ==================== 用户基本信息 ====================

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称")
    private String nickName;

    /**
     * 真实姓名
     */
    @Schema(description = "真实姓名")
    private String realName;

    /**
     * 姓名（显示名称）
     */
    @Schema(description = "姓名")
    private String name;

    /**
     * 手机号码
     */
    @Schema(description = "手机号码")
    private String phone;

    /**
     * 身份证号
     */
    @Schema(description = "身份证号")
    private String card;

    /**
     * 性别
     * <p>M-男，F-女，U-未知</p>
     */
    @Schema(description = "性别", example = "M")
    private String sex;

    // ==================== 组织架构 ====================

    /**
     * 租户ID
     * <p>多租户系统中的租户标识</p>
     */
    @Schema(description = "租户ID")
    private String tenantId;

    /**
     * 部门ID
     */
    @Schema(description = "部门ID")
    private String deptId;

    // ==================== 权限角色 ====================

    /**
     * 角色编码集合
     * <p>用户拥有的角色编码列表</p>
     */
    @Schema(description = "角色编码集合")
    private Set<String> roles;

    /**
     * 角色详情集合
     * <p>包含角色的完整信息（编码、名称、权限等）</p>
     */
    @Schema(description = "角色详情集合")
    private Set<RoleInfo> rolesByRole;

    /**
     * 权限编码集合
     * <p>用户拥有的权限编码列表</p>
     */
    @Schema(description = "权限编码集合")
    private Set<String> permission;

    /**
     * 数据权限类型
     * <p>控制用户可访问的数据范围</p>
     */
    @Schema(description = "数据权限类型")
    private DataFilterTypeEnum dataPermission;

    /**
     * 数据权限规则
     * <p>自定义的数据权限SQL或部门ID列表</p>
     */
    @Schema(description = "数据权限规则")
    private String dataPermissionRule;

    // ==================== Token配置 ====================

    /**
     * Token过期时间戳（秒）
     * <p>Token失效的绝对时间点</p>
     */
    @Schema(description = "Token过期时间（秒）")
    private Long expireTime;

    // ==================== 扩展信息 ====================

    /**
     * 扩展信息
     * <p>存储业务系统自定义的额外用户属性</p>
     */
    @Schema(description = "扩展信息")
    private Map<String, Object> ext;

    /**
     * 错误/提示消息
     * <p>认证失败时存储错误信息</p>
     */
    @Schema(description = "错误/提示消息")
    private String message;

    // ==================== 在线控制配置（用户级，优先级高于全局） ====================

    /**
     * 在线模式（用户级配置）
     * <p>优先级：用户配置 > 全局配置（plugin.oauth.server.online）</p>
     * <ul>
     *   <li><b>SINGLE</b> - 单设备在线，新登录踢掉所有旧设备</li>
     *   <li><b>MULTIPLE</b> - 多设备不限制数量</li>
     *   <li><b>LIMIT</b> - 限制最大在线数量，超出踢掉最早登录的设备</li>
     * </ul>
     * <p>为null时使用全局配置</p>
     */
    @Schema(description = "在线模式（SINGLE/MULTIPLE/LIMIT）")
    private String onlineMode;

    /**
     * 最大在线设备数量（用户级配置）
     * <p>仅当 onlineMode = LIMIT 时生效</p>
     * <p>优先级：用户配置 > 全局配置（plugin.oauth.server.max-online-count）</p>
     * <p>为null时使用全局配置，默认3</p>
     */
    @Schema(description = "最大在线设备数量")
    private Integer maxOnlineCount;

    /**
     * Token过期时间（秒）（用户级配置）
     * <p>优先级：用户配置 > 全局配置（plugin.oauth.server.expire）</p>
     * <p>为null时使用全局配置</p>
     * <p>可用于VIP用户设置更长的过期时间</p>
     */
    @Schema(description = "Token过期时间（秒）")
    private Long tokenExpire;

    /**
     * RefreshToken过期时间（秒）（用户级配置）
     * <p>优先级：用户配置 > 全局配置（plugin.oauth.server.refresh-expire）</p>
     * <p>为null时使用全局配置</p>
     */
    @Schema(description = "RefreshToken过期时间（秒）")
    private Long refreshTokenExpire;

    /**
     * 是否强制使用用户配置
     * <p>true时忽略全局配置，强制使用用户配置</p>
     */
    @Schema(description = "是否强制使用用户配置")
    private Boolean forceOnline;

    /**
     * 是否具备某个权限
     *
     * @param permission 权限
     * @return 是否具备某个权限
     */
    public boolean hasPermission(String permission) {
        if (Strings.isNullOrEmpty(permission)) {
            return false;
        }

        if (permission.contains(SYMBOL_ASTERISK)) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            for (String s : this.permission) {
                if (antPathMatcher.match(permission, s)) {
                    return true;
                }
            }
        }

        for (String s : this.permission) {
            if (Objects.equals(permission, s)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 是否具备某个角色
     *
     * @param roles 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String[] roles) {
        if (roles.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.roles)) {
            return false;
        }

        boolean hasRole = false;
        for (String role : roles) {
            hasRole |= hasRole(role);
        }

        return hasRole;
    }

    /**
     * 是否具备某个角色
     *
     * @param role 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String role) {
        if (Strings.isNullOrEmpty(role)) {
            return false;
        }

        if (role.contains(SYMBOL_ASTERISK)) {
            for (String s : roles) {
                if (PathMatcher.INSTANCE.match(role, s)) {
                    return true;
                }
            }
        }

        for (String s : roles) {
            if (Objects.equals(role, s)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPermission(String[] value) {
        if (value.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.permission)) {
            return false;
        }
        for (String s : value) {
            if (this.permission.contains(s)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAdmin() {
        return null != this.roles && this.roles.contains("ADMIN");
    }

    /**
     * 获取扩展信息
     *
     * @param name 名称
     * @return 扩展信息
     */
    public String getOption(String name) {
        return MapUtils.getString(ext, name);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public String getOption(String name, String defaultValue) {
        return MapUtils.getString(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name 名称
     * @return 扩展信息
     */
    public String getOptions(String... name) {
        return MapUtils.getString(ext, name);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Integer getOption(String name, Integer defaultValue) {
        return MapUtils.getInteger(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Long getOption(String name, Long defaultValue) {
        return MapUtils.getLong(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Double getOption(String name, Double defaultValue) {
        return MapUtils.getDouble(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Boolean getOption(String name, Boolean defaultValue) {
        return MapUtils.getBoolean(ext, name, defaultValue);
    }


    /**
     * 获取扩展信息
     *
     * @param name 名称
     * @return 扩展信息
     */
    public String getExtValue(String name) {
        return getOption(name);
    }

}
