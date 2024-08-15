package com.chua.starter.oauth.server.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.information.AuthInformation;
import com.chua.starter.oauth.server.support.parser.Authorization;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.provider.LoginProvider;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class HttpProtocol implements Protocol, InitializingBean {

    final AuthServerProperties authServerProperties;

    final LoginProvider loginProvider;

    final LoggerResolver loggerResolver;

    final LoginCheck loginCheck;

    final ApplicationContext applicationContext;

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
        AuthInformation authInformation = new AuthInformation(data, request, authServerProperties, applicationContext, loginCheck);
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
     * 执行升级操作的方法。
     *
     * @param data 包含升级相关数据的字符串，具体格式和内容根据业务逻辑而定。
     * @param request HttpServletRequest对象，用于获取客户端请求的相关信息。
     * @param response HttpServletResponse对象，用于向客户端发送响应。
     * @return ReturnResult<String> 包含升级操作结果的返回对象，其中可能包含成功与否、错误信息等。
     */
    @PostMapping("/upgrade")
    @ResponseBody
    public ReturnResult<String> upgrade(@RequestParam("data") String data, HttpServletRequest request, HttpServletResponse response) {
        AuthInformation authInformation = new AuthInformation(data, request, authServerProperties, applicationContext, loginCheck);
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

        ReturnResult<String> authentication = authorization.upgrade(address, authServerProperties.getCookieName());
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
