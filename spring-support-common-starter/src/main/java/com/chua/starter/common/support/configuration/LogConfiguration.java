package com.chua.starter.common.support.configuration;

import org.springframework.beans.factory.InitializingBean;

/**
 * log
 *
 * @author CH
 * @see com.chua.common.support.log.Log
 */
public class LogConfiguration implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("global.logger.impl", "slf4j");
    }
}
