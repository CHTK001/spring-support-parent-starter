package com.chua.starter.oauth.server.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.information.AuthInformation;
import com.chua.starter.oauth.server.support.parser.Authorization;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.provider.LoginProvider;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.chua.common.support.lang.code.ReturnCode.OK;
import static com.chua.common.support.lang.code.ReturnCode.RESOURCE_OAUTH_ERROR;


/**
 * http
 *
 * @author CH
 */
@Controller
@Extension("http")
@Conditional(OnBeanCondition.class)
@RequestMapping("${plugin.auth.server.context-path:}")
public class HttpProtocol implements Protocol, InitializingBean {

    @Resource
    private AuthServerProperties authServerProperties;

    @Resource
    private LoginProvider loginProvider;

    @Resource
    private LoggerResolver loggerResolver;

    @Value("${plugin.auth.server.context-path:}")
    private String contextPath;


    /**
     * 鉴权
     *
     * @return 鉴权
     */
    @PostMapping("/oauth")
    @ResponseBody
    public ReturnResult<String> oauth(@RequestBody AuthRequest request1, HttpServletRequest request, HttpServletResponse response) {
        String data = request1.getData();
        AuthInformation authInformation = new AuthInformation(data, request, authServerProperties);
        Authorization authorization = authInformation.resolve();
        String address = RequestUtils.getIpAddress(request);

        if (!authorization.hasKey()) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "密钥不存在", address);
            return ReturnResult.noAuth();
        }

        if (!authorization.hasTokenOrCookie()) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "无权限", address);
            return ReturnResult.noAuth();
        }

        ReturnResult<String> authentication = authorization.authentication();
        if (!OK.getCode().equals(authentication.getCode())) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "ak,sk限制登录", address);
            return ReturnResult.noAuth();
        }

        return authentication;
    }

    /**
     * 鉴权
     *
     * @return 鉴权
     */
    @PostMapping("/refresh")
    @ResponseBody
    public ReturnResult<String> refresh(@RequestParam("data") String data, HttpServletRequest request, HttpServletResponse response) {
        AuthInformation authInformation = new AuthInformation(data, request, authServerProperties);
        Authorization authorization = authInformation.resolve();
        String address = RequestUtils.getIpAddress(request);

        if (!authorization.hasKey()) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "密钥不存在", address);
            return ReturnResult.noAuth();
        }

        if (!authorization.hasTokenOrCookie()) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "无权限", address);
            return ReturnResult.noAuth();
        }

        ReturnResult<String> authentication = authorization.refresh();
        if (!OK.getCode().equals(authentication.getCode())) {
            loginProvider.logout(request, response);
            loggerResolver.register(AuthConstant.OAUTH, RESOURCE_OAUTH_ERROR.getCode(), "ak,sk限制登录", address);
            return ReturnResult.noAuth();
        }

        return authentication;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
