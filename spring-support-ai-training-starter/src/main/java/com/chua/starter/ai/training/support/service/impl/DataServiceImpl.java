package com.chua.starter.ai.training.support.service.impl;

import com.chua.common.support.network.download.extractor.Extractor;
import com.chua.common.support.network.download.extractor.ExtractorFactory;
import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.service.DataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * 文件名净化后为空时的回退名。
     */
    static final String DEFAULT_FILE_NAME = "upload.zip";

    /**
     * 训练配置属性。
     */
    private final TrainProperties properties;

    @Override
    public String saveUploadedFile(byte[] data, String name) throws Exception {
        // 魔数校验：压缩 本地文件头 PK\x03\x04（兼容空包 \x05\x06 / 分卷 \x07\x08），拦截伪装文件
        if (data == null || data.length < 4 || data[0] != 'P' || data[1] != 'K'
                || (data[2] != 3 && data[2] != 5 && data[2] != 7)) {
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

    /**
     * 解压上传的 ZIP 到数据根目录下以 UUID 命名的新目录。
     *
     * <p>解压统一走底层 {@link Extractor} SPI（utils-support-common-starter 的 zip4j 实现，
     * 含密码支持与 Zip Slip 前置校验），本模块不再自实现解压细节；任一条目越界时
     * SPI 在解压前整体拒绝，本方法以 {@link IllegalStateException} 上抛。</p>
     *
     * @param zipFilePath 上传保存后的 ZIP 文件路径
     * @return 解压目标目录（数据根目录下 UUID 子目录）的绝对路径
     * @throws IllegalArgumentException ZIP 文件不存在或格式不支持
     * @throws IllegalStateException    ZIP 解压失败（含路径越界被拒绝）
     */
    @Override
    public String unzip(String zipFilePath) throws Exception {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            throw new IllegalArgumentException("ZIP 文件不存在: " + zipFilePath);
        }

        // 数据根目录以 UUID 命名
        String uuid = UUID.randomUUID().toString();
        File destDir = new File(properties.getDataRootPath(), uuid);

        Extractor extractor = ExtractorFactory.getExtractor(zipFile.getName());
        if (extractor == null) {
            throw new IllegalArgumentException("不支持的压缩格式: " + zipFile.getName());
        }
        if (!extractor.extract(zipFile, destDir)) {
            throw new IllegalStateException("ZIP 解压失败: " + zipFilePath);
        }

        log.info("[Train] ZIP 已解压到: {}", destDir.getAbsolutePath());
        return destDir.getAbsolutePath();
    }

    @Override
    public boolean isZipFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".zip");
    }
}
