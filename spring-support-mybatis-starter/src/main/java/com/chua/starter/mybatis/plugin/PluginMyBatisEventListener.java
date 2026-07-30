package com.chua.starter.mybatis.plugin;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.mybatis.plugin.PluginMyBatisMapperRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PluginMyBatisEventListener {

    /**
     * SqlSessionFactory 列表
     */
    @Autowired(required = false)
    private List<SqlSessionFactory> sqlSessionFactories;

    /**
     * MyBatis Mapper 注册器
     */
    private final PluginMyBatisMapperRegistry mapperRegistry;

    public PluginMyBatisEventListener() {
        this.mapperRegistry = ServiceProvider.of(PluginMyBatisMapperRegistry.class).getExtension();
    }
}