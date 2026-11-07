// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import com.chua.common.support.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Magpie 网关路由源：把网关里的动态模型清单并进免费渠道路由表。
 *
 * <p><b>为什么单独一个源</b>：OAuth 渠道的动态路由只覆盖
 * {@code sys_ai_provider_model} 里登记过的模型；而 magpie 的模型目录由网关自己维护
 * （用户随时在 magpie 里增删 provider），落库会立刻过期。这里直接读
 * {@code GET /v1/models}，把当前可用的模型 id 全部注册成路由。</p>
 *
 * <p><b>两层路由</b>：{@code FreeChannelChatClient} 负责第一层（按模型名选到 magpie），
 * magpie 再负责第二层（在它自己的 provider / 账号之间选）。因此这里只注册
 * 「模型 id → 网关数据面」的映射，不感知 magpie 内部账号。</p>
 *
 * <p><b>渠道知识不在本模块</b>：magpie 的地址归一化、数据面鉴权、模型清单解析都在
 * 账号模块的渠道实现里。本类只按渠道标识取 {@link AccountChannelAuthManager}，
 * 调 {@code toRouteTarget} / {@code listModels} 两个契约方法；账号模块未引入时
 * 退化为「没有 magpie 路由」，不影响其它渠道。</p>
 *
 * <p><b>注册优先级</b>：调用方用 {@code putIfAbsent} 合并，magpie 永远是最低优先级——
 * 其它渠道显式登记的模型名不会被网关同名模型盖掉。另注册 {@code magpie/} 前缀兜底，
 * 允许用 {@code magpie/<provider>/<model>} 显式指定走网关。</p>
 *
 * <p>结果缓存 5 分钟（含失败结果），避免每次构建客户端都打一次网关。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MagpieRouteSource {

    /**
     * 渠道 / 前缀标识。
     */
    public static final String CHANNEL = "magpie";

    /**
     * 渠道 provider 行 ID（渠道管理按 {@code channel().toUpperCase()} 建行）。
     */
    static final String PROVIDER_ID = "MAGPIE";

    /**
     * 缓存有效期（毫秒）：5 分钟。
     */
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    /**
     * 供应商 Mapper。
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商连接 Mapper。
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    /**
     * 路由缓存：ownerScope → 快照。
     */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * 当前可用的 magpie 路由。
     *
     * @param ownerScope 归属作用域（{@code _free} / {@code _sys} / 用户标识）
     * @return 路由表；网关未绑定或不可达时返回空 Map
     */
    public Map<String, RouteTarget> routes(String ownerScope) {
        Snapshot snapshot = snapshot(ownerScope);
        if (snapshot == null) {
            return new LinkedHashMap<>();
        }
        Map<String, RouteTarget> routes = new LinkedHashMap<>();
        RouteTarget template = snapshot.target();
        for (String model : snapshot.models()) {
            // 精确 id 优先：routes.get(model) 命中后不会走前缀切割
            routes.put(model, template.withModel(model));
        }
        // 前缀兜底：magpie/<上游模型 id>（模型 id 自身可能带斜杠）
        routes.put(CHANNEL, template);
        return routes;
    }

    /**
     * 当前可用的 magpie 模型清单（供供应商页「拉取模型」展示）。
     *
     * @param ownerScope 归属作用域
     * @return 每项 {@code {id, name, provider}}；未绑定时返回空列表
     */
    public List<Map<String, Object>> modelRows(String ownerScope) {
        Snapshot snapshot = snapshot(ownerScope);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (snapshot == null) {
            return rows;
        }
        for (String id : snapshot.models()) {
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("id", id);
            row.put("name", id);
            row.put("baseUrl", snapshot.target().getBaseUrl());
            int slash = id.indexOf('/');
            if (slash > 0) {
                row.put("provider", id.substring(0, slash));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 清空缓存（magpie provider 增删改后调用，让路由与模型清单立即刷新）。
     */
    public void invalidate() {
        cache.clear();
    }

    /**
     * 构建（或读缓存的）网关快照。
     *
     * @param ownerScope 归属作用域
     * @return 快照；未绑定 / 不可达返回 {@code null}
     */
    private Snapshot snapshot(String ownerScope) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(ownerScope);
        if (cached != null && now - cached.builtAt() < CACHE_TTL_MILLIS) {
            return cached.snapshot();
        }
        Snapshot snapshot = load(ownerScope);
        cache.put(ownerScope, new Cached(snapshot, now));
        return snapshot;
    }

    /**
     * 从数据库连接凭据 + magpie 渠道实现加载快照。
     *
     * @param ownerScope 归属作用域
     * @return 快照；不可用时 {@code null}
     */
    private Snapshot load(String ownerScope) {
        try {
            SysAiProviderConnection conn = primaryConnection(ownerScope);
            if (conn == null || conn.getCredentials() == null || conn.getCredentials().isEmpty()) {
                return null;
            }
            AccountChannelAuthManager impl = magpieManager();
            if (impl == null) {
                log.debug("[MagpieRoute] 未注册 magpie 渠道实现（账号模块未引入），跳过网关路由");
                return null;
            }
            RouteTarget target = impl.toRouteTarget(conn.getCredentials());
            if (target == null) {
                return null;
            }
            Set<String> ids = new LinkedHashSet<>();
            for (Map<String, Object> model : impl.listModels(conn.getCredentials())) {
                Object id = model == null ? null : model.get("id");
                if (id != null && StringUtils.isNotBlank(String.valueOf(id))) {
                    ids.add(String.valueOf(id));
                }
            }
            log.debug("[MagpieRoute] 网关 {} 注册 {} 个模型", target.getBaseUrl(), ids.size());
            return new Snapshot(target, new ArrayList<>(ids));
        } catch (Exception e) {
            log.debug("[MagpieRoute] 读取网关模型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取 magpie 渠道的鉴权实现（按渠道标识大小写不敏感匹配）。
     *
     * @return 实现；账号模块未引入时返回 {@code null}
     */
    private AccountChannelAuthManager magpieManager() {
        for (Map.Entry<String, AccountChannelAuthManager> entry
                : ServiceProvider.of(AccountChannelAuthManager.class).list().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(CHANNEL)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 取该作用域下可用的第一条 magpie 连接（网关通常是单实例，多连接取最新的）。
     *
     * @param ownerScope 归属作用域
     * @return 连接；未绑定返回 {@code null}
     */
    private SysAiProviderConnection primaryConnection(String ownerScope) {
        SysAiProvider provider = providerMapper.selectById(PROVIDER_ID);
        if (provider == null) {
            provider = providerMapper.selectOne(new LambdaQueryWrapper<SysAiProvider>()
                    .eq(SysAiProvider::getPlatform, PROVIDER_ID)
                    .last("LIMIT 1"));
        }
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            return null;
        }
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, provider.getProviderId());
        if ("_free".equals(ownerScope) || "_sys".equals(ownerScope)) {
            wrapper.isNull(SysAiProviderConnection::getUserId);
        } else {
            boolean admin = AiUserContext.isAdmin();
            String userId = ownerScope;
            wrapper.and(w -> {
                w.eq(SysAiProviderConnection::getUserId, userId);
                if (admin) {
                    w.or().isNull(SysAiProviderConnection::getUserId);
                }
            });
        }
        wrapper.orderByDesc(SysAiProviderConnection::getRole)
                .orderByDesc(SysAiProviderConnection::getUpdatedAt)
                .last("LIMIT 1");
        SysAiProviderConnection conn = connectionMapper.selectOne(wrapper);
        if (conn != null || !("_free".equals(ownerScope) || "_sys".equals(ownerScope))) {
            return conn;
        }
        // 系统级作用域先按「历史 NULL 系统池」找；渠道管理改造后管理员绑定的连接带 userId，
        // magpie 作为共享网关不能被漏掉，故再退一步取该 provider 下任意一条连接。
        return connectionMapper.selectOne(new LambdaQueryWrapper<SysAiProviderConnection>()
                .eq(SysAiProviderConnection::getProviderId, provider.getProviderId())
                .orderByDesc(SysAiProviderConnection::getRole)
                .orderByDesc(SysAiProviderConnection::getUpdatedAt)
                .last("LIMIT 1"));
    }

    /**
     * 网关快照。
     *
     * @param target 路由模板（含数据面地址与 key）
     * @param models 当前可用模型 id
     */
    private record Snapshot(RouteTarget target, List<String> models) {
    }

    /**
     * 缓存条目。
     *
     * @param snapshot 快照（可为 null，表示「当前不可用」）
     * @param builtAt  构建时刻
     */
    private record Cached(Snapshot snapshot, long builtAt) {
    }
}
