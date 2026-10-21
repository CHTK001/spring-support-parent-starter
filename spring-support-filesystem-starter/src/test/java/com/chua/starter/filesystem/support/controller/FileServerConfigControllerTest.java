package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.filesystem.support.pojo.FileServerConfigResponse;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FileServerConfigController} 单测。
 *
 * <p>验证前端进入文件管理页前需要拿到的「当前文件服务器配置」：
 * embedded 返回应用内相对路径（contextPath + path-prefix），
 * standalone 返回完整 {@code http://host:port}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileServerConfigControllerTest {

    /**
     * 默认嵌入形态且无 context-path：server 必须是 /fs，与过滤器注册路径一致。
     */
    @Test
    void embeddedDefaultBuildsRelativeFsPath() {
        FileStorageServerProperties server = new FileStorageServerProperties();
        FileStorageProperties storage = new FileStorageProperties();
        storage.setDefaultProvider("local");

        FileServerConfigController controller = controller(server, storage, "");
        ReturnResult<FileServerConfigResponse> result = controller.serverConfig();

        FileServerConfigResponse config = result.getData();
        assertThat(config.getMode()).isEqualTo("embedded");
        assertThat(config.getServer()).isEqualTo("/fs");
        assertThat(config.getPathPrefix()).isEqualTo("/fs");
        assertThat(config.getBucket()).isEqualTo("local");
    }

    /**
     * 带 context-path 的嵌入形态：server 必须拼接 /system/api + /fs。
     */
    @Test
    void embeddedWithContextPathBuildsFullRelativePath() {
        FileStorageServerProperties server = new FileStorageServerProperties();
        FileStorageProperties storage = new FileStorageProperties();
        storage.setDefaultProvider("local");

        FileServerConfigController controller = controller(server, storage, "/system/api");
        ReturnResult<FileServerConfigResponse> result = controller.serverConfig();

        FileServerConfigResponse config = result.getData();
        assertThat(config.getMode()).isEqualTo("embedded");
        assertThat(config.getServer()).isEqualTo("/system/api/fs");
        assertThat(config.getPathPrefix()).isEqualTo("/fs");
    }

    /**
     * 根路径嵌入形态：server 回落到应用根路径。
     */
    @Test
    void embeddedRootPrefixFallsBackToAppRoot() {
        FileStorageServerProperties server = new FileStorageServerProperties();
        server.setPathPrefix("/");
        FileStorageProperties storage = new FileStorageProperties();
        storage.setDefaultProvider("local");

        FileServerConfigController controller = controller(server, storage, "");
        FileServerConfigResponse config = controller.serverConfig().getData();

        assertThat(config.getMode()).isEqualTo("embedded");
        assertThat(config.getServer()).isEqualTo("/");
        assertThat(config.getPathPrefix()).isEmpty();
    }

    /**
     * standalone：0.0.0.0 要归一成 127.0.0.1，并带配置端口。
     */
    @Test
    void standaloneNormalizesWildcardHostWithPort() {
        FileStorageServerProperties server = new FileStorageServerProperties();
        server.setMode(FileStorageServerProperties.Mode.STANDALONE);
        server.setHost("0.0.0.0");
        server.setPort(18080);
        FileStorageProperties storage = new FileStorageProperties();
        storage.setDefaultProvider("local");

        FileServerConfigResponse config = controller(server, storage, "/system/api").serverConfig().getData();

        assertThat(config.getMode()).isEqualTo("standalone");
        assertThat(config.getServer()).isEqualTo("http://127.0.0.1:18080");
        assertThat(config.getPathPrefix()).isEqualTo("/fs");
        assertThat(config.getBucket()).isEqualTo("local");
    }

    /**
     * standalone：显式 publicHost 优先于 host。
     */
    @Test
    void standaloneUsesPublicHostWhenConfigured() {
        FileStorageServerProperties server = new FileStorageServerProperties();
        server.setMode(FileStorageServerProperties.Mode.STANDALONE);
        server.setHost("0.0.0.0");
        server.setPublicHost("192.168.50.198");
        server.setPort(28080);
        FileStorageProperties storage = new FileStorageProperties();
        storage.setDefaultProvider("local");

        FileServerConfigResponse config = controller(server, storage, "").serverConfig().getData();

        assertThat(config.getServer()).isEqualTo("http://192.168.50.198:28080");
    }

    /**
     * 手动装配 controller。
     */
    private static FileServerConfigController controller(FileStorageServerProperties server,
                                                         FileStorageProperties storage,
                                                         String contextPath) {
        return new FileServerConfigController(server, storage, contextPath);
    }
}