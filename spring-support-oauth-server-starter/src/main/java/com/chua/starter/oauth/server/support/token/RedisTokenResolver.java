package com.chua.starter.oauth.server.support.token;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
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
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.TOKEN_PRE;

/**
 * redis token管理器
 *
 * @author CH
 */
@Extension("redis")
public class RedisTokenResolver implements TokenResolver {

    @Resource
    private RedisTemplate stringRedisTemplate;

    @Resource
    private AuthServerProperties authServerProperties;
    @Resource
    private LoginCheck loginCheck;

    @Override
    public ReturnResult<LoginResult> createToken(String address, UserResult userResult, String authType) {
        TokenGeneration tokenGeneration = ServiceProvider.of(TokenGeneration.class).getExtension(authServerProperties.getTokenGeneration());
        //token
        String generation = tokenGeneration.generation(userResult.getUid()) + "0" + DigestUtils.md5Hex(IdUtils.createTid());
        userResult.setAddress(address);
        String uid = userResult.getUid();

//        String serviceKey = PRE_KEY + uid + ":" + generation;
        String redisKey = TOKEN_PRE + generation;

        AuthServerProperties.Online online = authServerProperties.getOnline();
        if (online == AuthServerProperties.Online.SINGLE) {
            registerSingle(generation);
        }
        ValueOperations forValue = stringRedisTemplate.opsForValue();
        long expire = userResult.getExpire() == null ? authServerProperties.getExpire() : userResult.getExpire();
        userResult.setExpire(expire);
//        forValue.set(serviceKey, Json.toJson(userResult));
        forValue.set(redisKey, userResult);


        if (expire > 0) {
//            stringRedisTemplate.expire(serviceKey, Duration.ofSeconds(expire));
            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(expire));
        }

        return ReturnResult.ok(new LoginResult(generation));
    }

    /**
     * 单例模式
     *
     * @param generation 用户信息
     */
    private void registerSingle(String generation) {
        String uid = generation.substring(0, 128);
        registerUid(uid);
    }

    /**
     * 单例模式
     *
     * @param uid 用户信息
     */
    private void registerUid(String uid) {
        //
        Set<String> keys = stringRedisTemplate.keys(TOKEN_PRE + uid + "*");
        try {
            stringRedisTemplate.delete(keys);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void logout(String token) {
        stringRedisTemplate.delete(TOKEN_PRE + token);
    }

    @Override
    public void logout(String uid, LogoutType type) {
        if (type == LogoutType.UN_REGISTER || type == LogoutType.LOGOUT_ALL) {
            TokenGeneration tokenGeneration = ServiceProvider.of(TokenGeneration.class).getExtension(authServerProperties.getTokenGeneration());
            registerSingle(tokenGeneration.generation(uid));
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
    public ReturnResult<UserResult> refresh(Cookie[] cookies, String token) {
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

        UserResult userResult = Json.fromJson(s.toString(), UserResult.class);
        UserResult userResult1 = loginCheck.getUserInfo(userResult);
        if (null == userResult1) {
            return ReturnResult.ok(userResult);
        }
        stringRedisTemplate.opsForValue().set(token, userResult1);
        if (authServerProperties.isRenew()) {
            resetExpire(userResult1, token);
        }
        return ReturnResult.ok(userResult1);
    }

    /**
     * token续费
     *
     * @param userResult token信息
     * @param token      token
     */
    private void resetExpire(UserResult userResult, String token) {
        long expire = userResult.getExpire() == null ? authServerProperties.getExpire() : userResult.getExpire();
        userResult.setExpire(expire);
        if (expire > 0) {
            stringRedisTemplate.expire(token, Duration.ofSeconds(expire));
        }
    }
}
