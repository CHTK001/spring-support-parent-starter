package com.chua.starter.vuesql.support.channel;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.OpenResult;
import com.chua.starter.vuesql.pojo.SqlResult;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

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


    private static final ThreadLocal<JedisPool> LOCAL = new InheritableThreadLocal<>();
    @Override
    public String createUrl(WebsqlConfig config) {
        return null;
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        List<Construct> rs = new LinkedList<>();
        rs.add(Construct.builder().type(Type.DATABASE).icon("DATABASE").id(1).pid(0).name(config.getConfigDatabase()).build());
        rs.addAll(IntStream.range(2, 18).mapToObj(it -> Construct.builder().icon("DATABASE").id(it).pid(1).type(Type.TABLE).action(Action.OPEN).name((it - 2) + "").build()).collect(Collectors.toList()));
        return rs;
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        List<Keyword> rs = new LinkedList<>();
        rs.add(Keyword.builder().text("SET").build());
        rs.add(Keyword.builder().text("GET").build());
        rs.add(Keyword.builder().text("KEYS").build());
        rs.add(Keyword.builder().text("EXISTS").build());
        rs.add(Keyword.builder().text("EXPIRE").build());
        rs.add(Keyword.builder().text("TYPE").build());
        return rs;
    }

    @Override
    public SqlResult execute(WebsqlConfig websqlConfig, String sql, Integer pageNum, Integer pageSize, String sortColumn, String sortType) {
        return new SqlResult();
    }

    @Override
    public SqlResult explain(WebsqlConfig websqlConfig, String sql) {
        return new SqlResult();
    }

    @Override
    public OpenResult openTable(WebsqlConfig websqlConfig, String tableName, Integer pageNum, Integer pageSize) {
        WebsqlConfig websqlConfig1 = BeanUtils.copyProperties(websqlConfig, WebsqlConfig.class);
        websqlConfig1.setConfigDatabase(tableName);
        RedissonClient redisson = getRedisson(websqlConfig1);
        long count = redisson.getKeys().count();
        return null;
    }

    public RedissonClient getRedisson(WebsqlConfig websqlConfig) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + websqlConfig.getConfigIp() + ":" + websqlConfig.getConfigPort()).setPassword(websqlConfig.getConfigPassword());
        if(StringUtils.isNotEmpty(websqlConfig.getConfigDatabase())) {
            try {
                singleServerConfig.setDatabase(Integer.parseInt(websqlConfig.getConfigDatabase()));
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return Redisson.create(config);
    }

    public Jedis jedis(WebsqlConfig config) {
        Jedis jedis = new Jedis(config.getConfigIp(), config.getConfigPort());
        return jedis;
    }
}
