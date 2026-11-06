package com.chua.starter.account.support;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.oauth.client.support.execute.AuthSessionUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 账号与渠道模块的当前用户读取入口。
 *
 * <p>身份由 oauth 客户端写入：鉴权成功后把 {@link UserResume} 放进会话，
 * 并把用户标识写到请求属性 {@code userId}。本模块只做账号归属隔离需要的
 * 最小读取，不复制 AI 模块的用户上下文能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AccountUserContext {

    /**
     * 鉴权成功后写入的用户标识请求属性。
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private AccountUserContext() {
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
}
