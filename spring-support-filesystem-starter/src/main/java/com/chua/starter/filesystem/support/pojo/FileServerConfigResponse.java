package com.chua.starter.filesystem.support.pojo;

/**
 * 文件服务器运行期配置（前端进入文件管理页前拉取）。
 *
 * <p>承载「文件服务器当前部署形态与连接地址」：前端先请求本接口，
 * 再根据返回的 {@code mode} 决定如何拼文件服务地址，而不是写死独立端口。</p>
 *
 * <p><b>模型（与 /v2/setting/default 下发一致）</b>：</p>
 * <ul>
 *   <li>{@code mode=embedded}（默认）：{@code server} 是应用内相对路径
 *       （contextPath + path-prefix，如 {@code /fs} 或 {@code /system/api/fs}），
 *       由前端用当前页面 origin 拼出完整地址；</li>
 *   <li>{@code mode=standalone}：{@code server} 是完整 {@code http://host:port}，
 *       前端直接使用。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public record FileServerConfigResponse(

    /**
     * 部署形态：embedded / standalone
     */
    String mode,

    /**
     * 文件服务器地址：embedded=相对路径，standalone=完整 http://host:port
     */
    String server,

    /**
     * 内置形态路径前缀（如 /fs）
     */
    String pathPrefix,

    /**
     * 默认存储桶（默认 provider 名）
     */
    String bucket) {
}
