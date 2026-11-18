package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.event.ChannelCatalogChangedEvent;
import com.chua.starter.account.channel.mapper.SysAccountChannelConnectionMapper;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 渠道连接业务（账号模块唯一实现）。
 *
 * <p>连接是「某个账号在某条渠道下的一份凭据」，规则全部落在本类：</p>
 * <ul>
 *   <li><b>归属隔离</b>：同一渠道同一角色可以有多行，按 {@code user_id} 分家；
 *       普通用户只读写自己的行，管理员额外可见 {@code user_id IS NULL} 的系统池；</li>
 *   <li><b>新建必填</b>：新建连接必须带凭证，更新时未传凭证保留原值；</li>
 *   <li><b>删除范围</b>：只删自己名下的行，绝不连带别人的同角色凭证；</li>
 *   <li><b>续期语义</b>：凭据变化后广播 {@code connection} 事件，消费方复位健康状态。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountChannelConnectionService {

    /**
     * 渠道连接表
     */
    private final SysAccountChannelConnectionMapper connectionMapper;
    /**
     * 渠道业务（复用归属过滤与渠道定位）
     */
    private final AccountChannelService channelService;
    /**
     * 变更事件发布器
     */
    private final ApplicationEventPublisher events;

    /**
     * 归属作用域：免费/共享池（{@code _free}）。
     *
     * <p>免登录渠道取的是「不绑定任何账号」的公共凭据池。</p>
     */
    public static final String SCOPE_FREE = "_free";

    /**
     * 归属作用域：系统池（{@code _sys}），只看 {@code user_id IS NULL} 的行。
     */
    public static final String SCOPE_SYSTEM = "_sys";

    /**
     * 渠道下当前账号可见的连接（按创建时间升序）。
     *
     * @param providerId 渠道标识
     * @return 连接列表
     */
    public List<SysAccountChannelConnection> list(String providerId) {
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .orderByAsc(SysAccountChannelConnection::getCreatedAt);
        channelService.applyOwnerFilter(wrapper);
        return connectionMapper.selectList(wrapper);
    }

    /**
     * 渠道下全部连接（**不限归属**）。
     *
     * <p>只给后台任务用：签到、探活这类系统级作业需要覆盖所有账号的凭据，
     * 与「某个账号能看哪几行」是两件事。业务读路径一律用 {@link #list(String)}。</p>
     *
     * @param providerId 渠道标识
     * @return 连接列表
     */
    public List<SysAccountChannelConnection> listAll(String providerId) {
        return connectionMapper.selectList(new LambdaQueryWrapper<SysAccountChannelConnection>()
                .eq(SysAccountChannelConnection::getProviderId, providerId)
                .orderByAsc(SysAccountChannelConnection::getCreatedAt));
    }

    /**
     * 按归属作用域取渠道下的连接。
     *
     * <p>作用域口径（与路由侧 {@code ownerScope} 一致）：</p>
     * <ul>
     *   <li>{@link #SCOPE_FREE}：免登录共享池，<b>不限归属</b>，该渠道全部连接都可用；</li>
     *   <li>{@link #SCOPE_SYSTEM}：系统池，只看 {@code user_id IS NULL}；</li>
     *   <li>其它值（用户标识）：自己名下；管理员额外可见系统池。</li>
     * </ul>
     *
     * @param providerId 渠道标识
     * @param ownerScope 归属作用域
     * @return 连接列表
     */
    public List<SysAccountChannelConnection> listForScope(String providerId, String ownerScope) {
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId);
        if (!SCOPE_FREE.equals(ownerScope)) {
            applyPoolFilter(wrapper, ownerScope);
        }
        return connectionMapper.selectList(wrapper);
    }

    /**
     * 按归属作用域取渠道下的连接（系统级作用域只认系统池）。
     *
     * <p>与 {@link #listForScope(String, String)} 的差别只在 {@link #SCOPE_FREE}：
     * 这里 {@code _free} 与 {@code _sys} 都按系统池处理。聚合多密钥时用本口径，
     * 避免把某个账号的私有凭据当公共密钥分发给所有人。</p>
     *
     * @param providerId 渠道标识
     * @param ownerScope 归属作用域
     * @return 连接列表
     */
    public List<SysAccountChannelConnection> listPoolForScope(String providerId, String ownerScope) {
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId);
        applyPoolFilter(wrapper, ownerScope);
        return connectionMapper.selectList(wrapper);
    }

    /**
     * 取渠道下最新的一条连接（网关类渠道单实例，多连接取最新）。
     *
     * @param providerId 渠道标识
     * @return 连接；未绑定返回 {@code null}
     */
    public SysAccountChannelConnection findLatest(String providerId) {
        List<SysAccountChannelConnection> rows = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .orderByDesc(SysAccountChannelConnection::getRole)
                        .orderByDesc(SysAccountChannelConnection::getUpdatedAt)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 取该作用域下最新的一条连接。
     *
     * <p>系统级作用域（{@link #SCOPE_FREE} / {@link #SCOPE_SYSTEM}）先按历史
     * 「{@code user_id IS NULL} 系统池」找；渠道管理改造后管理员绑定的连接带 userId，
     * 共享网关不能被漏掉，故再退一步取该渠道任意一条连接。</p>
     *
     * @param providerId 渠道标识
     * @param ownerScope 归属作用域
     * @return 连接；未绑定返回 {@code null}
     */
    public SysAccountChannelConnection findLatestForScope(String providerId, String ownerScope) {
        if (SCOPE_FREE.equals(ownerScope) || SCOPE_SYSTEM.equals(ownerScope)) {
            SysAccountChannelConnection system = findLatestSystemPool(providerId);
            return system != null ? system : findLatest(providerId);
        }
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .orderByDesc(SysAccountChannelConnection::getRole)
                        .orderByDesc(SysAccountChannelConnection::getUpdatedAt)
                        .last("LIMIT 1");
        applyPoolFilter(wrapper, ownerScope);
        List<SysAccountChannelConnection> rows = connectionMapper.selectList(wrapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 取渠道下系统池里最新的一条连接。
     *
     * @param providerId 渠道标识
     * @return 连接；系统池为空返回 {@code null}
     */
    private SysAccountChannelConnection findLatestSystemPool(String providerId) {
        List<SysAccountChannelConnection> rows = connectionMapper.selectList(
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .isNull(SysAccountChannelConnection::getUserId)
                        .orderByDesc(SysAccountChannelConnection::getRole)
                        .orderByDesc(SysAccountChannelConnection::getUpdatedAt)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按连接标识取连接（不校验归属）。
     *
     * <p>用于后台任务：签到/探活/健康回填手里拿的是上一轮扫出来的 connection_id，
     * 与「当前请求属于哪个账号」无关。</p>
     *
     * @param connectionId 连接标识
     * @return 连接实体；不存在返回 {@code null}
     */
    public SysAccountChannelConnection find(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }
        return connectionMapper.selectById(connectionId);
    }

    /**
     * 按渠道 + 角色取连接；别人名下的连接按「不存在」处理。
     *
     * @param providerId 渠道标识
     * @param role       连接角色
     * @return 连接实体；不存在返回 {@code null}
     */
    public SysAccountChannelConnection find(String providerId, String role) {
        try {
            return require(providerId, role);
        } catch (ApiException e) {
            if (e.getErrorCode() == ApiErrorCode.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 整行保存连接（调用方已合并好字段）。
     *
     * @param conn 连接实体，{@code connectionId} 必填
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int update(SysAccountChannelConnection conn) {
        if (conn == null || conn.getConnectionId() == null) {
            return 0;
        }
        conn.setUpdatedAt(System.currentTimeMillis());
        int updated = connectionMapper.updateById(conn);
        if (updated > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.connection(conn.getConnectionId(),
                    "connection-updated:" + conn.getProviderId()));
        }
        return updated;
    }

    /**
     * 只更新连接凭证（签到续期语义）。
     *
     * @param connectionId 连接标识
     * @param credentials  新的凭证
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateCredentials(String connectionId, Map<String, Object> credentials) {
        if (connectionId == null || connectionId.isBlank() || credentials == null) {
            return 0;
        }
        SysAccountChannelConnection update = new SysAccountChannelConnection();
        update.setConnectionId(connectionId);
        update.setCredentials(credentials);
        update.setUpdatedAt(System.currentTimeMillis());
        int updated = connectionMapper.updateById(update);
        if (updated > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.connection(connectionId,
                    "connection-credentials:" + connectionId));
        }
        return updated;
    }

    /**
     * 系统级作用域（{@code _free} / {@code _sys}）只看系统池，其余按「本人 + 管理员见系统池」。
     *
     * @param wrapper    连接查询包装器
     * @param ownerScope 归属作用域
     */
    private void applyPoolFilter(LambdaQueryWrapper<SysAccountChannelConnection> wrapper, String ownerScope) {
        if (SCOPE_FREE.equals(ownerScope) || SCOPE_SYSTEM.equals(ownerScope)) {
            wrapper.isNull(SysAccountChannelConnection::getUserId);
            return;
        }
        boolean admin = AccountUserContext.isAdmin();
        String userId = ownerScope;
        wrapper.and(w -> {
            w.eq(SysAccountChannelConnection::getUserId, userId);
            if (admin) {
                w.or().isNull(SysAccountChannelConnection::getUserId);
            }
        });
    }

    /**
     * 取单条连接；别人名下的连接按「不存在」处理。
     *
     * <p>同一角色可能同时存在「自己名下」与「NULL 系统池」两行（管理员两者都可见），
     * 因此取 {@code user_id} 降序的第一条：自己名下优先，其次系统池。</p>
     *
     * @param providerId 渠道标识
     * @param role       连接角色
     * @return 连接实体
     */
    public SysAccountChannelConnection require(String providerId, String role) {
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .eq(SysAccountChannelConnection::getRole, role);
        channelService.applyOwnerFilter(wrapper);
        List<SysAccountChannelConnection> matches = connectionMapper.selectList(
                wrapper.orderByDesc(SysAccountChannelConnection::getUserId));
        if (matches.isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        return matches.get(0);
    }

    /**
     * 保存（新建或更新）连接。
     *
     * @param providerId 渠道标识
     * @param body       连接字段：role/label/base_url/auth_scheme/credentials/extra
     * @return 保存后的连接
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannelConnection save(String providerId, Map<String, Object> body) {
        channelService.require(providerId);
        String role = requireText(body, "role");
        SysAccountChannelConnection existing;
        try {
            existing = require(providerId, role);
        } catch (ApiException e) {
            if (e.getErrorCode() != ApiErrorCode.NOT_FOUND) {
                throw e;
            }
            existing = null;
        }
        long now = System.currentTimeMillis();
        if (existing == null) {
            if (body.get("credentials") == null) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "新建连接时 credentials 必填");
            }
            SysAccountChannelConnection conn = new SysAccountChannelConnection();
            conn.setConnectionId(String.valueOf(now + (int) (Math.random() * 1000)));
            conn.setProviderId(providerId);
            conn.setRole(role);
            conn.setLabel(str(body, "label"));
            conn.setBaseUrl(str(body, "base_url"));
            conn.setAuthScheme(str(body, "auth_scheme"));
            conn.setCredentials(cast(body.get("credentials")));
            conn.setExtra(cast(body.get("extra")));
            conn.setCreatedAt(now);
            conn.setUpdatedAt(now);
            connectionMapper.insert(conn);
            events.publishEvent(ChannelCatalogChangedEvent.connection(conn.getConnectionId(),
                    "connection-created:" + providerId + ":" + role));
            return conn;
        }
        // 更新时只覆盖显式传入的字段，未传凭证则保留原值
        if (body.containsKey("label")) {
            existing.setLabel(str(body, "label"));
        }
        if (body.containsKey("base_url")) {
            existing.setBaseUrl(str(body, "base_url"));
        }
        if (body.containsKey("auth_scheme")) {
            existing.setAuthScheme(str(body, "auth_scheme"));
        }
        if (body.containsKey("extra")) {
            existing.setExtra(cast(body.get("extra")));
        }
        if (body.containsKey("credentials")) {
            existing.setCredentials(cast(body.get("credentials")));
        }
        existing.setUpdatedAt(now);
        connectionMapper.updateById(existing);
        events.publishEvent(ChannelCatalogChangedEvent.connection(existing.getConnectionId(),
                "connection-updated:" + providerId + ":" + role));
        return existing;
    }

    /**
     * 删除连接（只删自己名下的行）。
     *
     * @param providerId 渠道标识
     * @param role       连接角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String providerId, String role) {
        channelService.require(providerId);
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .eq(SysAccountChannelConnection::getRole, role);
        // 账号隔离：别人的同 role 行不会被这条请求带走
        channelService.applyOwnerFilter(wrapper);
        int deleted = connectionMapper.delete(wrapper);
        if (deleted == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "connection 不存在: " + role);
        }
        events.publishEvent(ChannelCatalogChangedEvent.catalog("connection-removed:" + providerId + ":" + role));
    }

    /**
     * 取文本字段（空串归 null）。
     *
     * @param body 字段集合
     * @param key  键
     * @return 值或 null
     */
    private String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /**
     * 必填文本字段。
     *
     * @param body 字段集合
     * @param key  键
     * @return 值
     */
    private String requireText(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + key + " 不能为空");
        }
        return value;
    }

    /**
     * 泛型透传（JSON 列原样落库）。
     *
     * @param value 原始值
     * @param <T>   目标类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    private <T> T cast(Object value) {
        return (T) value;
    }
}
