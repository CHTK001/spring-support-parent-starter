package com.chua.starter.datasync.app;

import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * DataSync 数据库模式集成测试启动应用。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSyncApplicationDb {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(DataSyncAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(args);

        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[APP-DB] plugin.datasync.enabled=" + properties.isEnabled());
        System.out.println("[APP-DB] plugin.datasync.mapping-source=" + properties.getMappingSource());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.table-prefix=" + properties.getDatabaseMapping().getTablePrefix());
        System.out.println("[APP-DB] plugin.datasync.database-mapping.auto-init-table=" + properties.getDatabaseMapping().isAutoInitTable());

        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        DataSyncMappingManager mappingManager = server.mappingManager();
        System.out.println("[APP-DB] 启动后映射数=" + mappingManager.getMappings().size());
        for (var mapping : mappingManager.getMappings()) {
            System.out.println("[APP-DB] 映射: mappingId=" + mapping.mappingId()
                    + ", sourceId=" + mapping.sourceId()
                    + ", sinkId=" + mapping.sinkId()
                    + ", batch=" + mapping.batch()
                    + ", mappings=" + mapping.mappings());
        }

        System.out.println("[APP-DB] 按回车退出");
        System.in.read();
        server.stop();
        context.close();
    }
}
