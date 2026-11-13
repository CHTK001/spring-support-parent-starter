package com.chua.starter.common.support.tunnel;

import lombok.Data;

/**
 * 内网穿透映射的持久化记录。
 *
 * <p>ngrok 与 Cloudflare Tunnel 两种提供方共用同一份记录结构：字段是两侧能力并集，
 * 各自只读取自己关心的部分（{@code authtoken} 仅 ngrok、{@code token} 仅 Cloudflare）。
 * 这样两条持久化链路可以复用 {@link TunnelFileStore}，页面也只需要维护一套模型。</p>
 *
 * <p>记录本身不落数据库，按 {@code <storeDir>/<id>.json} 一文件一条保存，便于人工查看、
 * 备份与迁移。运行态（是否运行、公网地址、错误）不落盘，见 {@link TunnelView}；
 * 唯一例外是 {@code owner}——它是「当前由哪个实例运行」的所有权标记，
 * 多实例共享同一存储目录时靠它避免互相抢同一域名。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
public class TunnelRecord {

    /**
     * 记录标识（服务端生成的 UUID，同时也是文件名）。
     */
    private String id;

    /**
     * 提供方：{@code ngrok} / {@code cloudflared}，由所属模块写入。
     */
    private String provider;

    /**
     * 映射名称（页面展示用；为空时服务端按「提供方-端口」自动命名）。
     */
    private String name;

    /**
     * 本地待暴露端口，例如 {@code 8082}。
     */
    private int localPort;

    /**
     * 隧道模式。
     * <ul>
     *   <li>ngrok：{@code http} / {@code tcp} / {@code tls}</li>
     *   <li>cloudflared：{@code quick}（免账号随机域名）/ {@code named}（token 命名隧道）</li>
     * </ul>
     */
    private String mode = "http";

    /**
     * 固定域名：ngrok 的 {@code domain} 或 Cloudflare 的 {@code hostname}。
     */
    private String domain;

    /**
     * ngrok TCP 模式的远程地址，形如 {@code 1.tcp.ngrok.io:20000}。
     */
    private String remoteAddress;

    /**
     * HTTP 基础认证，格式 {@code user:password}（仅 ngrok http）。
     */
    private String basicAuth;

    /**
     * ngrok authtoken（为空时回退全局配置 / 环境变量 {@code NGROK_AUTHTOKEN}）。
     */
    private String authtoken;

    /**
     * Cloudflare Zero Trust 隧道 Token（{@code named} 模式使用）。
     */
    private String token;

    /**
     * 隧道元数据，控制台可见（仅 ngrok）。
     */
    private String metadata;

    /**
     * 是否在应用启动时自动拉起该映射。
     */
    private boolean autoStart;

    /**
     * 是否启用。停用的映射不会随自动启动恢复。
     */
    private boolean enabled = true;

    /**
     * 当前运行该隧道的实例控制地址（如 {@code http://127.0.0.1:8083}）。
     *
     * <p>只由启动成功的实例写入、停止时清空，用户编辑时忽略。多实例共享同一
     * 存储目录时，其他实例据此判断「这条隧道正被谁运行着」，避免两个实例
     * 同时拉起同一条隧道（同一域名互抢导致 ERR_NGROK_334 之类冲突）。</p>
     */
    private String owner;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间（毫秒时间戳）。
     */
    private long createdAt;

    /**
     * 最后更新时间（毫秒时间戳）。
     */
    private long updatedAt;
}
