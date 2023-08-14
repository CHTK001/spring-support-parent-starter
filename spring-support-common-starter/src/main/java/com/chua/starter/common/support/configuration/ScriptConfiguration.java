package com.chua.starter.common.support.configuration;

import com.chua.common.support.lang.expression.ExpressionProvider;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.BeanProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;

/**
 * 脚本注入
 *
 * @author CH
 */
public class ScriptConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    private BeanProperties beanProperties;
    private BeanRegister beanRegister;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (StringUtils.isEmpty(beanProperties.getStore())) {
            return;
        }

        register(registry, beanProperties.getStore());
    }

    /**
     * 注册对象
     *
     * @param registry 注册器
     * @param store    文件存放目录
     */
    private void register(BeanDefinitionRegistry registry, String store) {
        File file = new File(store);
        if (!file.exists()) {
            return;
        }

        registry(registry, file);
    }

    /**
     * 注册对象
     *
     * @param registry 注册器
     * @param file     文件存放目录
     */
    private void registry(BeanDefinitionRegistry registry, File file) {
        File[] files = file.listFiles();
        if (null == files) {
            return;
        }

        for (File file1 : files) {
            if (file1.isFile()) {
                continue;
            }

            registryBean(registry, file1);
        }
    }

    /**
     * 注册对象
     *
     * @param registry 注册器
     * @param file1    文件存放目录
     */
    private void registryBean(BeanDefinitionRegistry registry, File file1) {
        String name = file1.getName();
        if (!ClassUtils.isPresent(name)) {
            return;
        }

        Class<?> aClass = ClassUtils.forName(name);
        registryBean(registry, aClass, file1);
    }

    /**
     * 注册对象
     *
     * @param registry 注册器
     * @param aClass   类型
     * @param file1    文件存放目录
     */
    private void registryBean(BeanDefinitionRegistry registry, Class<?> aClass, File file1) {
        File[] files = file1.listFiles();
        if (null == files) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            if (!ExpressionProvider.isMatch(FileUtils.getExtension(file.getName()))) {
                continue;
            }
            registryOneBean(registry, aClass, file);
        }
    }

    /**
     * 注册对象
     *
     * @param registry 注册器
     * @param aClass   类型
     * @param file     脚本文件
     */
    private void registryOneBean(BeanDefinitionRegistry registry, Class<?> aClass, File file) {
        ExpressionProvider expressionProvider = ExpressionProvider.newScript().script(file).build();
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ScriptFactoryBean.class);
        beanDefinitionBuilder.addConstructorArgValue(aClass);
        beanDefinitionBuilder.addConstructorArgValue(expressionProvider);
        if(null != beanRegister) {
            beanRegister.register(aClass, file);
        }
        registry.registerBeanDefinition(FileUtils.getBaseName(file),
                beanDefinitionBuilder.getBeanDefinition()
        );
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.beanProperties = SpringBeanUtils.bindOrCreate(BeanProperties.PRE, BeanProperties.class);
        try {
            this.beanRegister = applicationContext.getBean(BeanRegister.class);
        } catch (BeansException ignored) {
        }
    }


    class ScriptFactoryBean implements FactoryBean<Object> {

        private final Object bean;
        private Class<?> aClass;
        private ExpressionProvider expressionProvider;

        public ScriptFactoryBean(Class<?> aClass, ExpressionProvider expressionProvider) {
            this.expressionProvider = expressionProvider;
            this.bean = this.expressionProvider.createProxy(aClass);
        }

        @Override
        public Object getObject() throws Exception {
            return bean;
        }

        @Override
        public Class<?> getObjectType() {
            return expressionProvider.getType();
        }
    }

    public interface BeanRegister {
        /**
         * 注册文件
         *
         * @param type 类型
         * @param file 文件
         */
        void register(Class<?> type, File file);
    }
}
