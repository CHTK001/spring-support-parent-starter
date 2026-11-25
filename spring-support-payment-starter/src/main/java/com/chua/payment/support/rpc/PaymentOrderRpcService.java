package com.chua.payment.support.rpc;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.PaymentOrderPageRequest;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付订单 RPC 门面（服务/客户端双模式的服务端契约）。
 *
 * <p>把 {@code PaymentOrderService} 中适合跨应用消费的核心能力收敛为稳定契约，
 * 经 {@code spring-support-rpc-starter}（Dubbo）暴露；入参 / 出参全部是
 * {@code Serializable} 的 DTO / VO，与内部实现解耦。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 消费者侧（其它服务端 / SDK）
 * public class CheckoutService {
 *
 *     @RpcResource(version = "1.0.0", degrade = "springbean")
 *     private PaymentOrderRpcService paymentOrderRpcService;   // RPC 代理；Dubbo 不可用时降级到本地 Bean
 *
 *     public OrderVO place(OrderCreateDTO dto) {
 *         return paymentOrderRpcService.createOrder(dto);
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 * @see com.chua.common.support.network.rpc.RpcService
 * @see com.chua.common.support.network.rpc.RpcResource
 */
public interface PaymentOrderRpcService {

    /**
     * 创建支付订单。
     *
     * @param dto 订单创建参数
     * @return 创建后的订单
     */
    OrderVO createOrder(OrderCreateDTO dto);

    /**
     * 按主键查询订单。
     *
     * @param id 订单标识
     * @return 订单详情
     */
    OrderVO getOrder(Long id);

    /**
     * 分页查询订单。
     *
     * @param request 分页与筛选参数
     * @return 订单分页结果
     */
    Page<OrderVO> listOrders(PaymentOrderPageRequest request);

    /**
     * 对指定订单发起渠道支付。
     *
     * @param id  订单标识
     * @param dto 支付发起参数
     * @return 渠道支付结果（支付链接 / SDK 调起参数等）
     */
    PaymentResult payOrder(Long id, OrderPayDTO dto);

    /**
     * 标记订单支付成功（状态机推进 + 流水落库）。
     *
     * @param id                订单标识
     * @param paidAmount        实付金额
     * @param thirdPartyOrderNo 第三方（渠道）订单号
     * @param operator          操作人
     * @return 是否处理成功
     */
    boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator);

    /**
     * 标记订单支付失败。
     *
     * @param id       订单标识
     * @param reason   失败原因
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean payFail(Long id, String reason, String operator);

    /**
     * 取消订单（未支付 / 支付中可直接取消；已支付订单按渠道能力决定是否可退）。
     *
     * @param id       订单标识
     * @param operator 操作人
     * @param reason   取消原因
     * @return 是否取消成功
     */
    boolean cancelOrder(Long id, String operator, String reason);

    /**
     * 对已支付订单发起退款。
     *
     * @param id  订单标识
     * @param dto 退款参数（金额 / 原因 / 操作人）
     * @return 是否受理成功
     */
    boolean refundOrder(Long id, RefundApplyDTO dto);

    /**
     * 标记退款成功。
     *
     * @param id           订单标识
     * @param refundAmount 退款金额
     * @param operator     操作人
     * @return 是否处理成功
     */
    boolean refundSuccess(Long id, BigDecimal refundAmount, String operator);

    /**
     * 标记退款失败。
     *
     * @param id       订单标识
     * @param reason   失败原因
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean refundFail(Long id, String reason, String operator);

    /**
     * 查询订单状态流转日志。
     *
     * @param id 订单标识
     * @return 状态日志列表（按时间升序）
     */
    List<OrderStateLogVO> listOrderLogs(Long id);

    /**
     * 生成全局唯一支付订单号（前缀 ORD）。
     *
     * @return 订单号
     */
    String generateOrderNo();
}
