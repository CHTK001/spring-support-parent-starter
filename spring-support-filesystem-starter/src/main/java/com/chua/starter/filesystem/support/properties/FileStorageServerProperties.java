package com.chua.starter.filesystem.support.properties;

import com.chua.common.support.network.server.ServerSetting;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储服务器配置属性。
 *
 * <p>支持两种部署形态（{@link #mode}）：</p>
 * <ul>
 *   <li>{@link Mode#EMBEDDED}（默认）—— 挂进宿主 Spring 应用，走应用端口 + {@link #pathPrefix}；</li>
 *   <li>{@link Mode#STANDALONE} —— 另起独立 HTTP 服务，监听 {@link #host}:{@link #port}。</li>
 * </ul>
 *
 * <p>配置示例:
 * <pre>
 * plugin:
 *   filesystem:
 *     storage-server:
 *       enable: true          # 是否启用文件服务器
 *       mode: embedded        # 部署形态: embedded(默认) / standalone
 *       path-prefix: /fs      # 内置形态的路径前缀 → /fs/{桶}/{键}
 *       host: 0.0.0.0         # 监听地址（仅 standalone）
 *       port: 18082           # 监听端口（仅 standalone，与前端 DEFAULT_SERVER 对齐）
 *       type: jdk             # 服务器引擎: jdk/nio/aio（仅 standalone）
 *       open-preview: true    # 允许 ?preview 内联预览
 *       open-download: true   # 允许 ?download 附件下载
 *       open-range: true      # 允许 Range 分段下载
 *       layout: true          # 目录列表页
 *       layout-type: SIMPLE   # 列表布局: SIMPLE/SYSTEM
 *       open-watermark: false # 水印响应头开关
 *       open-webjars: true    # webjars 静态资源（预览页依赖）
 *       open-remote-file: false
 *       third-url-preview: NONE
 *       image-filter: NONE
 *       private-buckets: ""   # 留空回落 plugin.oauth.file-storage.private-buckets
 *       frame-ancestors: "*"  # CSP frame-ancestors
 *       cors:
 *         enable: true
 *         allowed-origins: "*"
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = FileStorageServerProperties.PRE)
public class FileStorageServerProperties {

    /**
     * 配置属性前缀
     */
    public static final String PRE = "plugin.filesystem.storage-server";

    /**
     * 是否随应用启动文件服务器。
     *
     * <p>默认值：{@code true}。注意外层总开关 {@code plugin.filesystem.enable} 默认
     * {@code false}，需两者同时开启文件服务器才会装配。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       enable: true
     * }</pre>
     */
    private boolean enable = true;

    /**
     * 监听地址（仅 {@link Mode#STANDALONE} 形态生效）。
     *
     * <p>默认值：{@code "0.0.0.0"}（监听所有网卡；生产建议显式绑定内网地址）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       host: 0.0.0.0
     * }</pre>
     */
    private String host = "0.0.0.0";

    /**
     * 对外可达地址（用于 /v2/setting/default 下发的文件服务器地址）。
     *
     * <p>默认值：无（null）；留空时回退 {@link #host}（0.0.0.0 归一为 127.0.0.1）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       public-host: 192.168.1.10
     * }</pre>
     */
    private String publicHost;

    /**
     * 监听端口（仅 {@link Mode#STANDALONE} 形态生效；默认与前端文件服务器页 DEFAULT_SERVER 对齐）。
     *
     * <p>默认值：{@code 18082}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       port: 18082
     * }</pre>
     *
     * <p>前端 {@code pages/filesystem/src/api/filestorageApi.ts#DEFAULT_SERVER} 缺省
     * http://127.0.0.1:18082，后端 CORS 默认开启，二者同端口同源即可直连。</p>
     */
    // [P3C A20 豁免]
    private int port = 18082;

    /**
     * 部署形态（默认 {@link Mode#EMBEDDED}）。
     *
     * <p>默认值：{@code EMBEDDED}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       mode: embedded
     * }</pre>
     *
     * <p><b>EMBEDDED</b>：把文件存储的完整协议（{@code /{桶}/{键}}、列表、预览、下载、
     * Range、系统配置等）作为 Servlet 过滤器挂进宿主 Spring 应用，走应用自己的端口，
     * 对外地址形如 {@code http://host:应用端口}{@link #pathPrefix}/{桶}/{键}。</p>
     *
     * <p><b>STANDALONE</b>：由 {@code FileStorageServerLauncher} 另起一个独立 HTTP 服务，
     * 监听 {@link #host}:{@link #port}，路径不带前缀（{@code /{桶}/{键}}）。</p>
     */
    private Mode mode = Mode.EMBEDDED;

    /**
     * 内置形态的路径前缀。
     *
     * <p>默认值：{@code "/fs"}；规范化规则见 {@link #normalizedPathPrefix()}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       path-prefix: /fs
     * }</pre>
     *
     * <p>用于与应用自身的接口空间隔离：{@code /fs/local/a.txt} 在过滤器内会被还原成
     * {@code /local/a.txt}，因此前端只把「文件服务器地址」配成
     * {@code http://host:应用端口/fs} 即可，其余 URL 规则与独立服务完全一致。</p>
     *
     * <p>允许 {@code /} 或空串（表示占用根路径，可能与应用路由冲突，需自行评估）。</p>
     */
    private String pathPrefix = "/fs";

    /**
     * 服务器引擎：jdk / nio / aio（仅 {@link Mode#STANDALONE} 形态生效）。
     *
     * <p>默认值：{@code "jdk"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       type: jdk
     * }</pre>
     */
    private String type = "jdk";

    /**
     * 允许内联预览（?preview）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-preview: true
     * }</pre>
     */
    private boolean openPreview = true;

    /**
     * 允许附件下载（?download）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-download: true
     * }</pre>
     */
    private boolean openDownload = true;

    /**
     * 允许 Range 分段下载。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-range: true
     * }</pre>
     */
    private boolean openRange = true;

    /**
     * 启用水印响应头。
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-watermark: false
     * }</pre>
     */
    private boolean openWatermark = false;

    /**
     * 启用 webjars 静态资源（预览页依赖）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-webjars: true
     * }</pre>
     */
    private boolean openWebjars = true;

    /**
     * 允许远程文件透传（http(s) 开头的对象键）。
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-remote-file: false
     * }</pre>
     */
    private boolean openRemoteFile = false;

    /**
     * 三方 URL 预览能力（取值来自 {@code FileStorageUrlPreviewProvider} SPI 实现名，NONE 表示关闭）。
     *
     * <p>默认值：{@code "NONE"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       third-url-preview: NONE
     * }</pre>
     *
     * <p>只有选择到声明 {@code supportsUrlPreview()} 的实现（内置为 {@code SERVER}，即后台拉取）
     * 时，{@code ?url=<base64(http(s)://…)>} 才会被放行。</p>
     */
    private String thirdUrlPreview = "NONE";

    /**
     * 是否开启目录文件列表页面。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       layout: true
     * }</pre>
     */
    private boolean layout = true;

    /**
     * 文件列表布局类型：SIMPLE-简单文件列表树 / SYSTEM-资源管理器风格。
     *
     * <p>默认值：{@code "SIMPLE"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       layout-type: SIMPLE
     * }</pre>
     */
    private String layoutType = "SIMPLE";

    /**
     * 水印文本（{@code open-watermark: true} 时随响应头下发）。
     *
     * <p>默认值：无（null）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       watermark: internal-use
     * }</pre>
     */
    private String watermark;

    /**
     * 图片滤镜 SPI 名称（NONE 表示原图）。
     *
     * <p>默认值：{@code "NONE"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       image-filter: NONE
     * }</pre>
     */
    private String imageFilter = "NONE";

    /**
     * URL 参数能力 SPI 实现名（{@code FileStorageFileSetting}，默认 {@code jdk}）。
     *
     * <p>默认值：{@code "jdk"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       file-setting-key: jdk
     * }</pre>
     *
     * <p>决定 {@code ?size=100x100&format=webp} 这类文件级参数由哪个实现解析；
     * 管理界面从后台能力接口枚举可选项。</p>
     */
    private String fileSettingKey = "jdk";

    /**
     * 是否启用路径穿越防护（默认开启：拦截 ..、反斜杠、NUL 等穿越请求，并筛查文件动作参数）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       open-path-guard: true
     * }</pre>
     */
    private boolean openPathGuard = true;

    /**
     * 是否启用全局图片滤镜链（默认关闭；开启后每次图片请求按 filterChain 顺序施加滤镜）。
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       image-filter-enabled: false
     * }</pre>
     */
    private boolean imageFilterEnabled = false;

    /**
     * 全局图片滤镜链（紧凑串 {@code id:key=value;id2}；id 取自后台 FileStorageFilterSetting SPI 能力）。
     *
     * <p>默认值：无（null，不施加滤镜链）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       filter-chain: "id:key=value;id2"
     * }</pre>
     */
    private String filterChain;

    /**
     * 私有桶（逗号分隔，provider 名；{@code *} 表示全部私有）。
     *
     * <p>默认值：无（null）；留空时回退环境配置
     * {@code plugin.oauth.file-storage.private-buckets}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       private-buckets: local
     * }</pre>
     * <p>启动时作为 OAuth 鉴权过滤器（oauth 模块，反射挂载）的初始私有桶；
     * 运行期可在文件服务器页「系统配置」热更新（写入 FileStorageSetting.privateBuckets）。
     * 留空时回退环境配置 {@code plugin.oauth.file-storage.private-buckets}。</p>
     */
    private String privateBuckets;

    /**
     * 允许嵌入本文件服务器的来源（CSP {@code frame-ancestors}）。
     *
     * <p>默认值：{@code "*"}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       frame-ancestors: "*"
     * }</pre>
     * <p>文件服务器预览统一走 iframe，需允许被前端页面跨源嵌入；默认 {@code *}。
     * 该值同时用于关闭 X-Frame-Options（避免 DENY 阻断 iframe 渲染）。</p>
     */
    private String frameAncestors = "*";

    /**
     * 跨域配置（默认开启）。
     *
     * <p>默认值：见 {@link Cors} 各字段。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storage-server:
     *       cors:
     *         enable: true
     *         allowed-origins: "*"
     * }</pre>
     *
     * <p>文件服务器是独立端口的网关（见 {@link #port}），前端页面基本都是跨源访问它，
     * 因此默认开启 CORS。需要收紧时把 {@code enable} 置 false，
     * 或把 {@code allowedOrigins} 从 {@code *} 收成来源白名单。</p>
     */
    private Cors cors = new Cors();

    /**
     * 部署形态。
     */
    public enum Mode {
        /**
         * 内置到宿主 Spring 应用（Servlet 过滤器，走应用端口 + 路径前缀）
         */
        EMBEDDED,
        /**
         * 独立 HTTP 服务（单独监听 {@code host:port}）
         */
        STANDALONE
    }

    /**
     * 是否独立服务形态。
     *
     * @return true 表示由启动器另起独立 HTTP 服务
     */
    public boolean isStandalone() {
        return mode == Mode.STANDALONE;
    }

    /**
     * 是否内置形态（未配置时按内置处理）。
     *
     * @return true 表示挂进宿主应用
     */
    public boolean isEmbedded() {
        return mode != Mode.STANDALONE;
    }

    /**
     * 规范化后的路径前缀：保证以 {@code /} 开头、不以 {@code /} 结尾；根路径返回空串。
     *
     * @return 规范化前缀（如 {@code /fs}），或空串表示根路径
     */
    public String normalizedPathPrefix() {
        String prefix = pathPrefix == null ? "" : pathPrefix.trim();
        if (prefix.isEmpty() || "/".equals(prefix)) {
            return "";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    /**
     * 跨域（CORS）配置项。
     */
    @Data
    public static class Cors {

        /**
         * 是否开启 CORS。
         *
         * <p>默认值：{@code true}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         enable: true
         * }</pre>
         */
        private boolean enable = true;

        /**
         * 允许的来源，逗号分隔；{@code *} 表示任意来源。
         *
         * <p>默认值：{@code "*"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         allowed-origins: "*"
         * }</pre>
         *
         * <p>注意：开启 {@link #allowCredentials} 后，即使是 {@code *} 也不会字面返回通配符，
         * 而是回显实际请求来源——规范禁止「凭据 + 通配来源」并用。</p>
         */
        private String allowedOrigins = "*";

        /**
         * 是否允许携带凭据（Cookie / Authorization）。
         *
         * <p>默认值：{@code true}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         allow-credentials: true
         * }</pre>
         *
         * <p>预览页会以 {@code credentials:'include'} 发起跨域 fetch，此时浏览器强制要求
         * 响应带 {@code Access-Control-Allow-Credentials: true}，缺失会导致整个响应不可读。</p>
         */
        private boolean allowCredentials = true;

        /**
         * 允许的请求头。
         *
         * <p>默认值：{@code "Content-Type,Authorization,x-oauth-token,"
         * "X-Requested-With,X-Trace-Id,X-Req-Fingerprint"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         allowed-headers: "Content-Type,Authorization,x-oauth-token"
         * }</pre>
         *
         * <p><b>必须与前端实际发出的自定义头一致</b>：跨域预检时浏览器会把
         * {@code Access-Control-Request-Headers} 里的每个头与本列表比对，缺一个就整条预检失败
         * （控制台报 {@code Request header field xxx is not allowed by
         * Access-Control-Allow-Headers in preflight response}），业务请求根本发不出去。
         * 本系统 axios 拦截器会给<b>每个</b>请求附加两个第一方头
         * （见前端 {@code packages/utils/src/http.ts} 的
         * {@code FIRST_PARTY_HEADER_A = "X-Trace-Id"} 与
         * {@code FIRST_PARTY_HEADER_B = "X-Requested-With"}），
         * 另由加密管线附加 {@code X-Req-Fingerprint}。三者缺一不可。</p>
         *
         * <p>{@code x-oauth-token} 是文件服务器的**默认令牌入口**（服务端解析顺序为
         * 自定义头 → Authorization Bearer → 查询参数 → Cookie）。前端 {@code storageHttp()}
         * 与 {@code putObject()} 都会带这个头，跨域下它同样会进预检，漏了会让整个文件管理页
         * 的请求在预检阶段被拦掉。查询参数形式仍保留，供 img / iframe / 下载链接使用。</p>
         */
        private String allowedHeaders = "Content-Type,Authorization,x-oauth-token,"
                + "X-Requested-With,X-Trace-Id,X-Req-Fingerprint";

        /**
         * 允许前端 JavaScript 读取的响应头（跨域下自定义头默认不可读）。
         *
         * <p>默认值：{@code "Content-Disposition,Content-Range,Accept-Ranges,"
         * "X-FileStorage-Preview,X-FileStorage-Client-Renderer,X-FileStorage-Convert,"
         * "X-FileStorage-ImageOp,X-FileStorage-ImageOp-Reason"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         expose-headers: "Content-Disposition,Content-Range"
         * }</pre>
         */
        private String exposeHeaders = "Content-Disposition,Content-Range,Accept-Ranges,"
                + "X-FileStorage-Preview,X-FileStorage-Client-Renderer,"
                + "X-FileStorage-Convert,X-FileStorage-ImageOp,X-FileStorage-ImageOp-Reason";

        /**
         * 允许的请求方法。
         *
         * <p>默认值：{@code "GET,HEAD,POST,PUT,DELETE,PATCH,OPTIONS"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storage-server:
         *       cors:
         *         allowed-methods: "GET,HEAD,POST,PUT,DELETE,PATCH,OPTIONS"
         * }</pre>
         */
        private String allowedMethods = "GET,HEAD,POST,PUT,DELETE,PATCH,OPTIONS";

        /**
         * 将本配置写入服务器 setting 的 CORS 段。
         *
         * <p>独立文件服务（{@code FileStorageServerLauncher}）与服务管理节点
         * （{@code ServerNodeRegistry}，file-storage 过滤器）共用本方法，
         * 保证两条装配路径的跨域语义一致：谁改默认值，两边同时生效。</p>
         *
         * @param target 目标 CORS 配置（服务器 setting 的引用，内置 CorsFilter 每请求实时读取）
         */
        public void applyTo(ServerSetting.CorsConfig target) {
            if (target == null) {
                return;
            }
            target.setAllowOrigin(enable);
            target.setAllowedOrigins(allowedOrigins);
            target.setAllowedMethods(allowedMethods);
            target.setAllowedHeaders(allowedHeaders);
            target.setExposeHeaders(exposeHeaders);
            target.setAllowCredentials(allowCredentials);
        }
    }
}
