package com.chua.starter.ai.support.generate;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 生成媒体的落盘存储。
 *
 * <p><b>为什么要有它。</b>部分供应商（OpenAI 兼容系的 {@code b64_json}、本地同步出图）
 * 返回的是整张图的 base64 而不是地址。此前这类结果被原样写进任务行的 JSON 列，
 * 于是一个只读的「历史列表」接口每次都要把页内每一条的整张图读回来：
 * 实测 30 条任务 543~680ms，而单行读在有内联图时 28~47ms、无内联图时 11~15ms ——
 * 开销随行数线性增长，因为**每一行**都带着一张完整 base64 图。库也会一直涨。</p>
 *
 * <p><b>这一层做什么。</b>把内联字节解码后落盘，库里只留一个短句柄，
 * 于是 {@code result} / {@code reference} 两列都不再随图片体积增长，
 * 列表也就与图片大小无关。字节仍然按需经
 * {@code /v2/ai/generate/tasks/{taskId}/media/{i}} 吐出，前端无需改动。</p>
 *
 * <p><b>兼容存量。</b>改造前入库的行里仍是 {@code data:} 原文，这一层不动它们：
 * 句柄与 {@code data:} 各自可识别，媒体接口两种都能服务，
 * 所以不需要一次性重写历史数据（要不要回填是独立决定，见交付说明）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateMediaStore {

    /**
     * 句柄前缀。
     *
     * <p>必须能与另外两类值明确区分：{@code data:}（改造前的内联原文）与
     * {@code http(s)://}（供应商本来给的地址）。直接拿「不以前两者开头」当判据是不行的 ——
     * 供应商理论上可以返回相对路径，那样会被误当成本地句柄。</p>
     */
    public static final String HANDLE_PREFIX = "media:";

    /**
     * data URI 的固定开头（改造前的存量形态）
     */
    private static final String DATA_URI_PREFIX = "data:";

    /**
     * base64 载荷分隔符
     */
    private static final String BASE64_MARKER = ";base64,";

    /**
     * 允许落盘的媒体类型白名单。
     *
     * <p>类型取自 data URI 的自述头，但仍属外部写入的文本，故按白名单收口；
     * 不在名单内的一律按 {@code .bin} 落盘、以 {@code application/octet-stream} 回放。</p>
     */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * 单张图片的落盘上限（字节）：64MB。
     *
     * <p>只防「不是图片的东西被当成图片写满磁盘」，正常生成结果远小于此值。</p>
     */
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    /**
     * 设置分组名（与 {@code GenerateGlobalSettingProvider} 一致）
     */
    private static final String SETTING_GROUP = "generate";

    /**
     * 生成文件根目录的默认值
     */
    private static final String DEFAULT_BASE_PATH = "data/generate-media";

    /**
     * 上传目录名的默认值
     */
    private static final String DEFAULT_UPLOAD_DIR = "upload";

    /**
     * 生成结果在磁盘上的相对路径中，标识「这是产出而非上传」的层级位置。
     *
     * <p>层级：{@code {账号}/{image|video}/{taskId}/{序号}.ext}；
     * 上传件多一层上传目录：{@code {账号}/{image|video}/{上传目录}/{taskId}/…}。</p>
     */
    private static final String CATEGORY_GENERATED = "generated";

    /**
     * 上传件（参考图等）
     */
    private static final String CATEGORY_UPLOAD = "upload";

    /**
     * 全局设置读取器：基路径与上传目录名都取运行时设置，改完即时生效
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 落盘的媒体。
     *
     * @param bytes       字节
     * @param contentType 媒体类型
     */
    public record Stored(byte[] bytes, String contentType) {
    }

    /**
     * 判断一个媒体值是不是本存储的句柄。
     *
     * @param value 媒体值
     * @return true 表示句柄
     */
    public static boolean isHandle(String value) {
        return value != null && value.startsWith(HANDLE_PREFIX);
    }

    /**
     * 判断一个媒体值是不是内联 base64（改造前的存量形态）。
     *
     * <p>这是「如何区分三类值」的唯一判据，服务层与媒体接口共用一处，
     * 避免两边各写一遍之后慢慢分叉。三类分别是：
     * 本存储句柄（{@link #isHandle}）、内联 base64（本方法）、
     * 供应商本来给的真实地址（都不是）。</p>
     *
     * @param value 媒体值
     * @return true 表示内联 base64
     */
    public static boolean isInlineData(String value) {
        return value != null && value.startsWith(DATA_URI_PREFIX) && value.contains(BASE64_MARKER);
    }

    /**
     * 判断一个媒体值是否由本应用的媒体接口提供服务。
     *
     * @param value 媒体值
     * @return true 表示应指向 /media/ 或 /reference
     */
    public static boolean isStored(String value) {
        return isHandle(value) || isInlineData(value);
    }

    /**
     * 把内联 base64 落盘为<b>生成结果</b>，返回句柄。
     *
     * <p>落盘层级：{@code {基路径}/{账号}/{image|video}/{taskId}/{序号}.ext}。</p>
     *
     * <p>任何失败都只记日志并返回 null：媒体落盘失败不该把一个已经生成成功的任务打成失败，
     * 调用方拿到 null 会退回「本次不持久化该产物」，与改造前超限时的行为一致。</p>
     *
     * @param userId    账号，作为第一层目录，便于按账号备份与配额统计
     * @param mediaType 媒体类型（image / video），作为第二层目录
     * @param taskId    任务号，作为任务目录，便于整目录清理
     * @param index     产物下标，用作文件名，便于按位取回
     * @param dataUri   data URI 原文
     * @return 句柄；解析失败、超限或落盘失败时返回 null
     */
    public String save(String userId, String mediaType, String taskId, String index, String dataUri) {
        return save(userId, mediaType, taskId, CATEGORY_GENERATED, index, dataUri);
    }

    /**
     * 把内联 base64 落盘为<b>上传件</b>（参考图等），返回句柄。
     *
     * <p>比生成结果多一层上传目录：{@code {账号}/{image|video}/{上传目录}/{taskId}/…}，
     * 让「产出」与「上传」在磁盘上天然分开，备份与清理策略可以不同。</p>
     *
     * @param userId    账号
     * @param mediaType 媒体类型（image / video）
     * @param taskId    任务号
     * @param name      文件名（不含扩展名）
     * @param dataUri   data URI 原文
     * @return 句柄；失败时返回 null
     */
    public String saveUpload(String userId, String mediaType, String taskId, String name, String dataUri) {
        return save(userId, mediaType, taskId, CATEGORY_UPLOAD, name, dataUri);
    }

    /**
     * 落盘的统一实现。
     *
     * @param userId    账号
     * @param mediaType 媒体类型
     * @param taskId    任务号
     * @param category  类别（generated / upload）
     * @param index     文件名（不含扩展名）
     * @param dataUri   data URI 原文
     * @return 句柄；失败时返回 null
     */
    private String save(String userId, String mediaType, String taskId, String category,
            String index, String dataUri) {
        if (taskId == null || dataUri == null) {
            return null;
        }
        int marker = dataUri.indexOf(BASE64_MARKER);
        if (!dataUri.startsWith(DATA_URI_PREFIX) || marker < 0) {
            return null;
        }
        String declared = dataUri.substring(DATA_URI_PREFIX.length(), marker);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUri.substring(marker + BASE64_MARKER.length()).trim());
        } catch (IllegalArgumentException e) {
            log.warn("[GenerateMedia] 内联媒体 base64 解析失败 taskId={} index={}", taskId, index);
            return null;
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            log.warn("[GenerateMedia] 内联媒体大小异常 {} 字节（上限 {}），本次不落盘 taskId={}",
                    bytes.length, MAX_BYTES, taskId);
            return null;
        }
        String name = safeName(index) + extensionOf(declared);
        String relative = relativePath(userId, mediaType, category, taskId, name);
        Path target = root().resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.warn("[GenerateMedia] 落盘失败 taskId={} index={}: {}", taskId, index, e.getMessage());
            return null;
        }
        return HANDLE_PREFIX + relative.replace('\\', '/');
    }

    /**
     * 拼出相对根目录的落盘路径。
     *
     * <p>层级固定为 {@code {账号}/{类型}/{taskId}/{文件名}}；上传件在类型目录后多插一层上传目录。
     * 全部路径段都过一遍白名单字符收口，避免账号名或文件名里的字符改变路径解析。</p>
     *
     * @param userId    账号
     * @param mediaType 媒体类型
     * @param category  类别（generated / upload）
     * @param taskId    任务号
     * @param name      文件名
     * @return 相对路径（以 / 分隔）
     */
    private String relativePath(String userId, String mediaType, String category, String taskId, String name) {
        String type = "video".equalsIgnoreCase(mediaType) ? "video" : "image";
        StringBuilder sb = new StringBuilder(64);
        sb.append(safeName(userId)).append('/').append(type).append('/');
        if (CATEGORY_UPLOAD.equals(category)) {
            sb.append(safeName(uploadDirName())).append('/');
        }
        sb.append(safeName(taskId)).append('/').append(name);
        return sb.toString();
    }

    /**
     * 按句柄读回媒体。
     *
     * @param handle 句柄
     * @return 媒体字节与类型；句柄非法、越界或文件不存在时返回 null
     */
    public Stored load(String handle) {
        if (!isHandle(handle)) {
            return null;
        }
        Path root = root();
        Path file = root.resolve(relativize(handle)).normalize();
        // 目录穿越防护：句柄来自库里，但仍是拼接出来的路径，必须确认解析结果落在根目录内
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            log.warn("[GenerateMedia] 句柄不可达或越界: {}", handle);
            return null;
        }
        try {
            return new Stored(Files.readAllBytes(file), contentTypeOf(file.getFileName().toString()));
        } catch (IOException e) {
            log.warn("[GenerateMedia] 读取失败 {}: {}", handle, e.getMessage());
            return null;
        }
    }

    /**
     * 删除某个任务的全部媒体文件。
     *
     * <p>任务记录被删除时一并清理，避免磁盘上留下没有归属的图片。
     * 单文件删除失败不影响其余文件，也不影响记录删除本身。</p>
     *
     * <p><b>为什么是「递归找一个同名目录」而不是拼一个固定路径。</b>
     * 任务目录的深度随布局变化：新布局在 {@code {账号}/{类型}/{taskId}} 或
     * {@code {账号}/{类型}/{上传目录}/{taskId}}，而改造前的旧布局在 {@code {根}/{taskId}}。
     * 拼路径就得知道它是哪一种；按名字限深查找则两种都能覆盖，
     * 也不会因为用户改了基路径配置就漏删旧文件。</p>
     *
     * @param taskId 任务号
     */
    public void deleteAll(String taskId) {
        if (taskId == null) {
            return;
        }
        Path dir = findTaskDir(root(), safeName(taskId), 4);
        if (dir == null) {
            return;
        }
        deleteRecursively(dir);
    }

    /**
     * 在限定的深度内找出名为 taskId 的目录。
     *
     * @param dir    当前目录
     * @param taskId 目标目录名（已收口）
     * @param depth  剩余可下探的层数
     * @return 命中目录；未命中返回 null
     */
    private static Path findTaskDir(Path dir, String taskId, int depth) {
        if (depth < 0 || !Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> children = Files.list(dir)) {
            List<Path> dirs = children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (Path child : dirs) {
                if (taskId.equals(child.getFileName().toString())) {
                    return child;
                }
            }
            for (Path child : dirs) {
                Path hit = findTaskDir(child, taskId, depth - 1);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * 递归删除目录及其内容，并顺带清掉因此变空的父目录。
     *
     * @param dir 待删除目录
     */
    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("[GenerateMedia] 删除失败 {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("[GenerateMedia] 清理目录失败 {}: {}", dir, e.getMessage());
        }
    }

    /**
     * 媒体根目录（解析为绝对路径）。
     *
     * <p>取运行时设置项而不是启动时注入的属性：目录这是用户在设置页里能改的东西，
     * 用 {@code @Value} 读到的是启动那一刻的值，改了要重启才生效。
     * 读不到时回落默认值，设置表缺项也能正常落盘。</p>
     *
     * @return 绝对规范化的根目录
     */
    private Path root() {
        return Paths.get(basePath()).toAbsolutePath().normalize();
    }

    /**
     * 生成文件根目录（相对工作目录的基路径）。
     *
     * @return 基路径
     */
    private String basePath() {
        String value = readSetting("media_base_path", DEFAULT_BASE_PATH);
        return value.isBlank() ? DEFAULT_BASE_PATH : value;
    }

    /**
     * 上传目录名。
     *
     * @return 上传目录名
     */
    private String uploadDirName() {
        String value = readSetting("media_upload_dir", DEFAULT_UPLOAD_DIR);
        return value.isBlank() ? DEFAULT_UPLOAD_DIR : value;
    }

    /**
     * 读一个生成分组的设置项。
     *
     * @param field        字段名
     * @param defaultValue 缺省值
     * @return 设置值；读取失败时返回缺省值
     */
    private String readSetting(String field, String defaultValue) {
        try {
            String value = globalSettingReader.getStr(SETTING_GROUP, field);
            return value == null ? defaultValue : value.trim();
        } catch (Exception e) {
            // 设置读取失败不该让落盘整体不可用：目录用默认值即可，任务本身不受影响
            log.debug("[GenerateMedia] 读取设置项 {} 失败，回落默认值 {}: {}",
                    field, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 从句柄切出相对路径部分。
     *
     * @param handle 句柄
     * @return 相对路径
     */
    private static String relativize(String handle) {
        return handle.substring(HANDLE_PREFIX.length());
    }

    /**
     * 目录项名收口为白名单字符，避免任务号或下标里的字符影响路径解析。
     *
     * @param raw 原始值
     * @return 可安全用作路径段的值
     */
    private static String safeName(String raw) {
        String value = raw == null ? "" : raw;
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(SAFE_NAME.matcher(String.valueOf(c)).matches() ? c : '_');
        }
        String result = sb.toString();
        return result.isEmpty() ? "unknown" : result;
    }

    /**
     * 由 data URI 的自述类型给出扩展名。
     *
     * @param declared 自述类型，如 {@code image/png}
     * @return 扩展名（含点）
     */
    private static String extensionOf(String declared) {
        String type = declared == null ? "" : declared.toLowerCase(Locale.ROOT);
        if (type.contains("jpeg") || type.contains("jpg")) {
            return ".jpg";
        }
        if (type.contains("webp")) {
            return ".webp";
        }
        if (type.contains("gif")) {
            return ".gif";
        }
        if (type.contains("mp4")) {
            return ".mp4";
        }
        if (type.contains("png")) {
            return ".png";
        }
        return ".bin";
    }

    /**
     * 由扩展名反推媒体类型。
     *
     * @param name 文件名
     * @return 媒体类型
     */
    private static String contentTypeOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }
}
