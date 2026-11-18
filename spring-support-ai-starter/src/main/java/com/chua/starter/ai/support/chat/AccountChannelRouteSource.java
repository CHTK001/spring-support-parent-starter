// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.channel.service.AccountChannelConnectionService;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.service.ChannelHealthService;
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
     * 渠道业务（账号模块）：渠道行与认证方式检索.
     */
    private final AccountChannelService channelService;

    /**
     * 渠道模型清单业务（账号模块的查询接口，含缓存）
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 渠道连接业务（账号模块）：按归属作用域取可用凭据.
     */
    private final AccountChannelConnectionService connectionService;

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
        List<SysAccountChannel> oauthProviders = channelService.listEnabledByAuthScheme("oauth");
        for (SysAccountChannel provider : oauthProviders) {
            RouteTarget merged = mergedTarget(provider, ownerScope);
            if (merged == null) {
                continue;
            }
            List<SysAccountChannelModel> models = channelModelService.listEnabled(provider.getProviderId());
            // 按渠道名注册（供 prefix match / 排障用）
            routes.put(provider.getPlatform(), merged);
            // 按模型名注册（exact match）
            for (SysAccountChannelModel m : models) {
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
    private RouteTarget mergedTarget(SysAccountChannel provider, String ownerScope) {
        AccountChannelAuthManager spi = findSpi(provider.getPlatform());
        if (spi == null) {
            return null;
        }
        List<SysAccountChannelConnection> connections =
                connectionService.listForScope(provider.getProviderId(), ownerScope);
        if (connections.isEmpty()) {
            return null;
        }
        List<String> apiKeys = new ArrayList<>(connections.size());
        RouteTarget template = null;
        for (SysAccountChannelConnection conn : connections) {
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
