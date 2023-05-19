package com.chua.starter.script.adaptor;

import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.bean.FileScriptBean;
import com.chua.starter.script.configuration.ScriptProperties;
import com.chua.starter.script.marker.DelegateMarker;
import com.chua.starter.script.marker.Marker;
import com.chua.starter.script.watchdog.Watchdog;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 定义
 *
 * @author CH
 */
@ScriptExtension("file")
public class FileAdaptor implements Adaptor {

    @Override
    public List<BeanDefinition> analysis(ScriptProperties.Config config, Watchdog watchdog) {
        List<BeanDefinition> rs = new LinkedList<>();
        String bean = config.getSource();
        File file = new File(bean);
        if (!file.exists()) {
            return rs;
        }

        if (file.isDirectory()) {
            analysis(file, rs, watchdog);
        } else {
            analysisFile(file, rs, watchdog);
        }
        return rs;
    }

    private void analysis(File file, List<BeanDefinition> rs, Watchdog watchdog) {
        File[] files = file.listFiles();
        try {
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    analysisFile(file.toFile(), rs, watchdog);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }


    }

    private void analysisFile(File file, List<BeanDefinition> rs, Watchdog watchdog) {
        String extension = FilenameUtils.getExtension(file.getName());
        Marker marker = Marker.create(extension);
        if (marker instanceof DelegateMarker) {
            return;
        }

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String toString = IOUtils.toString(fileInputStream, UTF_8);
            String[] split = toString.split("\r\n", 2);
            String s = split[0];
            Class<?> aClass = null;
            if (!s.startsWith("package") && !s.startsWith("import")) {
                aClass = ClassUtils.forName(s, ClassLoader.getSystemClassLoader());
            }
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(FileScriptBean.class);
            beanDefinitionBuilder.addConstructorArgValue(file);
            beanDefinitionBuilder.addConstructorArgValue(watchdog);
            beanDefinitionBuilder.addConstructorArgValue(aClass);
            rs.add(beanDefinitionBuilder.getBeanDefinition());
        } catch (Exception ignored) {
        }
    }
}

