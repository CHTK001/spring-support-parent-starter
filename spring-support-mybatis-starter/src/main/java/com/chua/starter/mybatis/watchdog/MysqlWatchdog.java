package com.chua.starter.mybatis.watchdog;

import com.chua.mysql.support.binlog.BinaryLogClient;
import com.chua.mysql.support.binlog.event.Event;
import com.chua.mysql.support.binlog.event.TableMapEventData;
import com.chua.mysql.support.binlog.event.UpdateRowsEventData;
import com.chua.mysql.support.binlog.event.WriteRowsEventData;
import com.chua.starter.mybatis.marker.MysqlSqlMethodMarker;
import com.chua.starter.mybatis.method.DynamicSqlMethod;
import com.hazelcast.map.impl.event.EventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 监听器
 *
 * @author CH
 */
@SuppressWarnings("ALL")
@Slf4j
public class MysqlWatchdog implements BinaryLogClient.EventListener, AutoCloseable {

    private final MysqlSqlMethodMarker mysqlSqlMethodMarker;
    private BinaryLogClient client;
    private DataSource dataSource;

    private final Map<String, DynamicSqlMethod> cache = new ConcurrentHashMap<>();
    private int timeout;

    public MysqlWatchdog(DataSource dataSource, int timeout, MysqlSqlMethodMarker mysqlSqlMethodMarker) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        this.mysqlSqlMethodMarker = mysqlSqlMethodMarker;
        register(dataSource);
    }


    @Override
    public void close() throws Exception {
        if (null == client) {
            return;
        }
        client.disconnect();
    }

    private void register(DataSource dataSource) {
        this.dataSource = dataSource;
        try {
            Map<String, Object> beanMap = create(dataSource);
            String url = getValue(beanMap, "driverUrl", "url", "jdbcUrl");
            String username = getValue(beanMap, "username", "name", "jdbcUser");
            String password = getValue(beanMap, "password", "passwd");
            URI url1 = new URI(new URI(url).getSchemeSpecificPart());
            this.client = new BinaryLogClient(
                    url1.getHost(), url1.getPort(), username, password);
            client.setServerId(0);
            client.setConnectTimeout(timeout);
            client.registerEventListener(this);
            client.connect();
        } catch (Exception e) {
        }
    }

    private Map<String, Object> create(DataSource dataSource) {
        Map<String, Object> rs = new LinkedHashMap<>();
        ReflectionUtils.doWithFields(dataSource.getClass(), field -> {
            if (Modifier.isStatic(field.getModifiers())) {
                return;
            }

            ReflectionUtils.makeAccessible(field);
            try {
                rs.put(field.getName(), ReflectionUtils.getField(field, dataSource));
            } catch (Exception e) {
            }
        });
        return rs;
    }

    private String getValue(Map<String, Object> beanMap, String... name) {
        for (String s : name) {
            Object o = beanMap.get(s);
            if (null != o) {
                return o.toString();
            }
        }

        return null;
    }

    TableMapEventData tableMapEventData;

    @Override
    public void onEvent(Event event) {
        EventData data = event.getData();
        if (data instanceof TableMapEventData) {
            this.tableMapEventData = (TableMapEventData) data;
        }

        if (data instanceof UpdateRowsEventData && null != tableMapEventData) {
            update((UpdateRowsEventData) data, tableMapEventData);
            tableMapEventData = null;
            return;
        }


        if (data instanceof WriteRowsEventData && null != tableMapEventData) {
            append((WriteRowsEventData) data, tableMapEventData);
            tableMapEventData = null;
        }
    }

    private void append(WriteRowsEventData data, TableMapEventData tableMapEventData) {
        String table = tableMapEventData.getTable();
        if (!MysqlSqlMethodMarker.TABLE.equalsIgnoreCase(table)) {
            return;
        }

        List<Serializable[]> rows = data.getRows();
        for (Serializable[] row : rows) {
            update(row);
        }
    }

    private void update(UpdateRowsEventData data, TableMapEventData tableMapEventData) {
        String table = tableMapEventData.getTable();
        if (!MysqlSqlMethodMarker.TABLE.equalsIgnoreCase(table)) {
            return;
        }

        List<Map.Entry<Serializable[], Serializable[]>> rows = data.getRows();
        for (Map.Entry<Serializable[], Serializable[]> row : rows) {
            update(row.getValue());
        }
    }

    private void update(Serializable[] value) {
        mysqlSqlMethodMarker.refresh(value);

    }

    public void regiser(DynamicSqlMethod dynamicSqlMethod) {
        cache.put(dynamicSqlMethod.getName(), dynamicSqlMethod);
    }
}
