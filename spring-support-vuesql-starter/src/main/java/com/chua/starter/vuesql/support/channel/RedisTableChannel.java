package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.sql.SQLException;
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
        rs.add(Construct.builder().type(Type.DATABASE).icon("DATABASE").id(1).pid(0).name(config.getConfigDatabase()).build());
        rs.addAll(IntStream.range(2, 18).mapToObj(it -> {
            jedis.select(it - 2);
            return Construct.builder().icon("DATABASE")
                    .id(it)
                    .realName((it - 2) + "")
                    .pid(1)
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
    public Boolean update(WebsqlConfig config, String table, JSONArray data) {
        Jedis jedis = channelFactory.getConnection(config, Jedis.class, this::getJedis, it -> !it.isConnected());
        for (Object datum : data) {
            Map<String, Object> item = (Map<String, Object>) datum;
            Map newData = MapUtils.getType(item, "newData", Map.class);
            Map oldData = MapUtils.getType(item, "oldData", Map.class);
            String action = MapUtils.getString(item, "action");
        };

//        try {
//            jedis.select(Integer.parseInt(table.getString("realName")));
//        } catch (NumberFormatException ignored) {
//        }
//        if("delete".equalsIgnoreCase(data)) {
//            if(oldData instanceof JSONObject) {
//                jedis.del(((JSONObject) oldData).getString("key"));
//            } else if(oldData instanceof JSONArray){
//                ((JSONArray) oldData).forEach((item) -> {
//                    jedis.del(MapUtils.getString(item, "key"));
//                });
//            }
//            return true;
//        }
//        long expire = Long.parseLong(StringUtils.defaultString(newData.getString("ttl"), "0"));
//        String type = StringUtils.defaultString(newData.getString("type"), "string").toUpperCase();
//        switch (type) {
//            case "SET":
//                jedis.sadd(newData.getString("key"), StringUtils.defaultString(newData.getString("value"), ""));
//                break;
//            case "LIST":
//                jedis.lpush(newData.getString("key"), StringUtils.defaultString(newData.getString("value"), ""));
//                break;
//            case "ZSET":
//                jedis.zadd(newData.getString("key"), 0.0, StringUtils.defaultString(newData.getString("value"), ""));
//                break;
//            case "HASH":
//                String value = StringUtils.defaultString(newData.getString("value"), "");
//                if(value.contains(":")) {
//                    Map map = Converter.convertIfNecessary(value, Map.class);
//                    if(null != map) {
//                        map.forEach((k, v) -> {
//                            jedis.hset(newData.getString("key"), k + "", v + "");
//                        });
//                    }
//                }
//                break;
//            default:
//                jedis.set(newData.getString("key"), StringUtils.defaultString(newData.getString("value"), ""));
//                break;
//        }
//
//        if(expire > 0) {
//            jedis.expire(newData.getString("key"), expire);
//        }
        return true;
    }

    public Jedis getJedis(WebsqlConfig config) {
        Jedis jedis = null;
        if(StringUtils.isNotEmpty(config.getConfigPassword())) {
            String userInfo = StringUtils.defaultString(config.getConfigUsername(), "") + ":" + config.getConfigPassword() + "@";
            try {
                jedis  = new Jedis(new URI("redis://"+ userInfo +"" + config.getConfigIp() + ":" + config.getConfigPort()));
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
