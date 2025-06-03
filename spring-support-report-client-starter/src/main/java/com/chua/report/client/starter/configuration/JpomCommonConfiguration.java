package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.jpom.agent.ClientJpomApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * @author CH
 * @since 2025/6/3 8:52
 */
@ComponentScan(value = {"com.chua.report.client.starter.jpom.common"})
@Import({ClientJpomApplication.class})
public class JpomCommonConfiguration {
}
