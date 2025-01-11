package com.chua.starter.pay.support.configuration;

import com.chua.common.support.chain.ChainContext;
import com.chua.common.support.chain.FilterChain;
import com.chua.common.support.chain.filter.Filter;
import com.chua.common.support.collection.Option;
import com.chua.common.support.collection.Options;
import com.chua.common.support.eventbus.EventRouter;
import com.chua.common.support.eventbus.Eventbus;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.common.support.utils.*;
import com.chua.mica.support.client.MicaClient;
import com.chua.mica.support.client.session.MicaSession;
import com.chua.mica.support.server.MicaServer;
import com.chua.redis.support.eventbus.RedisEventbus;
import com.chua.starter.pay.support.properties.PayMqttProperties;
import com.chua.starter.pay.support.properties.PayNotifyProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
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

import java.util.UUID;

/**
 * 支付配置
 *
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
public class PayConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, DisposableBean, CommandLineRunner {


    private ServerProperties serverProperties;
    private MicaServer micaServer;
    private static MicaClient micaClient;
    private PayNotifyProperties payNotifyProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!payNotifyProperties.isEnable()) {
            return;
        }
        try {
            registerMqttServer();
            registryMqttClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void registryMqttClient() {
        PayNotifyProperties.MqttConfig mqttConfig = payNotifyProperties.getMqttConfig();
        if(null == micaClient) {
            micaClient = new MicaClient(
                    ClientSetting.builder()
                            .clientId(UUID.randomUUID().toString())
                            .port(NumberUtils.isPositive(mqttConfig.getPort(), serverProperties.getPort() + 10000))
                            .host(StringUtils.defaultString(mqttConfig.getHost(), "127.0.0.1"))
                            .build()
            );
            Thread.ofVirtual()
                    .name("延迟启动")
                    .start(() -> {
                        ThreadUtils.sleepSecondsQuietly(5);
                        micaClient.connect();
                        MicaSession session = (MicaSession) micaClient.createSession("default");
                        PayClientConfiguration.factory.register(session);
                    });
        }


    }

    private void registerMqttServer() {
        PayNotifyProperties.MqttConfig mqttConfig = payNotifyProperties.getMqttConfig();
        if (!mqttConfig.isOpenServer()) {
            return;
        }
        micaServer = new MicaServer(
                ServerSetting.builder()
                        .addDefaultMapping(false)
                        .options(new Options().addOption("defaultPublish", new Option("false")))
                        .port(NumberUtils.isPositive(mqttConfig.getPort(), serverProperties.getPort() + 10000))
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
        payNotifyProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(PayNotifyProperties.PRE, PayNotifyProperties.class);
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(micaServer);
        IoUtils.closeQuietly(micaClient);
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
