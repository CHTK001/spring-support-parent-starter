package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.IdUtils;
import com.chua.payment.support.dto.CouponDiscountPreview;
import com.chua.payment.support.dto.CouponIssueDTO;
import com.chua.payment.support.dto.CouponQueryDTO;
import com.chua.payment.support.entity.Coupon;
import com.chua.payment.support.entity.CouponTemplate;
import com.chua.payment.support.enums.CouponStatus;
import com.chua.payment.support.enums.CouponType;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.CouponMapper;
import com.chua.payment.support.mapper.CouponTemplateMapper;
import com.chua.payment.support.service.CouponService;
import com.chua.payment.support.vo.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户优惠券服务实现，负责发放、下单锁定、核销与退券。
 *
 * <p>核销并发控制：锁定与核销均使用条件更新（仅前序状态可流转），
 * 同一张券被并发下单时只有一个请求能锁定成功。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    /**
     * 模板启用状态
     */
    private static final String TEMPLATE_STATUS_ENABLED = "ENABLED";
    /**
     * 券号前缀
     */
    private static final String COUPON_NO_PREFIX = "CPN";

    /**
     * 用户优惠券映射器
     */
    private final CouponMapper couponMapper;
    /**
     * 优惠券模板映射器
     */
    private final CouponTemplateMapper couponTemplateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CouponVO> issue(CouponIssueDTO dto) {
        if (dto == null || dto.templateId() == null) {
            throw new PaymentException("优惠券模板不能为空");
        }
        if (dto.userIds() == null || dto.userIds().isEmpty()) {
            throw new PaymentException("发放用户不能为空");
        }
        CouponTemplate template = requireTemplate(dto.templateId());
        if (!TEMPLATE_STATUS_ENABLED.equals(template.getStatus())) {
            throw new PaymentException("优惠券模板已停用");
        }
        Set<Long> userIds = new LinkedHashSet<>(dto.userIds());
        checkPerUserLimit(template, userIds);
        int issueCount = userIds.size();
        if (template.getTotalCount() != null && template.getTotalCount() > 0) {
            reserveQuota(template.getId(), issueCount);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = resolveExpireAt(template, now);
        List<Coupon> issued = new ArrayList<>(issueCount);
        for (Long userId : userIds) {
            issued.add(insertCoupon(template, userId, expireAt));
        }
        return issued.stream().map(this::convertToVO).toList();
    }

    @Override
    public Page<CouponVO> page(CouponQueryDTO dto) {
        int pageNum = dto == null || dto.pageNum() == null || dto.pageNum() < 1 ? 1 : dto.pageNum();
        int pageSize = dto == null || dto.pageSize() == null || dto.pageSize() < 1 ? 10 : dto.pageSize();
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.merchantId() != null) {
                wrapper.eq(Coupon::getMerchantId, dto.merchantId());
            }
            if (dto.userId() != null) {
                wrapper.eq(Coupon::getUserId, dto.userId());
            }
            if (StringUtils.hasText(dto.status())) {
                wrapper.eq(Coupon::getStatus, dto.status());
            }
            if (StringUtils.hasText(dto.couponNo())) {
                wrapper.eq(Coupon::getCouponNo, dto.couponNo());
            }
        }
        wrapper.orderByDesc(Coupon::getCreatedAt);
        Page<Coupon> couponPage = couponMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<CouponVO> voPage = new Page<>(pageNum, pageSize, couponPage.getTotal());
        voPage.setRecords(couponPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public List<CouponVO> listByUser(Long merchantId, Long userId, String status) {
        if (merchantId == null || userId == null) {
            throw new PaymentException("商户与用户不能为空");
        }
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getMerchantId, merchantId)
                .eq(Coupon::getUserId, userId)
                .and(w -> w.isNull(Coupon::getDeleted).or().eq(Coupon::getDeleted, 0));
        if (StringUtils.hasText(status)) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreatedAt);
        return couponMapper.selectList(wrapper).stream().map(this::convertToVO).toList();
    }

    @Override
    public CouponDiscountPreview previewForOrder(Long merchantId, Long userId, String couponNo, BigDecimal orderAmount) {
        if (merchantId == null || userId == null) {
            throw new PaymentException("商户与用户不能为空");
        }
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentException("订单金额不能为负数");
        }
        Coupon coupon = requireByNo(couponNo);
        if (!merchantId.equals(coupon.getMerchantId()) || !userId.equals(coupon.getUserId())) {
            throw new PaymentException("优惠券不属于当前用户");
        }
        if (CouponStatus.FROZEN.name().equals(coupon.getStatus()) || CouponStatus.USED.name().equals(coupon.getStatus())) {
            throw new PaymentException("优惠券已被使用");
        }
        if (CouponStatus.EXPIRED.name().equals(coupon.getStatus())) {
            throw new PaymentException("优惠券已过期");
        }
        if (coupon.getExpireAt() != null && coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            markExpired(coupon.getId());
            throw new PaymentException("优惠券已过期");
        }
        if (coupon.getMinOrderAmount() != null && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new PaymentException("订单金额未达到优惠券使用门槛");
        }
        BigDecimal discount = coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount();
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return new CouponDiscountPreview(coupon.getId(), discount);
    }

    @Override
    public void reserveForOrder(String couponNo, Long orderId, String orderNo) {
        Coupon coupon = requireByNo(couponNo);
        int updated = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .set(Coupon::getStatus, CouponStatus.FROZEN.name())
                .set(Coupon::getOrderId, orderId)
                .set(Coupon::getOrderNo, orderNo)
                .eq(Coupon::getId, coupon.getId())
                .eq(Coupon::getStatus, CouponStatus.UNUSED.name()));
        if (updated == 0) {
            throw new PaymentException("优惠券已被使用，下单失败");
        }
    }

    @Override
    public boolean confirmUsed(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return false;
        }
        return couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .set(Coupon::getStatus, CouponStatus.USED.name())
                .set(Coupon::getUsedAt, LocalDateTime.now())
                .eq(Coupon::getOrderNo, orderNo)
                .eq(Coupon::getStatus, CouponStatus.FROZEN.name())) > 0;
    }

    @Override
    public boolean restoreByOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return false;
        }
        return couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .set(Coupon::getStatus, CouponStatus.UNUSED.name())
                .set(Coupon::getOrderId, null)
                .set(Coupon::getOrderNo, null)
                .set(Coupon::getUsedAt, null)
                .eq(Coupon::getOrderNo, orderNo)
                .in(Coupon::getStatus, CouponStatus.FROZEN.name(), CouponStatus.USED.name())) > 0;
    }

    /**
     * 校验每个用户的领取上限
     *
     * @param template 模板
     * @param userIds  发放用户集合
     */
    private void checkPerUserLimit(CouponTemplate template, Set<Long> userIds) {
        int perUserLimit = template.getPerUserLimit() == null || template.getPerUserLimit() < 1 ? 1 : template.getPerUserLimit();
        for (Long userId : userIds) {
            long count = couponMapper.selectCount(new LambdaQueryWrapper<Coupon>()
                    .eq(Coupon::getMerchantId, template.getMerchantId())
                    .eq(Coupon::getTemplateId, template.getId())
                    .eq(Coupon::getUserId, userId)
                    .and(w -> w.isNull(Coupon::getDeleted).or().eq(Coupon::getDeleted, 0)));
            if (count >= perUserLimit) {
                throw new PaymentException("用户已达到该券领取上限: " + userId);
            }
        }
    }

    /**
     * 原子预留发放名额，防止超发
     *
     * @param templateId 模板标识
     * @param issueCount 本次发放数量
     */
    private void reserveQuota(Long templateId, int issueCount) {
        int updated = couponTemplateMapper.update(null, new LambdaUpdateWrapper<CouponTemplate>()
                .setSql("pay_coupon_template_issued_count = pay_coupon_template_issued_count + " + issueCount)
                .eq(CouponTemplate::getId, templateId)
                .apply("(pay_coupon_template_total_count IS NULL OR pay_coupon_template_total_count = 0"
                        + " OR pay_coupon_template_issued_count + {0} <= pay_coupon_template_total_count)", issueCount));
        if (updated == 0) {
            throw new PaymentException("优惠券剩余发放数量不足");
        }
    }

    /**
     * 解析券的过期时间：领取后有效天数优先，其次固定有效期
     *
     * @param template 模板
     * @param now      当前时间
     * @return 过期时间
     */
    private LocalDateTime resolveExpireAt(CouponTemplate template, LocalDateTime now) {
        if (template.getValidDays() != null && template.getValidDays() > 0) {
            return now.plusDays(template.getValidDays());
        }
        if (template.getValidEndAt() != null) {
            return template.getValidEndAt();
        }
        throw new PaymentException("优惠券模板缺少有效期配置");
    }

    /**
     * 插入一张用户券
     *
     * @param template 模板
     * @param userId   用户ID
     * @param expireAt 过期时间
     * @return 已插入的券实体
     */
    private Coupon insertCoupon(CouponTemplate template, Long userId, LocalDateTime expireAt) {
        Coupon coupon = new Coupon();
        coupon.setMerchantId(template.getMerchantId());
        coupon.setUserId(userId);
        coupon.setTemplateId(template.getId());
        coupon.setCouponNo(IdUtils.createOrderNo(COUPON_NO_PREFIX));
        coupon.setName(template.getName());
        coupon.setType(template.getType());
        coupon.setAmount(template.getAmount());
        coupon.setMinOrderAmount(template.getMinOrderAmount());
        coupon.setStatus(CouponStatus.UNUSED.name());
        coupon.setExpireAt(expireAt);
        coupon.setDeleted(0);
        couponMapper.insert(coupon);
        return coupon;
    }

    /**
     * 惰性过期：将已过期但仍为未使用的券置为已过期
     *
     * @param couponId 券标识
     */
    private void markExpired(Long couponId) {
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .set(Coupon::getStatus, CouponStatus.EXPIRED.name())
                .eq(Coupon::getId, couponId)
                .eq(Coupon::getStatus, CouponStatus.UNUSED.name()));
    }

    /**
     * 按券号获取券，不存在时抛出异常
     *
     * @param couponNo 券号
     * @return 券实体
     */
    private Coupon requireByNo(String couponNo) {
        if (!StringUtils.hasText(couponNo)) {
            throw new PaymentException("券号不能为空");
        }
        Coupon coupon = couponMapper.selectOne(new LambdaQueryWrapper<Coupon>().eq(Coupon::getCouponNo, couponNo));
        if (coupon == null) {
            throw new PaymentException("优惠券不存在");
        }
        return coupon;
    }

    /**
     * 获取模板，不存在时抛出异常
     *
     * @param id 模板标识
     * @return 模板实体
     */
    private CouponTemplate requireTemplate(Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new PaymentException("优惠券模板不存在");
        }
        return template;
    }

    /**
     * 转换为视图对象
     *
     * @param coupon 券实体
     * @return 券视图对象
     */
    private CouponVO convertToVO(Coupon coupon) {
        CouponType couponType = CouponType.parse(coupon.getType());
        CouponStatus couponStatus = CouponStatus.parse(coupon.getStatus());
        return new CouponVO(
                coupon.getId(),
                coupon.getMerchantId(),
                coupon.getUserId(),
                coupon.getTemplateId(),
                coupon.getCouponNo(),
                coupon.getName(),
                coupon.getType(),
                couponType == null ? coupon.getType() : couponType.getDescription(),
                coupon.getAmount(),
                coupon.getMinOrderAmount(),
                coupon.getStatus(),
                couponStatus == null ? coupon.getStatus() : couponStatus.getDescription(),
                coupon.getOrderId(),
                coupon.getOrderNo(),
                coupon.getUsedAt(),
                coupon.getExpireAt(),
                coupon.getCreatedAt());
    }
}
