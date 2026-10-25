package com.chua.starter.filesystem.support.server;

import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 文件服务器装配器 —— 配置属性到运行期 {@link FileStorageSetting} 的映射门禁。
 *
 * <p>{@code FileStorageServerLauncher} 现在统一委托 {@link FileStorageServerAssembler#buildSetting}，
 * 属性漏拷不会报错、只在重启后表现为「配置回到默认值」。这里把 URL 参数能力
 * （{@code fileSettingKey}）的装配钉住，避免后续重构再次漏字段。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileStorageServerAssemblerTest {

    /**
     * 属性中的 URL 参数能力实现必须原样进入运行期配置。
     */
    @Test
    @DisplayName("properties.fileSettingKey 必须装配到运行时 FileStorageSetting")
    void buildSettingCopiesFileSettingKey() {
        FileStorageServerProperties properties = new FileStorageServerProperties();
        assertEquals("jdk", FileStorageServerAssembler.buildSetting(properties).getFileSettingKey(),
                "未显式配置时应使用内置 jdk");

        properties.setFileSettingKey("native");
        assertEquals("native", FileStorageServerAssembler.buildSetting(properties).getFileSettingKey(),
                "配置值应原样装配，不能回落到默认值");
    }
}
