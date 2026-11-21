package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.RemoteFileStatVO;
import com.chua.starter.monitor.support.model.UploadResultVO;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 主机文件上传统一入口：上传前做 md5 + 更新时间覆盖判定。
 *
 * <p>判定口径（定版）：md5 一致 → 跳过不传；不一致 → 目标文件更新时间较新则不覆盖并提示，
 * 源较新才覆盖。判定信息取不到（通道不支持 stat、时钟不可比、md5 计算失败）时，
 * 退回「直接覆盖」的既有行为，保证功能可用性不回退。</p>
 *
 * <p>两个上传入口（主机文件管理 {@code /ms/server/host/file/{hostId}/upload} 与
 * 系统管理 {@code /ms/system/files/upload}）都走本服务，避免判定逻辑分叉。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
public class HostFileUploadService {

    /**
     * 源端（浏览器）与目标机的时钟偏差容忍：目标比源「新」超过该值才认定目标较新。
     */
    private static final long CLOCK_SKEW_TOLERANCE_MILLIS = 60_000L;

    /**
     * 带覆盖判定的上传。
     *
     * @param provider                 采集通道
     * @param host                     目标主机
     * @param remotePath               远端目标路径（含文件名）
     * @param file                     上传文件
     * @param sourceLastModifiedMillis 源文件最后修改时间（浏览器 File.lastModified，epoch 毫秒），可为空
     * @param force                    目标较新时是否仍强制覆盖
     * @return 判定与上传结果，永不为 null
     */
    public UploadResultVO upload(SystemInfoProvider provider, MsServerHost host, String remotePath,
                                 MultipartFile file, Long sourceLastModifiedMillis, boolean force) {
        String sourceMd5 = md5Of(file);
        RemoteFileStatVO target = statQuietly(provider, host, remotePath);
        if (target != null && target.exists()) {
            if (sourceMd5 != null && sourceMd5.equalsIgnoreCase(target.md5())) {
                return new UploadResultVO(UploadResultVO.SKIPPED_SAME, remotePath,
                        "目标文件内容一致，已跳过上传", sourceMd5, target.md5(), target.lastModifiedMillis());
            }
            if (!force && targetNewer(sourceLastModifiedMillis, target.lastModifiedMillis())) {
                return new UploadResultVO(UploadResultVO.TARGET_NEWER, remotePath,
                        "目标文件更新时间较新，已停止覆盖", sourceMd5, target.md5(), target.lastModifiedMillis());
            }
        }
        doUpload(provider, host, remotePath, file);
        return new UploadResultVO(UploadResultVO.UPLOADED, remotePath, "上传成功",
                sourceMd5,
                target == null ? null : target.md5(),
                target == null ? null : target.lastModifiedMillis());
    }

    /**
     * 读取远端文件 stat；失败时退回 null（直接覆盖），不阻断上传。
     */
    private RemoteFileStatVO statQuietly(SystemInfoProvider provider, MsServerHost host, String remotePath) {
        try {
            return provider.statFile(host, remotePath);
        } catch (Exception e) {
            log.warn("远端 stat 失败，退回直接上传 host={} path={}: {}", host.getId(), remotePath, e.getMessage());
            return null;
        }
    }

    /**
     * 执行真实上传；失败统一转成 IllegalStateException，由控制器给出「上传失败: 原因」。
     */
    private void doUpload(SystemInfoProvider provider, MsServerHost host, String remotePath, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            boolean ok = provider.uploadFile(host, remotePath, in, file.getSize());
            if (!ok) {
                throw new IllegalStateException("通道返回上传失败: " + remotePath);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        }
    }

    /**
     * 流式计算上传内容 MD5，不把整份文件读进内存。
     *
     * <p>MultipartFile 由容器缓冲到临时文件，后续真实上传会重新打开流，
     * 这里先整遍读一遍只为算摘要。</p>
     */
    private String md5Of(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (DigestInputStream din = new DigestInputStream(in, md)) {
                byte[] buf = new byte[8192];
                while (din.read(buf) > 0) {
                    // 仅流过内容累计摘要，不保存字节
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            log.warn("源文件 MD5 计算失败 {}: {}", file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    /**
     * 目标是否比源新（超出时钟偏差容忍）。
     */
    private boolean targetNewer(Long sourceMillis, Long targetMillis) {
        if (sourceMillis == null || targetMillis == null) {
            return false;
        }
        return targetMillis - sourceMillis > CLOCK_SKEW_TOLERANCE_MILLIS;
    }
}
