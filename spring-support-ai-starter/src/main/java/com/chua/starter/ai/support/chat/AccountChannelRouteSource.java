// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.ChannelHealthService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.channel.AccountChannelAuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号渠道路由源：把渠道管理里已开启的 OAuth 渠道模型并进免费渠道。
 *
 * <p>一个渠道的多条连接（多账号）聚合成同一个 {@link RouteTarget} 的 {@code apiKeys}，
 * 由 {@code FreeChannelChatClient} 在 429 时换 key。渠道本身也按渠道名注册一条前缀路由
 * （{@code provider} 精确匹配在 {@code ModelClientResolver} 里处理）。</p>
 *
 * <p>渠道的协议知识住在账号模块的 {@link AccountChannelAuthManager} 里，本类只调
 * {@code toRouteTarget} 与查库，不重复维护任何平台的鉴权细节。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountChannelRouteSource implements ChannelRouteSource {

    /**
     * 供应商 Mapper.
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商模型 Mapper.
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 供应商连接 Mapper.
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    /**
     * 渠道健康探针：剔除已下线的连接.
     */
    private final ChannelHealthService channelHealthService;

    @Override
    public String name() {
        return "account";
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Map<String, RouteTarget> routes(String ownerScope) {
        Map<String, RouteTarget> routes = new LinkedHashMap<>();
        List<SysAiProvider> oauthProviders = providerMapper.selectList(
                Wrappers.<SysAiProvider>lambdaQuery()
                        .eq(SysAiProvider::getAuthScheme, "oauth")
                        .eq(SysAiProvider::getEnabled, true));
        for (SysAiProvider provider : oauthProviders) {
            RouteTarget merged = mergedTarget(provider, ownerScope);
            if (merged == null) {
                continue;
            }
            List<SysAiProviderModel> models = providerModelMapper.selectList(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getProviderId, provider.getProviderId())
                            .eq(SysAiProviderModel::getEnabled, true));
            // 按渠道名注册（供 prefix match / 排障用）
            routes.put(provider.getPlatform(), merged);
            // 按模型名注册（exact match）
            for (SysAiProviderModel m : models) {
                routes.put(m.getModel(), merged);
            }
            log.info("[AccountRoute] 渠道 {} 注册 {} 个模型 {} 个账号",
                    provider.getPlatform(), models.size(), merged.getApiKeys().size());
        }
        return routes;
    }

    /**
     * 把一个渠道的多条可用连接聚合成一条路由目标。
     *
     * @param provider   渠道供应商行
     * @param ownerScope 归属作用域
     * @return 聚合目标；渠道无实现 / 无可用连接时返回 {@code null}
     */
    private RouteTarget mergedTarget(SysAiProvider provider, String ownerScope) {
        AccountChannelAuthManager spi = findSpi(provider.getPlatform());
        if (spi == null) {
            return null;
        }
        List<SysAiProviderConnection> connections = connectionMapper.selectList(
                connectionWrapper(provider.getProviderId(), ownerScope));
        if (connections.isEmpty()) {
            return null;
        }
        List<String> apiKeys = new ArrayList<>(connections.size());
        RouteTarget template = null;
        for (SysAiProviderConnection conn : connections) {
            if (channelHealthService.isDown(conn.getConnectionId())) {
                continue;
            }
            RouteTarget target = spi.toRouteTarget(conn.getCredentials());
            if (target == null) {
                continue;
            }
            // 免鉴权渠道（authScheme=none）的 apiKey 为空串，允许通过
            if (StringUtils.isBlank(target.getApiKey())
                    && !"none".equals(target.getAuthScheme())) {
                continue;
            }
            if (template == null) {
                template = target;
            }
            apiKeys.add(target.getApiKey());
        }
        if (template == null || apiKeys.isEmpty()) {
            return null;
        }
        return RouteTarget.builder()
                .provider(template.getProvider())
                .baseUrl(template.getBaseUrl())
                .authScheme(template.getAuthScheme())
                .authHeader(template.getAuthHeader())
                .authPrefix(template.getAuthPrefix())
                .apiKeys(apiKeys)
                .headers(template.getHeaders())
                .autoSwitchOnRateLimit(true)
                .build();
    }

    /**
     * 连接查询条件：按归属作用域过滤。
     *
     * <p>免费渠道是<b>共享池</b>：渠道本身是通用资源，不按绑定者隔离——谁绑的账号都汇进
     * 同一条路由（用户口径：「整个渠道都是通用的」）。这里刻意不再限
     * {@code user_id IS NULL}，否则管理员绑的账号进不了免费池。</p>
     *
     * @param providerId 渠道供应商行 ID
     * @param ownerScope 归属作用域
     * @return 查询条件
     */
    private LambdaQueryWrapper<SysAiProviderConnection> connectionWrapper(
            String providerId, String ownerScope) {
        LambdaQueryWrapper<SysAiProviderConnection> wrapper =
                new LambdaQueryWrapper<SysAiProviderConnection>()
                        .eq(SysAiProviderConnection::getProviderId, providerId);
        if ("_free".equals(ownerScope)) {
            return wrapper;
        }
        if ("_sys".equals(ownerScope)) {
            return wrapper.isNull(SysAiProviderConnection::getUserId);
        }
        boolean admin = AiUserContext.isAdmin();
        String userId = ownerScope;
        return wrapper.and(w -> {
            w.eq(SysAiProviderConnection::getUserId, userId);
            if (admin) {
                w.or().isNull(SysAiProviderConnection::getUserId);
            }
        });
    }

    /**
     * 根据平台名查找对应的渠道 SPI 实现。
     *
     * <p>库里 {@code platform} 可能大写（实测 {@code OPENCODE} / {@code CLINE} / {@code GEMINI}），
     * 而 SPI 注册名是小写（{@code @Spi("opencode")}）。大小写不匹配会让整条渠道被跳过，
     * 表现为「渠道页有模型、免费池里一个都没有」，因此两种写法都试。</p>
     *
     * @param platform 平台名（如 {@code loomy} / {@code qoder} / {@code workbuddy}）
     * @return SPI 实例；未找到返回 {@code null}
     */
    private AccountChannelAuthManager findSpi(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        String trimmed = platform.trim();
        try {
            AccountChannelAuthManager hit = ServiceProvider.of(AccountChannelAuthManager.class)
                    .getExtension(trimmed);
            if (hit != null) {
                return hit;
            }
        } catch (Exception ignored) {
            // 继续尝试小写
        }
        try {
            return ServiceProvider.of(AccountChannelAuthManager.class)
                    .getExtension(trimmed.toLowerCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            log.debug("[AccountRoute] 未找到 SPI: {}", platform);
            return null;
        }
    }
}
