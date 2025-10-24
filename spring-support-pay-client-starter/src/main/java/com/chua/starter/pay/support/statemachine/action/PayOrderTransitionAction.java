package com.chua.starter.pay.support.statemachine.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderWaterMapper;
import com.chua.starter.pay.support.properties.PayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付订单状态转换统一Action处理器
 * <p>
 * 在状态机中统一处理：
 * <ul>
 *   <li>订单状态更新</li>
 *   <li>订单流水保存</li>
 *   <li>业务时间字段更新</li>
 *   <li>日志记录</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOrderTransitionAction implements Action<PayOrderStatus, PayOrderEvent> {

    private final PayMerchantOrderMapper payMerchantOrderMapper;
    private final PayMerchantOrderWaterMapper payMerchantOrderWaterMapper;
    private final PayProperties payProperties;

    /**
     * 消息头：订单编号
     */
    public static final String ORDER_CODE_HEADER = "orderCode";

    /**
     * 消息头：订单实体
     */
    public static final String ORDER_ENTITY_HEADER = "orderEntity";

    @Override
    public void execute(StateContext<PayOrderStatus, PayOrderEvent> context) {
        try {
            // 获取消息
            Message<PayOrderEvent> message = context.getMessage();
            if (message == null) {
                log.warn("状态机消息为空");
                return;
            }

            // 获取订单编号和订单实体
            String orderCode = message.getHeaders().get(ORDER_CODE_HEADER, String.class);
            PayMerchantOrder orderEntity = message.getHeaders().get(ORDER_ENTITY_HEADER, PayMerchantOrder.class);

            if (orderCode == null) {
                log.error("订单编号为空，无法执行状态转换");
                return;
            }

            // 获取源状态和目标状态
            PayOrderStatus sourceStatus = context.getSource() != null ? context.getSource().getId() : null;
            PayOrderStatus targetStatus = context.getTarget() != null ? context.getTarget().getId() : null;
            PayOrderEvent event = context.getEvent();

            log.info("订单状态转换开始, 订单编号: {}, 事件: {}, 源状态: {}, 目标状态: {}",
                    orderCode,
                    event,
                    sourceStatus != null ? sourceStatus.getName() : "null",
                    targetStatus != null ? targetStatus.getName() : "null");

            // 1. 查询订单
            PayMerchantOrder order = payMerchantOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayMerchantOrder>()
                            .eq(PayMerchantOrder::getPayMerchantOrderCode, orderCode)
            );

            if (order == null) {
                log.error("订单不存在, 订单编号: {}", orderCode);
                return;
            }

            // 2. 更新订单状态和相关字段
            updateOrderStatus(order, targetStatus, event, orderEntity);

            // 3. 保存订单流水
            saveOrderWater(orderCode, targetStatus);

            // 4. 记录日志
            if (payProperties.getEnableStateMachineLog()) {
                log.info("订单状态转换成功, 订单编号: {}, 事件: {}, 新状态: {}",
                        orderCode, event, targetStatus != null ? targetStatus.getName() : "null");
            }

        } catch (Exception e) {
            log.error("订单状态转换失败", e);
            throw new RuntimeException("订单状态转换失败", e);
        }
    }

    /**
     * 更新订单状态和相关字段
     *
     * @param order        订单实体
     * @param targetStatus 目标状态
     * @param event        触发事件
     * @param orderEntity  消息中携带的订单实体（可能为null）
     */
    private void updateOrderStatus(PayMerchantOrder order, PayOrderStatus targetStatus, 
                                   PayOrderEvent event, PayMerchantOrder orderEntity) {
        // 更新状态
        order.setPayMerchantOrderStatus(targetStatus);

        // 根据事件更新相关字段
        LocalDateTime now = LocalDateTime.now();

        switch (event) {
            case PAY_SUCCESS:
                // 支付成功：更新支付时间
                order.setPayMerchantOrderPayTime(now);
                if (orderEntity != null && orderEntity.getPayMerchantOrderTransactionId() != null) {
                    order.setPayMerchantOrderTransactionId(orderEntity.getPayMerchantOrderTransactionId());
                }
                break;

            case REFUND_SUCCESS:
            case REFUND_PART_SUCCESS:
                // 退款成功：更新退款相关字段
                if (orderEntity != null) {
                    if (orderEntity.getPayMerchantOrderRefundCode() != null) {
                        order.setPayMerchantOrderRefundCode(orderEntity.getPayMerchantOrderRefundCode());
                    }
                    if (orderEntity.getPayMerchantOrderRefundReason() != null) {
                        order.setPayMerchantOrderRefundReason(orderEntity.getPayMerchantOrderRefundReason());
                    }
                    if (orderEntity.getPayMerchantOrderRefundUserReceivedAccount() != null) {
                        order.setPayMerchantOrderRefundUserReceivedAccount(
                                orderEntity.getPayMerchantOrderRefundUserReceivedAccount());
                    }
                }
                order.setPayMerchantOrderRefundCreateTime(now);
                break;

            case TIMEOUT:
            case CANCEL:
            case CLOSE:
                // 订单结束：更新完成时间
                order.setPayMerchantOrderFinishedTime(now);
                break;

            case CREATE_FAILED:
                // 创建失败：记录失败原因
                if (orderEntity != null && orderEntity.getPayMerchantOrderFailureReason() != null) {
                    order.setPayMerchantOrderFailureReason(orderEntity.getPayMerchantOrderFailureReason());
                }
                break;

            default:
                // 其他事件不需要特殊处理
                break;
        }

        // 更新数据库
        payMerchantOrderMapper.updateById(order);

        log.debug("订单状态已更新, 订单编号: {}, 新状态: {}",
                order.getPayMerchantOrderCode(), targetStatus.getName());
    }

    /**
     * 保存订单流水
     *
     * @param orderCode 订单编号
     * @param status    订单状态
     */
    private void saveOrderWater(String orderCode, PayOrderStatus status) {
        try {
            PayMerchantOrderWater water = new PayMerchantOrderWater();
            water.setPayMerchantOrderWaterCode(generateWaterCode());
            water.setPayMerchantOrderCode(orderCode);
            water.setPayMerchantOrderStatus(status);

            payMerchantOrderWaterMapper.insert(water);

            log.debug("订单流水已保存, 订单编号: {}, 流水编号: {}, 状态: {}",
                    orderCode, water.getPayMerchantOrderWaterCode(), status.getName());

        } catch (Exception e) {
            log.error("保存订单流水失败, 订单编号: {}, 状态: {}", orderCode, status.getName(), e);
            // 流水保存失败不影响主流程
        }
    }

    /**
     * 生成流水编号
     *
     * @return 流水编号
     */
    private String generateWaterCode() {
        return "WATER-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

