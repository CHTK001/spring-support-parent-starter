package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.MerchantStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.MerchantService;
import com.chua.payment.support.vo.MerchantVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 商户服务实现类
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道mapper */
    private final MerchantChannelMapper merchantChannelMapper;
    /** Payment排序mapper */
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 创建Merchant */
    public MerchantVO createMerchant(MerchantDTO dto) {
        Merchant merchant = new Merchant();
        BeanUtils.copyProperties(dto, merchant);
        merchant.setMerchantNo(generateMerchantNo());
        merchant.setPaymentWalletEnabled(Boolean.TRUE.equals(dto.getPaymentWalletEnabled()));
        merchant.setPaymentAutoCloseEnabled(Boolean.TRUE.equals(dto.getPaymentAutoCloseEnabled()));
        merchant.setPaymentAutoCloseMinutes(dto.getPaymentAutoCloseMinutes() != null && dto.getPaymentAutoCloseMinutes() > 0 ? dto.getPaymentAutoCloseMinutes() : 30);
        merchant.setPaymentSplitTableEnabled(Boolean.TRUE.equals(dto.getPaymentSplitTableEnabled()));
        merchant.setPaymentProfitSharingEnabled(Boolean.TRUE.equals(dto.getPaymentProfitSharingEnabled()));
        merchant.setPaymentCouponEnabled(Boolean.TRUE.equals(dto.getPaymentCouponEnabled()));
        merchant.setStatus(MerchantStatus.PENDING.getCode());
        merchantMapper.insert(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 更新Merchant */
    public MerchantVO updateMerchant(Long id, MerchantDTO dto) {
        Merchant merchant = requireMerchant(id);
        BeanUtils.copyProperties(dto, merchant);
        merchant.setPaymentWalletEnabled(Boolean.TRUE.equals(dto.getPaymentWalletEnabled()));
        merchant.setPaymentAutoCloseEnabled(Boolean.TRUE.equals(dto.getPaymentAutoCloseEnabled()));
        merchant.setPaymentAutoCloseMinutes(dto.getPaymentAutoCloseMinutes() != null && dto.getPaymentAutoCloseMinutes() > 0 ? dto.getPaymentAutoCloseMinutes() : merchant.getPaymentAutoCloseMinutes());
        merchant.setPaymentSplitTableEnabled(Boolean.TRUE.equals(dto.getPaymentSplitTableEnabled()));
        merchant.setPaymentProfitSharingEnabled(Boolean.TRUE.equals(dto.getPaymentProfitSharingEnabled()));
        merchant.setPaymentCouponEnabled(Boolean.TRUE.equals(dto.getPaymentCouponEnabled()));
        merchantMapper.updateById(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 删除Merchant */
    public boolean deleteMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        long channelCount = merchantChannelMapper.selectCount(new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getMerchantId, id));
        if (channelCount > 0) {
            throw new PaymentException("商户下仍存在支付方式配置，不能删除");
        }
        long orderCount = paymentOrderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getMerchantId, id)
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0)));
        if (orderCount > 0) {
            throw new PaymentException("商户下仍存在订单，不能删除");
        }
        return merchantMapper.deleteById(merchant.getId()) > 0;
    }

    @Override
    /** 获取Merchant */
    public MerchantVO getMerchant(Long id) {
        return convertToVO(requireMerchant(id));
    }

    @Override
    /** ListMerchants */
    public Page<MerchantVO> listMerchants(int page, int size, String merchantName, Integer status) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(merchantName)) {
            wrapper.like(Merchant::getMerchantName, merchantName);
        }
        if (status != null) {
            wrapper.eq(Merchant::getStatus, status);
        }
        wrapper.orderByDesc(Merchant::getCreatedAt);

        Page<Merchant> merchantPage = merchantMapper.selectPage(new Page<>(page, size), wrapper);
        Page<MerchantVO> voPage = new Page<>(page, size, merchantPage.getTotal());
        voPage.setRecords(merchantPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** ActivateMerchant */
    public boolean activateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.ACTIVE.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** DeactivateMerchant */
    public boolean deactivateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.DISABLED.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    /** RequireMerchant */
    private Merchant requireMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        return merchant;
    }

    /** GenerateMerchantNo */
    private String generateMerchantNo() {
        return "M" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /** 转换ToVO */
    private MerchantVO convertToVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        BeanUtils.copyProperties(merchant, vo);
        vo.setStatusDesc(MerchantStatus.descriptionOf(merchant.getStatus()));
        vo.setChannelCount(Math.toIntExact(merchantChannelMapper.selectCount(
                new LambdaQueryWrapper<MerchantChannel>().eq(MerchantChannel::getMerchantId, merchant.getId())
        )));
        return vo;
    }
}

