package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.properties.MultiDataSourceProperties;
import com.chua.starter.datasource.properties.MultiDataSourceSettingProperties;
import com.chua.starter.datasource.properties.MultiHikariDataSourceProperties;
import com.chua.starter.datasource.properties.TransactionProperties;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.interceptor.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 事务
 *
 * @author CH
 * @since 2021-07-19
 */
@EnableConfigurationProperties({
        TransactionProperties.class,
        MultiDataSourceSettingProperties.class,
        MultiDataSourceProperties.class,
        MultiHikariDataSourceProperties.class,
})
@ConditionalOnClass(name = "org.springframework.jdbc.datasource.DataSourceTransactionManager")
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE + 10)
public class DataSourceTransactionConfiguration implements
        BeanDefinitionRegistryPostProcessor,
        ApplicationContextAware {
    /**
     * 写事务的超时时间为10秒
     */
    private static final int TX_METHOD_TIMEOUT = 10;
    private static final String ADVISOR = "_advisor";
    private static final String INTERCEPTOR = "_interceptor";

    private TransactionProperties transactionProperties;
    /**
     * 只读事务
     */
    private final String[] readOnly = new String[]{"get*", "query*", "find*", "list*", "count*", "exist*", "search*", "fetch*"};
    /**
     * 写事务
     */
    private final String[] writeOnly = new String[]{"add*", "save*", "insert*", "update*", "modify*", "delete*", "remove*"};
    /**
     * 无事务
     */
    private final String[] noOnly = new String[]{"noTx*"};
    /**
     * 注入事务管理器
     */
    public ApplicationContext applicationContext;
    /**
     * restful包下所有service包或者service的子包的任意类的任意方法
     */
    private static final String AOP_POINTCUT_EXPRESSION = "execution (* com.jun.cloud.restful..*.service..*.*(..))";

    /**
     * 设置事务拦截器
     */
    public TransactionAttributeSource springTransactionAttributeSource() {
        String[] readTx = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(transactionProperties.getReadOnly()).toArray(new String[0]);
        readTx = readTx.length == 0 ? readOnly : readTx;

        String[] writeTx = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(transactionProperties.getWriteOnly()).toArray(new String[0]);
        writeTx = writeTx.length == 0 ? writeOnly : writeTx;

        String[] noTx = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(transactionProperties.getNoTx()).toArray(new String[0]);
        noTx = noTx.length == 0 ? noOnly : noTx;

        Map<String, TransactionAttribute> txMap = new HashMap<>();

        if (readTx.length != 0) {
            //这里配置只读事务
            RuleBasedTransactionAttribute readOnlyTx = new RuleBasedTransactionAttribute();
            readOnlyTx.setReadOnly(true);
            readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

            for (String tx : readTx) {
                txMap.put(tx, readOnlyTx);
            }
        }

        if (writeTx.length != 0) {
            /*
             * 必须带事务
             * 当前存在事务就使用当前事务，当前不存在事务,就开启一个新的事务
             */
            RuleBasedTransactionAttribute requiredTx = new RuleBasedTransactionAttribute();
            //检查型异常也回滚
            requiredTx.setRollbackRules(Collections.singletonList(new RollbackRuleAttribute(Exception.class)));
            requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            requiredTx.setTimeout(Optional.ofNullable(transactionProperties.getTimeout()).orElse(TX_METHOD_TIMEOUT));

            for (String tx : writeTx) {
                txMap.put(tx, requiredTx);
            }
        }

        if (noTx.length != 0) {
            /*
             * 无事务地执行，挂起任何存在的事务
             */
            RuleBasedTransactionAttribute ruleBasedTransactionAttribute = new RuleBasedTransactionAttribute();
            ruleBasedTransactionAttribute.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

            for (String tx : noTx) {
                txMap.put(tx, ruleBasedTransactionAttribute);
            }
        }
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.setNameMap(txMap);

        return source;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        if (!transactionProperties.isEnable()) {
            return;
        }

        Map<String, DataSourceTransactionManager> beansOfType = applicationContext.getBeansOfType(DataSourceTransactionManager.class);
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
        Object next = beansWithAnnotation.values().iterator().next();
        String mainPackage = next.getClass().getPackage().getName();
        String mapper = Strings.isNullOrEmpty(transactionProperties.getTxMapper()) ? execute(mainPackage) : transactionProperties.getTxMapper();

        for (Map.Entry<String, DataSourceTransactionManager> entry : beansOfType.entrySet()) {
            String name = entry.getKey();
            TransactionManager transactionManager = entry.getValue();
            TransactionInterceptor transactionInterceptor = new TransactionInterceptor(transactionManager, springTransactionAttributeSource());
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(DefaultPointcutAdvisor.class);
            AspectJExpressionPointcut expressionPointcut = new AspectJExpressionPointcut();
            expressionPointcut.setExpression(mapper);
            beanDefinitionBuilder.addConstructorArgValue(expressionPointcut);
            beanDefinitionBuilder.addConstructorArgValue(transactionInterceptor);

            beanDefinitionRegistry.registerBeanDefinition(name + ADVISOR, beanDefinitionBuilder.getBeanDefinition());
        }
    }

    /**
     * 文件映射
     *
     * @param mainPackage 文件包
     * @return 事务
     */
    private String execute(String mainPackage) {
        return "execution (* " + mainPackage + "..*..*(..))";
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.transactionProperties = applicationContext.getBean(TransactionProperties.class);
    }
}
