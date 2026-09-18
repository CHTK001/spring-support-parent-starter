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

    /**
    * 列表配置。
    * @return 列表配置的结果
    */
    List<OrderPartitionConfigVO> listConfigs();

    /**
    * 更新配置。
    * @param businessType 业务类型
    * @param dto dto
    * @return 更新配置的结果
    */
    OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto);

    /**
    * preview。
    * @param businessType 业务类型
    * @return preview的结果
    */
    OrderPartitionPreviewVO preview(String businessType);

    /**
    * 执行auto创建。
    * @param businessType 业务类型
    */
    void executeAutoCreate(String businessType);

    /**
    * 执行automigrate。
    * @param businessType 业务类型
    */
    void executeAutoMigrate(String businessType);

    /**
    * 获取配置实体。
    * @param businessType 业务类型
    * @return 获取配置实体的结果
    */
    OrderPartitionConfig getConfigEntity(String businessType);
}

