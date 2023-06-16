package com.chua.starter.vuesql.service.impl;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.vuesql.mapper.WebsqlConfigMapper;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.service.WebsqlConfigService;
/**
 *    
 * @author CH
 */     
@Service
public class WebsqlConfigServiceImpl extends ServiceImpl<WebsqlConfigMapper, WebsqlConfig> implements WebsqlConfigService{

    @Override
    @Cacheable(cacheNames = "configId", key = "#configId")
    public WebsqlConfig forById(String configId) {
        return baseMapper.selectById(configId);
    }
}
