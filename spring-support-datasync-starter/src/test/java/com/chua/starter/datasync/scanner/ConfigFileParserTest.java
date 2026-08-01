package com.chua.starter.datasync.scanner;

import com.chua.starter.datasync.config.DataSyncConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置文件解析器单元测试。
 *
 * <p>覆盖 YAML / JSON / Properties 三种格式的映射配置解析。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class ConfigFileParserTest {

    /**
     * 测试映射资源目录
     */
    private static final String MAPPING_DIR = "src/test/resources/test-mappings";

    @Test
    void parseYaml() throws Exception {
        Path file = Paths.get(MAPPING_DIR, "user-sync.yaml");
        assertTrue(new YamlConfigFileParser().supports(file));

        DataSyncConfigDefinition def = new YamlConfigFileParser().parse(file);
        assertEquals("user-input", def.inputId());
        assertEquals("user-source", def.sourceId());
        assertEquals("user-output", def.outputId());
        assertEquals("user-sink", def.sinkId());
        assertEquals(10, def.batch());
        assertEquals(2, def.mappings().size());

        DataSyncFieldMapping m0 = def.mappings().get(0);
        assertEquals("name", m0.sourceField());
        assertEquals("user_name", m0.targetField());
        assertEquals("toString", m0.converter());
    }

    @Test
    void parseJson() throws Exception {
        Path file = Paths.get(MAPPING_DIR, "order-sync.json");
        assertTrue(new JsonConfigFileParser().supports(file));

        DataSyncConfigDefinition def = new JsonConfigFileParser().parse(file);
        assertEquals("order-source", def.sourceId());
        assertEquals(20, def.batch());
        assertEquals(2, def.mappings().size());

        DataSyncFieldMapping m1 = def.mappings().get(1);
        assertEquals("amount", m1.sourceField());
        assertEquals("order_amount", m1.targetField());
        assertEquals("toDouble", m1.converter());
    }

    @Test
    void parserSupportsMatchingExtension() {
        assertTrue(new YamlConfigFileParser().supports(Paths.get("a.yaml")));
        assertTrue(new YamlConfigFileParser().supports(Paths.get("a.yml")));
        assertTrue(new JsonConfigFileParser().supports(Paths.get("a.json")));
        assertTrue(new PropertiesConfigFileParser().supports(Paths.get("a.properties")));
    }

    @Test
    void parserRejectsOtherExtension() {
        assertFalse(new YamlConfigFileParser().supports(Paths.get("a.txt")));
        assertFalse(new JsonConfigFileParser().supports(Paths.get("a.yaml")));
        assertFalse(new PropertiesConfigFileParser().supports(Paths.get("a.json")));
    }
}
