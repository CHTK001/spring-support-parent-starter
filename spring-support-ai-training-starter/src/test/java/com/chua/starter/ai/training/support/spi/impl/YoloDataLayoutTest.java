package com.chua.starter.ai.training.support.spi.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link YoloTrainProvider#locateDataYaml(Path)} 数据布局定位单元测试。
 *
 * <p>YOLO(Ultralytics) 数据集的 data.yaml 可能位于解压根目录，也可能嵌套在
 * dataset/ 等子目录（取决于用户打包方式）。定位规则：递归向下最多
 * {@link YoloTrainProvider#MAX_DATA_YAML_DEPTH} 层、文件名大小写宽容、
 * 多候选时取目录层级最浅者。本测试覆盖各布局与边界。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class YoloDataLayoutTest {

    @TempDir
    Path tempDir;

    private Path mkdirs(String... segments) throws IOException {
        Path d = tempDir;
        for (String segment : segments) {
            d = d.resolve(segment);
        }
        return Files.createDirectories(d);
    }

    private void touchDataYaml(Path dir) throws IOException {
        Files.writeString(dir.resolve("data.yaml"),
                "path: .\nnames:\n  0: cat\n  1: dog\n");
    }

    @Test
    @DisplayName("根目录含 data.yaml（标准布局）→ 命中根目录")
    void locatesTopLevelDataYaml() throws IOException {
        touchDataYaml(tempDir);

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isEqualTo(tempDir.resolve("data.yaml").toAbsolutePath());
    }

    @Test
    @DisplayName("data.yaml 嵌套于 dataset/ 子目录 → 命中子目录")
    void locatesNestedDataYaml() throws IOException {
        Path dataset = mkdirs("dataset", "images");
        mkdirs("dataset", "labels");
        touchDataYaml(dataset);

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isEqualTo(dataset.resolve("data.yaml").toAbsolutePath());
    }

    @Test
    @DisplayName("多级嵌套（深度=3，达上限）→ 仍能命中")
    void locatesDeepNestedDataYamlAtMaxDepth() throws IOException {
        Path deep = mkdirs("a", "b");
        touchDataYaml(deep);

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isEqualTo(deep.resolve("data.yaml").toAbsolutePath());
    }

    @Test
    @DisplayName("超出最大深度（深度=4 > 3）→ 视为未找到，返回 null")
    void ignoresDataYamlBeyondMaxDepth() throws IOException {
        Path tooDeep = mkdirs("a", "b", "c");
        touchDataYaml(tooDeep);

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isNull();
    }

    @Test
    @DisplayName("目录下无任何 data.yaml → 返回 null")
    void returnsNullWhenNoDataYaml() throws IOException {
        mkdirs("dataset", "images");

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isNull();
    }

    @Test
    @DisplayName("根与子目录均含 data.yaml → 取目录层级最浅者（根目录）")
    void picksShallowestWhenMultipleCandidates() throws IOException {
        touchDataYaml(tempDir);
        Path dataset = mkdirs("dataset", "images");
        touchDataYaml(dataset);

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isEqualTo(tempDir.resolve("data.yaml").toAbsolutePath());
    }

    @Test
    @DisplayName("文件名大小写宽容（Data.YAML）→ 仍能命中")
    void isCaseInsensitiveOnFileName() throws IOException {
        Files.writeString(tempDir.resolve("Data.YAML"), "names:\n  0: cat\n");

        Path found = YoloTrainProvider.locateDataYaml(tempDir);

        assertThat(found).isEqualTo(tempDir.resolve("Data.YAML").toAbsolutePath());
    }

    @Test
    @DisplayName("目录不存在/不可读 → 抛出 IOException")
    void throwsWhenRootUnreadable() {
        Path ghost = tempDir.resolve("does-not-exist");

        assertThatThrownBy(() -> YoloTrainProvider.locateDataYaml(ghost))
                .isInstanceOf(IOException.class);
    }
}
