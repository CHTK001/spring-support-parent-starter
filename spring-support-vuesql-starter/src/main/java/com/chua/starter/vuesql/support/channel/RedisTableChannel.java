package com.chua.starter.vuesql.support.channel;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.SqlResult;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * redis
 *
 * @author CH
 */
@Component("redis")
public class RedisTableChannel implements TableChannel {
    @Override
    public String createUrl(WebsqlConfig config) {
        return null;
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        List<Construct> rs = new LinkedList<>();
        rs.add(Construct.builder().type(Type.DATABASE).id(1).pid(0).name(config.getConfigDatabase()).build());
        rs.addAll(IntStream.range(2, 18).mapToObj(it -> {
            return Construct.builder().id(it).pid(1).type(Type.TABLE).action(Action.OPEN).name(it + "").build();
        }).collect(Collectors.toList()));
        return rs;
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        return null;
    }

    @Override
    public SqlResult execute(WebsqlConfig websqlConfig, String sql, Integer pageNum, Integer pageSize, String sortColumn, String sortType) {
        return null;
    }

    @Override
    public SqlResult explain(WebsqlConfig websqlConfig, String sql) {
        return null;
    }

    public RedissonClient getRedisson(WebsqlConfig websqlConfig) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + websqlConfig.getConfigIp() + ":" + websqlConfig.getConfigPort()).setPassword(websqlConfig.getConfigPassword());
        return Redisson.create(config);
    }

    public Jedis jedis(WebsqlConfig config) {
        Jedis jedis = new Jedis(config.getConfigIp(), config.getConfigPort());
        return jedis;
    }
}
