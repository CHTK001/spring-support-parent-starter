package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 主机资产归档（导出 / 导入共用的 JSON 结构）。
 *
 * <p><b>为什么单独做一份归档格式，而不是复用 CSV 导出：</b>CSV 只能承载主机本身的
 * 展示字段，既带不上登录身份，更带不上口令；而「换机器迁移台账」真正省时间的恰恰是
 * 账号与绑定关系。这里用 JSON 把三层数据打包在一起：</p>
 * <ul>
 *   <li>{@code hosts}：主机台账；</li>
 *   <li>{@code identities}：登录身份，口令字段是<b>密文</b>（库中原样导出的 AES hex），
 *       因此导入端必须原样写回、不能再加密一次；</li>
 *   <li>{@code bindings}：主机 ↔ 身份绑定，用「地址 + 端口 + 身份名」而不是自增 ID 表达，
 *       这样跨库导入时 ID 不一致也能复原关系。</li>
 * </ul>
 *
 * <p>口令密文依赖部署侧 {@code plugin.server.support.credential-key}：换一套密钥的环境
 * 导入后这些口令解不开（不影响主机与绑定本身），所以 {@code note} 里写明这一点，
 * 避免排障时误判成「导入丢密码」。</p>
 *
 * @param format     格式标识，固定 {@code vue-support-host-archive}
 * @param version    格式版本，当前 1
 * @param exportedAt 导出时间（ISO-8601）
 * @param note       使用说明 / 迁移注意事项
 * @param hosts      主机台账
 * @param identities 登录身份（口令为密文）
 * @param bindings   主机-身份绑定
 * @author CH
 * @since 4.0.0.54
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HostArchiveVO(
        String format,
        int version,
        String exportedAt,
        String note,
        List<HostItem> hosts,
        List<IdentityItem> identities,
        List<BindingItem> bindings
) {

    /**
     * 归档格式标识。
     */
    public static final String FORMAT = "vue-support-host-archive";

    /**
     * 当前格式版本。
     */
    public static final int VERSION = 1;

    /**
     * 主机条目：只带「迁移要用的台账字段」，采集结果与在线状态不导出（换机器即失效）。
     *
     * @param name            主机名称
     * @param code            主机编码
     * @param address         主机地址
     * @param port            SSH 端口
     * @param osType          系统类型 LINUX/WINDOWS/MACOS
     * @param osArch          系统架构
     * @param osVersion       系统版本
     * @param cpuCores        CPU 核心数
     * @param memoryMb        内存容量 MB
     * @param diskGb          磁盘容量 GB
     * @param publicIp        公网 IP
     * @param privateIp       内网 IP
     * @param sshEnabled      SSH 开关 1/0
     * @param collectMode     采集方式 SCHEDULED/AGENT/...
     * @param collectEnabled  采集开关 1/0
     * @param collectInterval 采集间隔秒
     * @param remoteEnabled   远控开关 1/0
     * @param remoteProtocol  远控协议 SSH/RDP/VNC
     * @param remotePort      远控端口
     * @param remoteMode      远控模式 GUACD/DIRECT/NONE
     * @param account         默认登录账号（仅展示，真正凭据在 identities）
     * @param tags            标签 JSON
     * @param description     描述
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HostItem(
            String name,
            String code,
            String address,
            Integer port,
            String osType,
            String osArch,
            String osVersion,
            Integer cpuCores,
            Long memoryMb,
            Long diskGb,
            String publicIp,
            String privateIp,
            Integer sshEnabled,
            String collectMode,
            Integer collectEnabled,
            Integer collectInterval,
            Integer remoteEnabled,
            String remoteProtocol,
            Integer remotePort,
            String remoteMode,
            String account,
            String tags,
            String description
    ) {
    }

    /**
     * 登录身份条目。
     *
     * @param name      身份名称（跨库关联键）
     * @param authType  认证类型 PASSWORD/PUBLIC_KEY/NONE/...
     * @param username  登录用户名
     * @param password  口令密文（AES hex，库中原样导出）
     * @param keyId     引用密钥 ID（跨库无意义，仅记录）
     * @param remark    备注
     * @param emailSource 邮箱来源
     * @param provider  临时邮箱服务商
     * @param imapHost  IMAP 主机
     * @param imapPort  IMAP 端口
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdentityItem(
            String name,
            String authType,
            String username,
            String password,
            Long keyId,
            String remark,
            String emailSource,
            String provider,
            String imapHost,
            Integer imapPort
    ) {
    }

    /**
     * 主机-身份绑定。
     *
     * @param hostAddress  主机地址
     * @param hostPort     主机端口
     * @param identityName 身份名称
     * @param sort         优先级（越小越优先）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BindingItem(
            String hostAddress,
            Integer hostPort,
            String identityName,
            Integer sort
    ) {
    }
}
