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
 * 训练数据服务实现：ZIP 文件上传保存与解压。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {

    private final TrainProperties properties;

    @Override
    public String saveUploadedFile(byte[] data, String name) throws Exception {
        // 魔数校验：ZIP 本地文件头 PK\x03\x04（兼容空包 \x05\x06 / 分卷 \x07\x08），拦截伪装文件
        if (data == null || data.length < 4 || data[0] != 'P' || data[1] != 'K'
                || (data[2] != 3 && data[2] != 5 && data[2] != 7)) {
            throw new IllegalArgumentException("文件内容不是有效的 ZIP 格式");
        }
        Path uploadDir = Path.of(properties.getUploadPath());
        Files.createDirectories(uploadDir);
        String safeName = UUID.randomUUID() + "_" + name;
        Path target = uploadDir.resolve(safeName);
        Files.write(target, data);
        log.info("[Train] 文件已保存: {}", target);
        return target.toAbsolutePath().toString();
    }

    @Override
    public String unzip(String zipFilePath) throws Exception {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            throw new IllegalArgumentException("ZIP 文件不存在: " + zipFilePath);
        }

        // 数据根目录以 UUID 命名
        String uuid = UUID.randomUUID().toString();
        File destDir = new File(properties.getDataRootPath(), uuid);
        destDir.mkdirs();

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File outFile = new File(destDir, entry.getName());
                // 安全检查：防止 Zip Slip 攻击
                String canonicalDestPath = outFile.getCanonicalPath();
                String canonicalDestDir = destDir.getCanonicalPath();
                if (!canonicalDestPath.startsWith(canonicalDestDir + File.separator)) {
                    throw new SecurityException("ZIP 路径越界: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
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