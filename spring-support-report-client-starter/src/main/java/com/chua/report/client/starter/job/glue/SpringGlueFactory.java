package com.chua.report.client.starter.job.glue;

import com.chua.spring.support.configuration.SpringBeanUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class SpringGlueFactory extends GlueFactory{
    private static final Logger log = LoggerFactory.getLogger(SpringGlueFactory.class);

    public SpringGlueFactory() {
    }

    public void injectService(Object instance) {
        if (instance != null) {
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            if (applicationContext != null) {
                Field[] fields = instance.getClass().getDeclaredFields();
                Field[] var3 = fields;
                int var4 = fields.length;

                for(int var5 = 0; var5 < var4; ++var5) {
                    Field field = var3[var5];
                    if (!Modifier.isStatic(field.getModifiers())) {
                        Object fieldBean = null;
                        if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                            try {
                                Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                                if (resource.name() != null && resource.name().length() > 0) {
                                    fieldBean = applicationContext.getBean(resource.name());
                                } else {
                                    fieldBean = applicationContext.getBean(field.getName());
                                }
                            } catch (Exception var11) {
                            }

                            if (fieldBean == null) {
                                fieldBean = applicationContext.getBean(field.getType());
                            }
                        } else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                            Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                            if (qualifier != null && qualifier.value() != null && qualifier.value().length() > 0) {
                                fieldBean = applicationContext.getBean(qualifier.value());
                            } else {
                                fieldBean = applicationContext.getBean(field.getType());
                            }
                        }

                        if (fieldBean != null) {
                            field.setAccessible(true);

                            try {
                                field.set(instance, fieldBean);
                            } catch (IllegalArgumentException var9) {
                                log.error(var9.getMessage(), var9);
                            } catch (IllegalAccessException var10) {
                                log.error(var10.getMessage(), var10);
                            }
                        }
                    }
                }

            }
        }
    }
}
