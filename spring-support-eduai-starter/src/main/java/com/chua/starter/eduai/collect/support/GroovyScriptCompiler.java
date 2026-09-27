package com.chua.starter.eduai.collect.support;

import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Groovy 采集脚本编译器：按源码指纹缓存编译结果，避免调度轮询反复编译。
 * <p>
 * 缓存为访问序 LRU，淘汰时关闭对应类加载器并清理其类缓存，防止长时间运行下 Groovy
 * 类加载器堆积在 Metaspace。执行超时由调用方（Provider）在工作线程上控制，编译器只负责编译。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class GroovyScriptCompiler {

    /**
     * 编译缓存上限（个）
     */
    private static final int MAX_CACHE = 64;

    /**
     * 源码指纹 -> 编译产物
     */
    private final Map<String, Compiled> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Compiled> eldest) {
            if (size() > MAX_CACHE) {
                eldest.getValue().release();
                return true;
            }
            return false;
        }
    };

    private final ClassLoader parentClassLoader;

    /**
     * 以本类所在类加载器为父加载器，脚本可直接 import 业务类。
     */
    public GroovyScriptCompiler() {
        this(GroovyScriptCompiler.class.getClassLoader());
    }

    /**
     * @param parentClassLoader 脚本可见类的父加载器
     */
    public GroovyScriptCompiler(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    /**
     * 编译（或命中缓存）脚本源码。
     *
     * @param source 脚本源码
     * @return 脚本类（{@code groovy.lang.Script} 子类）
     */
    public Class<?> compile(String source) {
        String key = fingerprint(source);
        synchronized (cache) {
            Compiled compiled = cache.get(key);
            if (compiled != null) {
                return compiled.type;
            }
            Class<?> type = doCompile(source, key);
            cache.put(key, new Compiled(type));
            return type;
        }
    }

    /**
     * 清空编译缓存（脚本体系热更新时使用）。
     */
    public void clear() {
        synchronized (cache) {
            for (Compiled compiled : cache.values()) {
                compiled.release();
            }
            cache.clear();
        }
    }

    private Class<?> doCompile(String source, String key) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setSourceEncoding(StandardCharsets.UTF_8.name());
        try (GroovyClassLoader loader = new GroovyClassLoader(parentClassLoader, configuration)) {
            return loader.parseClass(source, "EduCollectScript_" + key.substring(0, 12) + ".groovy");
        } catch (Exception e) {
            throw new IllegalStateException("Groovy 脚本编译失败: " + e.getMessage(), e);
        }
    }

    private String fingerprint(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.format("%08x%08x", source.hashCode(), source.length());
        }
    }

    /**
     * 编译产物与其专属类加载器。
     */
    private static final class Compiled {

        private final Class<?> type;

        private Compiled(Class<?> type) {
            this.type = type;
        }

        private void release() {
            if (type.getClassLoader() instanceof GroovyClassLoader loader) {
                try {
                    loader.clearCache();
                    loader.close();
                } catch (Exception e) {
                    log.debug("采集脚本类加载器释放失败", e);
                }
            }
        }
    }
}
