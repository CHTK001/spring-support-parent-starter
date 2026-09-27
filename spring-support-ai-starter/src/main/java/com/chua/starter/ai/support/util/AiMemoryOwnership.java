package com.chua.starter.ai.support.util;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内内存态记录的归属工具。
 *
 * <p>AI 侧仍有一批控制器把业务数据放在进程内 {@code Map<String, Map<String, Object>>} 里
 * （预设、预设标签、机器人、Webhook、客服、IDMM 状态等）。这类记录没有数据库行可以挂
 * {@code user_id} 列，因此归属以记录内的 {@code user_id} 字段为准：创建时由服务端按当前登录用户
 * 写入，客户端传入的值一律覆盖。</p>
 *
 * <p>口径与落库表保持一致：本人可读写；管理员可读他人记录但不可修改或删除；他人一律 403。
 * 没有 {@code user_id} 的历史记录（改造前遗留）视为无主，任何登录用户都不能改写，避免被随意接管。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AiMemoryOwnership {

    /**
     * 归属字段名（与落库表的用户列同名口径）
     */
    public static final String USER_ID_FIELD = "user_id";

    /**
     * 公开可见字段名：为 true 时记录对所有登录用户可读（仍仅 owner/管理员可写）。
     */
    public static final String PUBLIC_FIELD = "is_public";

    private AiMemoryOwnership() {
    }

    /**
     * 判断记录是否被标记为公开可见。
     *
     * @param record 内存记录
     * @return true 表示对所有用户可读
     */
    public static boolean isPublic(Map<String, Object> record) {
        Object v = record == null ? null : record.get(PUBLIC_FIELD);
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /**
     * 新建一条已盖章归属的内存记录。
     *
     * @return 带当前登录用户归属的空记录
     */
    public static Map<String, Object> newOwnedRecord() {
        Map<String, Object> record = new LinkedHashMap<>();
        stampOwner(record);
        return record;
    }

    /**
     * 写入归属：按当前登录用户盖章，客户端传入的用户标识一律覆盖。
     *
     * @param record 待落内存的记录
     */
    public static void stampOwner(Map<String, Object> record) {
        record.put(USER_ID_FIELD, AiUserContext.requireUserId());
    }

    /**
     * 校验记录归属。
     *
     * @param record   内存记录
     * @param writable 是否要求写权限（{@code false} 表示只读校验，管理员可读他人记录）
     * @param what     业务对象名称，用于错误文案
     * @return 通过校验的记录本身
     */
    public static Map<String, Object> requireOwned(Map<String, Object> record, boolean writable, String what) {
        if (record == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, what + "不存在");
        }
        Object owner = record.get(USER_ID_FIELD);
        String userId = AiUserContext.requireUserId();
        if (owner != null && userId.equals(String.valueOf(owner))) {
            return record;
        }
        if (!writable && AiUserContext.isAdmin()) {
            return record;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN,
                "无权" + (writable ? "修改或删除他人的" : "访问他人的") + what);
    }

    /**
     * 取回记录并校验归属。
     *
     * @param records  内存态容器
     * @param id       记录标识
     * @param writable 是否要求写权限
     * @param what     业务对象名称，用于错误文案
     * @return 通过校验的记录本身
     */
    public static Map<String, Object> requireOwned(Map<String, ? extends Map<String, Object>> records,
                                                   String id, boolean writable, String what) {
        return requireOwned(records.get(id), writable, what);
    }

    /**
     * 只读校验：本人、管理员，或记录被标记为公开可见时放行；公开记录对非 owner 只读。
     *
     * @param record 内存记录
     * @param what   业务对象名称，用于错误文案
     * @return 通过校验的记录本身
     */
    public static Map<String, Object> requireReadable(Map<String, Object> record, String what) {
        if (record == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, what + "不存在");
        }
        Object owner = record.get(USER_ID_FIELD);
        String userId = AiUserContext.requireUserId();
        if (owner != null && userId.equals(String.valueOf(owner))) {
            return record;
        }
        if (AiUserContext.isAdmin() || isPublic(record)) {
            return record;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人的" + what);
    }

    /**
     * 按归属过滤列表：管理员可见全部，其余仅本人记录（不包含他人公开记录）。
     *
     * @param records 全量内存记录
     * @return 当前用户可见的记录
     */
    public static List<Map<String, Object>> visibleToCurrentUser(Collection<Map<String, Object>> records) {
        return visibleToCurrentUser(records, false);
    }

    /**
     * 按归属过滤列表：管理员可见全部；其余可见本人记录，且在 {@code includePublic=true}
     * 时额外包含他人标记为公开可见的记录。
     *
     * @param records       全量内存记录
     * @param includePublic 是否纳入他人公开记录
     * @return 当前用户可见的记录
     */
    public static List<Map<String, Object>> visibleToCurrentUser(Collection<Map<String, Object>> records,
                                                                 boolean includePublic) {
        String userId = AiUserContext.requireUserId();
        boolean admin = AiUserContext.isAdmin();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> record : records) {
            Object owner = record.get(USER_ID_FIELD);
            if (admin || (owner != null && userId.equals(String.valueOf(owner)))
                    || (includePublic && isPublic(record))) {
                result.add(record);
            }
        }
        return result;
    }
}
