package com.chua.starter.ai.support.util;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;

import java.util.Objects;

/**
 * 机器人 资源的调用方归属。
 *
 * <p>机器人 绑定以 {@code owner_key} 记录归属：登录用户为 {@code u:<userId>}，
 * 未登录以设备指纹归属 {@code fp:<指纹>}，两者皆无表示未归属（历史遗留）。
 * 该口径由 oauth 与设备指纹共同决定，登录态不一定经过鉴权过滤器
 * （机器人 管理接口在鉴权白名单内自行解析令牌），因此不能直接复用
 * {@link AiUserContext}，需要由调用方把已解析的归属传进来。</p>
 *
 * <p>读出口：管理员可跨归属查看，其余仅允许归属一致；
 * 写出口：只认归属本身，管理员对他人数据只读，只读账号一律拒绝。</p>
 *
 * @param ownerKey 归属标识（{@code u:<userId>} / {@code fp:<指纹>}，可为空）
 * @param admin    是否管理员或本地信任调用方
 * @author CH
 * @since 4.0.0.42
 */
public record AiBotCaller(String ownerKey, boolean admin) {

    /**
     * 登录归属前缀
     */
    public static final String USER_PREFIX = "u:";

    /**
     * 设备指纹归属前缀
     */
    public static final String FINGERPRINT_PREFIX = "fp:";

    /**
     * 以当前登录身份构造调用方。
     *
     * <p>用于鉴权过滤器已生效的接口：身份取请求属性，未登录时归属为空。</p>
     *
     * @return 登录归属调用方
     */
    public static AiBotCaller fromLogin() {
        String userId = AiUserContext.currentUserId();
        // admin 位取「业务数据能否跨账号」，而不是 isAdmin()：
        // 管理员在业务数据侧与普通账号同权（见 AiUserContext#canAccessOthersData）。
        return new AiBotCaller(userId == null ? null : USER_PREFIX + userId,
                AiUserContext.canAccessOthersData());
    }

    /**
     * 取登录归属中的用户标识。
     *
     * @param ownerKey 归属标识，可为空
     * @return 登录用户标识；设备指纹归属与未归属返回 {@code null}
     */
    public static String userOf(String ownerKey) {
        if (ownerKey == null) {
            return null;
        }
        if (!ownerKey.startsWith(USER_PREFIX)) {
            // 设备指纹归属不是登录用户，不能计入用户用量与用户数据
            return null;
        }
        String userId = ownerKey.substring(USER_PREFIX.length()).trim();
        return userId.isEmpty() ? null : userId;
    }

    /**
     * 当前调用方的登录用户标识。
     *
     * @return 登录用户标识；设备指纹归属与未归属返回 {@code null}
     */
    public String userId() {
        return userOf(ownerKey);
    }

    /**
     * 当前调用方是否可读取该归属的资源。
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @return 管理员或归属一致时为 {@code true}
     */
    public boolean canRead(String resourceOwnerKey) {
        return admin || ownerEquals(resourceOwnerKey);
    }

    /**
     * 校验当前调用方可否读取该归属的资源。
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @throws ApiException 越权时抛出，由全局异常处理转 403
     */
    public void requireReadable(String resourceOwnerKey) {
        if (canRead(resourceOwnerKey)) {
            return;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人机器人");
    }

    /**
     * 校验当前调用方可否修改该归属的资源。
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @throws ApiException 越权时抛出，由全局异常处理转 403
     */
    public void requireWritable(String resourceOwnerKey) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (ownerEquals(resourceOwnerKey)) {
            return;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
    }

    /**
     * 校验写权限，并额外允许管理员清理无主历史遗留记录。
     *
     * <p>与 {@link #requireWritable(String)} 的区别：当资源归属为空（{@code owner_key}
     * 为 null/空白，即无主的历史遗留数据）时，管理员可以删除——无主记录不属于任何用户，
     * 管理员清理不侵犯他人归属；明确归属他人的数据仍一律只读。</p>
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @throws ApiException 越权时抛出，由全局异常处理转 403
     */
    public void requireWritableOrAdminOrphan(String resourceOwnerKey) {
        requireWritableOrAdminPurge(resourceOwnerKey, null, null);
    }

    /**
     * 校验写权限，并额外允许管理员清理无主记录或未完成的无效绑定。
     *
     * <p>管理员在两种情况下可以删除，且都不侵犯他人的有效数据：</p>
     * <ul>
     *   <li><b>无主记录</b>：{@code owner_key} 为空，不属于任何用户；</li>
     *   <li><b>无效绑定</b>：{@code platform} 或 {@code botId} 为空，这类行从未构成可用连接器，
     *       只是失败流程残留的空壳（页面上表现为无名、默认插头图标、"默认模型"），
     *       清理它们不影响任何人的有效连接器。</li>
     * </ul>
     * <p>归属他人且字段完整的有效连接器，管理员仍只读；只读账号一律拒绝。</p>
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @param platform         绑定平台，可为空（空表示未完成的无效绑定）
     * @param botId            机器人唯一标识，可为空（空表示未完成的无效绑定）
     * @throws ApiException 越权时抛出，由全局异常处理转 403
     */
    public void requireWritableOrAdminPurge(String resourceOwnerKey, String platform, String botId) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (ownerEquals(resourceOwnerKey)) {
            return;
        }
        if (admin) {
            boolean orphan = normalize(resourceOwnerKey) == null;
            boolean incomplete = (platform == null || platform.isBlank())
                    || (botId == null || botId.isBlank());
            if (orphan || incomplete) {
                return;
            }
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
    }

    /**
     * 校验当前调用方可否新建一条记录（归属由服务端按当前身份写入）。
     *
     * <p>新建没有可被改写的他人资源，归属一致性无从比对，因此只拦只读账号；
     * 命中既有记录的改写仍走 {@link #requireWritable(String)}。</p>
     *
     * @throws ApiException 只读账号时抛出，由全局异常处理转 403
     */
    public void requireCreatable() {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
    }

    /**
     * 归属是否一致（空与空串视为同一档：未归属）。
     *
     * @param resourceOwnerKey 资源归属标识，可为空
     * @return 是否属于当前调用方
     */
    private boolean ownerEquals(String resourceOwnerKey) {
        return Objects.equals(normalize(this.ownerKey), normalize(resourceOwnerKey));
    }

    /**
     * 归一化归属标识。
     *
     * @param ownerKey 归属标识，可为空
     * @return  trimmed 后的归属标识，空串返回 {@code null}
     */
    private static String normalize(String ownerKey) {
        if (ownerKey == null) {
            return null;
        }
        String trimmed = ownerKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
