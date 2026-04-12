package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.mapper.OrderPartitionConfigMapper;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPartitionConfigServiceImplTest {

    private final OrderPartitionConfigMapper mapper = mock(OrderPartitionConfigMapper.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DataSource dataSource = mock(DataSource.class);
    private OrderPartitionConfigServiceImpl service;
    private OrderPartitionConfig config;

    @BeforeEach
    void setUp() {
        service = new OrderPartitionConfigServiceImpl(mapper, jdbcTemplate, dataSource);
        config = new OrderPartitionConfig();
        config.setBusinessType("ORDER");
        config.setSourceTable("payment_order");
        config.setPartitionPrefix("payment_order_part");
        config.setPartitionGranularity("MONTH");
        config.setRetentionDays(365);
        config.setCreateAheadDays(7);
        config.setMigrateBeforeDays(90);
        config.setAutoCreateEnabled(true);
        config.setAutoMigrateEnabled(false);
        config.setKeepSourceData(true);
        config.setCreateTaskKey("payment.order.partition.create");
        config.setMigrateTaskKey("payment.order.partition.migrate");
        when(mapper.selectOne(any())).thenReturn(config);
    }

    @Test
    void shouldPreviewNextPartitionTable() {
        OrderPartitionPreviewVO preview = service.preview("ORDER");
        assertEquals("payment_order", preview.getSourceTable());
        assertEquals("payment.order.partition.create", preview.getCreateTaskKey());
        assertEquals("payment.order.partition.migrate", preview.getMigrateTaskKey());
    }

    @Test
    void shouldUpdateConfig() {
        OrderPartitionConfigDTO dto = new OrderPartitionConfigDTO();
        dto.setPartitionPrefix("payment_order_archive");
        dto.setPartitionGranularity("DAY");
        dto.setRetentionDays(180);
        dto.setCreateAheadDays(2);
        dto.setMigrateBeforeDays(60);
        dto.setAutoCreateEnabled(true);
        dto.setAutoMigrateEnabled(true);
        dto.setKeepSourceData(false);

        service.updateConfig("ORDER", dto);

        assertEquals("payment_order_archive", config.getPartitionPrefix());
        assertEquals("DAY", config.getPartitionGranularity());
        assertEquals(180, config.getRetentionDays());
        assertEquals(2, config.getCreateAheadDays());
        assertEquals(60, config.getMigrateBeforeDays());
        verify(mapper).updateById(config);
    }
}
