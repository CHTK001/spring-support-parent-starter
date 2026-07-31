package com.chua.starter.datasync.app;

import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * DataSync 集成测试启动应用。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSyncApplication {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder()
                .sources(DataSyncAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(args);

        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[APP] plugin.datasync.enabled=" + properties.isEnabled());
        System.out.println("[APP] plugin.datasync.mapping-source=" + properties.getMappingSource());
        System.out.println("[APP] plugin.datasync.file-mapping.directory-path=" + properties.getFileMapping().getDirectoryPath());
        System.out.println("[APP] plugin.datasync.database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());

        DirectoryDataSyncMappingScanner scanner = context.getBean(DirectoryDataSyncMappingScanner.class);
        scanner.start();

        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        server.start();

        System.out.println("[APP] DataSync 已启动，按回车退出");
        System.in.read();

        server.stop();
        scanner.stop();
        context.close();
    }
}
