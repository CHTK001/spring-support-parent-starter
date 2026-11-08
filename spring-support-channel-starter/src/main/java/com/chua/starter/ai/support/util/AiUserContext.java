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
     * WebSocket 握手阶段写入的完整用户信息（UserResume），WS 工作线程据此显式建立身份
     */
    public static final String RESUME_ATTRIBUTE = "userResume";

    /**
     * 异步线程身份快照。
     *
     * <p>只在 {@link #capturing(Runnable)} 包装的任务执行期间存在： holder 在场即代表身份已在提交线程
     * 解析完毕（{@code resume} 为空表示提交线程本就匿名），工作线程不再回读请求对象。</p>
     */
    private static final ThreadLocal<AsyncIdentity> ASYNC_IDENTITY = new ThreadLocal<>();

    /**
     * 只读角色：与权限拦截器保持一致
     */
    private static final String READ_ONLY_ROLE = "READ_ONLY";

    private AiUserContext() {
    }

    /**
     * 提交线程解析出的身份快照。
     *
     * @param resume 身份，{@code null} 表示提交线程未登录
     */
    private record AsyncIdentity(UserResume resume) {
    }

    /**
     * 当前用户信息。
     *
     * @return 已登录返回用户信息，未登录或机器流量（AppKey）返回 {@code null}
     */
    public static UserResume currentUser() {
        AsyncIdentity snapshot = ASYNC_IDENTITY.get();
        if (snapshot != null) {
            return snapshot.resume();
        }
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
     * 业务数据是否允许跨账号访问。
     *
     * <p><b>恒为 {@code false}</b>，这是刻意的：需求明确「管理员除了设置中心的几个配置，
     * 其它的和普通用户一样，所有数据和业务都是账号隔离的」。此前各业务链路用
     * {@code AiUserContext.isAdmin()} 直接放行管理员读他人会话/记忆/底库/定时任务，
     * 等于管理员在业务侧有隐式特权，与该要求相反。</p>
     *
     * <p>为什么不直接把那些判断删掉、也不改 {@link #isAdmin()}：</p>
     * <ul>
     *   <li>{@link #isAdmin()} 同时服务「设置中心的管理员专属字段」等**正当**特权，
     *       改它会把设置中心一起废掉；</li>
     *   <li>保留本方法（而不是散落各处的裸判断），是为了让「这条数据链路允不允许跨账号」
     *       这一口径<b>集中可查、可改</b>：将来若真要给管理员开某个跨账号视图，
     *       只需在这里或按业务细分成几个具名方法，而不是逐个调用点考古。</li>
     * </ul>
     *
     * @return 恒为 {@code false}，即管理员在业务数据侧与普通账号完全同权
     */
    public static boolean canAccessOthersData() {
        return false;
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
     * 包装后的任务在执行期间借用提交线程的身份快照，执行完再还原工作线程原有状态，不会把身份残留给下一个任务。</p>
     *
     * <p>身份必须在提交线程内解析好：请求一旦结束，其 {@code RequestFacade} 就被 Tomcat 回收，
     * 届时再走会话读取会抛「The request object has been recycled」，整段落库回调连带失败。</p>
     *
     * @param task 异步任务，由调用方保证非空
     * @return 携带提交线程身份快照的包装任务
     */
    public static Runnable capturing(Runnable task) {
        AsyncIdentity identity = new AsyncIdentity(currentUser());
        RequestAttributes captured = RequestContextHolder.getRequestAttributes();
        // 捕获提交线程（请求线程）的上下文类加载器：ForkJoinPool 工作线程的 contextClassLoader
        // 在 fat jar 下可能无法枚举嵌套 jar 中的 ServiceLoader 注册（如 McpJsonMapperSupplier）
        ClassLoader capturedClassLoader = Thread.currentThread().getContextClassLoader();
        return () -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes();
            AsyncIdentity previousIdentity = ASYNC_IDENTITY.get();
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            if (captured != null) {
                RequestContextHolder.setRequestAttributes(captured);
            }
            if (capturedClassLoader != null) {
                Thread.currentThread().setContextClassLoader(capturedClassLoader);
            }
            ASYNC_IDENTITY.set(identity);
            try {
                task.run();
            } finally {
                if (previousIdentity != null) {
                    ASYNC_IDENTITY.set(previousIdentity);
                } else {
                    ASYNC_IDENTITY.remove();
                }
                if (previous != null) {
                    RequestContextHolder.setRequestAttributes(previous);
                } else {
                    RequestContextHolder.resetRequestAttributes();
                }
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        };
    }

    /**
     * 以显式给定的用户身份执行任务（WebSocket 等无请求上下文的通道使用）。
     *
     * <p>与 {@link #capturing(Runnable)} 的区别：身份不依赖调用线程的请求上下文，直接用握手阶段
     * 解析好的 {@link UserResume} 建立；执行期间不设置 RequestAttributes（WS 没有 HTTP 请求）。
     * 后续再经 {@link #capturing(Runnable)} 提交异步任务时，会从当前线程捕获到该身份并继续传递。</p>
     *
     * <p><b>可嵌套</b>：执行完还原线程原有身份（而不是一律清空），因此在已有身份的线程里
     * 再按另一个身份执行一段（如「重建某个账号的实例」）不会把外层身份弄丢。</p>
     *
     * @param resume 显式身份，不允许为 null
     * @param task   任务，不允许为 null
     */
    public static void runWith(UserResume resume, Runnable task) {
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        AsyncIdentity previousIdentity = ASYNC_IDENTITY.get();
        ASYNC_IDENTITY.set(new AsyncIdentity(resume));
        try {
            task.run();
        } finally {
            if (previousIdentity != null) {
                ASYNC_IDENTITY.set(previousIdentity);
            } else {
                ASYNC_IDENTITY.remove();
            }
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }
}
