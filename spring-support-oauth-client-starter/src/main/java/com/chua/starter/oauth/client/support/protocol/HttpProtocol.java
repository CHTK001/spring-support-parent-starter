package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.crypto.decode.KeyDecode;
import com.chua.common.support.crypto.encode.KeyEncode;
import com.chua.common.support.crypto.utils.DigestUtils;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Md5Utils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.springframework.beans.factory.InitializingBean;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.chua.starter.oauth.client.support.infomation.Information.*;

/**
 * http
 *
 * @author CH
 */
@Extension("http")
public class HttpProtocol extends AbstractProtocol implements InitializingBean {

    @Resource
    private AuthClientProperties authClientProperties;
    private KeyDecode decode;
    private KeyEncode encode;

    @Override
    public AuthenticationInformation approve(Cookie[] cookie, String token) {
        String key = UUID.randomUUID().toString();
        Map<String, Object> jsonObject = new HashMap<>(2);
        jsonObject.put("x-oauth-cookie", Optional.ofNullable(cookie).orElse(new Cookie[0]));
        jsonObject.put("x-oauth-token", token);

        String accessKey = authClientProperties.getAccessKey();
        String secretKey = authClientProperties.getSecretKey();
        String serviceKey = authClientProperties.getServiceKey();
        if (Strings.isNullOrEmpty(accessKey) || Strings.isNullOrEmpty(secretKey)) {
            accessKey = DefSecret.ACCESS_KEY;
            secretKey = DefSecret.SECRET_KEY;
        }

        String asString = Json.toJson(jsonObject);

        String request = encode.encodeHex(asString, Md5Utils.getInstance()
                .getMd5String(accessKey + DigestUtils.md5Hex(secretKey + key)));
        Map<String, Object> item2 = new HashMap<>(3);
        item2.put(AuthConstant.ACCESS_KEY, accessKey);
        item2.put(AuthConstant.SECRET_KEY, secretKey);
        item2.put(AuthConstant.OAUTH_VALUE, request);
        item2.put(AuthConstant.OAUTH_KEY, key);
        request = encode.encodeHex(Json.toJson(item2), serviceKey);

        Robin<String> balance = ServiceProvider.of(Robin.class).getExtension(authClientProperties.getBalance());
        Robin<String> stringRobin = balance.create();
        String[] split = SpringBeanUtils.getApplicationContext().getEnvironment().resolvePlaceholders(authClientProperties.getAuthAddress()).split(",");
        stringRobin.addNode(split);
        Node<String> robin = stringRobin.selectNode();
        HttpResponse<String> httpResponse = null;
        try {
            String url = robin.getContent();
            if (null == url) {
                return AuthenticationInformation.authServerError();
            }

            httpResponse = Unirest.post(
                            StringUtils.endWithAppend(StringUtils.startWithAppend(url, "http://"), "/") + StringUtils.startWithMove(authClientProperties.getOauthUrl(), "/"))
                    .header("x-oauth-timestamp", System.nanoTime() + "")
                    .field("data", request)
                    .asString();

        } catch (UnirestException ignored) {
        }

        if (null == httpResponse) {
            return AuthenticationInformation.authServerError();
        }


        int status = httpResponse.getStatus();
        String body = httpResponse.getBody();
        if (status > 400 && status < 600 || Strings.isNullOrEmpty(body)) {
            return AuthenticationInformation.authServerNotFound();
        }

        if (status == 200) {
            ReturnResult returnResult = Json.fromJson(body, ReturnResult.class);
            Integer code = returnResult.getCode();
            if (403 == code) {
                HttpServletRequest servletRequest = RequestUtils.getRequest();
                if (null != servletRequest) {
                    CookieUtil.remove(servletRequest, ResponseUtils.getResponse(), "x-oauth-cookie");
                }

                return new AuthenticationInformation(AUTHENTICATION_FAILURE, null);
            }

            Object data = returnResult.getData();
            if (Objects.isNull(data)) {
                return new AuthenticationInformation(AUTHENTICATION_SERVER_EXCEPTION, null);
            }

            if (code >= 200 && code < 300) {
                body = decode.decodeHex(data.toString(), key);
                return new AuthenticationInformation(OK, Json.fromJson(body, UserResume.class));
            }


            return new AuthenticationInformation(OTHER, null);
        }
        return AuthenticationInformation.authServerNotFound();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.encode = ServiceProvider.of(KeyEncode.class).getExtension(authClientProperties.getEncryption());
        this.decode = ServiceProvider.of(KeyDecode.class).getExtension(authClientProperties.getEncryption());
    }
}
