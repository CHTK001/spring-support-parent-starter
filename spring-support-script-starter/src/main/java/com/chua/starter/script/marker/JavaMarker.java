package com.chua.starter.script.marker;

import com.chua.common.support.lang.compile.Compiler;
import com.chua.common.support.lang.compile.JavassistCompiler;
import com.chua.common.support.lang.compile.JdkCompiler;
import com.chua.starter.script.ScriptExtension;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 编译器
 *
 * @author CH
 */
@ScriptExtension("java")
public class JavaMarker implements Marker {
    private static final Compiler  JAVASSIST_COMPILER = new JavassistCompiler();
    private static final Compiler JDK_COMPILER = new JdkCompiler();
    private static final Map<String, AtomicInteger> CNT = new ConcurrentHashMap<>();
    private Class<?> compiler;

    @Override
    public Object getBean(String source) {
        String key = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
        String suffix = "";
        if (CNT.containsKey(key)) {
            suffix = CNT.get(key).incrementAndGet() + "";
        } else {
            CNT.put(key, new AtomicInteger());
        }
        try {
            this.compiler = JAVASSIST_COMPILER.compiler(source, ClassUtils.getDefaultClassLoader(), suffix);
            return compiler.newInstance();
        } catch (Exception ignored) {
        }
        try {
            compiler = JDK_COMPILER.compiler(source, ClassUtils.getDefaultClassLoader());
            return compiler.newInstance();
        } catch (Throwable ignored) {
        }
        return null;
    }
}
