package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 浏览器验收用极简文件服务器（仅 test classpath，不进生产）。
 *
 * <p>手工挂载 {@link FileStorageServerFilter}：一个 {@code local} 公共桶（预览/下载全开）
 * + 一个 {@code vault} 私有语义桶（仅用于手动核对 openDownload 关闭场景时改代码）。
 * 端口 18082（与 FileStorageServerProperties 默认一致），layoutType=SYSTEM 走 Vue 壳。</p>
 *
 * <p>落盘目录：{@code D:/ch/project/data/fs-acceptance}（不存在则自动创建）。</p>
 */
public class FileServerAcceptanceMain {

    public static void main(String[] args) throws Exception {
        Path root = Path.of("D:/ch/project/data/fs-acceptance");
        Files.createDirectories(root);

        FileStorageSetting setting = FileStorageSetting.builder()
                .openPreview(true)
                .openDownload(true)
                .openRange(true)
                .openWebjars(true)
                .layout(true)
                .layoutType("SYSTEM")
                .build();

        FileStorageServerFilter filter = new FileStorageServerFilter(setting);
        filter.addFileStorage("local", FileStorage.createStorage("local", BucketSetting.builder()
                .bucket("local")
                .endpoint(root.resolve("local").toString())
                .build()));

        Server built = ServerBuilder.create()
                .type("jdk")
                .host("0.0.0.0")
                .port(18082)
                .build();
        ServerSetting.SecurityHeadersConfig security = built.getSetting().getSecurityHeaders();
        security.setEnabled(true);
        security.setFrameOptions("");
        built.addFilter(filter);
        built.start();
        System.out.println("[ACCEPTANCE] file server ready at http://127.0.0.1:18082/  bucket=local -> "
                + root.resolve("local"));
        Thread.currentThread().join();
    }
}
