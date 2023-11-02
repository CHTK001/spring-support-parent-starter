package com.chua.starter.common.support.spi;

import com.chua.common.support.spi.ServiceDefinition;
import com.chua.common.support.spi.finder.AbstractServiceFinder;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * spring
 * @author CH
 */
public class SpringServiceFinder extends AbstractServiceFinder {

    @Override
    protected List<ServiceDefinition> find() {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        if(null == applicationContext) {
            return Collections.emptyList();
        }
        List<ServiceDefinition> rs = new LinkedList<>();
        Map<String, ?> beansOfType = applicationContext.getBeansOfType(service);
        for (Map.Entry<String, ?> entry : beansOfType.entrySet()) {
            rs.addAll(buildDefinition(entry.getValue(), null, entry.getKey(), null));
        }
        return rs;
    }
}
