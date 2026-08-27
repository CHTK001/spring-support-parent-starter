package com.chua.payment.support.service;

import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;

import java.util.List;

/**
 * 订单分表配置服务接口，定义分片策略的管理与执行契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface OrderPartitionConfigService {

    List<OrderPartitionConfigVO> listConfigs();

    OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto);

    OrderPartitionPreviewVO preview(String businessType);

    void executeAutoCreate(String businessType);

    void executeAutoMigrate(String businessType);

    OrderPartitionConfig getConfigEntity(String businessType);
}

