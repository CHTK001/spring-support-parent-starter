package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nginx 站点（一个 server 块）聚合视图。
 *
 * <p>面向「按站点而不是按文件」来组织配置。运维真正关心的是「我那几个域名分别配了什么」，
 * 而不是 {@code /etc/nginx/conf.d/} 下有哪些文件。因此把散落在多个文件里的所有
 * {@code server} 块抽平成一张站点清单，每个站点带上定位它所需的
 * {@link #file} + {@link #nodePath}，点进去就能只改这一个块。</p>
 *
 * <p>{@link #nodePath} 是该 server 块在「所属文件的语法树」中的路径标识（形如 {@code 0/1/0}），
 * 与 {@code /config/tree} 返回的 id 同一套坐标，因此前端不需要为站点视图单独实现一套定位逻辑。</p>
 *
 * @param nodePath        该 server 块在其所属文件语法树中的路径标识
 * @param file            所属配置文件绝对路径
 * @param serverNames     server_name 列表，为空表示未配置（会匹配任意域名）
 * @param displayName     展示名：取第一个 server_name，没有则用「(未命名站点)」
 * @param listenPorts     监听的端口列表
 * @param https           是否启用 HTTPS（任一 listen 带 ssl 参数）
 * @param defaultServer   是否是兜底 server（server_name 为 _ 或没有 server_name）
 * @param rootDir         静态根目录，未配置为 null
 * @param indexFiles      索引文件列表
 * @param proxyTargets    反向代理目标列表
 * @param locationCount   location 块数量
 * @param upstreamRefs    引用的 upstream 名字列表
 * @param hasCertificate  是否配置了证书
 * @param warnings        该站点的可疑之处（只读提示）
 * @author CH
 * @since 4.0.0.42
 */
public record NginxSiteVO(
        String nodePath,
        String file,
        List<String> serverNames,
        String displayName,
        List<Integer> listenPorts,
        boolean https,
        boolean defaultServer,
        String rootDir,
        List<String> indexFiles,
        List<String> proxyTargets,
        int locationCount,
        List<String> upstreamRefs,
        boolean hasCertificate,
        List<String> warnings
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param nodePath        该 server 块在其所属文件语法树中的路径标识
     * @param file            所属配置文件绝对路径
     * @param serverNames     server_name 列表，为空表示未配置（会匹配任意域名）
     * @param displayName     展示名：取第一个 server_name，没有则用「(未命名站点)」
     * @param listenPorts     监听的端口列表
     * @param https           是否启用 HTTPS（任一 listen 带 ssl 参数）
     * @param defaultServer   是否是兜底 server（server_name 为 _ 或没有 server_name）
     * @param rootDir         静态根目录，未配置为 null
     * @param indexFiles      索引文件列表
     * @param proxyTargets    反向代理目标列表
     * @param locationCount   location 块数量
     * @param upstreamRefs    引用的 upstream 名字列表
     * @param hasCertificate  是否配置了证书
     * @param warnings        该站点的可疑之处（只读提示）
     */
    public NginxSiteVO {
        serverNames = List.copyOf(Objects.requireNonNull(serverNames, "serverNames 不能为 null"));
        listenPorts = List.copyOf(Objects.requireNonNull(listenPorts, "listenPorts 不能为 null"));
        indexFiles = List.copyOf(Objects.requireNonNull(indexFiles, "indexFiles 不能为 null"));
        proxyTargets = List.copyOf(Objects.requireNonNull(proxyTargets, "proxyTargets 不能为 null"));
        upstreamRefs = List.copyOf(Objects.requireNonNull(upstreamRefs, "upstreamRefs 不能为 null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings 不能为 null"));
    }
}
