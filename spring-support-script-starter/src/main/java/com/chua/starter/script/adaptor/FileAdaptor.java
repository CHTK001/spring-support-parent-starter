package com.chua.starter.script.adaptor;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.resource.ResourceProvider;
import com.chua.common.support.resource.resource.Resource;
import com.chua.common.support.utils.UrlUtils;
import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.bean.FileScriptBean;
import com.chua.starter.script.bean.SourceScriptBean;
import com.chua.starter.script.configuration.ScriptProperties;
import com.chua.starter.script.marker.DelegateMarker;
import com.chua.starter.script.marker.Marker;
import com.chua.starter.script.watchdog.Watchdog;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
            return find(rs, bean, watchdog);
        }

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

    private List<BeanDefinition> find(List<BeanDefinition> rs, String bean, Watchdog watchdog) {
        Set<Resource> resources = ResourceProvider.of(bean).getResources();
        for (Resource resource : resources) {
            analysisResource(resource, rs, watchdog);
        }
        return rs;
    }

    private void analysisResource(Resource resource, List<BeanDefinition> rs, Watchdog watchdog) {
        URL url = resource.getUrl();
        if (CommonConstant.FILE.equals(url.getProtocol())) {
            analysisFile(new File(url.getFile()), rs, watchdog);
            return;
        }

        String fileName = null;
        try {
            fileName = UrlUtils.getFileName(url.openConnection());
        } catch (IOException ignored) {
            return;
        }
        String extension = FilenameUtils.getExtension(fileName);
        Marker marker = Marker.create(extension);
        if (marker instanceof DelegateMarker) {
            return;
        }

        try (InputStream fileInputStream = resource.openStream()) {
            String toString = IOUtils.toString(fileInputStream, UTF_8);
            String[] split = toString.split("\r\n", 2);
            String s = split[0];
            Class<?> aClass = null;
            if (!s.startsWith("package") && !s.startsWith("import")) {
                aClass = ClassUtils.forName(s, ClassLoader.getSystemClassLoader());
            }
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SourceScriptBean.class);
            beanDefinitionBuilder.addConstructorArgValue(toString);
            beanDefinitionBuilder.addConstructorArgValue(extension);
            beanDefinitionBuilder.addConstructorArgValue(Collections.emptyMap());
            beanDefinitionBuilder.addConstructorArgValue(null);
            beanDefinitionBuilder.addConstructorArgValue(aClass);
            AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();
            rs.add(beanDefinition);
        } catch (Exception ignored) {
        }
    }

    private void analysis(File file, List<BeanDefinition> rs, Watchdog watchdog) {
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

