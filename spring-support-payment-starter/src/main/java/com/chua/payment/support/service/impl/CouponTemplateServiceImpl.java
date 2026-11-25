package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CouponTemplateDTO;
import com.chua.payment.support.dto.CouponTemplateQueryDTO;
import com.chua.payment.support.entity.CouponTemplate;
import com.chua.payment.support.enums.CouponType;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.CouponTemplateMapper;
import com.chua.payment.support.service.CouponTemplateService;
import com.chua.payment.support.vo.CouponTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 优惠券模板服务实现，负责模板的增删改查。
 *
 * @author CH
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class CouponTemplateServiceImpl implements CouponTemplateService {

    /**
     * 模板启用状态
     */
    private static final String STATUS_ENABLED = "ENABLED";
    /**
     * 模板停用状态
     */
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 优惠券模板映射器
     */
    private final CouponTemplateMapper couponTemplateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateVO create(CouponTemplateDTO dto) {
        validate(dto);
        CouponTemplate template = new CouponTemplate();
        BeanUtils.copyProperties(dto, template);
        template.setType(CouponType.parse(dto.type()).name());
        template.setMinOrderAmount(dto.minOrderAmount() == null ? BigDecimal.ZERO : dto.minOrderAmount());
        template.setTotalCount(dto.totalCount() == null || dto.totalCount() < 0 ? 0 : dto.totalCount());
        template.setIssuedCount(0);
        template.setPerUserLimit(dto.perUserLimit() == null || dto.perUserLimit() < 1 ? 1 : dto.perUserLimit());
        template.setStatus(StringUtils.hasText(dto.status()) ? dto.status() : STATUS_ENABLED);
        template.setDeleted(0);
        couponTemplateMapper.insert(template);
        return convertToVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplateVO update(Long id, CouponTemplateDTO dto) {
        validate(dto);
        CouponTemplate template = requireTemplate(id);
        BeanUtils.copyProperties(dto, template);
        template.setType(CouponType.parse(dto.type()).name());
        template.setMinOrderAmount(dto.minOrderAmount() == null ? BigDecimal.ZERO : dto.minOrderAmount());
        template.setTotalCount(dto.totalCount() == null || dto.totalCount() < 0 ? 0 : dto.totalCount());
        template.setPerUserLimit(dto.perUserLimit() == null || dto.perUserLimit() < 1 ? 1 : dto.perUserLimit());
        template.setStatus(StringUtils.hasText(dto.status()) ? dto.status() : template.getStatus());
        couponTemplateMapper.updateById(template);
        return convertToVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        CouponTemplate template = requireTemplate(id);
        if (template.getIssuedCount() != null && template.getIssuedCount() > 0) {
            throw new PaymentException("模板已发放优惠券，不能删除，可停用");
        }
        return couponTemplateMapper.deleteById(template.getId()) > 0;
    }

    @Override
    public CouponTemplateVO get(Long id) {
        return convertToVO(requireTemplate(id));
    }

    @Override
    public Page<CouponTemplateVO> page(CouponTemplateQueryDTO dto) {
        int pageNum = dto == null || dto.pageNum() == null || dto.pageNum() < 1 ? 1 : dto.pageNum();
        int pageSize = dto == null || dto.pageSize() == null || dto.pageSize() < 1 ? 10 : dto.pageSize();
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.merchantId() != null) {
                wrapper.eq(CouponTemplate::getMerchantId, dto.merchantId());
            }
            if (StringUtils.hasText(dto.name())) {
                wrapper.like(CouponTemplate::getName, dto.name());
            }
            if (StringUtils.hasText(dto.status())) {
                wrapper.eq(CouponTemplate::getStatus, dto.status());
            }
        }
        wrapper.orderByDesc(CouponTemplate::getCreatedAt);
        Page<CouponTemplate> templatePage = couponTemplateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<CouponTemplateVO> voPage = new Page<>(pageNum, pageSize, templatePage.getTotal());
        voPage.setRecords(templatePage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    /**
     * 校验模板参数
     *
     * @param dto 模板参数
     */
    private void validate(CouponTemplateDTO dto) {
        if (dto == null) {
            throw new PaymentException("模板参数不能为空");
        }
        if (dto.merchantId() == null) {
            throw new PaymentException("商户ID不能为空");
        }
        if (!StringUtils.hasText(dto.name())) {
            throw new PaymentException("模板名称不能为空");
        }
        if (CouponType.parse(dto.type()) == null) {
            throw new PaymentException("优惠券类型不合法");
        }
        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("优惠金额必须大于0");
        }
        if (dto.minOrderAmount() != null && dto.minOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentException("最低订单金额不能为负数");
        }
        boolean hasValidDays = dto.validDays() != null && dto.validDays() > 0;
        boolean hasFixedWindow = dto.validEndAt() != null;
        if (!hasValidDays && !hasFixedWindow) {
            throw new PaymentException("领取后有效天数与固定有效期至少配置一项");
        }
        if (dto.validStartAt() != null && dto.validEndAt() != null && dto.validStartAt().isAfter(dto.validEndAt())) {
            throw new PaymentException("有效期开始时间不能晚于结束时间");
        }
        if (StringUtils.hasText(dto.status()) && !STATUS_ENABLED.equals(dto.status()) && !STATUS_DISABLED.equals(dto.status())) {
            throw new PaymentException("模板状态不合法");
        }
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
     * @param template 模板实体
     * @return 模板视图对象
     */
    private CouponTemplateVO convertToVO(CouponTemplate template) {
        CouponType couponType = CouponType.parse(template.getType());
        return new CouponTemplateVO(
                template.getId(),
                template.getMerchantId(),
                template.getName(),
                template.getType(),
                couponType == null ? template.getType() : couponType.getDescription(),
                template.getAmount(),
                template.getMinOrderAmount(),
                template.getTotalCount(),
                template.getIssuedCount(),
                template.getPerUserLimit(),
                template.getValidDays(),
                template.getValidStartAt(),
                template.getValidEndAt(),
                template.getStatus(),
                template.getRemark(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
