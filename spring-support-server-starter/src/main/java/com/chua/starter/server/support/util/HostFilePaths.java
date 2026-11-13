package com.chua.starter.server.support.util;

import java.util.Locale;
import java.util.Set;

/**
 * 主机文件路径与类型工具。
 *
 * <p>「主机文件」浏览的是文件存储（本地桶或对象存储）里相对根目录的 key。key 来自前端，
 * 任何一次拼接都必须先归一化并挡住目录穿越，否则一个 {@code ../../etc/passwd} 就能把
 * 上传 / 删除 / 重命名打到存储根之外。这里把归一化与校验收在一处，控制器与存储实现
 * 不各写一套规则。</p>
 *
 * <p>类型（kind）也在这里判定：前端只按 kind 选图标与筛选项，不自己猜扩展名，
 * 避免「后端认得 jar、前端只认得 zip」这种两套口径。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
public final class HostFilePaths {

    /**
     * 路径分隔符
     */
    public static final String SEPARATOR = "/";

    /**
     * 类型：目录
     */
    public static final String KIND_FOLDER = "folder";

    /**
     * 类型：运行包（jar / war 等发布产物）
     */
    public static final String KIND_PACKAGE = "package";

    /**
     * 类型：压缩归档
     */
    public static final String KIND_ARCHIVE = "archive";

    /**
     * 类型：图片
     */
    public static final String KIND_IMAGE = "image";

    /**
     * 类型：文本 / 配置 / 日志
     */
    public static final String KIND_TEXT = "text";

    /**
     * 类型：代码 / 脚本
     */
    public static final String KIND_CODE = "code";

    /**
     * 类型：音视频
     */
    public static final String KIND_MEDIA = "media";

    /**
     * 类型：其它二进制
     */
    public static final String KIND_OTHER = "other";

    /**
     * 运行包扩展名
     */
    private static final Set<String> PACKAGE_EXTENSIONS =
            Set.of("jar", "war", "ear", "aar", "apk", "deb", "rpm", "msi", "dmg", "exe");

    /**
     * 压缩归档扩展名
     */
    private static final Set<String> ARCHIVE_EXTENSIONS =
            Set.of("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "iso", "zst");

    /**
     * 图片扩展名
     */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico", "tif", "tiff");

    /**
     * 文本 / 配置扩展名
     */
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("txt", "md", "log", "csv", "tsv", "json", "xml", "yml", "yaml", "ini",
                    "properties", "conf", "cfg", "env", "toml");

    /**
     * 代码 / 脚本扩展名
     */
    private static final Set<String> CODE_EXTENSIONS =
            Set.of("java", "kt", "js", "mjs", "cjs", "ts", "tsx", "jsx", "vue", "py", "go", "rs",
                    "c", "h", "cpp", "hpp", "cs", "sh", "bash", "zsh", "bat", "ps1", "sql", "html",
                    "css", "scss", "less", "php", "rb", "lua", "gradle");

    /**
     * 音视频扩展名
     */
    private static final Set<String> MEDIA_EXTENSIONS =
            Set.of("mp3", "mp4", "wav", "flac", "ogg", "aac", "m4a", "avi", "mkv", "mov", "wmv",
                    "flv", "webm");

    /**
     * 工具类不允许实例化
     */
    private HostFilePaths() {
    }

    /**
     * 归一化目录：统一用 {@code /}，去掉首尾分隔符，末段补齐一个分隔符。
     *
     * @param path 目录 key（可为空）
     * @return 归一化结果；空目录返回空串；含穿越段返回 null（调用方按非法路径处理）
     */
    public static String normalizeDir(String path) {
        String key = normalizeKey(path);
        if (key == null || key.isEmpty()) {
            return "";
        }
        return key + SEPARATOR;
    }

    /**
     * 归一化文件 / 目录 key：统一分隔符、去掉首尾分隔符。
     *
     * @param raw 原始 key（可为空）
     * @return 归一化结果；非法路径返回 null
     */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String key = raw.replace('\\', '/').trim();
        StringBuilder out = new StringBuilder(key.length());
        for (String segment : key.split(SEPARATOR)) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            if (out.length() > 0) {
                out.append(SEPARATOR);
            }
            out.append(segment);
        }
        return out.toString();
    }

    /**
     * 校验单个文件 / 目录名。
     *
     * @param name 名称
     * @return 合法返回 true
     */
    public static boolean isSafeName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || ".".equals(trimmed) || "..".equals(trimmed)) {
            return false;
        }
        return trimmed.indexOf('/') < 0 && trimmed.indexOf('\\') < 0 && trimmed.indexOf(':') < 0;
    }

    /**
     * 取 key 末段名称。
     *
     * @param key 归一化 key
     * @return 名称；空 key 返回空串
     */
    public static String nameOf(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        int index = key.lastIndexOf(SEPARATOR);
        return index < 0 ? key : key.substring(index + 1);
    }

    /**
     * 取 key 的父目录（不含末尾分隔符）。
     *
     * @param key 归一化 key
     * @return 父目录 key；顶层返回空串
     */
    public static String parentOf(String key) {
        if (key == null) {
            return "";
        }
        int index = key.lastIndexOf(SEPARATOR);
        return index < 0 ? "" : key.substring(0, index);
    }

    /**
     * 拼接子项 key。
     *
     * @param dir  父目录 key（可空）
     * @param name 子项名称
     * @return 子项 key；名称不合法返回 null
     */
    public static String child(String dir, String name) {
        if (!isSafeName(name)) {
            return null;
        }
        String parent = normalizeKey(dir);
        if (parent == null) {
            return null;
        }
        return parent.isEmpty() ? name.trim() : parent + SEPARATOR + name.trim();
    }

    /**
     * 取小写扩展名（不带点）。
     *
     * @param name 文件名
     * @return 扩展名；没有扩展名返回空串
     */
    public static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 判定条目类型。
     *
     * @param name      文件名
     * @param directory 是否目录
     * @return 类型常量；未知类型返回 {@link #KIND_OTHER}
     */
    public static String kindOf(String name, boolean directory) {
        if (directory) {
            return KIND_FOLDER;
        }
        String ext = extOf(name);
        if (PACKAGE_EXTENSIONS.contains(ext)) {
            return KIND_PACKAGE;
        }
        if (ARCHIVE_EXTENSIONS.contains(ext)) {
            return KIND_ARCHIVE;
        }
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return KIND_IMAGE;
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return KIND_TEXT;
        }
        if (CODE_EXTENSIONS.contains(ext)) {
            return KIND_CODE;
        }
        if (MEDIA_EXTENSIONS.contains(ext)) {
            return KIND_MEDIA;
        }
        return KIND_OTHER;
    }

    /**
     * 是否为可在线预览的文本类型。
     *
     * @param name 文件名
     * @return 文本或代码类型返回 true
     */
    public static boolean isPreviewableText(String name) {
        String kind = kindOf(name, false);
        return KIND_TEXT.equals(kind) || KIND_CODE.equals(kind);
    }

    /**
     * 按扩展名给出响应类型，供下载 / 内联预览使用。
     *
     * @param name 文件名
     * @return MIME 类型；未命中返回 {@code application/octet-stream}
     */
    public static String contentTypeOf(String name) {
        String kind = kindOf(name, false);
        String ext = extOf(name);
        if (KIND_IMAGE.equals(kind)) {
            if ("svg".equals(ext)) {
                return "image/svg+xml";
            }
            if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                return "image/jpeg";
            }
            if ("ico".equals(ext)) {
                return "image/x-icon";
            }
            return "image/" + ext;
        }
        if ("pdf".equals(ext)) {
            return "application/pdf";
        }
        if (KIND_TEXT.equals(kind) || KIND_CODE.equals(kind)) {
            return "text/plain;charset=UTF-8";
        }
        if (KIND_MEDIA.equals(kind)) {
            return "audio".equals(ext) ? "audio/mpeg" : "video/" + ext;
        }
        return "application/octet-stream";
    }
}
