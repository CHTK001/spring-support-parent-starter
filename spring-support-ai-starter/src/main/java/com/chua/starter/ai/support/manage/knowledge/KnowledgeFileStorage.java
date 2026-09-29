package com.chua.starter.ai.support.manage.knowledge;

import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ExistObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.ExistObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.filesystem.support.local.LocalFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库文件存储：把知识库目录的字节读写交给
 * {@code spring-support-filesystem-starter} 的 {@link FileStorage} SPI，而不是在控制器里散落
 * {@code Files.readAllBytes} / {@code Files.write} / {@code Files.list}。
 *
 * <h3>为什么选 spring-support-filesystem-starter</h3>
 * <ul>
 *   <li>{@code spring-support-ai-starter} 已经依赖它（含 {@code LocalFileStorage}），不需要新增依赖；</li>
 *   <li>{@link FileStorage} 契约（{@code putObject}/{@code getObject}/{@code deleteObject}/
 *       {@code existObject}/{@code listObject}）正好覆盖知识库需要的全部字节操作；</li>
 *   <li>{@link Metadata} 带 {@code name} / {@code size} / {@code lastModified} / {@code directory}，
 *       目录树所需的元数据不用再自己 {@code readAttributes}；</li>
 *   <li>{@link LocalFileStorage} 自带 {@code resolveSafely} 越界校验，比手写 {@code startsWith} 更集中。</li>
 * </ul>
 *
 * <h3>为什么不注册进 FileStorageTemplate</h3>
 * <p>注册成 provider 之后，{@code FileObjectController} 的 {@code GET /v2/file/view/{provider}/{key}}
 * 会按 {@code @Ignore} 语义<b>匿名</b>放出对象。知识库原文属于用户内容，不能无登录直读，
 * 因此这里只使用 SPI 实现本身，不把它挂到匿名回显路径上。若后续确实需要同源直链预览，
 * 应先在文件模块给 {@code /v2/file/view} 增加「私有 provider」判定，而不是先注册。</p>
 *
 * <h3>为什么每个知识库根一个 storage 实例</h3>
 * <p>知识库的 {@code root_path} 可能是托管目录（{@code <上传目录>/kb-<id>}），
 * 也可能是用户指定的<b>外部本地目录</b>（{@code kind=local} 且 {@code managed=false}），
 * 不共享同一个根，因此按根路径缓存实例。{@link LocalFileStorage} 的根是
 * {@code <basePath>/<bucket>}，这里拆成「父目录 + 末级目录名」来指向任意根。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@Slf4j
public class KnowledgeFileStorage {

    /**
     * 单目录列举上限。
     *
     * <p>{@code ListObjectRequest.limit} 默认只有 10，SPI 实现会按该值截断，
     * 直接透传会静默漏掉文件，所以这里给一个足够大的值。</p>
     */
    private static final int LIST_LIMIT = 10000;

    /**
     * 知识库根目录（绝对路径）到 storage 实例的缓存。
     *
     * <p>构造 {@link LocalFileStorage} 会做一次 {@code createDirectories}，
     * 不缓存的话每个文件操作都要重新建一遍目录。</p>
     */
    private final ConcurrentHashMap<String, FileStorage> rootStorages = new ConcurrentHashMap<>();

    /**
     * 取得（必要时创建）指定知识库根目录对应的文件存储。
     *
     * @param rootPath 知识库根目录（绝对或相对路径均可）
     * @return 该根目录对应的文件存储，不为 null
     */
    public FileStorage of(String rootPath) {
        String key = normalizeRoot(rootPath);
        return rootStorages.computeIfAbsent(key, this::createStorage);
    }

    /**
     * 写入文本文件（UTF-8）。
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @param content  文本内容，null 视为空文件
     * @return 写入结果，失败时 {@code resultCode} 为 FAILURE
     */
    public PutObjectResult writeText(String rootPath, String relPath, String content) {
        byte[] data = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        return putBytes(rootPath, relPath, data);
    }

    /**
     * 写入二进制文件。
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @param data     文件内容
     * @return 写入结果，失败时 {@code resultCode} 为 FAILURE
     */
    public PutObjectResult putBytes(String rootPath, String relPath, byte[] data) {
        return of(rootPath).putObject(PutObjectRequest.builder()
                .filePath(dirOf(relPath))
                .fileName(nameOf(relPath))
                .content(data)
                .build());
    }

    /**
     * 打开文件读取流。
     *
     * <p>调用方负责关闭返回的流；不存在或读取失败时返回的 {@code resultCode} 为 FAILURE。</p>
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @return 读取结果，含输入流与元数据
     */
    public GetObjectResult get(String rootPath, String relPath) {
        return of(rootPath).getObject(normalizeRel(relPath));
    }

    /**
     * 读取文本文件内容（UTF-8）。
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @return 文本内容；文件不存在或读取失败返回 null
     */
    public String readText(String rootPath, String relPath) {
        GetObjectResult result = get(rootPath, relPath);
        if (result == null || ObjectResult.ResultCode.FAILURE == result.getResultCode()
                || result.getInputStream() == null) {
            return null;
        }
        try (InputStream in = result.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[KnowledgeFileStorage] 读取失败: root={}, rel={}, 原因={}", rootPath, relPath, e.getMessage());
            return null;
        }
    }

    /**
     * 判断文件或目录是否存在。
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @return 存在返回 true
     */
    public boolean exists(String rootPath, String relPath) {
        ExistObjectResult result = of(rootPath).existObject(ExistObjectRequest.builder()
                .filePath(dirOf(relPath))
                .fileName(nameOf(relPath))
                .build());
        return result != null && result.isExists();
    }

    /**
     * 删除文件。
     *
     * @param rootPath 知识库根目录
     * @param relPath  相对根目录的路径
     * @return 删除成功返回 true
     */
    public boolean delete(String rootPath, String relPath) {
        DeleteObjectResult result = of(rootPath).deleteObject(normalizeRel(relPath));
        return result != null && ObjectResult.ResultCode.SUCCESS == result.getResultCode();
    }

    /**
     * 列出目录下的直接子条目（不含递归）。
     *
     * @param rootPath   知识库根目录
     * @param dirRelPath 相对根目录的目录路径，空串表示根目录
     * @param limit      单次上限，非正数时用 {@link #LIST_LIMIT}
     * @return 子条目元数据；目录不存在或列举失败返回空列表
     */
    public List<Metadata> list(String rootPath, String dirRelPath, int limit) {
        ListObjectResult result = of(rootPath).listObject(ListObjectRequest.builder()
                .filePath(dirRelPath == null ? "" : dirRelPath.replace('\\', '/'))
                .limit(limit > 0 ? limit : LIST_LIMIT)
                .build());
        if (result == null || result.getMetadata() == null) {
            return List.of();
        }
        return result.getMetadata();
    }

    /**
     * 创建目录（含多级父目录）。
     *
     * @param rootPath   知识库根目录
     * @param dirRelPath 相对根目录的目录路径
     * @return 创建成功返回 true
     */
    public boolean mkdir(String rootPath, String dirRelPath) {
        // 对象存储没有真实目录，SPI 约定用「键以 / 结尾」表达建目录语义
        PutObjectResult result = of(rootPath).putObject(PutObjectRequest.builder()
                .filePath(normalizeRel(dirRelPath))
                .fileName("")
                .build());
        return result != null && ObjectResult.ResultCode.SUCCESS == result.getResultCode();
    }

    /**
     * 根目录绝对路径归一化。
     *
     * @param rootPath 知识库根目录
     * @return 归一化后的绝对路径字符串
     */
    private String normalizeRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("知识库根目录不能为空");
        }
        return Paths.get(rootPath).toAbsolutePath().normalize().toString();
    }

    /**
     * 按绝对根目录创建文件存储实例。
     *
     * @param absoluteRoot 归一化后的绝对根目录
     * @return 文件存储实例
     */
    private FileStorage createStorage(String absoluteRoot) {
        Path root = Paths.get(absoluteRoot);
        Path parent = root.getParent();
        if (parent == null) {
            // LocalFileStorage 的根固定是 <basePath>/<bucket>；文件系统根（如 "/"、"D:\"）没有父目录
            // 可拆，无法表达。这里明确报错——basePath 为空时它的兜底行为是落到系统临时目录，
            // 那会把知识库文件静默写到别处。
            throw new IllegalArgumentException("知识库根目录不能是文件系统根: " + absoluteRoot);
        }
        BucketSetting setting = BucketSetting.builder()
                .bucket(root.getFileName().toString())
                .extraProperties(Map.of("basePath", parent.toString()))
                .build();
        FileStorage storage = new LocalFileStorage(setting);
        log.info("[KnowledgeFileStorage] 知识库存储就绪: root={}", absoluteRoot);
        return storage;
    }

    /**
     * 取相对路径的父目录段。
     *
     * @param relPath 相对路径
     * @return 父目录段；顶层文件返回空串
     */
    private static String dirOf(String relPath) {
        String normalized = normalizeRel(relPath);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? "" : normalized.substring(0, index);
    }

    /**
     * 取相对路径的文件名段。
     *
     * @param relPath 相对路径
     * @return 文件名段
     */
    private static String nameOf(String relPath) {
        String normalized = normalizeRel(relPath);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    /**
     * 相对路径归一化：反斜杠转正斜杠、去掉前导斜杠。
     *
     * @param relPath 相对路径
     * @return 归一化后的相对路径
     */
    private static String normalizeRel(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        String normalized = relPath.replace('\\', '/');
        int start = 0;
        while (start < normalized.length() && normalized.charAt(start) == '/') {
            start++;
        }
        return normalized.substring(start);
    }
}
