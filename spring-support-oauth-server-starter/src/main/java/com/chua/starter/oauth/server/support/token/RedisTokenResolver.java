package com.chua.starter.oauth.server.support.token;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.generation.TokenGeneration;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.TOKEN_PRE;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.TOKEN_REFRESH_PRE;

/**
 * redis token管理器
 *
 * @author CH
 */
@Extension("redis")
@SuppressWarnings("ALL")
public class RedisTokenResolver implements TokenResolver {

    @AutoInject
    private RedisTemplate stringRedisTemplate;

    @AutoInject
    private AuthServerProperties authServerProperties;
    @AutoInject
    private LoginCheck loginCheck;

    @Override
    public ReturnResult<LoginResult> createToken(String address, UserResult userResult, String authType) {
        TokenGeneration tokenGeneration = ServiceProvider.of(TokenGeneration.class).getExtension(authServerProperties.getTokenGeneration());
        //token
        String token = tokenGeneration.generation(userResult.getUid()) + "0" + DigestUtils.md5Hex(IdUtils.createTid());
        userResult.setAddress(address);
        String uid = userResult.getUid();

//        String serviceKey = PRE_KEY + uid + ":" + generation;
        String redisKey = TOKEN_PRE + token;
        String refreshToken = tokenGeneration.generation(userResult.getUid()) + "0" + DigestUtils.md5Hex(IdUtils.createTid());
        String redisRefreshKey = TOKEN_REFRESH_PRE + refreshToken;

        AuthServerProperties.Online online = authServerProperties.getOnline();
        if (online == AuthServerProperties.Online.SINGLE) {
            unRegisterSingle(token);
        }
        ValueOperations forValue = stringRedisTemplate.opsForValue();
        long expire = userResult.getExpire() == null ? authServerProperties.getExpire() : userResult.getExpire();
        long refreshExpire = userResult.getExpire() == null ? authServerProperties.getRefreshExpire() : userResult.getExpire() + 86400L;
        userResult.setExpire(expire);
        userResult.setRefreshToken(refreshToken);
        userResult.setRefreshExpire(refreshExpire);
        forValue.set(redisKey, userResult);
        forValue.set(redisRefreshKey, userResult);


        if (expire > 0) {
            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(expire));
            stringRedisTemplate.expire(redisRefreshKey, Duration.ofSeconds(refreshExpire));
        }

        LoginResult loginResult = new LoginResult(token);
        loginResult.setRefreshToken(refreshToken);
        return ReturnResult.ok(loginResult);
    }

    /**
     * 注销其它相同账号
     *
     * @param generation 用户信息
     */
    private void unRegisterSingle(String generation) {
        String uid = generation.substring(0, 128);
        unRegisterUid(uid);
    }

    /**
     * 单例模式
     *
     * @param uid 用户信息
     */
    @SuppressWarnings("ALL")
    private void unRegisterUid(String uid) {
        Set<String> keys = stringRedisTemplate.keys(TOKEN_PRE + uid + "*");
        try {
            stringRedisTemplate.delete(keys);
        } catch (Throwable ignored) {
        }
        Set<String> keys1 = stringRedisTemplate.keys(TOKEN_REFRESH_PRE + uid + "*");
        try {
            stringRedisTemplate.delete(keys1);
        } catch (Throwable ignored) {
        }
    }

    private void logoutRefresh(String token) {
        stringRedisTemplate.delete(TOKEN_REFRESH_PRE + token);
    }
    @Override
    @SuppressWarnings("ALL")
    public void logout(String token) {
        String tokenRedisKey = TOKEN_PRE + token;
        Object s = stringRedisTemplate.opsForValue().get(tokenRedisKey);
        UserResult userResult = BeanUtils.copyProperties(s, UserResult.class);
        if (null != userResult) {
            stringRedisTemplate.delete(TOKEN_PRE + token);
            String refreshToken = userResult.getRefreshToken();
            if (StringUtils.isNotEmpty(refreshToken)) {
                logoutRefresh(refreshToken);
            }
        }
    }

    @Override
    @SuppressWarnings("ALL")
    public void logout(String uid, LogoutType type) {
        if (type == LogoutType.UN_REGISTER || type == LogoutType.LOGOUT_ALL) {
            TokenGeneration tokenGeneration = ServiceProvider.of(TokenGeneration.class).getExtension(authServerProperties.getTokenGeneration());
            unRegisterSingle(tokenGeneration.generation(uid));
            return;
        }

        if (type == LogoutType.LOGOUT) {
            logout(uid);
        }

    }

    @Override
    public ReturnResult<UserResult> resolve(Cookie[] cookies, String token) {
        Cookie cookie = CookieUtil.getCookie(cookies, authServerProperties.getCookieName());
        String cv = token;
        if (StringUtils.isEmpty(StringUtils.ifValid(cv, ""))) {
            cv = null == cookie ? null : cookie.getValue();
        }

        if (null == cv) {
            return ReturnResult.noAuth();
        }
        cv = TOKEN_PRE + cv;
        Object s = stringRedisTemplate.opsForValue().get(cv);
        if (null == s) {
            return ReturnResult.noAuth();
        }
        UserResult userResult = (UserResult) s;
        if (authServerProperties.isRenew()) {
            resetExpire(userResult, token);
        }
        return ReturnResult.ok(userResult);
    }

    @Override
    public ReturnResult<UserResult> upgradeForTimestamp(String token, String refreshToken) {
        ReturnResult<UserResult> userResultReturnResult = upgradeForVersion(token, token);
        if(!userResultReturnResult.isOk()) {
            return userResultReturnResult;
        }

        if (authServerProperties.isRenew()) {
            resetExpire(userResultReturnResult.getData(), token);
        }
        return ReturnResult.ok(userResultReturnResult.getData());
    }

    @Override
    public ReturnResult<UserResult> upgradeForVersion(String token, String refreshToken) {
        String redisKey = TOKEN_PRE + token;
        Object s = stringRedisTemplate.opsForValue().get(redisKey);
        if (null == s) {
            return ReturnResult.noAuth();
        }

        UserResult userResult = BeanUtils.copyProperties(s, UserResult.class);
        UserResult newUserResult = loginCheck.getUserInfo(userResult);
        if (null == newUserResult) {
            return ReturnResult.ok(userResult);
        }
        stringRedisTemplate.opsForValue().set(redisKey, newUserResult);
        return ReturnResult.ok(newUserResult);
    }

    @Override
    public ReturnResult<UserResult> upgradeForRefresh(String token, String refreshToken) {
        String redisRefreshKey = TOKEN_REFRESH_PRE + refreshToken;
        Object s = stringRedisTemplate.opsForValue().get(redisRefreshKey);
        if (null == s) {
            return ReturnResult.noAuth();
        }
        String redisKey = TOKEN_PRE + token;
        UserResult userResult = BeanUtils.copyProperties(s, UserResult.class);
        return ReturnResult.ok(userResult, userResult.getAuthType());
    }

    @Override
    public void logout(Cookie[] cookies, String token, String refreshToken, String cookieName) {
        Cookie cookie = CookieUtil.getCookie(cookies, cookieName);
        if(null != cookie) {
            logout(cookie.getValue());
        }

        if(StringUtils.isNotBlank(token)) {
            logout(token);
        }

        if (StringUtils.isNotBlank(refreshToken)) {
            logoutRefresh(token);
        }
    }


    /**
     * token续费
     *
     * @param userResult token信息
     * @param token      token
     */
    @SuppressWarnings("unchecked")
    private void resetExpire(UserResult userResult, String token) {
        long expire = userResult.getExpire() == null ? authServerProperties.getExpire() : userResult.getExpire();
        userResult.setExpire(expire);
        if (expire > 0) {
            stringRedisTemplate.expire(token, Duration.ofSeconds(expire));
        }
    }
}
