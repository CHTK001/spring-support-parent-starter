package com.chua.starter.job.support.glue;

import com.chua.starter.job.support.handler.JobHandler;
import groovy.lang.GroovyClassLoader;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Glue工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class GlueFactory {

    private static GlueFactory glueFactory = new GlueFactory();

    public static GlueFactory getInstance() {
        return glueFactory;
    }

    public static void refreshInstance(int type) {
        if (type == 0) {
            glueFactory = new GlueFactory();
        } else if (type == 1) {
            glueFactory = new SpringGlueFactory();
        }
    }

    /**
     * Groovy类加载器
     */
    private final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
    private final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 加载新实例
     *
     * @param codeSource 代码源
     * @return JobHandler实例
     * @throws Exception 异常
     */
    public JobHandler loadNewInstance(String codeSource) throws Exception {
        if (codeSource != null && !codeSource.trim().isEmpty()) {
            Class<?> clazz = getCodeSourceClass(codeSource);
            if (clazz != null) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof JobHandler) {
                    this.injectService(instance);
                    return (JobHandler) instance;
                } else {
                    throw new IllegalArgumentException(">>>>>>>>>>> glue, loadNewInstance error, "
                            + "cannot convert from instance[" + instance.getClass() + "] to JobHandler");
                }
            }
        }
        throw new IllegalArgumentException(">>>>>>>>>>> glue, loadNewInstance error, instance is null");
    }

    private Class<?> getCodeSourceClass(String codeSource) {
        try {
            byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
            String md5Str = new BigInteger(1, md5).toString(16);

            Class<?> clazz = CLASS_CACHE.get(md5Str);
            if (clazz == null) {
                clazz = groovyClassLoader.parseClass(codeSource);
                CLASS_CACHE.putIfAbsent(md5Str, clazz);
            }
            return clazz;
        } catch (Exception e) {
            return groovyClassLoader.parseClass(codeSource);
        }
    }

    /**
     * 注入服务
     *
     * @param instance 实例
     */
    public void injectService(Object instance) {
        // 默认空实现，子类可重写
    }
}
