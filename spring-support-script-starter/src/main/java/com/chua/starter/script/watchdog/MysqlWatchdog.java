package com.chua.starter.script.watchdog;

import com.chua.common.support.mysql.binlog.BinaryLogClient;
import com.chua.common.support.mysql.binlog.event.Event;
import com.chua.common.support.mysql.binlog.event.EventData;
import com.chua.common.support.mysql.binlog.event.TableMapEventData;
import com.chua.common.support.mysql.binlog.event.UpdateRowsEventData;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.bean.ScriptBean;
import org.springframework.util.ReflectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.script.adaptor.MysqlAdaptor.TABLE;


/**
 * 监听器
 *
 * @author CH
 */
@SuppressWarnings("ALL")
@ScriptExtension("mysql")
public class MysqlWatchdog implements DataSourceWatchdog, BinaryLogClient.EventListener {

    private final Map<String, ScriptBean> cache = new ConcurrentHashMap<>();
    private BinaryLogClient client;
    private DataSource dataSource;
    private int timeout;

    @Override
    public void register(Object source, ScriptBean scriptBean) {
        if (source instanceof Map) {
            initRefresh((Map) source, scriptBean);
        }
    }

    @Override
    public void timeout(int timeout) {
        this.timeout = timeout;
    }

    private void initRefresh(Map source, ScriptBean scriptBean) {
        scriptBean.refresh(MapUtils.getString(source, "content"));
        registerCache(source, scriptBean);
    }

    private void registerCache(Map source, ScriptBean scriptBean) {
        String name = MapUtils.getString(source, "name");
        cache.put(name, scriptBean);
    }


    @Override
    public void close() throws Exception {
        if (null == client) {
            return;
        }
        client.disconnect();
    }

    @Override
    public void register(DataSource dataSource) {
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
            client.setConnectTimeout(TimeUnit.SECONDS.toMillis(timeout));
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
        }
    }

    private void update(UpdateRowsEventData data, TableMapEventData tableMapEventData) {
        String table = tableMapEventData.getTable();
        if (!TABLE.equalsIgnoreCase(table)) {
            return;
        }

        List<Map.Entry<Serializable[], Serializable[]>> rows = data.getRows();
        for (Map.Entry<Serializable[], Serializable[]> row : rows) {
            update(row.getValue());
        }
    }

    private void update(Serializable[] value) {
        String name = value[1].toString();
        ScriptBean scriptBean = cache.get(name);
        if (null == scriptBean) {
            return;
        }
        String content = new String((byte[]) value[3]);
        scriptBean.refresh(content);
    }
}
