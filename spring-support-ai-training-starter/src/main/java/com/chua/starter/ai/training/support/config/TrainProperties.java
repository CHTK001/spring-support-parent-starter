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
 * 通过 {@code plugin.ai.training.*} 前缀注入，支持 Mac/Linux/窗口 多环境配置。</p>
 *
 * <p>完整 YAML 配置示例（前缀 {@code plugin.ai.training}，路径请按实际环境调整）：</p>
 *
 * <pre>
 * plugin:
 *   ai:
 *     training:
 *       enabled: true                                    # 总开关，false 时整个训练模块不装配
 *       max-size: 3000                                   # 上传大小上限展示值（MB），不拦截上传
 *       model-path: D:/aias/models/resnet50_dl4j_inference.v3.zip   # resnet50 预训练模型（ZIP 或目录）
 *       data-root-path: D:/aias/data/image_root/         # 数据解压根目录（其下按 UUID 建子目录）
 *       save-path: D:/aias/models/modelv2/               # 新训练模型保存目录
 *       upload-path: D:/aias/data/upload/                # 上传 ZIP 落盘目录（自动创建）
 * </pre>
 *
 * <p>注意：{@code plugin.ai.training.yolo.python} / {@code plugin.ai.training.yolo.script}
 * 是 JVM 系统属性（{@code -D} 传入），不属于本配置类的 YAML 绑定项。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.ai.training")
public class TrainProperties {

    /**
     * 是否启用 AI 训练模块。
     *
     * <p>含义：总开关，控制 {@code TrainAutoConfiguration} 整体装配（{@code @ConditionalOnProperty}，
     * {@code matchIfMissing = true}）。置 {@code false} 时 {@code DataService} / {@code TrainService} /
     * {@code TrainController} 等训练 Bean 均不注册。</p>
     *
     * <p>默认值：{@code true}（配置缺失时同样按开启处理）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       enabled: true
     * </pre>
     */
    private boolean enabled = true;

    /**
     * 文件上传大小限制（MB）。
     *
     * <p>含义：上传大小上限的<b>展示值</b>，仅用于服务信息面板（{@code [上传限制: 3000MB]}），
     * 不会拦截或校验实际上传；真实上传上限取决于宿主的
     * {@code spring.servlet.multipart.max-file-size} / {@code max-request-size}。</p>
     *
     * <p>默认值：{@code 3000}（MB）。字段无 ≤0 回落逻辑：配置为 0 或负数时按原值展示，不做修正。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       max-size: 3000
     * </pre>
     */
    // [P3C A20 豁免]
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
     * 预训练模型路径（ZIP 压缩包或目录，AIAS: resnet50_dl4j_inference.v3.zip）。
     *
     * <p>含义：{@code resnet50} 引擎冷启动使用的预训练模型路径，经 {@code TrainContext#modelPath}
     * 传入训练引擎；路径必须真实存在，否则 {@code resnet50} 训练启动失败（模块不做提前校验）。
     * {@code yolo} 引擎不使用该项。</p>
     *
     * <p>默认值：{@code {base}/resnet50_dl4j_inference.v3.zip}，其中 {@code {base}} 为
     * {@code defaultBaseDir()}：Windows 为 {@code C:\aias\file}，其他平台为 {@code ~/.aias/file}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       model-path: D:/aias/models/resnet50_dl4j_inference.v3.zip
     * </pre>
     */
    private String modelPath = DEFAULT_BASE_DIR + File.separator + "resnet50_dl4j_inference.v3.zip";

    /**
     * 数据解压根目录。
     *
     * <p>含义：上传 ZIP 解压后的根目录，每次解压在其下创建 UUID 子目录
     * （{@code DataServiceImpl#unzip}，底层 Extractor SPI 解压）；目录需进程可写。</p>
     *
     * <p>默认值：{@code {base}/image_root/}（{@code {base}} 同上：Windows {@code C:\aias\file}，
     * 其他平台 {@code ~/.aias/file}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       data-root-path: D:/aias/data/image_root/
     * </pre>
     */
    private String dataRootPath = DEFAULT_BASE_DIR + File.separator + "image_root" + File.separator;

    /**
     * 新训练模型保存目录。
     *
     * <p>含义：训练产出的新模型保存目录，经 {@code TrainContext#savePath} 传入引擎；
     * {@code yolo} 场景同时作为 Python 子进程 {@code /start} 的 {@code project} 参数；目录需进程可写。</p>
     *
     * <p>默认值：{@code {base}/modelv2/}（{@code {base}} 同上：Windows {@code C:\aias\file}，
     * 其他平台 {@code ~/.aias/file}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       save-path: D:/aias/models/modelv2/
     * </pre>
     */
    private String savePath = DEFAULT_BASE_DIR + File.separator + "modelv2" + File.separator;

    /**
     * 文件上传目录。
     *
     * <p>含义：{@code POST /v2/train/upload} 上传 ZIP 的落盘目录（{@code DataServiceImpl}
     * 保存时自动创建）。</p>
     *
     * <p>默认值：{@code {base}/}（{@code {base}} 同上：Windows {@code C:\aias\file}，
     * 其他平台 {@code ~/.aias/file}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ai:
     *     training:
     *       upload-path: D:/aias/data/upload/
     * </pre>
     */
    private String uploadPath = DEFAULT_BASE_DIR + File.separator;
}
