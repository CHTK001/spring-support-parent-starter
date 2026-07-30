package com.chua.starter.panel.config;

import com.chua.starter.panel.cache.InMemoryPanelConnectionCache;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.panel.cache.PanelConnectionCache;
import com.chua.starter.panel.cache.PanelSshSessionRegistry;
import com.chua.starter.panel.controller.PanelInfluxController;
import com.chua.starter.panel.controller.PanelInfluxV3Controller;
import com.chua.starter.panel.controller.PanelJdbcController;
import com.chua.starter.panel.controller.PanelDatasourceController;
import com.chua.starter.panel.controller.PanelMongoController;
import com.chua.starter.panel.controller.PanelDockerController;
import com.chua.starter.panel.controller.PanelKafkaController;
import com.chua.starter.panel.controller.PanelMqttController;
import com.chua.starter.panel.controller.PanelRabbitMQController;
import com.chua.starter.panel.controller.PanelNacosController;
import com.chua.starter.panel.controller.PanelNginxController;
import com.chua.starter.panel.controller.PanelPrometheusController;
import com.chua.starter.panel.controller.PanelRedisController;
import com.chua.starter.panel.controller.PanelZookeeperController;
import com.chua.starter.panel.controller.PanelSystemConfigController;
import com.chua.starter.panel.service.DockerPanelService;
import com.chua.starter.panel.service.InfluxPanelService;
import com.chua.starter.panel.service.InfluxV3PanelService;
import com.chua.starter.panel.service.JdbcPanelService;
import com.chua.starter.panel.service.KafkaPanelService;
import com.chua.starter.panel.service.MqttPanelService;
import com.chua.starter.panel.service.RabbitMQPanelService;
import com.chua.starter.panel.service.MongoPanelService;
import com.chua.starter.panel.service.NacosPanelService;
import com.chua.starter.panel.service.NginxPanelService;
import com.chua.starter.panel.service.PanelAiService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.service.PanelDatasourceService;
import com.chua.starter.panel.service.PanelDocumentService;
import com.chua.starter.panel.service.PanelRemarkService;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.panel.service.PanelSqlTemplateService;
import com.chua.starter.panel.service.PrometheusPanelService;
import com.chua.starter.panel.service.RedisPanelService;
import com.chua.starter.panel.service.ZookeeperPanelService;
import com.chua.starter.panel.service.impl.DefaultInfluxPanelService;
import com.chua.starter.panel.service.impl.DefaultInfluxV3PanelService;
import com.chua.starter.panel.service.impl.DefaultDockerPanelService;
import com.chua.starter.panel.service.impl.DefaultKafkaPanelService;
import com.chua.starter.panel.service.impl.DefaultMqttPanelService;
import com.chua.starter.panel.service.impl.DefaultRabbitMQPanelService;
import com.chua.starter.panel.service.impl.DefaultJdbcPanelService;
import com.chua.starter.panel.service.impl.DefaultMongoPanelService;
import com.chua.starter.panel.service.impl.DefaultNacosPanelService;
import com.chua.starter.panel.service.impl.DefaultNginxPanelService;
import com.chua.starter.panel.service.impl.DefaultPanelAiService;
import com.chua.starter.panel.service.impl.DefaultPanelConnectionService;
import com.chua.starter.panel.service.impl.DefaultPanelDatasourceService;
import com.chua.starter.panel.service.impl.DefaultPanelDocumentService;
import com.chua.starter.panel.service.impl.DefaultPanelRemarkService;
import com.chua.starter.panel.service.impl.DefaultPanelSqlTemplateService;
import com.chua.starter.panel.service.impl.DefaultPrometheusPanelService;
import com.chua.starter.panel.service.impl.DefaultRedisPanelService;
import com.chua.starter.panel.service.impl.DefaultZookeeperPanelService;
import com.influxdb.client.InfluxDBClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSch;
import com.mongodb.client.MongoClient;
import org.apache.curator.framework.CuratorFramework;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 面板模块自动配置。
 *
 * @author CH
 */
@AutoConfiguration
@EnableConfigurationProperties(PanelProperties.class)
@EnableScheduling
@MapperScan("com.chua.starter.panel.software.mapper")
@ComponentScan("com.chua.starter.panel.software")
@ConditionalOnProperty(prefix = PanelProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class PanelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PanelConnectionCache panelConnectionCache(PanelProperties properties) {
        return new InMemoryPanelConnectionCache(properties.getConnectionCacheTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelConnectionService panelConnectionService(PanelConnectionCache panelConnectionCache) {
        return new DefaultPanelConnectionService(panelConnectionCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcPanelService jdbcPanelService(
            PanelConnectionCache panelConnectionCache,
            PanelProperties properties,
            ObjectProvider<ChatClient> chatClientProvider) {
        return new DefaultJdbcPanelService(panelConnectionCache, properties, chatClientProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDocumentService panelDocumentService(JdbcPanelService jdbcPanelService) {
        return new DefaultPanelDocumentService(jdbcPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelAiService panelAiService(
            JdbcPanelService jdbcPanelService,
            PanelProperties properties,
            ObjectProvider<ChatClient> chatClientProvider) {
        return new DefaultPanelAiService(jdbcPanelService, properties, chatClientProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelRemarkService panelRemarkService() {
        return new DefaultPanelRemarkService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDatasourceService panelDatasourceService(
            PanelProperties properties,
            ObjectMapper objectMapper,
            PanelConnectionService panelConnectionService,
            ObjectProvider<RedisPanelService> redisPanelServiceProvider,
            ObjectProvider<MongoPanelService> mongoPanelServiceProvider,
            ObjectProvider<ZookeeperPanelService> zookeeperPanelServiceProvider,
            ObjectProvider<PrometheusPanelService> prometheusPanelServiceProvider,
            ObjectProvider<InfluxPanelService> influxPanelServiceProvider,
            ObjectProvider<InfluxV3PanelService> influxV3PanelServiceProvider,
            ObjectProvider<NacosPanelService> nacosPanelServiceProvider,
            ObjectProvider<NginxPanelService> nginxPanelServiceProvider,
            ObjectProvider<DockerPanelService> dockerPanelServiceProvider,
            ObjectProvider<KafkaPanelService> kafkaPanelServiceProvider) {
        return new DefaultPanelDatasourceService(
                properties,
                objectMapper,
                panelConnectionService,
                redisPanelServiceProvider,
                mongoPanelServiceProvider,
                zookeeperPanelServiceProvider,
                prometheusPanelServiceProvider,
                influxPanelServiceProvider,
                influxV3PanelServiceProvider,
                nacosPanelServiceProvider,
                nginxPanelServiceProvider,
                dockerPanelServiceProvider,
                kafkaPanelServiceProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelSqlTemplateService panelSqlTemplateService() {
        return new DefaultPanelSqlTemplateService();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisPanelService redisPanelService() {
        return new DefaultRedisPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelRedisController panelRedisController(RedisPanelService redisPanelService) {
        return new PanelRedisController(redisPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelSystemConfigController panelSystemConfigController() {
        return new PanelSystemConfigController();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelDatasourceController panelDatasourceController(PanelDatasourceService panelDatasourceService) {
        return new PanelDatasourceController(panelDatasourceService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MongoClient.class)
    public MongoPanelService mongoPanelService() {
        return new DefaultMongoPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MongoClient.class)
    public PanelMongoController panelMongoController(MongoPanelService mongoPanelService) {
        return new PanelMongoController(mongoPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CuratorFramework.class)
    public ZookeeperPanelService zookeeperPanelService() {
        return new DefaultZookeeperPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CuratorFramework.class)
    public PanelZookeeperController panelZookeeperController(ZookeeperPanelService zookeeperPanelService) {
        return new PanelZookeeperController(zookeeperPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrometheusPanelService prometheusPanelService() {
        return new DefaultPrometheusPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    public NacosPanelService nacosPanelService() {
        return new DefaultNacosPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelPrometheusController panelPrometheusController(PrometheusPanelService prometheusPanelService) {
        return new PanelPrometheusController(prometheusPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelNacosController panelNacosController(NacosPanelService nacosPanelService) {
        return new PanelNacosController(nacosPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(InfluxDBClient.class)
    public InfluxPanelService influxPanelService() {
        return new DefaultInfluxPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(InfluxDBClient.class)
    public PanelInfluxController panelInfluxController(InfluxPanelService influxPanelService) {
        return new PanelInfluxController(influxPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public InfluxV3PanelService influxV3PanelService() {
        return new DefaultInfluxV3PanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelInfluxV3Controller panelInfluxV3Controller(InfluxV3PanelService influxV3PanelService) {
        return new PanelInfluxV3Controller(influxV3PanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JSch.class)
    public PanelSshSessionRegistry panelSshSessionRegistry() {
        return new PanelSshSessionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JSch.class)
    public NginxPanelService nginxPanelService(PanelSshSessionRegistry panelSshSessionRegistry) {
        return new DefaultNginxPanelService(panelSshSessionRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JSch.class)
    public PanelNginxController panelNginxController(NginxPanelService nginxPanelService, ObjectMapper objectMapper) {
        return new PanelNginxController(nginxPanelService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.github.dockerjava.api.DockerClient")
    public DockerPanelService dockerPanelService() {
        return new DefaultDockerPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.github.dockerjava.api.DockerClient")
    public PanelDockerController panelDockerController(DockerPanelService dockerPanelService) {
        return new PanelDockerController(dockerPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.apache.kafka.clients.admin.AdminClient")
    public KafkaPanelService kafkaPanelService() {
        return new DefaultKafkaPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.apache.kafka.clients.admin.AdminClient")
    public PanelKafkaController panelKafkaController(KafkaPanelService kafkaPanelService) {
        return new PanelKafkaController(kafkaPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMQPanelService rabbitMQPanelService() {
        return new DefaultRabbitMQPanelService();
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelRabbitMQController panelRabbitMQController(RabbitMQPanelService rabbitMQPanelService) {
        return new PanelRabbitMQController(rabbitMQPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttPanelService mqttPanelService(
            ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        return new DefaultMqttPanelService(socketSessionTemplateProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelMqttController panelMqttController(MqttPanelService mqttPanelService) {
        return new PanelMqttController(mqttPanelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PanelJdbcController panelJdbcController(
            PanelConnectionService panelConnectionService,
            JdbcPanelService jdbcPanelService,
            PanelDocumentService panelDocumentService,
            PanelAiService panelAiService,
            PanelRemarkService panelRemarkService,
            PanelSqlTemplateService panelSqlTemplateService) {
        return new PanelJdbcController(
                panelConnectionService,
                jdbcPanelService,
                panelDocumentService,
                panelAiService,
                panelRemarkService,
                panelSqlTemplateService);
    }
}
