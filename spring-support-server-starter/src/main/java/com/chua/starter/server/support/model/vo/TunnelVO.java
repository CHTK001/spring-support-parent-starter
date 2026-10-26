package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 主机隧道视图对象
 *
 * <p>字段与前端 {@code ScTunnelWorkbench.vue} 的 Tunnel 接口对齐：
 * id / name / dir（forward|reverse）/ proto / status（running|stopped|fail）/
 * from / to / via，外加建立表单的监听/目标/回流字段。后端响应经
 * {@code from}/{@code to} 描述转发路径，前端直接展示。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "主机隧道")
public class TunnelVO {

    /**
     * 隧道 id（进程内自增）
     */
    @Schema(description = "隧道 id")
    private Long id;

    /**
     * 隧道名称
     */
    @Schema(description = "隧道名称", example = "开发库直连")
    private String name;

    /**
     * 方向：forward 正向（ssh -L）/ reverse 反向（ssh -R）
     */
    @Schema(description = "方向", example = "forward")
    private String dir;

    /**
     * 协议（TCP/HTTP，展示用）
     */
    @Schema(description = "协议", example = "TCP")
    private String proto;

    /**
     * 运行态：running / stopped / fail
     */
    @Schema(description = "运行态", example = "running")
    private String status;

    /**
     * 转发源（正向: 本机监听地址:端口；反向: 远端监听地址:端口）
     */
    @Schema(description = "转发源", example = "127.0.0.1:3306")
    private String from;

    /**
     * 转发目标（正向: 远端目标:端口；反向: 本机回流:端口）
     */
    @Schema(description = "转发目标", example = "10.0.2.15:3306")
    private String to;

    /**
     * 经哪台主机（展示用）
     */
    @Schema(description = "经由主机")
    private String via;

    /**
     * 本机监听地址（建立表单；正向必填）
     */
    @Schema(description = "本机监听地址", example = "127.0.0.1")
    private String listen;

    /**
     * 监听/远端端口（建立表单）
     */
    @Schema(description = "监听端口", example = "3306")
    private String port;

    /**
     * 目标主机（正向；ssh -L 的远端目标）
     */
    @Schema(description = "目标主机", example = "10.0.2.15")
    private String targetHost;

    /**
     * 目标端口（正向）
     */
    @Schema(description = "目标端口", example = "3306")
    private String targetPort;

    /**
     * 回流主机（反向；ssh -R 接回本机）
     */
    @Schema(description = "回流主机", example = "192.168.1.21")
    private String backHost;

    /**
     * 回流端口（反向）
     */
    @Schema(description = "回流端口", example = "3000")
    private String backPort;

    /**
     * 登录身份名（建立表单，展示用）
     */
    @Schema(description = "登录身份", example = "deploy（SSH 密钥）")
    private String identity;
}
