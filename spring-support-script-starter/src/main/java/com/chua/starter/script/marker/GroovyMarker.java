package com.chua.starter.script.marker;

import com.chua.starter.script.ScriptExtension;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.util.ClassUtils;

/**
 * 编译器
 *
 * @author CH
 */
@ScriptExtension("groovy")
public class GroovyMarker implements Marker {

    public static CompilerConfiguration config = new CompilerConfiguration();
    private Class aClass;

    {
        config.setSourceEncoding("UTF-8");
    }


    @Override
    public Object getBean(String source) {
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(ClassUtils.getDefaultClassLoader(), config);
        try {
            this.aClass = groovyClassLoader.parseClass(source);
            return aClass.newInstance();
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }
}
