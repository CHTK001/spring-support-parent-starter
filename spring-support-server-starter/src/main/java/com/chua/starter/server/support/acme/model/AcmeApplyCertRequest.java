package com.chua.starter.server.support.acme.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 申请证书请求，包含账户、域名（主域名与 SAN）、挑战类型、部署模式及 SSH 远程部署信息。
 *
 * @param acmeAccountId ACME 账户 标识，关联已保存的 ACME 账户
 * @param primaryDomain 主域名（必填） <p>域名格式要求：</p> <ul>
 *                      <li>普通域名：example.com、www.example.com</li>
 *                      <li>通配符域名：*.example.com（仅支持 DNS-01
 *                      验证）</li> </ul> <p>域名注册商推荐：</p> <ul>
 *                      <li>国内：<a
 *                      href="https://wanwang.aliyun.com/"阿里云万网</a>、<a
 *                      href="https://dnspod.cloud.tencent.com/"腾讯云DNSPod</a></li> <li>国际：<a
 *                      href="https://www.namesilo.com/">NameSilo</a>、<a
 *                      href="https://www.cloudflare.com/">Cloudflare</a>、<a
 *                      href="https://www.godaddy.com/">GoDaddy</a></li> </ul>
 * @param sanDomains 备用域名列表（SAN，可选）
 *                   <p>用于一张证书保护多个域名，格式同
 *                   主域名</p> <p>示例：["www.example.com", "api.example.com",
 *                   "admin.example.com"]</p>
 * @param challengeType 挑战类型，取值 HTTP-01 或
 *                      DNS-01，通配符域名仅支持 DNS-01
 * @param deployMode 部署模式，取值
 *                   本地（本机）/ssh（远程）/manual（手动）
 *                   ，默认 manual
 * @param webrootPath webroot 验证路径，如 /var/www/HTML，本机/SSH
 *                    模式时使用
 * @param sshHost SSH 远程部署相关字段：SSH 主机地址
 * @param sshPort SSH 端口，默认 22
 * @param sshUsername SSH 用户名
 * @param sshPassword SSH 密码
 *
 * @since 2025-10-17
 * @author CH
 */
@ApiModel(description = "申请证书请求")
@Schema(description = "申请证书请求")
public record AcmeApplyCertRequest(
        @ApiModelProperty("ACME账户ID") @Schema(description = "ACME账户ID") Integer acmeAccountId,
        @ApiModelProperty(value = "主域名", notes = "支持普通域名(example.com)和通配符域名(*.example.com)，通配符仅支持DNS-01验证")
        @Schema(description = "主域名。支持普通域名(example.com)和通配符域名(*.example.com)，通配符仅支持DNS-01验证。" +
            "域名注册商推荐: 阿里云万网(https://wanwang.aliyun.com)、腾讯云DNSPod(https://dnspod.cloud.tencent.com)、" +
            "NameSilo(https://www.namesilo.com)、Cloudflare(https://www.cloudflare.com)")
        String primaryDomain,
        @ApiModelProperty(value = "备用域名列表(SAN)", notes = "可选，用于一张证书保护多个域名，如 www.example.com, api.example.com")
        @Schema(description = "备用域名列表(SAN)，可选。用于一张证书保护多个域名，如 [\"www.example.com\", \"api.example.com\"]")
        List<String> sanDomains,
        @ApiModelProperty("挑战类型: HTTP-01 或 DNS-01")
        @Schema(description = "挑战类型: HTTP-01 或 DNS-01")
        String challengeType,
        @ApiModelProperty("部署模式: local(本机)/ssh(远程)/manual(手动)，默认manual")
        @Schema(description = "部署模式: local(本机)/ssh(远程)/manual(手动)，默认manual")
        String deployMode,
        @ApiModelProperty("webroot路径，如 /var/www/html（ssh模式使用）")
        @Schema(description = "webroot路径，如 /var/www/html（ssh模式使用）")
        String webrootPath,
        @ApiModelProperty("SSH主机地址") @Schema(description = "SSH主机地址") String sshHost,
        @ApiModelProperty("SSH端口，默认22")
        @Schema(description = "SSH端口，默认22")
        Integer sshPort,
        @ApiModelProperty("SSH用户名") @Schema(description = "SSH用户名") String sshUsername,
        @ApiModelProperty("SSH密码") @Schema(description = "SSH密码") String sshPassword,
        @ApiModelProperty("部署目标主机ID（主机资产），传入时由后端解析 SSH 凭据，无需前端再填地址/端口/用户名/密码")
        @Schema(description = "部署目标主机ID（主机资产）。传入时后端按主机绑定的登录身份解析 SSH 地址、端口、用户名与密码/密钥，"
                + "请求中的 sshHost/sshPort/sshUsername/sshPassword 仅在主机凭据缺失时作为兜底。")
        Long sshHostId
) {
    /**
     * 规范构造器：对备用域名列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变；备用域名列表可 缺省 且元素可能为 空，
     * 故保留 空 语义并采用可空安全包装。</p>
     *
     * <p>其余引用型组件均允许 缺省（服务层以 “参数错误” 统一兜底），
     * 因此不在此处加非空断言，避免把业务级提示变成 500。</p>
     *
     * @param acmeAccountId ACME 账户 标识
     * @param primaryDomain 主域名
     * @param sanDomains    备用域名列表（SAN）
     * @param challengeType 挑战类型
     * @param deployMode    部署模式
     * @param webrootPath   webroot 验证路径
     * @param sshHost       SSH 主机地址
     * @param sshPort       SSH 端口
     * @param sshUsername   SSH 用户名
     * @param sshPassword   SSH 密码
     * @param sshHostId     部署目标主机 ID（主机资产），非空时后端据此解析 SSH 凭据
     */
    public AcmeApplyCertRequest {
        sanDomains = sanDomains == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(sanDomains));
    }
}
