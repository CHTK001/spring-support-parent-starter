package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.mybatis.method.DynamicSqlMethod;
import com.chua.starter.mybatis.watchdog.MysqlWatchdog;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 方法构建器
 *
 * @author CH
 */
@Slf4j
public class MysqlSqlMethodMarker implements SqlMethodMarker {

    static final Map<DataSource, List<AbstractMethod>> CACHE = new ConcurrentHashMap<>();

    final List<MysqlWatchdog> watchdogs = new CopyOnWriteArrayList<>();
    public static final String TABLE = "t_spring_mybatis_info";
    private int timeout;
    private boolean openWatchdog;
    private Map<String, DynamicSqlMethod> cacheMethod = new ConcurrentHashMap<>();
    private DataSource dataSource;

    public MysqlSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
        this.openWatchdog = watchdog;
    }

    @Override
    public List<AbstractMethod> analysis(String source) {
        this.dataSource = findDataSource(source);
        if (null == dataSource) {
            return Collections.emptyList();
        }

        return CACHE.computeIfAbsent(dataSource, s -> {
            if (openWatchdog) {
                MysqlWatchdog watchdog = new MysqlWatchdog(dataSource, timeout, this);
                watchdogs.add(watchdog);
                return createSqlMethod(dataSource, watchdog);
            }
            return createSqlMethod(dataSource, null);
        });
    }


    private List<AbstractMethod> createSqlMethod(DataSource dataSource, MysqlWatchdog watchdog) {
        QueryRunner queryRunner = new QueryRunner(dataSource);
        if (!isExist(dataSource)) {
            createTable(queryRunner);
            return Collections.emptyList();
        }
        return analysisSqlMethod(dataSource, queryRunner, watchdog);
    }

    private List<AbstractMethod> analysisSqlMethod(DataSource dataSource, QueryRunner queryRunner, MysqlWatchdog watchdog) {
        List<AbstractMethod> rs = new LinkedList<>();
        try {
            List<Map<String, Object>> query = queryRunner.query("SELECT * FROM " + TABLE, new MapListHandler());
            for (Map<String, Object> stringObjectMap : query) {
                analysisSqlMethod(stringObjectMap, rs, watchdog);
            }

            return rs;
        } catch (SQLException ignored) {
        }

        return Collections.emptyList();
    }

    private void analysisSqlMethod(Map<String, Object> stringObjectMap, List<AbstractMethod> rs, MysqlWatchdog watchdog) {
        DynamicSqlMethod dynamicSqlMethod = new DynamicSqlMethod(MapUtils.getString(stringObjectMap, "name"), stringObjectMap, watchdog);
        rs.add(dynamicSqlMethod);
        cacheMethod.put(dynamicSqlMethod.getName(), dynamicSqlMethod);
    }

    /**
     * 检测表是否存在
     *
     * @param dataSource 数据源
     * @return 检测表是否存在
     */
    private boolean isExist(DataSource dataSource) {
        List<String> ls = new ArrayList<>(1);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getTables(null, null, TABLE, new String[]{"TABLE"});
            while (resultSet.next()) {
                String tableName = resultSet.getString(3);
                ls.add(tableName);
            }
        } catch (SQLException ignored) {
        }

        return !ls.isEmpty();
    }

    /**
     * 创建表
     *
     * @param queryRunner query
     */
    private void createTable(QueryRunner queryRunner) {
        StringBuilder stringBuilder = new StringBuilder("CREATE TABLE ");
        stringBuilder.append(TABLE).append(" (\n");
        stringBuilder.append("`id` int NOT NULL AUTO_INCREMENT,\n");
        stringBuilder.append("`name` varchar(255) DEFAULT NULL COMMENT 'mybatis名称',\n");
        stringBuilder.append("`mapper_type` varchar(255) DEFAULT NULL COMMENT 'mapper类型',\n");
        stringBuilder.append("`return_type` varchar(255) DEFAULT NULL COMMENT '返回类型',\n");
        stringBuilder.append("`model_type` varchar(255)  DEFAULT NULL COMMENT '实体类型',\n");
        stringBuilder.append("`content` longtext COMMENT '源码',\n");
        stringBuilder.append("`type`  varchar(255) COMMENT '源码类型; SQL;SCRIPT' DEFAULT 'SQL',\n");
        stringBuilder.append(" PRIMARY KEY (`id`) USING BTREE\n");
        stringBuilder.append(" )");

        try {
            queryRunner.execute(stringBuilder.toString());
        } catch (SQLException ignored) {
        }
    }

    private DataSource findDataSource(String bean) {
        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        Map<String, DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        if (!Strings.isNullOrEmpty(bean)) {
            return beansOfType.get(bean);
        }
        for (Map.Entry<String, DataSource> entry : beansOfType.entrySet()) {
            DataSource dataSource = entry.getValue();
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String databaseProductName = metaData.getDatabaseProductName();
                if ("mysql".equalsIgnoreCase(databaseProductName)) {
                    return dataSource;
                }
            } catch (SQLException ignored) {
            }
        }
        return null;
    }

    @Override
    public void destroy() throws Exception {
        for (MysqlWatchdog watchdog : watchdogs) {
            try {
                watchdog.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void refresh(String name) {
        try {
            QueryRunner queryRunner = new QueryRunner(dataSource);
            try {
                List<Map<String, Object>> query = queryRunner.query("SELECT * FROM " + TABLE + " WHERE `name` like '%" + name + "%'", new MapListHandler());
                for (Map<String, Object> stringObjectMap : query) {
                    refresh(stringObjectMap);
                }

            } catch (SQLException ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private void refresh(Map<String, Object> param) {
        DynamicSqlMethod dynamicSqlMethod = cacheMethod.get(MapUtils.getString(param, "name"));
        if (null == dynamicSqlMethod) {
            createSqlMethod(param);
            return;
        }

        dynamicSqlMethod.refresh(MapUtils.getString(param, "content"), MapUtils.getString(param, "type"));
    }

    public void refresh(Serializable[] value) {
        DynamicSqlMethod dynamicSqlMethod = cacheMethod.get(value[1].toString());

        if (null == dynamicSqlMethod) {
            createSqlMethod(value);
            return;
        }

        dynamicSqlMethod.refresh(new String((byte[]) value[5]), value[6].toString());
    }

    private void createSqlMethod(Map<String, Object> param) {
        try {
            DynamicSqlMethod dynamicSqlMethod = new DynamicSqlMethod(MapUtils.getString(param, "name"), param, null);
            Class<?> mapperClass = ClassUtils.forName(MapUtils.getString(param, "mapper_type"), ClassLoader.getSystemClassLoader());
            Class<?> modelClass = ClassUtils.forName(MapUtils.getString(param, "model_type"), ClassLoader.getSystemClassLoader());
            MapperBuilderAssistant mapperBuilderAssistant = new MapperBuilderAssistant(SpringBeanUtils.getApplicationContext().getBean(SqlSessionFactory.class).getConfiguration(), null);
            mapperBuilderAssistant.setCurrentNamespace(mapperClass.getTypeName());
            log.info("动态添加Mapper语句[{}]", mapperClass.getTypeName() + "." + MapUtils.getString(param, "name"));
            TableInfo tableInfo = TableInfoHelper.initTableInfo(mapperBuilderAssistant, modelClass);
            dynamicSqlMethod.inject(mapperBuilderAssistant, mapperClass, modelClass, tableInfo);
        } catch (Exception ignored) {
        }
    }

    private void createSqlMethod(Serializable[] value) {
        Map<String, Object> param = new HashMap<>(3);
        try {
            String sql = new String((byte[]) value[5]);
            param.put("content", sql);
            param.put("mapper_type", value[2].toString());
            param.put("model_type", value[4].toString());
            DynamicSqlMethod dynamicSqlMethod = new DynamicSqlMethod(value[1].toString(), param, null);
            Class<?> mapperClass = ClassUtils.forName(value[2].toString(), ClassLoader.getSystemClassLoader());
            Class<?> modelClass = ClassUtils.forName(value[4].toString(), ClassLoader.getSystemClassLoader());
            MapperBuilderAssistant mapperBuilderAssistant = new MapperBuilderAssistant(SpringBeanUtils.getApplicationContext().getBean(SqlSessionFactory.class).getConfiguration(), null);
            mapperBuilderAssistant.setCurrentNamespace(mapperClass.getTypeName());
            log.info("动态添加Mapper语句[{}]", mapperClass.getTypeName() + "." + value[1].toString());
            TableInfo tableInfo = TableInfoHelper.initTableInfo(mapperBuilderAssistant, modelClass);
            dynamicSqlMethod.inject(mapperBuilderAssistant, mapperClass, modelClass, tableInfo);
        } catch (Exception ignored) {
        }
    }

}
