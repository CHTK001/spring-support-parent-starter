package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CouponDiscountPreview;
import com.chua.payment.support.dto.CouponIssueDTO;
import com.chua.payment.support.dto.CouponQueryDTO;
import com.chua.payment.support.vo.CouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户优惠券服务接口，定义发放、查询与核销状态流转契约。
 *
 * <p>核销链路：下单预览校验（{@link #previewForOrder}）→ 订单落库后锁定（{@link #reserveForOrder}）→
 * 支付成功核销（{@link #confirmUsed}）→ 退款成功退券（{@link #restoreByOrder}）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public interface CouponService {

    /**
     * 按模板向用户列表发放优惠券（每用户一张）
     *
     * @param dto 发放参数
     * @return 发放成功的券视图列表
     */
    List<CouponVO> issue(CouponIssueDTO dto);

    /**
     * 分页查询用户优惠券
     *
     * @param dto 查询参数
     * @return 优惠券分页结果
     */
    Page<CouponVO> page(CouponQueryDTO dto);

    /**
     * 查询用户名下优惠券列表
     *
     * @param merchantId 商户ID
     * @param userId     用户ID
     * @param status     状态过滤，空为全部
     * @return 优惠券视图列表
     */
    List<CouponVO> listByUser(Long merchantId, Long userId, String status);

    /**
     * 下单前预览校验：校验券归属、状态、有效期与订单门槛，返回可抵扣金额
     *
     * <p>只做校验与金额计算，不锁定券；商户券开关由调用方校验。
     * 过期券会被惰性置为 EXPIRED 并拒绝使用。</p>
     *
     * @param merchantId  商户ID
     * @param userId      用户ID
     * @param couponNo    券号
     * @param orderAmount 订单金额（优惠前）
     * @return 抵扣预览结果
     */
    CouponDiscountPreview previewForOrder(Long merchantId, Long userId, String couponNo, BigDecimal orderAmount);

    /**
     * 订单落库后锁定券：以条件更新（仅 UNUSED 可锁）防并发重复核销，并绑定订单信息
     *
     * @param couponNo 券号
     * @param orderId  订单ID
     * @param orderNo  订单号
     */
    void reserveForOrder(String couponNo, Long orderId, String orderNo);

    /**
     * 支付成功核销：将订单关联的锁定券置为已核销
     *
     * @param orderNo 订单号
     * @return 是否发生核销
     */
    boolean confirmUsed(String orderNo);

    /**
     * 退券：将订单关联的券恢复为未使用（退款成功）
     *
     * @param orderNo 订单号
     * @return 是否发生恢复
     */
    boolean restoreByOrder(String orderNo);
}
