package com.chua.starter.mybatis.endpoint;

import com.chua.starter.mybatis.method.SupportInjector;
import com.chua.starter.mybatis.reloader.Reload;
import org.apache.ibatis.session.Configuration;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.util.Collection;

/**
 * endpoint
 *
 * @author CH
 */
@WebEndpoint(id = "mybatis-extension")
public class MybatisEndpoint {

    private Reload reload;

    private Configuration configuration;
    private SupportInjector supportInjector;

    public MybatisEndpoint(Reload reload, Configuration configuration, SupportInjector supportInjector) {
        this.reload = reload;
        this.configuration = configuration;
        this.supportInjector = supportInjector;
    }

    /**
     * 热加载
     *
     * @param name xmlname/mappername
     * @param type 类型; name, mapper
     * @return 结果
     */
    @WriteOperation
    public String reload(@Selector String name, @Selector String type) {
        if (null == reload) {
            return "加载器不存在";
        }

        if ("name".equalsIgnoreCase(type)) {
            if (null == supportInjector) {
                return "mapper注入器不存在";
            }
            return this.supportInjector.refresh(name);
        }
        return this.reload.reload(name);
    }

    /**
     * 热加载
     *
     * @return 结果
     */
    @ReadOperation
    public Collection<String> list() {
        return configuration.getMappedStatementNames();
    }
}
