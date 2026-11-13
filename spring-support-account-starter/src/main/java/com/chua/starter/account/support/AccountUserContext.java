package com.chua.starter.account.support;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 账号与渠道模块的当前用户读取入口。
 *
 * <p><b>为什么不用编译期依赖 oauth</b>：账号管理是最底层模块（只应在 common 之上），
 * server / 设备 / 模板都要能复用它，不应该被迫拖入 oauth-client。这里按「请求属性
 * {@code userId} → oauth 会话」的顺序读取：oauth 客户端鉴权成功后会同时把用户标识
 * 写进请求属性；没有 oauth 的宿主至少也能按请求属性工作。oauth 会话通过反射读取，
 * 存在才用，不存在自动跳过。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AccountUserContext {

    /**
     * 鉴权成功后写入的用户标识请求属性。
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    /**
     * oauth 会话工具类（可选依赖，按类名反射）
     */
    private static final String AUTH_SESSION_UTILS =
            "com.chua.starter.oauth.client.support.execute.AuthSessionUtils";

    /**
     * oauth 用户信息类（可选依赖，按类名反射）
     */
    private static final String USER_RESUME =
            "com.chua.starter.oauth.client.support.user.UserResume";

    private AccountUserContext() {
    }

    /**
     * 当前用户标识。
     *
     * @return 用户标识，未登录返回 {@code null}
     */
    public static String currentUserId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(USER_ID_ATTRIBUTE);
            String userId = attribute == null ? null : StringUtils.trimToNull(attribute.toString());
            if (userId != null) {
                return userId;
            }
        }
        return sessionUserId();
    }

    /**
     * 要求已登录。
     *
     * @return 当前用户标识
     * @throws ApiException 无用户身份时抛出
     */
    public static String requireUserId() {
        String userId = currentUserId();
        if (userId == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    /**
     * 当前请求。
     *
     * @return 请求；非 Web 线程返回 {@code null}
     */
    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * 从 oauth 会话读取用户标识（可选依赖）。
     *
     * <p>反射失败一律返回 {@code null}：账号模块不能因为宿主没装 oauth 就启动失败。</p>
     *
     * @return 用户标识；无 oauth 或未登录返回 {@code null}
     */
    private static String sessionUserId() {
        try {
            Class<?> resumeType = Class.forName(USER_RESUME);
            Class<?> utilsType = Class.forName(AUTH_SESSION_UTILS);
            Method getUserInfo = utilsType.getMethod("getUserInfo", Class.class);
            Object resume = getUserInfo.invoke(null, resumeType);
            if (resume == null) {
                return null;
            }
            Object userId = resumeType.getMethod("getUserId").invoke(resume);
            return userId == null ? null : StringUtils.trimToNull(userId.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
