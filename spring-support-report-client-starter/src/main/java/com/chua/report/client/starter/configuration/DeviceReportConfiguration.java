package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.properties.ReportClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * 设备数据上报配置
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Configuration
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chua.report.client.starter.service",
    "com.chua.report.client.starter.task"
})
@EnableConfigurationProperties(ReportClientProperties.class)
@ConditionalOnProperty(prefix = ReportClientProperties.PRE, name = "enable", havingValue = "true")
public class DeviceReportConfiguration {

    /**
     * 配置RestTemplate用于HTTP推送
     */
    @Bean("deviceReportRestTemplate")
    @ConditionalOnMissingBean(name = "deviceReportRestTemplate")
    public RestTemplate deviceReportRestTemplate(ReportClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.info("设备数据上报RestTemplate配置完成 - 连接超时: {}ms, 读取超时: {}ms", 
            properties.getConnectTimeout(), properties.getReadTimeout());
        
        return restTemplate;
    }
}
