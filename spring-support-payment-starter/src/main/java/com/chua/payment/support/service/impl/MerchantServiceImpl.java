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
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantMapper merchantMapper;
    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO createMerchant(MerchantDTO dto) {
        Merchant merchant = new Merchant();
        BeanUtils.copyProperties(dto, merchant);
        merchant.setMerchantNo(generateMerchantNo());
        merchant.setWalletEnabled(Boolean.TRUE.equals(dto.getWalletEnabled()));
        merchant.setCompositeEnabled(Boolean.TRUE.equals(dto.getCompositeEnabled()));
        merchant.setAutoCloseEnabled(Boolean.TRUE.equals(dto.getAutoCloseEnabled()));
        merchant.setAutoCloseMinutes(dto.getAutoCloseMinutes() != null && dto.getAutoCloseMinutes() > 0 ? dto.getAutoCloseMinutes() : 30);
        merchant.setStatus(MerchantStatus.PENDING.getCode());
        merchantMapper.insert(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO updateMerchant(Long id, MerchantDTO dto) {
        Merchant merchant = requireMerchant(id);
        BeanUtils.copyProperties(dto, merchant);
        merchant.setWalletEnabled(Boolean.TRUE.equals(dto.getWalletEnabled()));
        merchant.setCompositeEnabled(Boolean.TRUE.equals(dto.getCompositeEnabled()));
        merchant.setAutoCloseEnabled(Boolean.TRUE.equals(dto.getAutoCloseEnabled()));
        merchant.setAutoCloseMinutes(dto.getAutoCloseMinutes() != null && dto.getAutoCloseMinutes() > 0 ? dto.getAutoCloseMinutes() : merchant.getAutoCloseMinutes());
        merchantMapper.updateById(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    public MerchantVO getMerchant(Long id) {
        return convertToVO(requireMerchant(id));
    }

    @Override
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
    public boolean activateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.ACTIVE.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deactivateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.DISABLED.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    private Merchant requireMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        return merchant;
    }

    private String generateMerchantNo() {
        return "M" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

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
