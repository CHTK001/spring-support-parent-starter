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
        // SPI "local" = 本模块 LocalFileStorage：根目录 = extraProperties.basePath/bucket
        // （"filesystem" 实现在 utils-support-filesystem-starter，本验收 classpath 未包含）
        FileStorage local = FileStorage.createStorage("local", BucketSetting.builder()
                .bucket("local")
                .extraProperties(java.util.Map.of("basePath", root.toString()))
                .build());
        filter.addFileStorage("local", local);
        // 全量预览验收桶：D:\fs-preview-test（275 个后缀各一个测试文件，见用户要求）
        Path bulkRoot = Path.of("D:/fs-preview-test");
        Files.createDirectories(bulkRoot);
        FileStorage all = FileStorage.createStorage("local", BucketSetting.builder()
                .bucket("all")
                .extraProperties(java.util.Map.of("basePath", bulkRoot.toString()))
                .build());
        filter.addFileStorage("all", all);

        // 自诊断：直调 SPI 验证根目录/读写/列举是否正常
        var pr = local.putObject(com.chua.common.support.storage.request.PutObjectRequest.builder()
                .fileName("selftest.txt").content("self test".getBytes()).build());
        var gr = local.getObject(com.chua.common.support.storage.request.GetObjectRequest.builder()
                .fileName("hello.md").build());
        var lr = local.listObject(com.chua.common.support.storage.request.ListObjectRequest.builder().build());
        System.out.println("[DIAG] put=" + pr.getResultCode() + " putUrl=" + pr.getUrl()
                + " getHello=" + gr.getResultCode() + ":" + gr.getMessage()
                + " list=" + lr.getResultCode() + ":" + (lr.getMetadata() == null ? -1 : lr.getMetadata().size())
                + " first=" + (lr.getMetadata() == null || lr.getMetadata().isEmpty() ? "-"
                        : lr.getMetadata().get(0).getName() + " @ " + lr.getMetadata().get(0).getPath()));

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
