package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.RemoteTokenService;
import com.chua.starter.server.support.service.ServerHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远控短令牌签发接口
 *
 * <p>根据服务器信息（hostId + 身份）签发短令牌：目标机地址/端口/凭据由后端解密后
 * 登记进 令牌 登记表（{@link RemoteTokenService}），接口响应只含随机 令牌 与
 * 会话 URL——服务器信息不出后端。</p>
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ServerConstants.API_PREFIX + "/remote")
public class RemoteTokenController {

    /**
     * 主机服务（主机信息 + 凭据解析）
    */
    private final ServerHostService serverHostService;

    /**
     * 远控短令牌服务
    */
    private final RemoteTokenService remoteTokenService;

    /**
     * 签发远控短令牌
     *
     * @param hostId     主机 标识，必填
     * @param protocol   协议（rdp/vnc/ssh/telnet），默认 ssh
     * @param identityId 登录身份 标识，可空（空时取主机默认凭据）
     * @param targetPort 目标端口覆盖，可空（空=协议默认端口/主机配置端口）
     * @return token + 会话 URL（未配置 web 前端时 url 为 空，前端走 WS 直连）
     */
    @GetMapping("/token")
    public ReturnResult<Map<String, Object>> issueToken(@RequestParam Long hostId,
                                                        @RequestParam(defaultValue = "ssh") String protocol,
                                                        @RequestParam(required = false) Long identityId,
                                                        @RequestParam(required = false) Integer targetPort) {
        // 主机信息
        ReturnResult<MsServerHost> hostResult = serverHostService.get(hostId);
        if (!hostResult.isOk()) {
            return ReturnResult.error(hostResult.getMsg());
        }
        MsServerHost host = hostResult.getData();

        // 凭据（后端解密，不外泄）
        ReturnResult<SshCredential> credential = serverHostService.resolveCredential(hostId, identityId);
        if (!credential.isOk()) {
            return ReturnResult.error(credential.getMsg());
        }
        SshCredential cred = credential.getData();

 // 端口：显式 Target端口 优先，其次主机配置端口，最后协议默认
        Integer port = targetPort;
        if (port == null) {
            port = host.getServerHostPort();
        }

        // 登记并签发
        String token = remoteTokenService.issueToken(protocol,
                host.getServerHostAddress(), port,
                cred.username(), cred.password());

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("token", token);
        data.put("url", remoteTokenService.buildSessionUrl(token));
        data.put("hostName", host.getServerHostName());
        data.put("protocol", protocol);
        return ReturnResult.ok(data);
    }
}
