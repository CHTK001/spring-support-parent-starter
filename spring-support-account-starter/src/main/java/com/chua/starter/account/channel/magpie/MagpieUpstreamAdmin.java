// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.ChannelUpstreamAdmin;
import com.chua.starter.common.support.channel.ChannelUpstreamCodec;
import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import com.chua.starter.common.support.channel.MagpieSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Magpie 网关的上游 provider 管理实现。
 *
 * <p>渠道页的「上游」页签通过本实现增删改查网关里的 provider：读写走管理面
 * {@code /api/*}（页面 Cookie 鉴权），凭据一律脱敏后才回传前端。</p>
 *
 * <p><b>为什么要单独一层</b>：连接记录在 AI 模块，账号模块看不到；AI 的控制器只负责
 * 「按渠道 + 连接主键取出凭据」，具体的请求体形态、动作白名单、脱敏规则都属于
 * magpie 自己的知识，因此全部留在本模块。</p>
 *
 * <p>管理面默认不开（桌面版需在「设置 › 网络与共享 › 浏览器访问」打开），
 * 不可用时异常信息会给出可操作原因，不影响数据面（对话 / 模型 / 配额）。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieUpstreamAdmin implements ChannelUpstreamAdmin {

    /**
     * 放行的分项动作：{@code detect} / {@code models} / {@code test} 是只读探测，
     * {@code on} / {@code off} 是 magpie 自己的启停端点（{@code POST /api/provider/{on|off}}，
     * 请求体只有 {@code id}）。
     *
     * <p>启停为什么不走 {@link #save}：save 收的是整份 provider 视图，字段缺省时
     * 由网关按预设补齐；停一条上游只想翻一个开关，走网关为它单开的端点最稳。</p>
     */
    private static final Set<String> ACTIONS = Set.of("detect", "models", "test", "on", "off");

    /**
     * 放行的插件动作：与 magpie {@code POST /api/plugins/{op}} 的 switch 一致。
     */
    private static final Set<String> PLUGIN_ACTIONS = Set.of("add", "remove", "update", "upgrade",
            "off", "options", "prefer", "registry", "mirror", "check");

    /**
     * 线上形态适配（SPI）。
     *
     * <p>列表脱敏、列表派生物清理、{@code balanceToken}/{@code models} 归一这些
     * magpie 私有协议知识都在 {@link MagpieProviderCodec} 里，本类只按渠道别名取用。</p>
     */
    private volatile ChannelUpstreamCodec codec;

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    /**
     * 取本渠道的 provider 形态适配实现。
     *
     * @return 适配实现
     */
    private ChannelUpstreamCodec codec() {
        ChannelUpstreamCodec local = codec;
        if (local == null) {
            synchronized (this) {
                local = codec;
                if (local == null) {
                    local = ServiceProvider.of(ChannelUpstreamCodec.class).getExtension(channel());
                    if (local == null) {
                        throw new ChannelException(ChannelException.CODE_NOT_IMPLEMENTED,
                                "渠道 " + channel() + " 缺少 ChannelUpstreamCodec 实现");
                    }
                    codec = local;
                }
            }
        }
        return local;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 有插件机制（{@code /api/plugins*}），故插件页签可用。</p>
     */
    @Override
    public boolean supportsPlugins() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 内置登录与插件登录共用 {@code /api/signin/{id}} 轮询。</p>
     */
    @Override
    public boolean supportsSignin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 的 provider id 与我们的渠道 id 并不总是同名（trae → {@code trae-cn}、
     * minimax → {@code minimax-code}、gemini → {@code google}）。映射放在
     * {@link MagpieSupport#providerIdsOf(String)}，与前端左栏使用同一份对照表。</p>
     */
    @Override
    public List<String> providerIdsOf(String channel) {
        return MagpieSupport.providerIdsOf(channel);
    }

    /**
     * {@inheritDoc}
     *
     * <p>整份对照表直接来自 {@link MagpieSupport#VENDOR_PROVIDERS}：它就是后端
     * 判断「渠道是否由网关承载」与「按哪个 provider 取额度」用的那一份，
     * 前端拿到的是同一份，不再各自维护。</p>
     */
    @Override
    public Map<String, List<String>> ownedChannels() {
        return MagpieSupport.VENDOR_PROVIDERS;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只做地址归一化，不探活。</p>
     */
    @Override
    public Map<String, Object> describe(Map<String, Object> credentials) {
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("baseUrl", MagpieSupport.baseUrl(credentials));
        out.put("pageUrl", MagpieSupport.pageUrl(credentials));
        return out;
    }

    /**
     * {@inheritDoc}
     *
     * <p>管理面 {@code GET /api/providers}；provider 的 {@code key} / {@code keyList}
     * 只留末四位，并给出 {@code keyCount} / {@code hasKey} 供前端判断是否已配置。</p>
     */
    @Override
    public Map<String, Object> list(Map<String, Object> credentials) {
        List<Map<String, Object>> providers = MagpieAdminClient.providers(credentials);
        List<Map<String, Object>> safe = new ArrayList<>(providers.size());
        ChannelUpstreamCodec codec = codec();
        for (Map<String, Object> provider : providers) {
            Map<String, Object> row = codec.desensitize(provider);
            attachIcon(credentials, row);
            safe.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("providers", safe);
        return out;
    }

    /**
     * 给一行 provider 附上品牌图标的 data URL（{@code iconDataUrl}）。
     *
     * <p>图标只挂在 magpie 管理面且需要页面 Cookie，浏览器取不到；由后端取一次、
     * 转 data URL 随列表下发，前端才能把这些渠道显示成真实软件图标而不是首字母。
     * 取不到（{@code generic} 没有实体文件、管理面不可达）就不放这个字段，
     * 前端自然回落到文字徽标。</p>
     *
     * @param credentials 网关连接凭据
     * @param row         已脱敏的 provider 行
     */
    private static void attachIcon(Map<String, Object> credentials, Map<String, Object> row) {
        Object icon = row.get("icon");
        if (!(icon instanceof String name) || name.isBlank()) {
            return;
        }
        String dataUrl = MagpieAdminClient.iconDataUrl(credentials, name);
        if (dataUrl != null && !dataUrl.isBlank()) {
            row.put("iconDataUrl", dataUrl);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>请求体形如 {@code { provider: {...}, isNew?, from? }}；{@code provider} 缺失时
     * 把其余字段当作 provider 字段。{@code isNew} 对应 magpie 的同级 {@code new:true}，
     * {@code from} 用于重命名（旧 id）。</p>
     */
    @Override
    public Map<String, Object> save(Map<String, Object> credentials, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
        Map<String, Object> provider = codec().toUpstreamInput(body);
        if (Boolean.TRUE.equals(body.get("isNew")) || Boolean.TRUE.equals(body.get("new"))) {
            provider.put("new", true);
        }
        String from = asString(body.get("from"));
        if (from != null && !from.isBlank()) {
            provider.put("from", from.trim());
        }
        return MagpieAdminClient.save(credentials, provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> delete(Map<String, Object> credentials, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "必须指定要删除的 provider id");
        }
        return MagpieAdminClient.delete(credentials, providerId.trim());
    }

    /**
     * {@inheritDoc}
     *
     * <p>放行 {@code detect} / {@code models} / {@code test} / {@code on} / {@code off}；
     * {@code models} 的响应额外归一成 {@code modelIds}，前端不必再解一层对象。</p>
     */
    @Override
    public Map<String, Object> action(Map<String, Object> credentials, String action,
                                      Map<String, Object> payload) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(normalized)) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                    "不支持的 magpie provider 动作: " + action
                            + "（仅 detect / models / test / on / off）");
        }
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
        Map<String, Object> result = MagpieAdminClient.action(credentials, normalized, codec().toUpstreamInput(body));
        if ("models".equals(normalized)) {
            result.put("modelIds", MagpieAdminClient.modelIds(result));
        }
        return result;
    }

    // ── 插件 / 登录 / 额度 ──

    /**
     * 插件清单（可选市场），并把 magpie 的字段归一成前端稳定形态。
     *
     * <p>{@code /api/plugins} 的返回里 {@code plugins[]} 直接内嵌包的 Entry，
     * 不同版本的键名不完全一致；这里统一成 {@code spec/name/off/version/latest/
     * providers/moved/error}，前端不再猜。</p>
     *
     * @param credentials 网关连接凭据
     * @param market      是否同时读市场（慢，可能访问 npm / GitHub）
     * @return {@code {plugins:[...], bun, bunVer, mirror, registry, listings}}
     */
    @Override
    public Map<String, Object> plugins(Map<String, Object> credentials, boolean market) {
        Map<String, Object> raw = MagpieAdminClient.plugins(credentials, market);
        Map<String, Object> state = asMap(raw.get("state"));
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("plugins", normalizePlugins(state.get("plugins")));
        copyIfPresent(state, out, "bun", "bunVer", "mirror", "registry", "error");
        if (market) {
            Map<String, Object> marketState = asMap(raw.get("market"));
            out.put("listings", marketState.get("listings") == null ? List.of() : marketState.get("listings"));
            Object marketError = marketState.get("error");
            if (marketError != null) {
                out.put("marketError", marketError);
            }
        }
        return out;
    }

    /**
     * 插件动作（白名单），装 / 卸 / 升级后由调用方刷新路由。
     *
     * <p>{@code add} 首次会下载 Bun 再 npm 安装，magpie 自己给了 5 分钟超时；
     * 调用链必须允许多等，不能按普通接口的短超时处理。</p>
     *
     * @param credentials 网关连接凭据
     * @param op          动作名
     * @param payload     请求体
     * @return 上游响应
     */
    @Override
    public Map<String, Object> pluginAction(Map<String, Object> credentials, String op,
                                            Map<String, Object> payload) {
        String normalized = op == null ? "" : op.trim().toLowerCase(Locale.ROOT);
        if (!PLUGIN_ACTIONS.contains(normalized)) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                    "不支持的 magpie 插件动作: " + op + "（仅 add / remove / update / upgrade / off / "
                            + "options / prefer / registry / mirror / check）");
        }
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        body.remove("connectionId");
        body.remove("label");
        return MagpieAdminClient.pluginAction(credentials, normalized, body);
    }

    /**
     * 发起上游登录：插件 provider 走 {@code /api/plugin-signin}，内置走 {@code /api/signin}。
     *
     * @param credentials 网关连接凭据
     * @param payload     {@code {plugin?, provider, method, inputs, key}} 或 {@code {agent, site, proxy, user}}
     * @return 登录会话
     */
    @Override
    public Map<String, Object> startSignin(Map<String, Object> credentials, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        body.remove("connectionId");
        body.remove("label");
        Object plugin = body.remove("plugin");
        boolean viaPlugin = Boolean.TRUE.equals(plugin) || body.get("provider") != null;
        try {
            return viaPlugin
                    ? MagpieAdminClient.startPluginSignin(credentials, body)
                    : MagpieAdminClient.startSignin(credentials, body);
        } catch (ChannelException failure) {
            throw signinFailure(body, failure);
        }
    }

    /**
     * 把网关「没有登录方式」的回绝翻成人话。
     *
     * <p>纯 API Key 上游（自带 {@code chat} 基址 + {@code keyList}）在 magpie 里没有登录
     * 实现，{@code POST /api/signin} 会回 400 {@code magpie can't sign in to <id> accounts}。
     * 这类 4xx 是「这个请求本来就不成立」，不是网关坏了：原样透出会变成
     * {@code 502 渠道上游返回非 2xx}，用户看不懂也不知道下一步做什么。</p>
     *
     * @param body    请求体（取 {@code provider} / {@code agent} 作为上游标识）
     * @param failure 网关原始异常
     * @return 面向用户的异常
     */
    private static ChannelException signinFailure(Map<String, Object> body, ChannelException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (!message.contains("can't sign in to")) {
            return failure;
        }
        Object target = body.get("provider") != null ? body.get("provider") : body.get("agent");
        String id = target == null ? "该上游" : "「" + target + "」";
        return new ChannelException(ChannelException.CODE_INVALID_PARAM,
                "上游" + id + "用 API Key 接入，网关里没有它的登录方式：换 Key 请直接编辑上游凭据，不需要登录");
    }

    /**
     * 插件登录前的一问：把页面给的答案交给网关，由插件校验并决定下一问。
     *
     * @param credentials 网关连接凭据
     * @param payload     {@code {provider, method, inputs, key?, value?}}
     * @return {@code {prompt, inputs}} 或 {@code {error, inputs}}
     */
    @Override
    public Map<String, Object> signinPrompt(Map<String, Object> credentials,
                                            Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        body.remove("connectionId");
        body.remove("label");
        if (body.get("provider") == null) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                    "缺少要登录的 provider");
        }
        return MagpieAdminClient.pluginSigninPrompt(credentials, body);
    }

    /**
     * 查询登录会话（内置与插件共用一个轮询端点）。
     *
     * @param credentials 网关连接凭据
     * @param id          会话 id
     * @return 会话状态
     */
    @Override
    public Map<String, Object> signinStatus(Map<String, Object> credentials, String id) {
        if (id == null || id.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "登录会话 id 缺失");
        }
        return MagpieAdminClient.signinStatus(credentials, id.trim());
    }

    /**
     * 取消登录会话。
     *
     * @param credentials 网关连接凭据
     * @param id          会话 id
     */
    @Override
    public void cancelSignin(Map<String, Object> credentials, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        MagpieAdminClient.cancelSignin(credentials, id.trim());
    }

    /**
     * 管理面额度卡（含签到状态与插件账号）。
     *
     * @param credentials 网关连接凭据
     * @param provider    provider id（可空）
     * @param user        账号（可空）
     * @return {@code {quotas:[...]}}
     */
    @Override
    public Map<String, Object> quotas(Map<String, Object> credentials, String provider, String user) {
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("quotas", withCustomCredit(
                MagpieAdminClient.adminQuotas(credentials, provider, user), provider, user, credentials));
        return out;
    }

    /**
     * 让 magpie 重新读一张额度卡。
     *
     * @param credentials 网关连接凭据
     * @param provider    provider id（必填）
     * @param user        账号（可空）
     * @return {@code {quotas:[...]}}
     */
    @Override
    public Map<String, Object> refreshQuota(Map<String, Object> credentials, String provider, String user) {
        if (provider == null || provider.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "必须指定要刷新的 provider");
        }
        String wanted = provider.trim();
        Map<String, Object> out = new LinkedHashMap<>(2);
        List<Map<String, Object>> quotas;
        try {
            quotas = MagpieAdminClient.refreshQuota(credentials, wanted, user);
        } catch (ChannelException e) {
            // 网关这条上游根本没有读额度的端点时刷新会失败；只要自建余额源能给出读数，
            // 这次刷新就算成功（按钮上「刷新」的语义是「重新读一次」，不限定走网关）。
            List<Map<String, Object>> custom = withCustomCredit(List.of(), wanted, user, credentials);
            if (custom.isEmpty()) {
                throw e;
            }
            out.put("quotas", custom);
            return out;
        }
        out.put("quotas", withCustomCredit(quotas, wanted, user, credentials));
        return out;
    }

    /**
     * 网关额度卡补齐自建读数。
     *
     * <p>只处理「点名了一个上游」的调用（账号卡）：全量列表不逐家代查，否则一次打开
     * 上游页签会把 44 家上游各问一遍自建源。点名上游时，若网关没有它的卡、或卡里
     * 既没有窗口也没有余额（SiliconFlow 下线余额接口就是这种），才问
     * {@link ChannelUpstreamProvider#credit(String, String, Map)}：</p>
     * <ul>
     *   <li>自建源给了读数：填进同一张卡（保留网关原有的 {@code seeAt} 等字段），
     *       去掉 {@code error}，标 {@code source=spi}；没有卡就补一张；</li>
     *   <li>自建源只给了原因：网关有卡就保留网关原文；没有卡才补一条自建失败；</li>
     *   <li>自建源不接管：原样返回，页面继续显示网关的原因。</li>
     * </ul>
     *
     * <p>包内可见是为了单测：注入假实现即可验证补齐 / 不补齐 / 追加三种分支。</p>
     *
     * @param quotas      网关额度卡
     * @param provider    上游 id（为空表示全量，直接原样返回）
     * @param user        上游账号（可空）
     * @param credentials 网关连接凭据（只读）
     * @param impls       自建余额实现列表
     * @return 补齐后的额度卡
     */
    static List<Map<String, Object>> withCustomCredit(List<Map<String, Object>> quotas, String provider,
                                                      String user, Map<String, Object> credentials,
                                                      List<ChannelUpstreamProvider> impls) {
        if (MagpieSupport.isBlank(provider) || quotas == null) {
            return quotas;
        }
        String wanted = provider.trim();
        Map<String, Object> card = null;
        for (Map<String, Object> row : quotas) {
            if (row != null && wanted.equalsIgnoreCase(MagpieUpstreamCreditSpi.text(row.get("provider")))
                    && matchesUser(row, user)) {
                card = row;
                break;
            }
        }
        if (!unreadable(card)) {
            return quotas;
        }
        Map<String, Object> snapshot = MagpieUpstreamCreditSpi.credit(impls, wanted, user, credentials);
        if (snapshot == null) {
            return quotas;
        }
        List<Map<String, Object>> windows = MagpieUpstreamCreditSpi.windowsOf(snapshot);
        String error = MagpieUpstreamCreditSpi.text(snapshot.get("error"));
        if (windows.isEmpty()) {
            // 自建源也只给得出失败原因：网关有原文就别覆盖，没有卡才补一条
            if (card != null || error.isEmpty()) {
                return quotas;
            }
            List<Map<String, Object>> out = new ArrayList<>(quotas.size() + 1);
            out.addAll(quotas);
            out.add(failureCard(wanted, error));
            return out;
        }
        Map<String, Object> filled = card == null
                ? new LinkedHashMap<>(8) : new LinkedHashMap<>(card);
        filled.remove("error");
        filled.put("provider", wanted);
        if (MagpieUpstreamCreditSpi.text(filled.get("name")).isEmpty()) {
            filled.put("name", wanted);
        }
        filled.put("windows", windows);
        filled.put("source", "spi");
        filled.put("readAt", MagpieUpstreamCreditSpi.readAtOf(snapshot));
        if (card == null) {
            List<Map<String, Object>> out = new ArrayList<>(quotas.size() + 1);
            out.addAll(quotas);
            out.add(filled);
            return out;
        }
        List<Map<String, Object>> out = new ArrayList<>(quotas.size());
        for (Map<String, Object> row : quotas) {
            out.add(row == card ? filled : row);
        }
        return out;
    }

    /**
     * 补齐自建读数（走真实 SPI 注册表）。
     *
     * @param quotas      网关额度卡
     * @param provider    上游 id
     * @param user        上游账号（可空）
     * @param credentials 网关连接凭据
     * @return 补齐后的额度卡
     */
    private static List<Map<String, Object>> withCustomCredit(List<Map<String, Object>> quotas, String provider,
                                                             String user, Map<String, Object> credentials) {
        return withCustomCredit(quotas, provider, user, credentials, MagpieUpstreamCreditSpi.providers());
    }

    /**
     * 网关这张卡是否没有可用读数。
     *
     * <p>「有窗口」或「有余额值」都算读到了（余额卡只有 {@code balance} 没有窗口，
     * 不能因为窗口为空就当成读不到）。</p>
     *
     * @param card 额度卡
     * @return true 表示网关读不到
     */
    private static boolean unreadable(Map<String, Object> card) {
        if (card == null) {
            return true;
        }
        if (card.get("windows") instanceof List<?> windows && !windows.isEmpty()) {
            return false;
        }
        return MagpieUpstreamCreditSpi.text(card.get("balance")).isEmpty();
    }

    /**
     * 卡上的账号是否与请求一致（没点名账号时任何卡都算）。
     *
     * @param card 额度卡
     * @param user 请求的账号（可空）
     * @return true 表示命中
     */
    private static boolean matchesUser(Map<String, Object> card, String user) {
        if (MagpieSupport.isBlank(user)) {
            return true;
        }
        String cardUser = MagpieUpstreamCreditSpi.text(card.get("user"));
        return cardUser.isEmpty() || cardUser.equalsIgnoreCase(user.trim());
    }

    /**
     * 自建源读失败、网关也没有这张卡时补的说明卡。
     *
     * @param provider 上游 id
     * @param error    失败原因
     * @return 额度卡
     */
    private static Map<String, Object> failureCard(String provider, String error) {
        Map<String, Object> card = new LinkedHashMap<>(4);
        card.put("provider", provider);
        card.put("name", provider);
        card.put("error", error);
        card.put("source", "spi");
        return card;
    }

    // ── 私有辅助 ──

    /**
     * 归一化插件行：magpie 不同版本键名不完全一致，这里统一成稳定字段。
     *
     * @param raw 原始列表
     * @return 归一化后的列表
     */
    private static List<Map<String, Object>> normalizePlugins(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(8);
            row.put("spec", firstOf(map, "spec", "Spec"));
            row.put("name", firstOf(map, "name", "Name", "ownName", "OwnName"));
            row.put("off", Boolean.TRUE.equals(firstOf(map, "off", "Off")));
            row.put("version", firstOf(map, "version", "Version"));
            row.put("latest", firstOf(map, "latest", "Latest"));
            Object providers = firstOf(map, "providers", "Providers");
            row.put("providers", providers == null ? List.of() : providers);
            Object moved = firstOf(map, "moved", "Moved");
            row.put("moved", moved == null ? List.of() : moved);
            row.put("error", firstOf(map, "error", "Error"));
            out.add(row);
        }
        return out;
    }

    /**
     * 取第一个非空的候选键。
     *
     * @param map 原始行
     * @param keys 候选键
     * @return 值；都没有时返回 {@code null}
     */
    private static Object firstOf(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 把 Map 转成字符串键副本（非 Map 返回空）。
     *
     * @param raw 原始值
     * @return 字符串键 Map
     */
    private static Map<String, Object> asMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
        }
        return out;
    }

    /**
     * 拷贝存在的键。
     *
     * @param source 源
     * @param target 目标
     * @param keys   键
     */
    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    /**
     * 读取字符串字段。
     *
     * @param value 原始值
     * @return 字符串；非字符串返回 {@code null}
     */
    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
