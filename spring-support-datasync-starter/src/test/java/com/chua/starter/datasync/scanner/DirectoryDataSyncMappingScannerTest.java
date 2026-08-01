package com.chua.starter.datasync.scanner;

import com.chua.starter.datasync.config.DirectoryConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.mapping.DefaultDataSyncMappingManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 目录扫描器单元测试。
 *
 * <p>覆盖多文件扫描、空目录、目录不存在等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DirectoryDataSyncMappingScannerTest {

    /**
     * 测试映射资源目录
     */
    private static final String MAPPING_DIR = "src/test/resources/test-mappings";

    @Test
    void scanMultiFiles() {
        DataSyncMappingManager manager = new DefaultDataSyncMappingManager();
        DirectoryDataSyncMappingScanner scanner = new DirectoryDataSyncMappingScanner(
                manager,
                new TestDirectoryConfig(MAPPING_DIR),
                List.of(new YamlConfigFileParser(), new JsonConfigFileParser(), new PropertiesConfigFileParser())
        );
        scanner.start();

        assertEquals(2, manager.getMappings().size());
        assertTrue(manager.getMappings().stream().anyMatch(m -> "user-sync".equals(m.mappingId())));
        assertTrue(manager.getMappings().stream().anyMatch(m -> "order-sync".equals(m.mappingId())));
        scanner.stop();
    }

    @Test
    void scanNonExistentDirectoryDoesNotThrow() {
        DataSyncMappingManager manager = new DefaultDataSyncMappingManager();
        DirectoryDataSyncMappingScanner scanner = new DirectoryDataSyncMappingScanner(
                manager,
                new TestDirectoryConfig("./no-such-dir-xyz"),
                List.of(new YamlConfigFileParser())
        );
        assertDoesNotThrow(scanner::start);
        assertEquals(0, manager.getMappings().size());
        scanner.stop();
    }

    /**
     * 测试用目录配置实现。
     */
    private static class TestDirectoryConfig implements DirectoryConfigDefinition {
        private final String path;

        TestDirectoryConfig(String path) {
            this.path = path;
        }

        @Override
        public String directoryPath() {
            return path;
        }

        @Override
        public String inputId() {
            return null;
        }

        @Override
        public String sourceId() {
            return null;
        }

        @Override
        public String outputId() {
            return null;
        }

        @Override
        public String sinkId() {
            return null;
        }

        @Override
        public List<DataSyncFieldMapping> mappings() {
            return List.of();
        }

        @Override
        public int batch() {
            return 100;
        }

        @Override
        public String cronType() {
            return null;
        }

        @Override
        public String cron() {
            return null;
        }

        @Override
        public Map<String, Object> params() {
            return Map.of();
        }
    }
}
