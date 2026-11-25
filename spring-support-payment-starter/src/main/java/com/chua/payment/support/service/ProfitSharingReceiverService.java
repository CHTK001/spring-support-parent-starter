package com.chua.payment.support.service;

import com.chua.payment.support.dto.ProfitSharingReceiverDTO;
import com.chua.payment.support.vo.ProfitSharingReceiverVO;

import java.util.List;

/**
 * 分账接收方服务接口，定义接收方的维护与查询契约。
 *
 * @author CH
 * @since 4.0.0
 */
public interface ProfitSharingReceiverService {

    /**
     * 创建分账接收方（同商户同渠道同账号判重）
     *
     * @param dto 接收方参数
     * @return 创建后的接收方视图
     */
    ProfitSharingReceiverVO create(ProfitSharingReceiverDTO dto);

    /**
     * 更新分账接收方（账号与渠道类型不可变更）
     *
     * @param id  接收方ID
     * @param dto 接收方参数
     * @return 更新后的接收方视图
     */
    ProfitSharingReceiverVO update(Long id, ProfitSharingReceiverDTO dto);

    /**
     * 删除分账接收方（逻辑删除）
     *
     * @param id 接收方ID
     * @return 是否删除成功
     */
    boolean delete(Long id);

    /**
     * 查询分账接收方详情
     *
     * @param id 接收方ID
     * @return 接收方视图
     */
    ProfitSharingReceiverVO get(Long id);

    /**
     * 按商户与渠道查询启用的分账接收方列表
     *
     * @param merchantId  商户ID
     * @param channelType 渠道类型: WECHAT/ALIPAY，空为全部
     * @param status      状态: ENABLED/DISABLED，空为全部
     * @return 接收方视图列表
     */
    List<ProfitSharingReceiverVO> listByMerchant(Long merchantId, String channelType, String status);

    /**
     * 启用/停用分账接收方
     *
     * @param id     接收方ID
     * @param status 目标状态: ENABLED/DISABLED
     * @return 更新后的接收方视图
     */
    ProfitSharingReceiverVO updateStatus(Long id, String status);
}
