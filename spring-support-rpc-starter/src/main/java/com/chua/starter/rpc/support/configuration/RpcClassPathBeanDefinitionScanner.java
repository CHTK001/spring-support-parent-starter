package com.chua.starter.rpc.support.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors;

/**
* RPC组件包扫描器
* <p>用于扫描指定包路径下的RPC相关组件，并将其注册为Spring Bean</p>
*
* @since 2023-01-01
* @author CH
 */
@Getter
@Setter
public class RpcClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {

    /**
    * 扫描包路径与Bean定义集合的映射关系缓存
    * <p>key: 包路径, value: 该包下的所有候选Bean定义</p>
    */
    private final ConcurrentMap<String, Set<BeanDefinition>> beanDefinitionMap = new ConcurrentHashMap<>();


    /**
    * 构造RPC包扫描器实例
    *
    * @param registry           Bean定义注册器，用于注册扫描到的Bean定义
    *                           <p>例如: {@code DefaultListableBeanFactory} 实例</p>
    * @param useDefaultFilters  是否使用默认过滤器
    *                           <p>true: 使用Spring默认的@Component等注解过滤规则</p>
    *                           <p>false: 不使用默认过滤器，需手动添加过滤规则</p>
    * @param environment        环境配置信息，包含应用的配置属性
    *                           <p>例如: {@code StandardEnvironment} 实例</p>
    * @param resourceLoader     资源加载器，用于加载类路径资源
    *                           <p>例如: {@code DefaultResourceLoader} 实例</p>
    */
    public RpcClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters, Environment environment,
                                             ResourceLoader resourceLoader) {

        /**
        * 父。
        * @param registry registry
        * @param useDefaultFilters use默认过滤器
        */
        super(registry, useDefaultFilters);

        /**
        * 设置环境。
        * @param environment 环境
        */
        setEnvironment(environment);

        /**
        * 设置resource加载。
        * @param resourceLoader resource加载
        */
        setResourceLoader(resourceLoader);

        /**
        * 注册注解配置处理器。
        * @param registry registry
        */
        registerAnnotationConfigProcessors(registry);

    }

    /**
    * 构造RPC包扫描器实例（不使用默认过滤器）
    *
    * @param registry       Bean定义注册器，用于注册扫描到的Bean定义
    *                       <p>例如: {@code DefaultListableBeanFactory} 实例</p>
    * @param environment    环境配置信息，包含应用的配置属性
    *                       <p>例如: {@code StandardEnvironment} 实例</p>
    * @param resourceLoader 资源加载器，用于加载类路径资源
    *                       <p>例如: {@code DefaultResourceLoader} 实例</p>
    */
    public RpcClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, Environment environment,
                                             ResourceLoader resourceLoader) {

        /**
        * this。
        * @param registry registry
        * @param false false
        * @param environment 环境
        * @param resourceLoader resource加载
        */
        this(registry, false, environment, resourceLoader);

    }

    /**
    * 查找指定包路径下的候选组件
    *
    * @param basePackage 基础包路径，例如: "com.example.服务"
    * @return 指定包路径下的所有候选Bean定义集合
    * @throws IllegalArgumentException 当基础包路径为空时抛出异常
    */
    @Override
    public Set<BeanDefinition> findCandidateComponents(String basePackage) {
        // 参数校验
        if (basePackage == null || basePackage.trim().isEmpty()) {
            /**
            * IllegalArgumentException。
            * @param "基础包路径不能为空" "基础包路径不能为空"
            * @return IllegalArgumentException的结果
            */
            throw new IllegalArgumentException("基础包路径不能为空");
        }
        
        Set<BeanDefinition> beanDefinitions = beanDefinitionMap.get(basePackage);
        // 如果缓存中不存在，则执行扫描操作
        if (Objects.isNull(beanDefinitions)) {
            beanDefinitions = super.findCandidateComponents(basePackage);
            beanDefinitionMap.put(basePackage, beanDefinitions);
        }
        return beanDefinitions;
    }
}
