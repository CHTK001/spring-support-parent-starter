package com.chua.starter.script.adaptor;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.bean.FileScriptBean;
import com.chua.starter.script.bean.SourceScriptBean;
import com.chua.starter.script.configuration.ScriptProperties;
import com.chua.starter.script.marker.DelegateMarker;
import com.chua.starter.script.marker.Marker;
import com.chua.starter.script.watchdog.DataSourceWatchdog;
import com.chua.starter.script.watchdog.Watchdog;
import com.google.common.base.Strings;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 定义
 *
 * @author CH
 */
@ScriptExtension("mysql")
public class MysqlAdaptor implements Adaptor {

    public static final String TABLE = "T_SPRING_BEAN_INFO";

    static final String SOURCE = "SOURCE";
    static final String FILE = "FILE";

    @Override
    public List<BeanDefinition> analysis(ScriptProperties.Config config, Watchdog watchdog) {
        List<BeanDefinition> rs = new LinkedList<>();
        String bean = config.getSource();
        DataSource dataSource = findDataSource(bean);
        if (null == dataSource) {
            return rs;
        }
        analysis(dataSource, rs, watchdog);
        return rs;
    }

    private void analysis(DataSource dataSource, List<BeanDefinition> rs, Watchdog watchdog) {
        if (watchdog instanceof DataSourceWatchdog) {
            ((DataSourceWatchdog) watchdog).register(dataSource);
        }

        QueryRunner queryRunner = new QueryRunner(dataSource);
        if (!isExist(dataSource)) {
            createTable(queryRunner);
            return;
        }

        analysisBean(dataSource, queryRunner, rs, watchdog);
    }

    /**
     * 分析对象
     *
     * @param dataSource  dataSource
     * @param queryRunner 查询其
     * @param rs          结果
     * @param watchdog    监听器
     */
    private void analysisBean(DataSource dataSource, QueryRunner queryRunner, List<BeanDefinition> rs, Watchdog watchdog) {
        try {
            List<Map<String, Object>> query = queryRunner.query("SELECT * FROM " + TABLE + " where `status` = 1", new MapListHandler());
            for (Map<String, Object> stringObjectMap : query) {
                analysisBean(stringObjectMap, rs, watchdog);
            }
        } catch (SQLException ignored) {
        }

    }

    /**
     * 分析对象
     *
     * @param stringObjectMap 查询其
     * @param rs              结果
     * @param watchdog        监听器
     */
    private void analysisBean(Map<String, Object> stringObjectMap, List<BeanDefinition> rs, Watchdog watchdog) {
        String type = MapUtils.getString(stringObjectMap, "type");
        if (FILE.equalsIgnoreCase(type)) {
//            analysisFileBean(stringObjectMap, rs, watchdog);
            return;
        }


        if (SOURCE.equalsIgnoreCase(type)) {
            analysisSourceBean(stringObjectMap, rs, watchdog);
            return;
        }
    }

    private void analysisSourceBean(Map<String, Object> stringObjectMap, List<BeanDefinition> rs, Watchdog watchdog) {
        Marker marker = Marker.create(MapUtils.getString(stringObjectMap, "mode"));
        if (null == marker) {
            return;
        }
        String className = MapUtils.getString(stringObjectMap, "interfaces");
        Class<?> aClass = null;
        try {
            aClass = ClassUtils.forName(className, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException ignored) {
        }

        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SourceScriptBean.class);
        beanDefinitionBuilder.addConstructorArgValue(MapUtils.getString(stringObjectMap, "content"));
        beanDefinitionBuilder.addConstructorArgValue(MapUtils.getString(stringObjectMap, "mode"));
        beanDefinitionBuilder.addConstructorArgValue(stringObjectMap);
        beanDefinitionBuilder.addConstructorArgValue(watchdog);
        beanDefinitionBuilder.addConstructorArgValue(aClass);
        AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();
        beanDefinition.setAttribute("bean", MapUtils.getString(stringObjectMap, "name"));
        rs.add(beanDefinition);
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
        stringBuilder.append("`name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'Bean名称',\n");
        stringBuilder.append("`type` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'FILE: 文件; SOURCE:源码',\n");
        stringBuilder.append("`content` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '源码/文件路径',\n");
        stringBuilder.append("  `mode` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '编译模式; java, groovy',\n");
        stringBuilder.append("`status` int NULL DEFAULT 1 COMMENT '0:关闭;1:开启',\n");
        stringBuilder.append("`interfaces` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '脚本实现的类/接口(程序启动之后不允许修改)',\n\n");
        stringBuilder.append("`freer` int NULL DEFAULT 1 COMMENT '优先级',\n");
        stringBuilder.append(" PRIMARY KEY (`id`) USING BTREE\n");
        stringBuilder.append(" )");

        try {
            queryRunner.execute(stringBuilder.toString());
        } catch (SQLException ignored) {
        }
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
                if ("mysql" .equalsIgnoreCase(databaseProductName)) {
                    return dataSource;
                }
            } catch (SQLException ignored) {
            }
        }
        return null;
    }

    private void analysis(File file, List<BeanDefinition> rs, Watchdog watchdog) {
        File[] files = file.listFiles();
        try {
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    analysisFile(file.toFile(), rs, watchdog);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }


    }

    private void analysisFile(File file, List<BeanDefinition> rs, Watchdog watchdog) {
        String extension = FilenameUtils.getExtension(file.getName());
        Marker marker = Marker.create(extension);
        if (marker instanceof DelegateMarker) {
            return;
        }

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String toString = IOUtils.toString(fileInputStream, UTF_8);
            String[] split = toString.split("\r\n", 2);
            String s = split[0];
            Class<?> aClass = null;
            if (!s.startsWith("package") && !s.startsWith("import")) {
                aClass = ClassUtils.forName(s, ClassLoader.getSystemClassLoader());
            }
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(FileScriptBean.class);
            beanDefinitionBuilder.addConstructorArgValue(file);
            beanDefinitionBuilder.addConstructorArgValue(watchdog);
            beanDefinitionBuilder.addConstructorArgValue(aClass);
            rs.add(beanDefinitionBuilder.getBeanDefinition());
        } catch (Exception ignored) {
        }
    }
}

