package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.OwnerPasswordUpdate;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.StaticUserStore;
import com.chua.starter.oauth.client.support.user.UserResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号安全控制器。
 *
 * <p>「设置 - 账号安全」修改当前登录账号密码。账号体系以 oauth 为准（不走 ai-starter 内置的
 * 占位改密）：静态协议（本地开发）更新 oauth-client 的可变凭据存储，立即对后续登录生效；
 * 其它接入方式（http 等）账号由认证中心管理，应在认证中心修改。</p>
 *
 * <p>装配条件与 {@code AuthClientConfiguration} 保持一致：{@code plugin.oauth.enable=false}
 * 时 oauth 客户端整体关闭，{@code AuthClientProperties} 不会被注册，本控制器无条件注入会
 * 导致应用启动失败，故此时一并跳过装配。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/user")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.oauth", name = "enable", havingValue = "true", matchIfMissing = true)
@Tag(name = "账号安全", description = "当前登录账号口令修改")
public class OwnerAccountController {

    /**
     * 认证客户端配置
     */
    private final AuthClientProperties authClientProperties;

    /**
     * 修改当前账号密码。
     *
     * @param body 密码修改请求
     * @return 修改结果
     */
    @Operation(summary = "修改当前账号密码")
    @PutMapping("/updateOwner")
    public ReturnResult<?> updateOwner(@RequestBody OwnerPasswordUpdate body) {
        if (null == body) {
            return ReturnResult.error("参数不能为空");
        }
        String oldPassword = body.getOldPassword();
        String newPassword = body.getNewPassword();
        String confirmPassword = body.getConfirmPassword();

        if (StringUtils.isBlank(oldPassword)) {
            return ReturnResult.error("请输入原密码");
        }
        if (StringUtils.isBlank(newPassword)) {
            return ReturnResult.error("请输入新密码");
        }
        if (newPassword.length() < 6) {
            return ReturnResult.error("新密码长度不能少于 6 位");
        }
        if (!newPassword.equals(confirmPassword)) {
            return ReturnResult.error("两次输入的新密码不一致");
        }
        if (newPassword.equals(oldPassword)) {
            return ReturnResult.error("新密码不能与原密码相同");
        }

        UserResult user = AuthClientExecute.INSTANCE.getSafeUserResult();
        if (null == user || StringUtils.isBlank(user.getUsername())) {
            return ReturnResult.error("未登录或登录状态已失效");
        }
        String username = user.getUsername();

        String protocol = authClientProperties.getProtocol();
        if ("static".equalsIgnoreCase(protocol)) {
            AuthClientProperties.TempUser temp = authClientProperties.getTemp();
            String rawConfig = null != temp ? temp.getUser() : null;
            StaticUserStore store = StaticUserStore.get();
            // 确保已按配置初始化（登录链路通常已初始化）
            store.effective(rawConfig);
            if (!store.matches(username, oldPassword)) {
                return ReturnResult.error("原密码不正确");
            }
            if (!store.updatePassword(username, newPassword)) {
                return ReturnResult.error("修改失败：账号不存在");
            }
            log.info("[账号安全]账号 {} 已修改密码", username);
            return ReturnResult.ok();
        }

        return ReturnResult.error("当前接入方式（" + protocol + "）的账号由认证中心管理，请在认证中心修改密码");
    }
}
