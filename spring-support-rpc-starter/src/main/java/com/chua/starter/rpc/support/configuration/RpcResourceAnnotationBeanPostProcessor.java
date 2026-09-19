package com.chua.starter.rpc.support.configuration;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcResource;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.rpc.support.attrbute.RpcAttribute;
import com.chua.starter.rpc.support.properties.RpcProperties;
import com.chua.starter.rpc.support.resource.RpcResourceBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * rpc资源注释配置
 *
 * @since 2024/04/07
 * @author CH
 */
public class RpcResourceAnnotationBeanPostProcessor extends AbstractAnnotationBeanPostProcessor implements BeanFactoryPostProcessor {

    /**
     *
     */
    public static final String BEAN_NAME = "referenceAnnotationBeanPostProcessor";

    /**
     * 缓存 大小
     */
    private static final int CACHE_SIZE = Integer.getInteger(BEAN_NAME + ".cache.size", 32);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, String> injectedFieldReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final ConcurrentMap<InjectionMetadata.InjectedElement, String> injectedMethodReferenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    /**
     * RPC属性
    */
    private RpcProperties rpcProperties;
    /**
     * RPC客户端
    */
    private RpcClient rpcClient;

    /**
     * 创建 rpcresource注解Beanpost处理器 实例
    */
    public RpcResourceAnnotationBeanPostProcessor() {
        /**
         * 父。
         * @param RpcResource.class rpcresource.类
         */
        super(RpcResource.class);
    }


    @Override
    /**
     * Post处理Bean工厂
    */
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return;
        }
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType != null) {
                AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
                try {
                    /**
                     * prepareinjection。
                     * @param metadata metadata
                     */
                    prepareInjection(metadata);
                } catch (BeansException e) {
                    throw e;
                } catch (Exception e) {
                    /**
                     * illegal状态异常。
                     * @param failed" 失败"
                     * @param e e
                     * @return illegal状态异常的结果
                     */
                    throw new IllegalStateException("Prepare rpc reference injection element failed", e);
                }
            }
        }

        if (beanFactory instanceof AbstractBeanFactory) {
            List<BeanPostProcessor> beanPostProcessors = ((AbstractBeanFactory) beanFactory).getBeanPostProcessors();
            for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
                if (beanPostProcessor == this) {
                    beanDefinitionRegistry.removeBeanDefinition(BEAN_NAME);
                    break;
                }
            }
        }
    }

    @Override
    /**
     * Post处理合并Beandefinition
    */
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if(!rpcProperties.isEnable()) {
            return;
        }
        AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
        metadata.checkConfigMembers(beanDefinition);
        try {
            /**
             * prepareinjection。
             * @param metadata metadata
             */
            prepareInjection(metadata);
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param failed" 失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("Prepare reference injection element failed", e);
        }
    }

    @Override
    /**
     * Post处理属性
    */
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        if(!rpcProperties.isEnable()) {
            return pvs;
        }
        try {
            AnnotatedInjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
            /**
             * prepareinjection。
             * @param metadata metadata
             */
            prepareInjection(metadata);
            metadata.inject(bean, beanName, pvs);
        } catch (BeansException ex) {
            throw ex;
        } catch (Throwable ex) {
            /**
             * Bean创建异常。
             * @param beanName Bean名称
             * @param getAnnotationType( 获取注解类型(
             * @return Bean创建异常的结果
             */
            throw new BeanCreationException(beanName, "Injection of @" + getAnnotationType().getSimpleName()
                    + " dependencies is failed", ex);
        }
        return pvs;
    }


    @Override
    /**
     * 执行获取injectedBean
    */
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType, AnnotatedInjectElement injectedElement) throws Exception {
        if(!rpcProperties.isEnable()) {
            return bean;
        }
        if (injectedElement.injectedObject == null) {
            /**
             * illegal状态异常。
             * @param injection" injection"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("The AnnotatedInjectElement of bean should be inited before injection");
        }


        return getBeanFactory().getBean((String) injectedElement.injectedObject);
    }

    /**
     * prepareinjection
     *
     * @param metadata metadata
     */
    protected void prepareInjection(AnnotatedInjectionMetadata metadata) throws BeansException {
        try {
            for (AnnotatedFieldElement fieldElement : metadata.getFieldElements()) {
                if (fieldElement.injectedObject != null) {
                    continue;
                }
                Class<?> injectedType = fieldElement.field.getType();
                AnnotationAttributes attributes = fieldElement.attributes;
                String referenceBeanName = registerReferenceBean(fieldElement.getPropertyName(), injectedType, attributes, fieldElement.field);

                fieldElement.injectedObject = referenceBeanName;
                injectedFieldReferenceBeanCache.put(fieldElement, referenceBeanName);

            }

            for (AnnotatedMethodElement methodElement : metadata.getMethodElements()) {
                if (methodElement.injectedObject != null) {
                    continue;
                }
                Class<?> injectedType = methodElement.getInjectedType();
                AnnotationAttributes attributes = methodElement.attributes;
                String referenceBeanName = registerReferenceBean(methodElement.getPropertyName(), injectedType, attributes, methodElement.method);

                methodElement.injectedObject = referenceBeanName;
                injectedMethodReferenceBeanCache.put(methodElement, referenceBeanName);
            }
        } catch (ClassNotFoundException e) {
            /**
             * Bean创建异常。
             * @param failed" 失败"
             * @param e e
             * @return Bean创建异常的结果
             */
            throw new BeanCreationException("prepare reference annotation failed", e);
        }
    }

    /**
     * 注册引用Bean
     *
     * @param propertyName 财产名称
     * @param injectedType injected类型
     * @param attributes attributes
     * @param member member
     * @return 注册引用Bean的结果
     */
    public String registerReferenceBean(String propertyName, Class<?> injectedType, Map<String, Object> attributes, Member member) throws BeansException {

        boolean renameable = true;
        String referenceBeanName = MapUtils.getString(attributes, RpcAttribute.ID);
        if (StringUtils.hasText(referenceBeanName)) {
            renameable = false;
        } else {
            referenceBeanName = propertyName;
        }

        String checkLocation = "Please check " + member.toString();


 // 获取 接口
        String interfaceName = MapUtils.getString(attributes, RpcAttribute.INTERFACE);
        if (com.chua.common.support.utils.StringUtils.isBlank(interfaceName)) {
            /**
             * Bean创建异常。
             * @param checkLocation 检查位置
             * @return Bean创建异常的结果
             */
            throw new BeanCreationException("Need to specify the 'interfaceName' or 'interfaceClass' attribute of 'bean' if enable generic. " + checkLocation);
        }

 // 检查 Bean definition
        if (beanDefinitionRegistry.containsBeanDefinition(referenceBeanName)) {
            BeanDefinition prevBeanDefinition = beanDefinitionRegistry.getBeanDefinition(referenceBeanName);
            String prevBeanType = prevBeanDefinition.getBeanClassName();
            String prevBeanDesc = referenceBeanName + "[" + prevBeanType + "]";

            int index = 2;
            if (renameable) {
                String newReferenceBeanName = null;
                while (newReferenceBeanName == null || beanDefinitionRegistry.containsBeanDefinition(newReferenceBeanName)
                        || beanDefinitionRegistry.isAlias(newReferenceBeanName)) {
                    newReferenceBeanName = referenceBeanName + "#" + index;
                    index++;
                }
                referenceBeanName = newReferenceBeanName;
            }
        }
        attributes.put(RpcAttribute.ID, referenceBeanName);

 // 注册 the 引用 Bean definition 转为 the Bean工厂
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClassName(RpcResourceBean.class.getName());
        beanDefinition.getPropertyValues().add(RpcAttribute.ID, referenceBeanName);
        beanDefinition.getPropertyValues().add("rpcClient", rpcClient);

 // 设置 attribute instead 的 财产 值
 // Beandefinition.设置attribute(常量.引用_PROPS, attributes);
        beanDefinition.setAttribute(RpcAttribute.INTERFACE_CLASS, injectedType);
        beanDefinition.setAttribute(RpcAttribute.INTERFACE_NAME, interfaceName);

 // 创建 decorated definition for 引用 Bean, Avoid 存在 instantiated When.js.js.js getting the Bean类型 的 引用Bean
 // 参见 org.springframework.Bean.订单.支持.抽象Bean工厂#获取类型for工厂Bean()
        GenericBeanDefinition targetDefinition = new GenericBeanDefinition();
        targetDefinition.setBeanClass(injectedType);
        beanDefinition.setDecoratedDefinition(new BeanDefinitionHolder(targetDefinition, referenceBeanName + "_decorated"));

 // 标志 对象 类型 自 Spring 5.2
 // Beandefinition.设置attribute(常量.对象_类型_ATTRIBUTE, 接口类);

        beanDefinitionRegistry.registerBeanDefinition(referenceBeanName, beanDefinition);
 // 引用Bean管理器.注册引用KeyandBean名称(引用Key, 引用Bean名称);
        return referenceBeanName;
    }


    @Override
    /**
     * 设置环境
    */
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        BindResult<RpcProperties> bindResult = Binder.get(environment).bind(RpcProperties.PRE, RpcProperties.class);
        if(bindResult.isBound()) {
            rpcProperties = bindResult.get();
 // 未显式配置 令牌 时，由 accessKey/secretKey/服务Key 派生，避免配置死区
            if (rpcProperties.getConsumer().getToken() == null) {
                rpcProperties.getConsumer().setToken(deriveToken(rpcProperties));
            }
            this.rpcClient = RpcClient.createClient(rpcProperties.getType().name(),
                    rpcProperties.getRegistry(),
                    rpcProperties.getConsumer(),
                    rpcProperties.getApplicationName());
            return;
        }
        rpcProperties = new RpcProperties();
        rpcProperties.setEnable(false);

    }

    /**
     * 由 {@link RpcProperties#getAccessKey()}、{@link RpcProperties#getSecretKey()}、
     * {@link RpcProperties#getServiceKey()} 派生调用令牌。
     *
     * @param properties RPC 配置
     * @return 派生令牌，字段均未配置时返回 {@code null}
     */
    private static String deriveToken(RpcProperties properties) {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        String serviceKey = properties.getServiceKey();
        boolean any = StringUtils.hasText(accessKey) || StringUtils.hasText(secretKey) || StringUtils.hasText(serviceKey);
        if (!any) {
            return null;
        }
        return com.chua.common.support.utils.DigestUtils.md5Hex(
                accessKey + ":" + secretKey + ":" + serviceKey);
    }

}
