package com.chua.starter.ai.training.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * AI 训练平台配置属性。
 *
 * <p>对应 AIAS 2_training_platform 的 {@code file.*} 配置段，
   * 通过 {@code ai.training.*} 前缀注入，支持 Mac/Linux/窗口 多环境配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "ai.training")
public class TrainProperties {

    /**
     * 是否启用 AI 训练模块，默认 true。
     *
     * <p>对应自动配置上的 {@code ai.training.enabled} 门控（默认开启，置 false 可整体关闭）。</p>
     */
    private boolean enabled = true;

    /**
     * 文件上传大小限制（MB），默认 3000。
     */
    private int maxSize = 3000;

    /**
      * 默认数据根目录：窗口 保持与 AIAS 一致（C:\aias\文件），
      * 其余平台落 ~/.aias/文件，保证 Mac/Linux 开箱即用。
     */
    private static final String DEFAULT_BASE_DIR = defaultBaseDir();

    /**
     * 默认basedir。
     * @return 默认basedir的结果
     */
    private static String defaultBaseDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "C:" + File.separator + "aias" + File.separator + "file";
        }
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".aias", "file").toString();
    }

    /**
      * 预训练模型路径（压缩 或目录，AIAS: resnet50_dl4j_推理.v3.压缩）。
     */
    private String modelPath = DEFAULT_BASE_DIR + File.separator + "resnet50_dl4j_inference.v3.zip";

    /**
     * 数据解压根目录。
     */
    private String dataRootPath = DEFAULT_BASE_DIR + File.separator + "image_root" + File.separator;

    /**
     * 新训练模型保存目录。
     */
    private String savePath = DEFAULT_BASE_DIR + File.separator + "modelv2" + File.separator;

    /**
     * 文件上传目录。
     */
    private String uploadPath = DEFAULT_BASE_DIR + File.separator;
}