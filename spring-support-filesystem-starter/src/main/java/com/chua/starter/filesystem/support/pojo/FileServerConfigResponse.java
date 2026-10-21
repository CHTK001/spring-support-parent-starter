package com.chua.starter.filesystem.support.pojo;

import lombok.Data;

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
@Data
public class FileServerConfigResponse {

    /**
     * 部署形态：embedded / standalone
     */
    private String mode;

    /**
     * 文件服务器地址：embedded=相对路径，standalone=完整 http://host:port
     */
    private String server;

    /**
     * 内置形态路径前缀（如 /fs）
     */
    private String pathPrefix;

    /**
     * 默认存储桶（默认 provider 名）
     */
    private String bucket;

    /**
     * 构造运行期配置。
     *
     * @param mode       部署形态
     * @param server     文件服务器地址
     * @param pathPrefix 内置形态路径前缀
     * @param bucket     默认存储桶
     */
    public FileServerConfigResponse(String mode, String server, String pathPrefix, String bucket) {
        this.mode = mode;
        this.server = server;
        this.pathPrefix = pathPrefix;
        this.bucket = bucket;
    }
}
