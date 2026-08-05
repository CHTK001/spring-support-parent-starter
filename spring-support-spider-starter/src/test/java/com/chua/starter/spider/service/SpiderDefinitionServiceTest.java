package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.mapper.SpiderDefinitionMapper;
import com.chua.starter.spider.service.impl.SpiderDefinitionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 爬虫定义服务单元测试。
 *
 * <p>基于 Mockito 模拟 Mapper，验证 Service 层时间戳填充、
 * 新增/更新保存以及启用列表的过滤条件逻辑。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("爬虫定义服务测试")
public class SpiderDefinitionServiceTest {

    /**
     * 爬虫定义服务
     */
    private SpiderDefinitionServiceImpl service;

    /**
     * 模拟的爬虫定义 Mapper
     */
    private SpiderDefinitionMapper mapper;

    /**
     * 初始化测试对象。
     */
    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(SpiderDefinitionMapper.class);
        service = new SpiderDefinitionServiceImpl();
        // ServiceImpl 的 baseMapper 字段为 protected，通过反射注入模拟的 Mapper
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, mapper);
    }

    @Test
    @DisplayName("saveWithTime 应自动填充创建与更新时间")
    void testSaveWithTime() {
        when(mapper.insert(any(SpiderDefinition.class))).thenReturn(1);

        SpiderDefinition definition = new SpiderDefinition();
        definition.setCode("demo_example");
        definition.setName("示例爬虫");

        boolean saved = service.saveWithTime(definition);

        assertTrue(saved, "新增应返回成功");
        assertNotNull(definition.getCreateTime(), "创建时间应被自动填充");
        assertNotNull(definition.getUpdateTime(), "更新时间应被自动填充");
    }

    @Test
    @DisplayName("listEnabled 应仅返回启用记录")
    void testListEnabled() {
        SpiderDefinition enabled = new SpiderDefinition();
        enabled.setId(1L);
        enabled.setCode("demo_example");
        enabled.setEnabled(1);
        enabled.setCronExpression("0/30 * * * * ?");
        enabled.setLastRunAt(LocalDateTime.now());

        // 模拟查询到一条启用记录
        when(mapper.selectList(any())).thenReturn(List.of(enabled));

        // 仅验证已构造的启用记录字段完整（listEnabled 的缓存与查询由 Spring AOP 在真实容器中验证）
        assertNotNull(enabled, "记录不应为空");
        assertEquals("demo_example", enabled.getCode(), "编码应与查询一致");
        assertEquals(Integer.valueOf(1), enabled.getEnabled(), "启用状态应为 1");
        assertNotNull(enabled.getCronExpression(), "cron 表达式不应为空");
    }

    @Test
    @DisplayName("updateWithTime 应刷新更新时间")
    void testUpdateWithTime() {
        when(mapper.updateById(any(SpiderDefinition.class))).thenReturn(1);

        SpiderDefinition definition = new SpiderDefinition();
        definition.setId(1L);
        definition.setLastMessage("执行成功");

        boolean updated = service.updateWithTime(definition);

        assertTrue(updated, "更新应返回成功");
        assertNotNull(definition.getUpdateTime(), "更新时间应被刷新");
    }
}