package com.chua.starter.job.support.glue;

import com.chua.starter.job.support.handler.JobHandler;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Glue工厂 - 动态代码编译工厂
 * <p>
 * 支持通过Groovy动态编译代码并生成JobHandler实例。
 * 使用MD5缓存已编译的类，避免重复编译相同代码。
 * </p>
 * 
 * <h3>使用示例:</h3>
 * <pre>{@code
 * // 获取工厂实例
 * GlueFactory factory = GlueFactory.getInstance();
 * 
 * // 动态编译Groovy代码
 * String groovyCode = """
 *     import com.chua.starter.job.support.handler.JobHandler;
 *     public class MyHandler implements JobHandler {
 *         public void execute() {
 *             System.out.println("Hello from Groovy!");
 *         }
 *     }
 *     """;
 * JobHandler handler = factory.loadNewInstance(groovyCode);
 * handler.execute();
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see SpringGlueFactory
 * @see JobHandler
 */
@Slf4j
public class GlueFactory {

    /**
     * 单例实例，可通过 {@link #refreshInstance(int)} 切换实现
     */
    private static GlueFactory glueFactory = new GlueFactory();

    /**
     * 获取工厂单例实例
     *
     * @return GlueFactory实例
     */
    public static GlueFactory getInstance() {
        return glueFactory;
    }

    /**
     * 刷新工厂实例
     * <p>
     * 根据类型创建不同的工厂实现:
     * <ul>
     *     <li>0 - 基础GlueFactory，不支持依赖注入</li>
     *     <li>1 - SpringGlueFactory，支持Spring依赖注入</li>
     * </ul>
     * </p>
     *
     * @param type 工厂类型: 0=基础, 1=Spring
     */
    public static void refreshInstance(int type) {
        if (type == 0) {
            glueFactory = new GlueFactory();
            log.info("切换到基础GlueFactory");
        } else if (type == 1) {
            glueFactory = new SpringGlueFactory();
            log.info("切换到SpringGlueFactory，支持依赖注入");
        }
    }

    /**
     * Groovy类加载器，用于动态编译Groovy代码
     */
    private final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

    /**
     * 类缓存，使用MD5作为键避免重复编译
     */
    private final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * 加载新实例
     * <p>
     * 从代码源编译并创建 JobHandler 实例。
     * 如果相同代码已编译过，则使用缓存的类。
     * </p>
     *
     * @param codeSource Groovy源代码
     * @return 编译后的JobHandler实例
     * @throws Exception 编译或实例化失败时抛出异常
     * @throws IllegalArgumentException 如果代码为空或编译结果不是JobHandler
     */
    public JobHandler loadNewInstance(String codeSource) throws Exception {
        if (codeSource == null || codeSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Glue代码源不能为空");
        }

        Class<?> clazz = getCodeSourceClass(codeSource);
        if (clazz == null) {
            throw new IllegalArgumentException("Glue代码编译失败，无法生成类");
        }

        Object instance = clazz.getDeclaredConstructor().newInstance();
        if (!(instance instanceof JobHandler)) {
            throw new IllegalArgumentException(
                    String.format("Glue类型错误: %s 必须实现 JobHandler 接口", clazz.getName()));
        }

        // 注入依赖服务
        this.injectService(instance);
        log.debug("成功加载Glue实例: {}", clazz.getName());
        return (JobHandler) instance;
    }

    /**
     * 获取代码源对应的类
     * <p>
     * 使用MD5缓存机制，相同代码只编译一次
     * </p>
     *
     * @param codeSource 源代码
     * @return 编译后的Class对象
     */
    private Class<?> getCodeSourceClass(String codeSource) {
        try {
            String md5Key = computeMd5(codeSource);

            // 从缓存获取
            Class<?> clazz = CLASS_CACHE.get(md5Key);
            if (clazz != null) {
                log.trace("使用缓存的Glue类: md5={}", md5Key);
                return clazz;
            }

            // 编译并缓存
            clazz = groovyClassLoader.parseClass(codeSource);
            CLASS_CACHE.putIfAbsent(md5Key, clazz);
            log.debug("编译新的Glue类: {}, md5={}", clazz.getName(), md5Key);
            return clazz;
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5算法不可用，直接编译代码");
            return groovyClassLoader.parseClass(codeSource);
        }
    }

    /**
     * 计算字符串的MD5哈希值
     *
     * @param content 内容
     * @return MD5哈希字符串
     * @throws NoSuchAlgorithmException 如果MD5算法不可用
     */
    private String computeMd5(String content) throws NoSuchAlgorithmException {
        byte[] md5Bytes = MessageDigest.getInstance("MD5")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return new BigInteger(1, md5Bytes).toString(16);
    }

    /**
     * 注入服务依赖
     * <p>
     * 基础实现为空，SpringGlueFactory会重写此方法
     * 实现@Autowired和@Resource注解的依赖注入
     * </p>
     *
     * @param instance 需要注入依赖的实例
     */
    public void injectService(Object instance) {
        // 默认空实现，子类可重写
    }

    /**
     * 获取当前缓存的类数量
     *
     * @return 缓存数量
     */
    public int getCacheSize() {
        return CLASS_CACHE.size();
    }

    /**
     * 清空类缓存
     */
    public void clearCache() {
        CLASS_CACHE.clear();
        log.info("Glue类缓存已清空");
    }
}
