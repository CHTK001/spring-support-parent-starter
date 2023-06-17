package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.*;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * redis
 *
 * @author CH
 */
@Component("redis")
public class RedisTableChannel implements TableChannel {
    @Resource
    private ChannelFactory channelFactory;

    public String createUrl(WebsqlConfig config) {
        return null;
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        List<Construct> rs = new LinkedList<>();
        Jedis jedis = channelFactory.getConnection(config, Jedis.class, this::getJedis, it -> !it.isConnected());
        rs.add(Construct.builder().type(Type.DATABASE).icon("DATABASE").id(String.valueOf(1)).pid(String.valueOf(0)).name(config.getConfigDatabase()).build());
        rs.addAll(IntStream.range(2, 18).mapToObj(it -> {
            jedis.select(it - 2);
            return Construct.builder().icon("DATABASE")
                    .id(String.valueOf(it))
                    .realName((it - 2) + "")
                    .pid(String.valueOf(1))
                    .type(Type.TABLE)
                    .action(Action.OPEN)
                    .name((it - 2) + "(" + jedis.dbSize() + ")").build();
        }).collect(Collectors.toList()));
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
        OpenResult result;
        List<Map<String, Object>> rs;
        List<Column> columns;
        int start = (pageNum - 1) * pageSize;
        int end = pageNum * pageSize;
        try (Jedis jedis = getJedis(websqlConfig1)) {
            Set<String> keys = jedis.keys("*");
            result = new OpenResult();
            rs = new LinkedList<>();
            columns = ImmutableBuilder.<Column>builder()
                    .add(Column.builder().columnName("key").build())
                    .add(Column.builder().columnName("value").build())
                    .add(Column.builder().columnName("ttl").build())
                    .newLinkedList();

            int index = 0;
            for (String key : keys) {
                if (start <= index && index < end) {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("key", key);
                    value.put("value", jedis.get(key));
                    value.put("ttl", jedis.ttl(key));
                    rs.add(value);
                }
                index ++;
            }
            result.setTotal(keys.size());
        }

        result.setColumns(columns);
        result.setData(rs);

        return result;
    }

    @Override
    @SuppressWarnings("ALL")
    public Boolean update(WebsqlConfig config, String table, JSONArray data) {
        Jedis jedis = channelFactory.getConnection(config, Jedis.class, this::getJedis, it -> !it.isConnected());
        for (Object datum : data) {
            Map<String, Object> item = (Map<String, Object>) datum;
            Map newData = MapUtils.getType(item, "newData", Map.class);
            Map oldData = MapUtils.getType(item, "oldData", Map.class);
            String action = MapUtils.getString(item, "action");
            try {
                jedis.select(Integer.parseInt(table));
            } catch (NumberFormatException ignored) {
            }
            if("delete".equalsIgnoreCase(action)) {
                jedis.del(MapUtils.getString(oldData, "key"));
                return true;
            }

            if("update".equalsIgnoreCase(action)) {
                jedis.del(MapUtils.getString(oldData, "key"));
            }

            long expire = Long.parseLong(StringUtils.defaultString(MapUtils.getString(newData, "ttl"), "0"));
            String type = StringUtils.defaultString(MapUtils.getString(newData, "type"), "string").toUpperCase();
            switch (type) {
                case "SET":
                    jedis.sadd(MapUtils.getString(newData, "key"), StringUtils.defaultString(MapUtils.getString(newData, "value"), ""));
                    break;
                case "LIST":
                    jedis.lpush(MapUtils.getString(newData, "key"), StringUtils.defaultString(MapUtils.getString(newData, "value"), ""));
                    break;
                case "ZSET":
                    jedis.zadd(MapUtils.getString(newData, "key"), 0.0, StringUtils.defaultString(MapUtils.getString(newData, "value"), ""));
                    break;
                case "HASH":
                    String value = StringUtils.defaultString(MapUtils.getString(newData, "value"), "");
                    if(value.contains(":")) {
                        Map map = Converter.convertIfNecessary(value, Map.class);
                        if(null != map) {
                            map.forEach((k, v) -> {
                                jedis.hset(MapUtils.getString(newData, "key"), k + "", v + "");
                            });
                        }
                    }
                    break;
                default:
                    jedis.set(MapUtils.getString(newData, "key"), StringUtils.defaultString(MapUtils.getString(newData, "value"), ""));
                    break;
            }

            if (expire > 0) {
                jedis.expire(MapUtils.getString(newData, "key"), expire);
            }
        }
        ;


        return true;
    }


    @Override
    public OperatorResult doExecute(WebsqlConfig websqlConfig, String tableName, String action) {
        return new OperatorResult();
    }

    public Jedis getJedis(WebsqlConfig config) {
        Jedis jedis = null;
        if (StringUtils.isNotEmpty(config.getConfigPassword())) {
            String userInfo = StringUtils.defaultString(config.getConfigUsername(), "") + ":" + config.getConfigPassword() + "@";
            try {
                jedis = new Jedis(new URI("redis://" + userInfo + "" + config.getConfigIp() + ":" + config.getConfigPort()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            jedis  = new Jedis(config.getConfigIp(), config.getConfigPort());
        }
        try {
            jedis.select(Integer.parseInt(config.getConfigDatabase()));
        } catch (NumberFormatException ignored) {
        }

        return jedis;
    }
}
