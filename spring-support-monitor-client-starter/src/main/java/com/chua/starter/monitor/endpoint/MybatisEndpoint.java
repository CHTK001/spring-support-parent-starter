package com.chua.starter.monitor.endpoint;

import com.chua.starter.monitor.mybatis.SupportInjector;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import jakarta.annotation.Resource;

/**
 * redis端点
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/02
 */

@ConditionalOnBean(SupportInjector.class)
@WebEndpoint(id = "mybatis")
public class MybatisEndpoint {
    @Resource
    private SupportInjector supportInjector;;
    @ReadOperation
    public Object read() {
        return supportInjector.getStatementMap().keySet();
    }

}
