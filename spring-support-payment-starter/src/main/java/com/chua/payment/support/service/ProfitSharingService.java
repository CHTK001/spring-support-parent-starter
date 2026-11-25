package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.ProfitSharingOrderQueryDTO;
import com.chua.payment.support.dto.ProfitSharingReturnDTO;
import com.chua.payment.support.event.PaymentSuccessEvent;
import com.chua.payment.support.vo.ProfitSharingOrderVO;
import com.chua.payment.support.vo.ProfitSharingReturnVO;

import java.util.List;

/**
 * 分账服务接口，定义分账登记执行、查询与回退契约。
 *
 * <p>分账链路：支付成功事件（{@link #shareForOrder}）→ 按商户分账开关与接收方配置登记分账单 →
 * 调用渠道网关执行分账 → 回写分账状态；退分账走 {@link #returnSharing}。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public interface ProfitSharingService {

    /**
     * 支付成功后登记并执行分账
     *
     * <p>商户未开启分账或无启用接收方时静默跳过；同一订单重复事件幂等（返回已有分账单）。</p>
     *
     * @param event 支付成功事件
     * @return 分账单视图；未触发分账时返回 {@code null}
     */
    ProfitSharingOrderVO shareForOrder(PaymentSuccessEvent event);

    /**
     * 分页查询分账单
     *
     * @param dto 查询参数
     * @return 分账单分页结果
     */
    Page<ProfitSharingOrderVO> pageOrders(ProfitSharingOrderQueryDTO dto);

    /**
     * 按分账单号查询分账单详情
     *
     * @param sharingOrderNo 分账单号
     * @return 分账单视图
     */
    ProfitSharingOrderVO getBySharingOrderNo(String sharingOrderNo);

    /**
     * 对已成功的分账单发起回退
     *
     * @param dto 回退参数
     * @return 回退单视图
     */
    ProfitSharingReturnVO returnSharing(ProfitSharingReturnDTO dto);

    /**
     * 按分账单号查询回退记录列表
     *
     * @param sharingOrderNo 分账单号
     * @return 回退单视图列表
     */
    List<ProfitSharingReturnVO> listReturns(String sharingOrderNo);
}
