package com.chua.starter.common.support.api.decode;

import com.chua.common.support.lang.algorithm.crypto.KeyLogCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * keylog 服务端长期密钥提供器。
 *
 * <p>keylog 协议的服务端持有一对长期 X25519 密钥：公钥经 sys_setting
 * （{@code CodecServerPublicKey}）下发给前端；私钥只留在服务端，绝不进
 * 设置表、绝不出服务器。</p>
 *
 * <p>为避免「重启即换钥」导致客户端公钥指纹失配，密钥对持久化在本地文件
 * （{@code plugin.api.decode.keylog-key-file}），内容为
 * {@code pair:<公钥hex>:<私钥hex>}。同机多个服务（system/monitor/ai 等）
 * 指向同一文件时，先启动者生成并落盘、后启动者直接读取，保证整机的公钥一致。</p>
 *
 * <p>未配置文件路径时退化为进程内生成（仅单服务部署可用，重启即换钥）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class KeyLogServerKeyProvider {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(KeyLogServerKeyProvider.class);

    /**
     * 密钥串角色前缀（与 KeyLogCodec 的 pair 角色一致）
     */
    private static final String SPEC_PREFIX = "pair:";

    /**
     * 密钥文件路径；null 表示进程内生成
     */
    private final String keyFilePath;

    /**
     * pair 角色密钥串（懒加载）
     */
    private volatile String pairSpec;

    /**
     * 服务端长期公钥 hex（懒加载）
     */
    private volatile String publicKeyHex;

    /**
     * 创建 keylog服务端密钥提供器
     *
     * @param keyFilePath 密钥文件路径；空表示进程内生成
     */
    public KeyLogServerKeyProvider(String keyFilePath) {
        this.keyFilePath = (null == keyFilePath || keyFilePath.isBlank()) ? null : keyFilePath.trim();
    }

    /**
     * 获取 pair 角色密钥串（{@code pair:<公钥hex>:<私钥hex>}），首次调用时读取或生成。
     *
     * @return pair 密钥串
     */
    public String getPairSpec() {
        if (null == pairSpec) {
            synchronized (this) {
                if (null == pairSpec) {
                    loadOrGenerate();
                }
            }
        }
        return pairSpec;
    }

    /**
     * 获取服务端长期公钥 hex（X.509 SPKI），用于写入 sys_setting 下发前端。
     *
     * @return 公钥十六进制
     */
    public String getPublicKeyHex() {
        getPairSpec();
        return publicKeyHex;
    }

    /**
     * 读取或生成长期密钥对。
     *
     * <p>文件存在且格式合法 → 直接读取；否则生成新密钥对，先写临时文件再原子替换，
     * 避免并发启动读到半截文件。文件不可用时退化为进程内密钥。</p>
     */
    private void loadOrGenerate() {
        if (null != keyFilePath) {
            Path path = new File(keyFilePath).toPath();
            try {
                if (Files.isRegularFile(path)) {
                    String line = Files.readString(path, StandardCharsets.UTF_8).trim();
                    String[] parts = line.split(":");
                    if (line.startsWith(SPEC_PREFIX) && parts.length == 3
                            && !parts[1].isBlank() && !parts[2].isBlank()) {
                        this.pairSpec = line;
                        this.publicKeyHex = parts[1];
                        log.info("[KeyLog] server key loaded from {}", keyFilePath);
                        return;
                    }
                    log.warn("[KeyLog] key file {} has invalid format, regenerating", keyFilePath);
                }
                String[] pair = KeyLogCodec.generateKeyPair();
                String spec = SPEC_PREFIX + pair[0] + ":" + pair[1];
                Path parent = path.toAbsolutePath().getParent();
                if (null != parent) {
                    Files.createDirectories(parent);
                }
                Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(tmp, spec, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception atomicUnsupported) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                this.pairSpec = spec;
                this.publicKeyHex = pair[0];
                log.info("[KeyLog] new server key generated at {}", keyFilePath);
                return;
            } catch (Exception e) {
                log.warn("[KeyLog] key file {} unavailable ({}), fallback to in-memory key",
                        keyFilePath, e.getMessage());
            }
        }
        String[] pair = KeyLogCodec.generateKeyPair();
        this.pairSpec = SPEC_PREFIX + pair[0] + ":" + pair[1];
        this.publicKeyHex = pair[0];
        log.info("[KeyLog] in-memory server key generated (no key file configured)");
    }
}
