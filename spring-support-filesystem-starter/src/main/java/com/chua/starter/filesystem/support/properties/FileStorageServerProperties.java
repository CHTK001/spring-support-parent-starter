package com.chua.starter.filesystem.support.properties;

import com.chua.common.support.network.server.ServerSetting;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储服务器配置属性（随应用自动启动的独立文件存储网关）。
 *
 * <p>配置示例:
 * <pre>
 * plugin:
 *   filesystem:
 *     storage-server:
 *       enable: true          # 是否随应用启动文件服务器
 *       host: 0.0.0.0         # 监听地址
 *       port: 18082           # 监听端口（与前端"文件服务器"页面 DEFAULT_SERVER 对齐）
 *       type: jdk             # 服务器引擎: jdk/nio/aio
 *       open-preview: true    # 允许 ?preview 内联预览
 *       open-download: true   # 允许 ?download 附件下载
 *       open-range: true      # 允许 Range 分段下载
 *       layout: true          # 目录列表页
 *       layout-type: SIMPLE   # 列表布局: SIMPLE/SYSTEM
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
     * 是否随应用启动文件服务器
     */
    private boolean enable = true;

    /**
     * 监听地址
     */
    private String host = "0.0.0.0";

    /**
     * 对外可达地址（用于 /v2/setting/default 下发的文件服务器地址）；
     * 留空时回退 {@link #host}（0.0.0.0 归一为 127.0.0.1）。
     */
    private String publicHost;

    /**
     * 监听端口（默认与前端文件服务器页 DEFAULT_SERVER 对齐）
     *
     * <p>前端 {@code pages/filesystem/src/api/filestorageApi.ts#DEFAULT_SERVER} 缺省
     * http://127.0.0.1:18082，后端 CORS 默认开启，二者同端口同源即可直连。</p>
     */
    private int port = 18082;

    /**
     * 服务器引擎：jdk / nio / aio
     */
    private String type = "jdk";

    /**
     * 允许内联预览（?preview）
     */
    private boolean openPreview = true;

    /**
     * 允许附件下载（?download）
     */
    private boolean openDownload = true;

    /**
     * 允许 Range 分段下载
     */
    private boolean openRange = true;

    /**
     * 启用水印响应头
     */
    private boolean openWatermark = false;

    /**
     * 启用 webjars 静态资源（预览页依赖）
     */
    private boolean openWebjars = true;

    /**
     * 允许远程文件透传（http(s) 开头的对象键）
     */
    private boolean openRemoteFile = false;

    /**
     * 开启三方 URL 预览（{@code ?url=<base64(http(s)://…)>}，拉取远端内容走预览链路）
     */
    private boolean openThirdPreview = false;

    /**
     * 是否开启目录文件列表页面
     */
    private boolean layout = true;

    /**
     * 文件列表布局类型：SIMPLE-简单文件列表树 / SYSTEM-资源管理器风格
     */
    private String layoutType = "SIMPLE";

    /**
     * 水印文本
     */
    private String watermark;

    /**
     * 图片滤镜 SPI 名称（NONE 表示原图）
     */
    private String imageFilter = "NONE";

    /**
     * 是否启用路径穿越防护（默认开启：拦截 ..、反斜杠、NUL 等穿越请求，并筛查文件动作参数）
     */
    private boolean openPathGuard = true;

    /**
     * 是否启用全局图片滤镜链（默认关闭；开启后每次图片请求按 filterChain 顺序施加滤镜）
     */
    private boolean imageFilterEnabled = false;

    /**
     * 全局图片滤镜链（紧凑串 {@code id:key=value;id2}；id 取自后台 FileStorageFilterSetting SPI 能力）
     */
    private String filterChain;

    /**
     * 私有桶（逗号分隔，provider 名；{@code *} 表示全部私有）。
     * <p>启动时作为 OAuth 鉴权过滤器（oauth 模块，反射挂载）的初始私有桶；
     * 运行期可在文件服务器页「系统配置」热更新（写入 FileStorageSetting.privateBuckets）。
     * 留空时回退环境配置 {@code plugin.oauth.file-storage.private-buckets}。</p>
     */
    private String privateBuckets;

    /**
     * 允许嵌入本文件服务器的来源（CSP {@code frame-ancestors}）。
     * <p>文件服务器预览统一走 iframe，需允许被前端页面跨源嵌入；默认 {@code *}。
     * 该值同时用于关闭 X-Frame-Options（避免 DENY 阻断 iframe 渲染）。</p>
     */
    private String frameAncestors = "*";

    /**
     * 跨域配置（默认开启）。
     *
     * <p>文件服务器是独立端口的网关（见 {@link #port}），前端页面基本都是跨源访问它，
     * 因此默认开启 CORS。需要收紧时把 {@code enable} 置 false，
     * 或把 {@code allowedOrigins} 从 {@code *} 收成来源白名单。</p>
     */
    private Cors cors = new Cors();

    /**
     * 跨域（CORS）配置项。
     */
    @Data
    public static class Cors {

        /**
         * 是否开启 CORS（默认开启）
         */
        private boolean enable = true;

        /**
         * 允许的来源，逗号分隔；{@code *} 表示任意来源。
         *
         * <p>注意：开启 {@link #allowCredentials} 后，即使是 {@code *} 也不会字面返回通配符，
         * 而是回显实际请求来源——规范禁止「凭据 + 通配来源」并用。</p>
         */
        private String allowedOrigins = "*";

        /**
         * 是否允许携带凭据（Cookie / Authorization）。默认开启。
         *
         * <p>预览页会以 {@code credentials:'include'} 发起跨域 fetch，此时浏览器强制要求
         * 响应带 {@code Access-Control-Allow-Credentials: true}，缺失会导致整个响应不可读。</p>
         */
        private boolean allowCredentials = true;

        /**
         * 允许的请求头
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
         * 允许前端 JavaScript 读取的响应头（跨域下自定义头默认不可读）
         */
        private String exposeHeaders = "Content-Disposition,Content-Range,Accept-Ranges,"
                + "X-FileStorage-Preview,X-FileStorage-Client-Renderer,"
                + "X-FileStorage-Convert,X-FileStorage-ImageOp,X-FileStorage-ImageOp-Reason";

        /**
         * 允许的请求方法
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
