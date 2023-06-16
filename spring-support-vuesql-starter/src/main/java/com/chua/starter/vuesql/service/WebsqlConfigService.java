package com.chua.starter.vuesql.service;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.cache.annotation.Cacheable;

/**
 * @author CH
 */
public interface WebsqlConfigService extends IService<WebsqlConfig> {

    WebsqlConfig forById(String configId);
}
