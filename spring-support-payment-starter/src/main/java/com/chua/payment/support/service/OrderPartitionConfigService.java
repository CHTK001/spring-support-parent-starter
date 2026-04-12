package com.chua.payment.support.service;

import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;

import java.util.List;

public interface OrderPartitionConfigService {

    List<OrderPartitionConfigVO> listConfigs();

    OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto);

    OrderPartitionPreviewVO preview(String businessType);

    void executeAutoCreate(String businessType);

    void executeAutoMigrate(String businessType);

    OrderPartitionConfig getConfigEntity(String businessType);
}
