package com.chua.starter.oauth.server.support.provider;

import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.ThirdPartyProperties;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 三方地址
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path:}")
public class ThirdPartyProvider {

    @Resource
    private ThirdPartyProperties thirdPartyProperties;
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("/third-index")
    public String thirdIndex(HttpServletRequest request) {

        return "third-index";
    }
    /**
     * gitee页面
     *
     * @return 登录页
     */
    @ResponseBody
    @GetMapping("/gitee")
    public String gitee() {
        AuthRequest authRequest = new AuthGiteeRequest(AuthConfig.builder()
                .clientId(thirdPartyProperties.getGitee().getClientId())
                .deviceId("gitee")
                .clientSecret(thirdPartyProperties.getGitee().getClientSecret())
                .redirectUri(thirdPartyProperties.getGitee().getRedirectUri())
                .build());

        return authRequest.authorize(AuthStateUtils.createState());
    }
}
