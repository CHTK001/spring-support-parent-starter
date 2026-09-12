package com.chua.report.client.starter.job.glue;


import com.chua.report.client.starter.job.handler.JobHandler;
import groovy.lang.GroovyClassLoader;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
* 胶水厂
*
* @版本 1.0.0
* @since 2024/03/11
* @author CH
 */
public class GlueFactory {


    /** 胶水工厂（volatile 保证多线程可见性，refreshinstance 会重新赋值） */
    private static volatile GlueFactory glueFactory = new GlueFactory();
    /**
    * 获取Instance
    *
    * @return 获取instance的结果
     */
    public static GlueFactory getInstance(){
        return glueFactory;
    }
    /**
    * refreshinstance
    *
    * @param type 类型
     */
    public static void refreshInstance(int type){
        if (type == 0) {
            glueFactory = new GlueFactory();
        } else if (type == 1) {
            glueFactory = new SpringGlueFactory();
        }
    }


    /**
    * Groovy 类 加载
     */
    private final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
    private final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(); // 类缓存

    /**
    * 加载 新 instance, 原型
    *
    * @param codeSource
    * @return
    * @throws Exception
     */
    public JobHandler loadNewInstance(String codeSource) throws Exception{
        if (codeSource!=null && codeSource.trim().length()>0) {
            Class<?> clazz = getCodeSourceClass(codeSource);
            if (clazz != null) {
                Object instance = clazz.newInstance();
                if (instance!=null) {
                    if (instance instanceof JobHandler) {
                        this.injectService(instance);
                        return (JobHandler) instance;
                    } else {
                        throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, "
                                + "cannot convert from instance["+ instance.getClass() +"] to IJobHandler");
                    }
                }
            }
        }
        /**
        * illegal参数异常。
        * @param null" 空"
        * @return illegal参数异常的结果
         */
        throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, instance is null");
    }
    /**
    * 获取编码源类。
    * @param codeSource 编码源
    * @return 获取编码源类的结果
     */
    private Class<?> getCodeSourceClass(String codeSource){
        try {
            // md5
            byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
            String md5Str = new BigInteger(1, md5).toString(16);

            Class<?> clazz = CLASS_CACHE.get(md5Str);
            if(clazz == null){
                clazz = groovyClassLoader.parseClass(codeSource);
                CLASS_CACHE.putIfAbsent(md5Str, clazz);
            }
            return clazz;
        } catch (Exception e) {
            return groovyClassLoader.parseClass(codeSource);
        }
    }

    /**
    * inject 服务 的 Bean 字段
    *
    * @param instance
     */
    public void injectService(Object instance) {
 // 执行 something
    }

}
