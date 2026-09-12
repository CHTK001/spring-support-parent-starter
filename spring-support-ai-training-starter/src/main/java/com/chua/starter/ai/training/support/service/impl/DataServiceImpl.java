package com.chua.starter.ai.training.support.service.impl;

import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.service.DataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.UUID;

/**
   * 训练数据服务实现：压缩 文件上传保存与解压。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {

    /** 文件名净化后为空时的回退名。 */
    static final String DEFAULT_FILE_NAME = "upload.zip";

    private final TrainProperties properties; // 属性

    @Override
    public String saveUploadedFile(byte[] data, String name) throws Exception {
 // 魔数校验：压缩 本地文件头 PK\x03\x04（兼容空包 \x05\x06 / 分卷 \x07\x08），拦截伪装文件
        if (data == null || data.length < 4 || data[0] != 'P' || data[1] != 'K'
                || (data[2] != 3 && data[2] != 5 && data[2] != 7)) {
            /**
             * illegal参数异常。
             * @param 格式" 格式"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("文件内容不是有效的 ZIP 格式");
        }
        Path uploadDir = Path.of(properties.getUploadPath());
        Files.createDirectories(uploadDir);
 // 文件名净化（基名 + 剔除非法字符）：原始文件名 可能携带路径分隔符/盘符，
 // 直接拼入 路径.resolve 有逃逸上传目录的隐患（配合随机前缀防重名）
        String safeName = UUID.randomUUID() + "_" + sanitizeFileName(name);
        Path target = uploadDir.resolve(safeName);
        Files.write(target, data);
        log.info("[Train] 文件已保存: {}", target);
        return target.toAbsolutePath().toString();
    }

    /**
     * 净化客户端原始文件名：仅保留末段路径基名，并剔除路径分隔符与
      * 窗口/通用非法字符（{@code \ / : * ? " < > |} 及控制字符）。
     * 净化结果为空（全非法字符/纯路径）时回退 {@link #DEFAULT_FILE_NAME}。
     *
     * <p>包可见便于单测直接覆盖净化规则。</p>
     *
     * @param name 客户端原始文件名（可为 空）
     * @return 安全文件名（不含任何路径分隔符/非法字符）
     */
    static String sanitizeFileName(String name) {
        if (name == null) {
            return DEFAULT_FILE_NAME;
        }
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");
        return cleaned.isBlank() ? DEFAULT_FILE_NAME : cleaned;
    }

    @Override
    public String unzip(String zipFilePath) throws Exception {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            /**
             * illegal参数异常。
             * @param zipFilePath 压缩文件路径
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("ZIP 文件不存在: " + zipFilePath);
        }

        // 数据根目录以 UUID 命名
        String uuid = UUID.randomUUID().toString();
        File destDir = new File(properties.getDataRootPath(), uuid);
        destDir.mkdirs();

        /**
         * 尝试。
         * @param ZipFile(zipFile 压缩文件(压缩文件
         */
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File outFile = new File(destDir, entry.getName());
 // 安全检查：防止 压缩 Slip 攻击
                String canonicalDestPath = outFile.getCanonicalPath();
                String canonicalDestDir = destDir.getCanonicalPath();
                if (!canonicalDestPath.startsWith(canonicalDestDir + File.separator)) {
                    /**
                     * 安全性异常。
                     * @param entry.getName( entry.获取名称(
                     * @return 安全性异常的结果
                     */
                    throw new SecurityException("ZIP 路径越界: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    /**
                     * 尝试。
                     * @param FileOutputStream(outFile 文件输出流(出文件
                     */
                    try (OutputStream os = new FileOutputStream(outFile)) {
                        zip.getInputStream(entry).transferTo(os);
                    }
                }
            }
        }
        log.info("[Train] ZIP 已解压到: {}", destDir.getAbsolutePath());
        return destDir.getAbsolutePath();
    }

    @Override
    public boolean isZipFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".zip");
    }
}