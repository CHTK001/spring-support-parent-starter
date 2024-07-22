package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.monitor.request.MonitorRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;


@Spi("ip")
public class IpAdaptor implements Adaptor<MonitorRequest>{

    @AutoInject
    private RedisTemplate stringRedisTemplate;
    @Override
    public void doAdaptor(MonitorRequest o) {
        ValueOperations valueOperations = stringRedisTemplate.opsForValue();
        String s = o.getUid() + ":" + o.getData();
        valueOperations.increment(s);
        Long expire = stringRedisTemplate.getExpire(s);
        if(null == expire || expire < 1) {
            stringRedisTemplate.expire(s, 10, TimeUnit.MINUTES);
        }
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

}
