package com.chua.starter.eduai.support.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 教育 AI 业务侧的当前用户读取与归属隔离入口。
 *
 * <p>身份由 oauth 客户端写入：{@code AuthFilter} 鉴权成功后把 {@link UserResume} 放进会话，
 * 并把用户标识写到请求属性 {@code userId}。业务代码统一从这里取，不要自行解析令牌，
 * 否则鉴权口径会分叉。</p>
 *
 * <p>依赖请求线程上下文：调度线程（edu-collect-dispatch）、队列监听等异步路径取不到身份，
 * 必须从父行（如采集任务）继承归属，而不是读上下文。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class EduUserContext {

    /**
     * AuthFilter 认证成功后写入的用户标识请求属性
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    /**
     * 无身份时使用的永假归属值（保证查询 fail-closed，不泄漏数据）
     */
    private static final String DENY_ALL_OWNER = "__anonymous__";

    private EduUserContext() {
    }

    /**
     * 当前用户信息。
     *
     * @return 已登录返回用户信息，未登录返回 {@code null}
     */
    public static UserResume currentUser() {
        UserResume resume = AuthSessionUtils.getUserInfo(UserResume.class);
        if (resume != null && StringUtils.isNotBlank(resume.getUserId())) {
            return resume;
        }
        HttpServletRequest request = AuthSessionUtils.getRequest();
        if (request == null) {
            return resume;
        }
        Object attribute = request.getAttribute(USER_ID_ATTRIBUTE);
        String userId = attribute == null ? null : attribute.toString();
        if (StringUtils.isBlank(userId)) {
            return resume;
        }
        if (resume == null) {
            resume = new UserResume();
        }
        resume.setUserId(userId);
        return resume;
    }

    /**
     * 当前用户标识。
     *
     * @return 用户标识，未登录返回 {@code null}
     */
    public static String currentUserId() {
        UserResume resume = currentUser();
        return resume == null ? null : StringUtils.trimToNull(resume.getUserId());
    }

    /**
     * 当前用户名。
     *
     * @return 用户名，未登录返回 {@code null}
     */
    public static String currentUsername() {
        UserResume resume = currentUser();
        return resume == null ? null : StringUtils.trimToNull(resume.getUsername());
    }

    /**
     * 要求已登录。
     *
     * @return 当前用户标识
     * @throws BusinessException 无用户身份时抛出
     */
    public static String requireUserId() {
        String userId = currentUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }

    /**
     * 是否管理员（超级管理员或管理员），管理员不受归属隔离限制。
     *
     * @return 是否管理员的结果
     */
    public static boolean isAdmin() {
        UserResume resume = currentUser();
        if (resume == null) {
            return false;
        }
        Set<String> roles = resume.getRoles();
        return AuthConstant.isSuperAdmin(roles) || AuthConstant.isAdmin(roles);
    }

    /**
     * 为查询条件追加归属隔离谓词：非管理员只见自己的数据（归属为空的行为系统预置数据，所有人可见）。
     *
     * @param wrapper     查询条件
     * @param ownerColumn 实体的归属字段 getter 引用
     * @param <T>         实体类型
     */
    public static <T> void scopeOwned(LambdaQueryWrapper<T> wrapper, SFunction<T, String> ownerColumn) {
        if (isAdmin()) {
            return;
        }
        String userId = currentUserId();
        if (userId == null) {
            wrapper.eq(ownerColumn, DENY_ALL_OWNER);
            return;
        }
        wrapper.and(w -> w.eq(ownerColumn, userId).or().isNull(ownerColumn));
    }

    /**
     * 读权限校验：管理员、本人或系统预置数据（归属为空）可读。
     *
     * @param owner 行归属用户标识
     * @throws BusinessException 无权访问时抛出
     */
    public static void checkReadable(String owner) {
        if (isAdmin() || StringUtils.isBlank(owner)) {
            return;
        }
        String userId = currentUserId();
        if (userId == null || !userId.equals(owner)) {
            throw new BusinessException("无权访问该数据");
        }
    }

    /**
     * 写权限校验：仅管理员或本人可修改/删除，系统预置数据对普通用户只读。
     *
     * @param owner 行归属用户标识
     * @throws BusinessException 无权操作时抛出
     */
    public static void checkWritable(String owner) {
        if (isAdmin()) {
            return;
        }
        if (StringUtils.isBlank(owner)) {
            throw new BusinessException("系统预置数据不允许修改");
        }
        String userId = currentUserId();
        if (userId == null || !userId.equals(owner)) {
            throw new BusinessException("无权操作该数据");
        }
    }
}
