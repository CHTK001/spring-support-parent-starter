package com.chua.starter.ai.support.util;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Set;

/**
 * AI 业务侧的当前用户读取入口。
 *
 * <p>身份由 oauth 客户端写入：{@code AuthFilter} 鉴权成功后把 {@link UserResume} 放进会话，
 * 并把用户标识写到请求属性 {@code userId}。业务代码统一从这里取，不要自行解析令牌，
 * 否则鉴权口径会分叉。</p>
 *
 * <p>依赖请求线程上下文：异步线程（SSE 推送、线程池任务）取不到身份，
 * 需要把任务交给 {@link #capturing(Runnable)} 包装后再提交，身份才会跟着落到异步线程。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AiUserContext {

    /**
     * AuthFilter 认证成功后写入的用户标识请求属性
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    /**
     * 只读角色：与权限拦截器保持一致
     */
    private static final String READ_ONLY_ROLE = "READ_ONLY";

    private AiUserContext() {
    }

    /**
     * 当前用户信息。
     *
     * @return 已登录返回用户信息，未登录或机器流量（AppKey）返回 {@code null}
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
     * @throws ApiException 无用户身份时抛出，由全局异常处理转 401
     */
    public static String requireUserId() {
        String userId = currentUserId();
        if (userId == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    /**
     * 是否管理员（超级管理员或管理员）。
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
     * 是否只读账号。
     *
     * @return 只读账号禁止任何写操作，优先于角色与权限
     */
    public static boolean isReadonly() {
        UserResume resume = currentUser();
        if (resume == null) {
            return false;
        }
        return resume.isReadonly() || resume.hasRole(READ_ONLY_ROLE);
    }

    /**
     * 携带当前身份提交异步任务。
     *
     * <p>请求上下文挂在请求线程上，SSE 回调和线程池任务里 {@link #currentUserId()} 只会取到空，
     * 用量记录与消息落库因此落成无主数据，归属校验也被当作机器流量跳过。
     * 包装后的任务在执行期间借用提交线程的上下文，执行完再还原工作线程原有状态，不会把身份残留给下一个任务。</p>
     *
     * @param task 异步任务，由调用方保证非空
     * @return 携带提交线程请求上下文的包装任务
     */
    public static Runnable capturing(Runnable task) {
        RequestAttributes captured = RequestContextHolder.getRequestAttributes();
        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes();
            if (captured != null) {
                RequestContextHolder.setRequestAttributes(captured);
            }
            try {
                task.run();
            } finally {
                if (previous != null) {
                    RequestContextHolder.setRequestAttributes(previous);
                } else {
                    RequestContextHolder.resetRequestAttributes();
                }
            }
        };
    }
}
