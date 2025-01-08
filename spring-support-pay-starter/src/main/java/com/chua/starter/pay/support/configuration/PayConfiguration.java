package com.chua.starter.pay.support.configuration;

import com.chua.common.support.eventbus.EventRouter;
import com.chua.common.support.eventbus.Eventbus;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.utils.IoUtils;
import com.chua.mica.support.client.MicaClient;
import com.chua.mica.support.server.MicaServer;
import com.chua.redis.support.eventbus.RedisEventbus;
import com.chua.starter.pay.support.properties.PayMqttProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 支付配置
 * @author CH
 * @since 2024/12/27
 */
@Slf4j
@MapperScan("com.chua.starter.pay.support.mapper")
@ComponentScan({
        "com.chua.starter.pay.support.service",
        "com.chua.starter.pay.support.controller",
        "com.chua.starter.pay.support.scheduler",
})
@EnableConfigurationProperties(PayMqttProperties.class)
@EnableScheduling
@EnableTransactionManagement
public class PayConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, DisposableBean {


    private ServerProperties serverProperties;
    private MicaServer micaServer;
    private MicaClient micaClient;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            registerMqttServer();
            registryMqttClient();
            registry.registerBeanDefinition("micaClient", BeanDefinitionBuilder.genericBeanDefinition(MicaClient.class, () -> micaClient).getBeanDefinition());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void registryMqttClient() {
        micaClient = new MicaClient(
                ClientSetting.builder()
                        .port(serverProperties.getPort() + 1000)
                        .host("0.0.0.0")
                        .build()
        );

        micaClient.connect();
    }

    private void registerMqttServer() {
        micaServer = new MicaServer(
                ServerSetting.builder()
                        .port(serverProperties.getPort() + 1000)
                        .host("0.0.0.0")
                        .build()
        );
        try {
            micaServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void registerDataSource() {
        //        Properties properties = new Properties();
//        Map<String, DataSource> dataSourceMap = new HashMap<>(1);
//        Collection<DataSource> beanList = SpringBeanUtils.getBeanList(DataSource.class);
//        int index = 0;
//        for (DataSource dataSource : beanList) {
//            if (dataSource instanceof HikariDataSource hikariDataSource) {
//                dataSourceMap.put("dataSource" + index ++,  hikariDataSource);
//            }
//        }
//        List< LogicTable > logicTables = List.of(
//                LogicTable.builder()
//                        .logicTable("pay_merchant_order_water")
//                        .strategy(Strategy.TABLE)
//                        .logicColumnName("create_time")
//                        .build()
//        );
//        V5ShardingTableFactory v5ShardingTableFactory = new V5ShardingTableFactory(properties, dataSourceMap, logicTables);
//        DataSource dataSource = v5ShardingTableFactory.dataSource();
//        System.out.println();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        serverProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("server", ServerProperties.class);
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(micaServer);
        IoUtils.closeQuietly(micaClient);
    }
}
