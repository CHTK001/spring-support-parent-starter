package com.chua.starter.monitor.endpoint;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.report.Report;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.RedisConnection;

/**
 * redis端点
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/02
 */

@ConditionalOnClass(RedisConnection.class)
@WebEndpoint(id = "redis")
public class RedisEndpoint implements DisposableBean {
    private Report redis;

    @ReadOperation
    public Object read() {
        this.redis = ServiceProvider.of(Report.class).getExtension("Redis");
        return redis.report();
    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(redis);
    }
}
