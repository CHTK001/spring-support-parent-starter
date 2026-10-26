package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.StaticResourceServerFilter;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.filesystem.support.local.LocalFileStorage;

/**
 * 文件存储服务器启动器。
 *
 * <p>基于 JDK HTTP Server + {@link FileStorageServerFilter} 启动独立的
 * REST 文件存储服务，用于手动验证与联调。</p>
 *
 * <p>启动后可通过以下接口操作 local 存储桶：</p>
 * <ul>
 *   <li>{@code PUT http://127.0.0.1:18080/local/hello.txt}（body 为文件内容）— 上传</li>
 *   <li>{@code GET http://127.0.0.1:18080/local/hello.txt} — 下载/预览</li>
 *   <li>{@code GET http://127.0.0.1:18080/local/hello.txt?download=true} — 附件下载</li>
 *   <li>{@code GET http://127.0.0.1:18080/local}（携带 Range 头可分段）— 列表/分段下载</li>
 *   <li>{@code HEAD http://127.0.0.1:18080/local/hello.txt} — 存在性检查</li>
 *   <li>{@code DELETE http://127.0.0.1:18080/local/hello.txt} — 删除</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0
 */
public class StartFileStorageServer {

    /**
     * 默认监听端口
     */
    private static final int DEFAULT_PORT = 18080;

    /**
     * 程序入口。
     *
     * @param args 参数[0] 可选端口号，默认 18080
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        // 构建文件存储设置：开启预览、下载、分段、水印
        FileStorageSetting setting = FileStorageSetting.builder()
                .openPreview(true)
                .openDownload(true)
                .openRange(true)
                .openWatermark(true)
                .watermark("CONFIDENTIAL")
                .openRemoteFile(true)
                .layout(true)
                .layoutType("SYSTEM")
                .build();

        // 创建过滤器并注册本地存储实例
        FileStorageServerFilter filter = new FileStorageServerFilter(setting);
        FileStorage storage = new LocalFileStorage(BucketSetting.builder().bucket("demo").build());
        filter.addFileStorage("local", storage);

        // 构建 JDK HTTP 服务器并挂载过滤器
        Server server = ServerBuilder.create()
                .type("jdk")
                .host("127.0.0.1")
                .port(port)
                .build();
        // 跨域（CORS）：与 Spring 真实装配路径 FileStorageServerLauncher 保持同一语义。
        // 文件服务器是独立端口网关，前端（Vue dev server 在另一端口）基本都是跨源访问，
        // 缺 Access-Control-Allow-Origin 会让浏览器拦掉整个 capabilities/config 响应，
        // 页面表现为「下拉无选项 / 当前能力空串」——实测踩过。
        // 这里直接开 allowOrigin + 通配来源，等价于 cors 默认配置（enable=true, *）。
        ServerSetting.CorsConfig cors = server.getSetting().getCors();
        cors.setAllowOrigin(true);
        cors.setAllowedOrigins("*");
        cors.setAllowedMethods("GET,HEAD,POST,PUT,DELETE,PATCH,OPTIONS");
        // 预览/缩略图要读 Content-Disposition 与 Content-Length，需显式暴露
        cors.setExposeHeaders("Content-Disposition,Content-Length,Content-Range,Accept-Ranges");
        // Vue 静态资源（类路径 static/assets/ → /assets/）
        server.addFilter(new StaticResourceServerFilter("/assets", "static/assets"));
        // 文件预览第三方依赖（Univer、PDF.js、highlight.js，类路径 static/preview-vendor/ → /preview-vendor/）
        server.addFilter(new StaticResourceServerFilter("/preview-vendor", "static/preview-vendor"));
        server.addFilter(filter);
        server.start();

        System.out.println("========================================");
        System.out.println("FileStorage 服务已启动");
        System.out.println("地址: http://127.0.0.1:" + port + "/local/{key}");
        System.out.println("配置: http://127.0.0.1:" + port + "/config?token=" + filter.getToken());
        System.out.println("示例: curl -X PUT --data-binary @a.txt http://127.0.0.1:" + port + "/local/a.txt");
        System.out.println("========================================");

        // 将令牌写入临时文件，便于脚本化验收
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "filestorage-token.txt"),
                    filter.getToken());
        } catch (Exception e) {
            System.err.println("写入令牌临时文件失败，已忽略: " + e.getMessage());
        }

        // 阻塞主线程保持服务运行
        Thread.currentThread().join();
    }
}