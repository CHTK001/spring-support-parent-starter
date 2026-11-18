// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import com.chua.starter.common.support.text.json.Json;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Magpie 统一网关的地址 / 凭据归一化辅助。
 *
 * <p>Magpie（{@code ghcr.io/yetone/magpie}）把多家上游（Codex、Claude、Gemini、
 * OpenAI 兼容服务…）聚合到一个网关，对外是<b>两个不同端口、不同鉴权</b>的面：</p>
 * <ul>
 *   <li><b>数据面</b> {@code :3425/v1/*}：OpenAI 兼容对话、模型清单、配额读数。
 *       本机回环访问不需要 key，其它机器需要网关 key；</li>
 *   <li><b>管理面</b> {@code :3430/api/*}：页面自己的 provider 增删改查、签到、用量。
 *       桌面版默认<b>不</b>开放该端口，需在「设置 › 网络与共享 › 浏览器访问」打开，
 *       或用 {@code magpie web} / {@code magpie serve --web} 启动；
 *       鉴权是<b>页面 Key</b>（启动链接里的 {@code ?k=}），换成
 *       {@code magpie_web_<端口>} Cookie 后随请求发送。</li>
 * </ul>
 *
 * <p>⚠️ 两者不是同一个地址：把 {@code /api/*} 打到 3425 只会得到 404
 * （该端口上的 {@code /api/hello} 是 Claude Desktop 兼容端点，不是管理探活），
 * 把 {@code /v1/*} 打到 3430 则要求先开共享 + 网关 key。因此凭据里分开存
 * {@code base_url}（数据面）与 {@code page_url}（管理面）。</p>
 *
 * <p>凭据形态（{@code sys_ai_provider_connection.credentials}）：</p>
 * <pre>{@code
 * {
 *   "base_url": "http://127.0.0.1:3425",   // 数据面，模型/对话/配额
 *   "gateway_key": "sk-magpie-...",        // 数据面 key，本机可空
 *   "page_url": "http://127.0.0.1:3430",   // 管理面（可空，缺省按端口推导）
 *   "page_key": "0123456789abcdef0123...", // 页面 key（管理面必需）
 *   "nickname": "****abcd"
 * }
 * }</pre>
 *
 * <p>本机直连时 Magpie 数据面接受任意网关 key，故 {@code gateway_key} 允许为空，
 * 向数据面发请求时用占位值 {@link #LOCAL_KEY}；远程（Docker 发布端口 / 开启 sharing）
 * 必须填真实 key。管理面则<b>始终</b>需要页面 Key。</p>
 *
 * <p>用户也可以把 {@code magpie web} 启动时打印的整条链接
 * （{@code http://127.0.0.1:3430/?k=xxx}）直接粘进「网关地址」：这里会拆出页面 Key，
 * 并把数据面还原成同主机的 {@link #GATEWAY_PORT}。</p>
 *
 * <p><b>存放位置</b>：本类放在 common，因为 magpie 的地址归一化需被账号模块（渠道鉴权 /
 * 额度 / 上游管理）与 AI 模块（用量解析、路由）共同使用；两种依赖关系通过 {@code common}
 * 共享，任何一方都不直接依赖另一方。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
public final class MagpieSupport {

    /**
     * 渠道标识（与 {@code @Spi} 别名一致）。
     */
    public static final String CHANNEL = "magpie";

    /**
     * 默认网关地址（magpie 桌面版 / {@code magpie serve} 的数据面）。
     */
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:3425";

    /**
     * 默认页面地址（{@code magpie web} / 浏览器访问的管理面）。
     */
    public static final String DEFAULT_ADMIN_URL = "http://127.0.0.1:3430";

    /**
     * 本机直连时的占位网关 key（magpie 本机不校验 key，但请求仍要带 Authorization 头）。
     */
    public static final String LOCAL_KEY = "magpie-local";

    /**
     * 数据面默认端口。
     */
    public static final int GATEWAY_PORT = 3425;

    /**
     * 管理面（页面）默认端口。
     */
    public static final int PAGE_PORT = 3430;

    /**
     * 页面鉴权 Cookie 名前缀：magpie 用 {@code magpie_web_<页面端口>}。
     */
    public static final String COOKIE_PREFIX = "magpie_web_";

    /**
     * 数据面基址后缀。
     */
    public static final String V1 = "/v1";

    /**
     * 管理面 provider 列表。
     */
    public static final String PROVIDERS_PATH = "/api/providers";

    /**
     * 管理面 provider 动作（save / delete / detect / models / test）。
     */
    public static final String PROVIDER_ACTION_PATH = "/api/provider/";

    /**
     * 管理面用量 / 签到前缀（{@code /api/usage/workbuddy-checkin} 等）。
     */
    public static final String USAGE_PATH = "/api/usage/";

    /**
     * 管理面插件清单 / 插件市场。
     */
    public static final String PLUGINS_PATH = "/api/plugins";

    /**
     * 管理面插件动作前缀（{@code /api/plugins/add} 等）。
     */
    public static final String PLUGIN_ACTION_PATH = "/api/plugins/";

    /**
     * 管理面插件登录（{@code POST /api/plugin-signin}）。
     */
    public static final String PLUGIN_SIGNIN_PATH = "/api/plugin-signin";

    /**
     * 管理面插件登录的一问（{@code POST /api/plugin-signin/prompt}）。
     *
     * <p>插件登录前可能先要一个输入（手机号 / 验证码 / 选项），这一问由插件
     * 自己定义；问完才允许发起 {@link #PLUGIN_SIGNIN_PATH}。</p>
     */
    public static final String PLUGIN_SIGNIN_PROMPT_PATH = "/api/plugin-signin/prompt";

    /**
     * 管理面内置 provider 登录（{@code POST /api/signin}）。
     */
    public static final String SIGNIN_PATH = "/api/signin";

    /**
     * 管理面登录会话（{@code GET /api/signin/{id}}，插件登录共用）。
     */
    public static final String SIGNIN_PATH_PREFIX = "/api/signin/";

    /**
     * 管理面额度卡（{@code GET /api/usage/quotas}，含签到状态与插件账号）。
     */
    public static final String ADMIN_QUOTAS_PATH = "/api/usage/quotas";

    /**
     * 管理面插件签到（{@code POST /api/usage/plugin-checkin}，逐 provider）。
     */
    public static final String PLUGIN_CHECKIN_PATH = "/api/usage/plugin-checkin";

    /**
     * 读配额端点（{@code /v1/magpie/quotas}）。
     */
    public static final String QUOTAS_PATH = "/v1/magpie/quotas";

    /**
     * 数据面模型清单端点。
     */
    public static final String MODELS_PATH = "/v1/models";

    /**
     * 管理面不可用时统一提示：桌面版默认不开放页面端口。
     */
    public static final String PAGE_DISABLED_HINT = "Magpie 管理面不可达：桌面版默认不把页面暴露成 HTTP 接口。"
            + "请打开 magpie「设置 › 网络与共享 › 浏览器访问」，或用 magpie web / magpie serve --web 启动"
            + "（默认 127.0.0.1:3430），然后在绑定信息里填写页面 Key。";

    /**
     * 页面 Key 缺失时的统一提示。
     */
    public static final String PAGE_KEY_MISSING_HINT = "Magpie 管理面需要页面 Key："
            + "把 magpie 启动时打印的链接（http://127.0.0.1:3430/?k=…）整条粘进「网关地址」，"
            + "或在「页面 Key」里填写其中的 k 值。";

    /**
     * 页面 Key 被拒时的统一提示。
     */
    public static final String PAGE_KEY_INVALID_HINT = "Magpie 页面 Key 被拒（401/403）："
            + "magpie 每次启动都会换新 key（除非设置了 MAGPIE_WEB_KEY），请重新复制启动链接里的 k=…";

    /**
     * 管理面内置的签到的上游（magpie 的固定端点，不是通用任务接口）。
     */
    public static final List<String> CHECKIN_VENDORS = List.of("workbuddy", "trae", "minimax", "qoder");

    /**
     * 业务渠道 → magpie provider id 候选（按优先级）。
     *
     * <p>与前端 {@code magpieOwnership.ts} 的 {@code MAGPIE_OWNED_VENDORS} 同源同序：
     * 前端用它定位左栏与「上游」页签，后端用它判断某渠道是否由网关承载、以及该按哪个
     * provider 过滤额度 / 签到。两张表只允许有一份口径，改这里就要改那里。</p>
     */
    public static final Map<String, List<String>> VENDOR_PROVIDERS = Map.ofEntries(
            // 可移交插件的 vendor：插件接管后 provider id 可能带 -ai 后缀
            Map.entry("workbuddy", List.of("workbuddy-ai", "workbuddy")),
            Map.entry("zcode", List.of("zcode")),
            Map.entry("qoder", List.of("qoder")),
            Map.entry("qoder-cn", List.of("qoder-cn")),
            Map.entry("trae", List.of("trae-cn", "trae-global")),
            Map.entry("minimax", List.of("minimax-code", "minimax-code-global")),
            Map.entry("loomy", List.of("loomy", "hub-loomy")),
            Map.entry("lobsterai", List.of("hub-lobsterai")),
            Map.entry("raccoon", List.of("raccoon")),
            // 走 magpie 预设的渠道：不接管登录，只接管上游地址 / key / 模型目录
            Map.entry("opencode", List.of("opencode-zen", "opencode-go", "opencode-zen-free")),
            Map.entry("kilo", List.of("kilo")),
            Map.entry("cline", List.of("cline", "clinepass")),
            Map.entry("gemini", List.of("google", "google-vertex")));

    /**
     * 某业务渠道在 magpie 里对应的 provider id 候选。
     *
     * <p>不在对照表里的渠道按同名处理（如 {@code opencode-go} 这类本身就是 provider id 的）。</p>
     *
     * @param channel 业务渠道标识
     * @return provider id 候选；入参为空返回空列表
     */
    public static List<String> providerIdsOf(String channel) {
        if (isBlank(channel)) {
            return List.of();
        }
        String key = channel.trim().toLowerCase(Locale.ROOT);
        List<String> known = VENDOR_PROVIDERS.get(key);
        return known == null ? List.of(key) : known;
    }

    /**
     * 需要从用户粘贴地址上剥掉的尾巴（按长度从长到短匹配）。
     */
    private static final List<String> TRIM_SUFFIXES = List.of(
            "/v1/chat/completions", "/chat/completions", "/v1/models", "/v1", "/api");

    /**
     * 本机回环地址的 host 白名单（按 {@link URI#getHost()} 的小写形态比较）。
     */
    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "127.0.0.1", "localhost", "::1", "[::1]", "0.0.0.0");

    /**
     * 本机地址缓存的存活时长（毫秒）：网卡地址变化后最多滞后这么久。
     */
    private static final long LOCAL_HOSTS_TTL_MILLIS = 60000L;

    /**
     * 局域网建议地址最多给几个（多网卡时按枚举顺序取前几个）。
     */
    private static final int LAN_URL_LIMIT = 3;

    /**
     * 本机地址缓存（回环 + 全部网卡地址），{@code null} 表示还没算过。
     */
    private static volatile Set<String> cachedLocalHosts;

    /**
     * 本机地址缓存的写入时刻（{@link System#currentTimeMillis()}）。
     */
    private static volatile long cachedLocalHostsAt;

    /**
     * 指定 magpie 本地配置目录的环境变量（优先于用户目录推导）。
     */
    public static final String ENV_CONFIG_DIR = "MAGPIE_CONFIG_DIR";

    /**
     * Windows 用户目录环境变量。
     */
    public static final String ENV_USER_PROFILE = "USERPROFILE";

    /**
     * XDG 配置根环境变量（非 Windows 平台）。
     */
    public static final String ENV_XDG_CONFIG_HOME = "XDG_CONFIG_HOME";

    /**
     * 用户目录系统属性（没有 USERPROFILE / XDG_CONFIG_HOME 时的最后兜底）。
     */
    public static final String PROP_USER_HOME = "user.home";

    /**
     * 用户目录下的配置根目录名。
     */
    private static final String DOT_CONFIG = ".config";

    /**
     * magpie 在配置根下的目录名。
     */
    private static final String MAGPIE_DIR = "magpie";

    /**
     * {@code magpie web} 的页面 Key 文件（{@code key} 字段，每次启动可能变化）。
     */
    private static final String WEB_PAGE_FILE = "web-page.json";

    /**
     * magpie 本地设置文件（网关 key 在其中以 {@code lanKey} 保存）。
     */
    private static final String SETTINGS_FILE = "settings.json";

    /**
     * 页面 Key 在 {@code web-page.json} 里的字段名。
     */
    private static final String FIELD_PAGE_KEY = "key";

    /**
     * 网关 key 在 {@code settings.json} 里的字段名。
     */
    private static final String FIELD_LAN_KEY = "lanKey";

    /**
     * 本地配置文件的读取次数（含首次）：magpie 运行期会反复改写这些文件，
     * 首次解析失败隔一会儿再读一次。
     */
    private static final int FILE_READ_ATTEMPTS = 2;

    /**
     * 本地配置文件重读前的等待（毫秒）。
     */
    private static final long FILE_RETRY_MILLIS = 50L;

    /**
     * key 来源：读自 magpie 本地配置文件。
     */
    public static final String KEY_SOURCE_FILE = "file";

    /**
     * key 来源：连接凭据里配置的。
     */
    public static final String KEY_SOURCE_CREDENTIAL = "credential";

    /**
     * key 来源：两处都没有。
     */
    public static final String KEY_SOURCE_NONE = "none";

    /**
     * 工具类，禁止实例化。
     */
    private MagpieSupport() {
    }

    /**
     * 从凭据里取网关根地址（永远非空）。
     *
     * @param credentials 连接凭据，可为空
     * @return 根地址，形如 {@code http://127.0.0.1:3425}
     */
    public static String baseUrl(Map<String, Object> credentials) {
        return normalize(readString(credentials, "base_url", "baseUrl", "url", "endpoint"));
    }

    /**
     * 从凭据里取网关 key（可能为空串）。
     *
     * <p>凭据里没有配置、且网关地址落在<b>本机</b>（回环或本机任一网卡 IP）时，
     * 退回到 magpie 自己写在 {@code <配置目录>/settings.json} 里的 {@code lanKey}
     * （桌面版落地在磁盘上的真实网关 key）；读不到仍返回空串，由调用方决定用
     * {@link #LOCAL_KEY} 占位。</p>
     *
     * @param credentials 连接凭据，可为空
     * @return 网关 key；未配置且本地读不到时返回空串
     */
    public static String gatewayKey(Map<String, Object> credentials) {
        String key = credentialGatewayKey(credentials);
        if (!key.isEmpty()) {
            return key;
        }
        if (!isLocalEndpoint(baseUrl(credentials))) {
            return "";
        }
        return readConfigString(credentials, SETTINGS_FILE, FIELD_LAN_KEY);
    }

    /**
     * 从凭据里取管理面（页面）地址（永远非空）。
     *
     * <p>没显式配置时按「数据面端口 3425 → 页面 3430、其它端口原样」推导，
     * 兼容反向代理把两个面放在同一个域名下的部署。</p>
     *
     * @param credentials 连接凭据，可为空
     * @return 页面地址，形如 {@code http://127.0.0.1:3430}
     */
    public static String pageUrl(Map<String, Object> credentials) {
        String explicit = readString(credentials,
                "page_url", "pageUrl", "web_url", "webUrl", "admin_url", "adminUrl");
        if (explicit != null && !explicit.isBlank()) {
            return normalizePage(explicit);
        }
        return gatewayToPage(baseUrl(credentials));
    }

    /**
     * 从凭据里取页面 Key（可能为空串）。
     *
     * <p>{@code magpie web} 默认每次启动都换页面 Key，凭据里存下的是旧值；因此网关地址落在
     * <b>本机</b>（回环或本机任一网卡 IP）时<b>优先</b>现读
     * {@code <配置目录>/web-page.json} 的 {@code key}（那才是当前进程认的 key），
     * 文件不存在或解析失败才回落到凭据里的 {@code page_key}。</p>
     *
     * @param credentials 连接凭据，可为空
     * @return 页面 Key；未配置且本地读不到时返回空串
     */
    public static String pageKey(Map<String, Object> credentials) {
        String key = credentialPageKey(credentials);
        if (!isLocalEndpoint(baseUrl(credentials))) {
            return key;
        }
        String fileKey = readConfigString(credentials, WEB_PAGE_FILE, FIELD_PAGE_KEY);
        return fileKey.isEmpty() ? key : fileKey;
    }

    /**
     * 取网关 key / 页面 Key 的来源，供上层展示「这个 key 是从哪儿来的」。
     *
     * <p>判定顺序与 {@link #gatewayKey(Map)} / {@link #pageKey(Map)} 完全一致：
     * 页面 Key 在本机时文件优先，网关 key 则是凭据优先、凭据为空才读本地文件。</p>
     *
     * @param credentials 连接凭据，可为空
     * @param page        true 取页面 Key 的来源，false 取网关 key 的来源
     * @return {@link #KEY_SOURCE_FILE} / {@link #KEY_SOURCE_CREDENTIAL} / {@link #KEY_SOURCE_NONE}
     */
    public static String keySource(Map<String, Object> credentials, boolean page) {
        boolean local = isLocalEndpoint(baseUrl(credentials));
        if (page) {
            if (local && !readConfigString(credentials, WEB_PAGE_FILE, FIELD_PAGE_KEY).isEmpty()) {
                return KEY_SOURCE_FILE;
            }
            return credentialPageKey(credentials).isEmpty() ? KEY_SOURCE_NONE : KEY_SOURCE_CREDENTIAL;
        }
        if (!credentialGatewayKey(credentials).isEmpty()) {
            return KEY_SOURCE_CREDENTIAL;
        }
        if (local && !readConfigString(credentials, SETTINGS_FILE, FIELD_LAN_KEY).isEmpty()) {
            return KEY_SOURCE_FILE;
        }
        return KEY_SOURCE_NONE;
    }

    /**
     * 取 magpie 的本地配置目录（只用于读磁盘，不做 URL 归一）。
     *
     * <p>优先级：凭据里的 {@code config_dir / configDir / configPath}（去空白、去首尾引号）
     * → 环境变量 {@value #ENV_CONFIG_DIR} → {@code %USERPROFILE%\.config\magpie}
     * → {@code $XDG_CONFIG_HOME/magpie} → {@code user.home/.config/magpie}。</p>
     *
     * <p>桌面版 magpie 只监听数据面 {@code :3425}，不提供 {@code :3430} 管理面，
     * 状态只落在这些文件里；本方法给同机回落用，远程网关不适用。</p>
     *
     * @param credentials 连接凭据，可为空
     * @return 配置目录；都取不到时返回空串
     */
    public static String configDir(Map<String, Object> credentials) {
        String explicit = unquote(readString(credentials, "config_dir", "configDir", "configPath"));
        if (!isBlank(explicit)) {
            return explicit;
        }
        String env = unquote(System.getenv(ENV_CONFIG_DIR));
        if (!isBlank(env)) {
            return env;
        }
        String profile = System.getenv(ENV_USER_PROFILE);
        if (!isBlank(profile)) {
            return Paths.get(profile.trim(), DOT_CONFIG, MAGPIE_DIR).toString();
        }
        String xdg = System.getenv(ENV_XDG_CONFIG_HOME);
        if (!isBlank(xdg)) {
            return cutTrailingSeparator(xdg.trim()) + "/" + MAGPIE_DIR;
        }
        String home = System.getProperty(PROP_USER_HOME);
        if (isBlank(home)) {
            return "";
        }
        return cutTrailingSeparator(home.trim()) + "/" + DOT_CONFIG + "/" + MAGPIE_DIR;
    }

    /**
     * 网关地址是否落在本机回环上（只看 {@code 127.0.0.1 / localhost / ::1 / 0.0.0.0}）。
     *
     * <p>只回答「地址形态」；判断「能不能读本机配置目录」请用
     * {@link #isLocalEndpoint(String)} —— 用本机网卡 IP（{@code 192.168.x.x}）访问
     * 同一台 magpie 时，配置文件同样可读。</p>
     *
     * @param baseUrl 网关根地址（可缺 scheme）
     * @return true 表示本机回环
     */
    public static boolean isLoopback(String baseUrl) {
        String host = hostOf(baseUrl);
        return !host.isEmpty() && LOOPBACK_HOSTS.contains(host);
    }

    /**
     * 网关地址是否指向<b>本机</b>（回环，或本机任一网卡上的地址）。
     *
     * <p>这是「能不能读本机 magpie 配置文件」的唯一判据：后端与 magpie 同机时，
     * {@code http://127.0.0.1:3425} 与 {@code http://192.168.50.101:3425} 没有区别，
     * 都能读 {@code web-page.json} / {@code settings.json} / {@code providers.json}。
     * 远程网关返回 false，仍只能走 HTTP + 两把 Key。</p>
     *
     * @param baseUrl 网关根地址（可缺 scheme）
     * @return true 表示本机地址
     */
    public static boolean isLocalEndpoint(String baseUrl) {
        String host = hostOf(baseUrl);
        if (host.isEmpty()) {
            return false;
        }
        return LOOPBACK_HOSTS.contains(host) || localHosts().contains(host);
    }

    /**
     * 给本机网关推荐的局域网地址，形如 {@code http://192.168.50.101:3425}。
     *
     * <p>只在本机地址时给；多网卡时全部列出（最多 {@value #LAN_URL_LIMIT} 个），
     * 由用户挑手机那侧能到的那张网卡。取不到就得空列表，不编一个地址出来。</p>
     *
     * @param baseUrl 网关根地址（用于取 scheme 与端口）
     * @return 局域网地址列表，可能为空
     */
    public static List<String> lanUrls(String baseUrl) {
        if (!isLocalEndpoint(baseUrl)) {
            return List.of();
        }
        URI uri = parse(baseUrl);
        String scheme = uri == null || uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri == null ? -1 : uri.getPort();
        List<String> out = new ArrayList<>(LAN_URL_LIMIT);
        for (String ip : localIpv4()) {
            if (out.size() >= LAN_URL_LIMIT) {
                break;
            }
            out.add(port > 0 ? scheme + "://" + ip + ":" + port : scheme + "://" + ip);
        }
        return out;
    }

    /**
     * 取地址里的 host（小写、去 IPv6 方括号与作用域），解析不了返回空串。
     *
     * @param baseUrl 网关根地址
     * @return 规范化 host
     */
    private static String hostOf(String baseUrl) {
        URI uri = parse(baseUrl);
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        return normalizeHost(uri.getHost());
    }

    /**
     * 规范化 host 形态：去方括号、去 IPv6 作用域（{@code %12}）、转小写。
     *
     * @param host 原始 host
     * @return 规范化 host（输入为空时返回空串）
     */
    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String text = host.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        int zone = text.indexOf('%');
        if (zone >= 0) {
            text = text.substring(0, zone);
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * 本机全部地址（回环 + 各网卡地址），带 {@value #LOCAL_HOSTS_TTL_MILLIS} 毫秒缓存。
     *
     * <p>网卡枚举失败（权限 / 平台差异）时只返回回环集合，异常不冒到请求链路。</p>
     *
     * @return 规范化 host 集合
     */
    private static Set<String> localHosts() {
        long now = System.currentTimeMillis();
        Set<String> cached = cachedLocalHosts;
        if (cached != null && now - cachedLocalHostsAt < LOCAL_HOSTS_TTL_MILLIS) {
            return cached;
        }
        Set<String> hosts = new LinkedHashSet<>(LOOPBACK_HOSTS);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    String host = normalizeHost(addresses.nextElement().getHostAddress());
                    if (!host.isEmpty()) {
                        hosts.add(host);
                    }
                }
            }
        } catch (Exception e) {
            // 取不到网卡列表：只按回环判断，不影响其它链路
        }
        cachedLocalHosts = hosts;
        cachedLocalHostsAt = now;
        return hosts;
    }

    /**
     * 本机可对外使用的 IPv4 列表（排除回环与 {@code 169.254} 链路本地地址）。
     *
     * @return IPv4 列表，可能为空
     */
    private static List<String> localIpv4() {
        List<String> out = new ArrayList<>(LAN_URL_LIMIT);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (!ip.isEmpty() && !out.contains(ip)) {
                        out.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            // 同 localHosts：取不到就返回已收集的部分
        }
        return out;
    }

    /**
     * 数据面请求头（{@code Authorization: Bearer <key>}，key 为空时用本机占位值）。
     *
     * @param gatewayKey 网关 key，可空
     * @return 请求头
     */
    public static Map<String, String> dataHeaders(String gatewayKey) {
        Map<String, String> headers = jsonHeaders();
        headers.put("Authorization", "Bearer " + effectiveKey(gatewayKey));
        return headers;
    }

    /**
     * 基础 JSON 请求头（管理面不加 Bearer：它认 Cookie，不认网关 key）。
     *
     * @return 请求头
     */
    public static Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * 数据面地址归一化：去空白、剥 {@code /v1} 等数据面尾巴、去尾部每秒。
     *
     * @param raw 原始地址
     * @return 根地址；空白时返回 {@link #DEFAULT_BASE_URL}
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        String base = trimTrailingSlash(raw.trim());
        for (String suffix : TRIM_SUFFIXES) {
            if (base.endsWith(suffix)) {
                base = trimTrailingSlash(base.substring(0, base.length() - suffix.length()));
                break;
            }
        }
        return base.isEmpty() ? DEFAULT_BASE_URL : base;
    }

    /**
     * 页面地址归一化：除了 {@link #normalize(String)} 的尾巴，还剥掉启动链接里的查询串。
     *
     * @param raw 原始地址（可带 {@code ?k=…}）
     * @return 页面根地址；空白时返回 {@link #DEFAULT_ADMIN_URL}
     */
    public static String normalizePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ADMIN_URL;
        }
        String base = normalize(stripQuery(raw));
        return base.isBlank() ? DEFAULT_ADMIN_URL : base;
    }

    /**
     * 数据面基址（根地址 + {@code /v1}）。
     *
     * @param baseUrl 根地址
     * @return 数据面基址
     */
    public static String v1Url(String baseUrl) {
        return normalize(baseUrl) + V1;
    }

    /**
     * 生效的网关 key：空串时退化为本机占位值。
     *
     * @param gatewayKey 网关 key，可空
     * @return 非空 key
     */
    public static String effectiveKey(String gatewayKey) {
        return gatewayKey == null || gatewayKey.isBlank() ? LOCAL_KEY : gatewayKey.trim();
    }

    /**
     * 脱敏：保留末四位，其余用星号。
     *
     * @param key 原始 key
     * @return 脱敏文本
     */
    public static String maskKey(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.length() <= 4) {
            return trimmed.isEmpty() ? "" : "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 端口是否为 magpie 页面（管理面）端口。
     *
     * @param url 地址
     * @return true 表示 3430
     */
    public static boolean isPageUrl(String url) {
        return portOf(url) == PAGE_PORT;
    }

    /**
     * 数据面地址 → 页面地址（3425 换 3430，其它端口视为同一个反代入口）。
     *
     * @param baseUrl 数据面地址
     * @return 页面地址
     */
    public static String gatewayToPage(String baseUrl) {
        return swapPort(baseUrl, GATEWAY_PORT, PAGE_PORT, DEFAULT_ADMIN_URL);
    }

    /**
     * 页面地址 → 数据面地址（3430 换 3425，其它端口视为同一个反代入口）。
     *
     * @param pageUrl 页面地址
     * @return 数据面地址
     */
    public static String gatewayForPage(String pageUrl) {
        return swapPort(pageUrl, PAGE_PORT, GATEWAY_PORT, DEFAULT_BASE_URL);
    }

    /**
     * 页面鉴权 Cookie 名（{@code magpie_web_<页面端口>}）。
     *
     * @param pageUrl 页面地址
     * @return Cookie 名
     */
    public static String cookieName(String pageUrl) {
        URI uri = parse(pageUrl);
        int port = uri == null ? -1 : uri.getPort();
        // 反代（https://host、http://host）下取不到显式端口，就按 magpie 默认页面端口命名：
        // 这个值只是「握手拿不到 Set-Cookie」时的兜底，正常情况下用上游下发的那一份。
        return COOKIE_PREFIX + (port > 0 ? port : PAGE_PORT);
    }

    /**
     * 从 magpie 启动链接里取页面 Key（{@code ?k=…}）。
     *
     * @param raw 原始地址，可为空
     * @return 页面 Key；没有 {@code k} 参数时返回 {@code null}
     */
    public static String linkKey(String raw) {
        if (raw == null) {
            return null;
        }
        int queryAt = raw.indexOf('?');
        if (queryAt < 0) {
            return null;
        }
        String query = raw.substring(queryAt + 1);
        int hashAt = query.indexOf('#');
        if (hashAt >= 0) {
            query = query.substring(0, hashAt);
        }
        for (String piece : query.split("&")) {
            int eq = piece.indexOf('=');
            if (eq > 0 && "k".equals(piece.substring(0, eq))) {
                return URLDecoder.decode(piece.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 去掉地址上的查询串 / 锚点。
     *
     * @param raw 原始地址
     * @return 只剩 scheme + host + port + path 的地址
     */
    public static String stripQuery(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        int cut = value.length();
        int queryAt = value.indexOf('?');
        if (queryAt >= 0) {
            cut = Math.min(cut, queryAt);
        }
        int hashAt = value.indexOf('#');
        if (hashAt >= 0) {
            cut = Math.min(cut, hashAt);
        }
        return value.substring(0, cut);
    }

    /**
     * 从凭据里取首个非空字符串字段，供跨模块凭据解析使用。
     *
     * @param source 源 Map
     * @param keys   候选键
     * @return 值；全空返回 {@code null}
     */
    public static String readString(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 判断空串。
     *
     * @param value 字符串
     * @return 空或 null 返回 {@code true}
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 凭据里配置的网关 key（已去空白，缺省空串）。
     *
     * @param credentials 连接凭据，可为空
     * @return 网关 key；未配置时返回空串
     */
    private static String credentialGatewayKey(Map<String, Object> credentials) {
        String key = readString(credentials,
                "gateway_key", "gatewayKey", "api_key", "apiKey", "access_token", "key");
        return key == null ? "" : key.trim();
    }

    /**
     * 凭据里配置的页面 Key（已去空白，缺省空串）。
     *
     * @param credentials 连接凭据，可为空
     * @return 页面 Key；未配置时返回空串
     */
    private static String credentialPageKey(Map<String, Object> credentials) {
        String key = readString(credentials,
                "page_key", "pageKey", "web_key", "webKey", "admin_key", "adminKey");
        return key == null ? "" : key.trim();
    }

    /**
     * 去掉首尾空白与外层成对的单 / 双引号（用户从界面复制路径时常带引号）。
     *
     * @param value 原始值
     * @return 去引号后的值；入参为 null 时返回空串
     */
    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.length() >= 2) {
            char head = result.charAt(0);
            char tail = result.charAt(result.length() - 1);
            if ((head == '"' && tail == '"') || (head == '\'' && tail == '\'')) {
                result = result.substring(1, result.length() - 1).trim();
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 去掉尾部路径分隔符（{@code /} 与 {@code \} 都算）。
     *
     * @param value 路径
     * @return 去尾分隔符后的路径
     */
    private static String cutTrailingSeparator(String value) {
        String result = value;
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last != '/' && last != '\\') {
                break;
            }
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 读 magpie 本地配置文件里的一个字符串字段。
     *
     * <p>只读，不写回；magpie 运行期间会反复改写这些文件，读不到就当没有，绝不抛异常。</p>
     *
     * @param credentials 连接凭据（其中可指定配置目录）
     * @param fileName    配置目录下的固定文件名
     * @param field       要读的字段名
     * @return 字段值；文件不存在 / 解析失败 / 字段为空时返回空串
     */
    private static String readConfigString(Map<String, Object> credentials, String fileName, String field) {
        Map<String, Object> payload = readConfigObject(credentials, fileName);
        if (payload == null) {
            return "";
        }
        Object value = payload.get(field);
        return value instanceof String text ? text.trim() : "";
    }

    /**
     * 读 magpie 本地配置文件并解析成字符串键 Map。
     *
     * <p>解析失败（多半是 magpie 正在写盘）隔 {@link #FILE_RETRY_MILLIS} 毫秒重读一次，
     * 仍失败返回 {@code null}；文件不存在直接返回 {@code null}，不重试。</p>
     *
     * @param credentials 连接凭据（其中可指定配置目录）
     * @param fileName    配置目录下的固定文件名
     * @return 解析结果；不可用时返回 {@code null}
     */
    private static Map<String, Object> readConfigObject(Map<String, Object> credentials, String fileName) {
        String dir = configDir(credentials);
        if (isBlank(dir)) {
            return null;
        }
        Path file = Paths.get(dir, fileName);
        for (int attempt = 0; attempt < FILE_READ_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleepQuietly();
            }
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Map<String, Object> payload = parseConfigObject(file);
            if (payload != null) {
                return payload;
            }
        }
        return null;
    }

    /**
     * 解析一个本地 JSON 文件（对象形态）。
     *
     * @param file 文件
     * @return 字符串键 Map；读取或解析失败返回 {@code null}
     */
    private static Map<String, Object> parseConfigObject(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            Object parsed = Json.fromJson(text, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>(map.size());
                map.forEach((key, value) -> {
                    if (key != null) {
                        out.put(String.valueOf(key), value);
                    }
                });
                return out;
            }
        } catch (Exception e) {
            // 文件正被改写：交给调用方重试一次，两次都失败就当作没有
        }
        return null;
    }

    /**
     * 静默等待一小段时间（解析重试用），被中断时恢复中断标记。
     */
    private static void sleepQuietly() {
        try {
            Thread.sleep(FILE_RETRY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 换端口：显式端口命中 {@code from} 时换成 {@code to}；没有显式端口（反代域名）
     * 或其它端口时原样保留，避免给反代地址硬塞一个 3430。
     *
     * @param url      地址
     * @param from     需要替换的端口
     * @param to       目标端口
     * @param fallback 解析失败时的返回值
     * @return 换端口后的地址
     */
    private static String swapPort(String url, int from, int to, String fallback) {
        URI uri = parse(url);
        if (uri == null || uri.getHost() == null) {
            return fallback;
        }
        int port = uri.getPort();
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        if (port == from) {
            return scheme + "://" + uri.getHost() + ":" + to;
        }
        if (port > 0) {
            return scheme + "://" + uri.getHost() + ":" + port;
        }
        return scheme + "://" + uri.getHost();
    }

    /**
     * 取地址端口（无端口时按 scheme 推导，解析失败返回 -1）。
     *
     * @param url 地址
     * @return 端口；解析失败返回 -1
     */
    private static int portOf(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return -1;
        }
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    /**
     * 宽松解析 URI（补全缺失的 scheme）。
     *
     * @param url 地址
     * @return URI；无法解析返回 {@code null}
     */
    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 去尾部斜杠。
     *
     * @param value 字符串
     * @return 去尾斜杠后的字符串
     */
    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
