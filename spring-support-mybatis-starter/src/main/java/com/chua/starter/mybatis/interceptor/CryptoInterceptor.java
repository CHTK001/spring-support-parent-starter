package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.core.utils.ClassUtils;
import com.chua.common.support.crypto.Codec;
import com.chua.starter.common.support.annotations.Crypto;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 加密解密拦截器
 * 用于在执行SQL前加密参数，在执行SQL后解密结果
 *
 * @author CH
 * @since 2025/9/4 14:46
 */
@Slf4j
@Intercepts({
        // 加密参数
        @Signature(type = ParameterHandler.class, method = "setParameters",
                args = {PreparedStatement.class}),
        // 解密结果
        @Signature(type = ResultSetHandler.class, method = "handleResultSets",
                args = {Statement.class})
})
public class CryptoInterceptor implements Interceptor {

    /**
     * 缓存类对应的加密字段列表，Key为Class对象，Value为该类所有带@Crypto注解的字段列表
     */
    private static final Map<Class<?>, List<CryptoFieldInfo>> CRYPTO_FIELD_CACHE = new ConcurrentReferenceHashMap<>(256);

    /**
     * 加密字段信息
     */
    private record CryptoFieldInfo(Field field, Crypto annotation) {
    }
    /**
     * 拦截MyBatis的参数设置和结果处理
     *
     * @param invocation MyBatis调用上下文对象
     * @return 原始方法的执行结果
     * @throws Throwable 当方法执行出现异常时抛出
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        if (target instanceof ParameterHandler parameterHandler) {
            encrypt(parameterHandler.getParameterObject());
        } else if (target instanceof ResultSetHandler) {
            List<?> result = (List<?>) invocation.proceed();
            for (Object obj : result) {
                decrypt(obj);
            }
            return result;
        }
        return invocation.proceed();
    }

    /**
     * 加密参数对象
     *
     * @param param 参数对象，可能为Map或实体对象
     */
    private void encrypt(Object param) {
        if (param == null) {
            return;
        }
        // 只处理 Map（MyBatis 默认把多参数封装成 Map）
        if (param instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (v != null) {
                    processCryptoFields(v, true);
                }
            });
        } else {
            processCryptoFields(param, true);
        }
    }

    /**
     * 解密结果对象
     *
     * @param obj 结果对象
     */
    private void decrypt(Object obj) {
        if (obj == null) {
            return;
        }
        processCryptoFields(obj, false);
    }

    /**
     * 处理对象的加密/解密字段
     *
     * @param obj    目标对象
     * @param encrypt true表示加密，false表示解密
     */
    private void processCryptoFields(Object obj, boolean encrypt) {
        Class<?> clazz = obj.getClass();
        List<CryptoFieldInfo> cryptoFields = getCryptoFields(clazz);
        if (cryptoFields.isEmpty()) {
            return;
        }

        for (CryptoFieldInfo fieldInfo : cryptoFields) {
            try {
                Field field = fieldInfo.field();
                Crypto crypto = fieldInfo.annotation();
                ClassUtils.setAccessible(field);
                String value = (String) field.get(obj);
                if (value == null) {
                    continue;
                }

                Codec codec = Codec.build(crypto.cryptoType().name(), crypto.key());
                String processedValue = encrypt
                        ? codec.encodeHex(value)
                        : codec.decodeHex(value);
                field.set(obj, processedValue);
            } catch (IllegalAccessException e) {
                log.error("处理加密字段失败: {}", fieldInfo.field().getName(), e);
                throw new RuntimeException("处理加密字段失败: " + fieldInfo.field().getName(), e);
            }
        }
    }

    /**
     * 获取类对应的加密字段列表，使用缓存提升性能
     *
     * @param clazz 目标类
     * @return 加密字段信息列表
     */
    private List<CryptoFieldInfo> getCryptoFields(Class<?> clazz) {
        return CRYPTO_FIELD_CACHE.computeIfAbsent(clazz, this::scanCryptoFields);
    }

    /**
     * 扫描类中所有带@Crypto注解的字段
     *
     * @param clazz 目标类
     * @return 加密字段信息列表
     */
    private List<CryptoFieldInfo> scanCryptoFields(Class<?> clazz) {
        List<CryptoFieldInfo> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            Crypto crypto = field.getAnnotation(Crypto.class);
            if (crypto != null) {
                fields.add(new CryptoFieldInfo(field, crypto));
            }
        }
        return fields;
    }
}
