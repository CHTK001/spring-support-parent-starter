package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.crypto.Codec;
import com.chua.starter.common.support.annotations.Crypto;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * @author CH
 * @since 2025/9/4 14:46
 */
@Intercepts({
        // 加密参数
        @Signature(type = ParameterHandler.class, method = "setParameters",
                args = {PreparedStatement.class}),
        // 解密结果
        @Signature(type = ResultSetHandler.class, method = "handleResultSets",
                args = {Statement.class})
})
public class CryptoInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        if (target instanceof ParameterHandler) {
            encrypt(((ParameterHandler) target).getParameterObject());
        } else if (target instanceof ResultSetHandler) {
            List<?> result = (List<?>) invocation.proceed();
            for (Object obj : result) {
                decrypt(obj);
            }
            return result;
        }
        return invocation.proceed();
    }

    /* ================= 加密 ================= */
    private void encrypt(Object param) {
        if (param == null) return;
        // 只处理 Map（MyBatis 默认把多参数封装成 Map）
        if (param instanceof Map) {
            ((Map<?, ?>) param).forEach((k, v) -> {
                if (v != null) tryEncrypt(v);
            });
        } else {
            tryEncrypt(param);
        }
    }

    private void tryEncrypt(Object obj) {
        Class<?> c = obj.getClass();
        for (Field f : c.getDeclaredFields()) {
            Crypto crypto = f.getAnnotation(Crypto.class);
            if (crypto == null) {
                continue;
            }
            f.setAccessible(true);
            try {
                String raw = (String) f.get(obj);
                if (raw != null) {
                    f.set(obj, Codec.build(crypto.cryptoType().name(), crypto.key()).encodeHex(raw));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* ================= 解密 ================= */
    private void decrypt(Object obj) {
        tryEncrypt(obj);
        Class<?> c = obj.getClass();
        for (Field f : c.getDeclaredFields()) {
            Crypto crypto = f.getAnnotation(Crypto.class);
            if (crypto == null) {
                continue;
            }
            f.setAccessible(true);
            try {
                String raw = (String) f.get(obj);
                if (raw != null) {
                    f.set(obj, Codec.build(crypto.cryptoType().name(), crypto.key()).encodeHex(raw));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
