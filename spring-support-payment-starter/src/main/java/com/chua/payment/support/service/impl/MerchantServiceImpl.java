// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 商户服务实现，负责商户的增删改查、激活与停用操作。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    /**
     * 商户映射器
     */
    private final MerchantMapper merchantMapper;
    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付订单映射器
     */
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建商户
     *
     * @param dto dto
     * @return 创建出的商户视图对象
     */
    public MerchantVO createMerchant(MerchantDTO dto) {
        Merchant merchant = new Merchant();
        BeanUtils.copyProperties(dto, merchant);
        merchant.setMerchantNo(generateMerchantNo());
        merchant.setPaymentWalletEnabled(Boolean.TRUE.equals(dto.paymentWalletEnabled()));
        merchant.setPaymentAutoCloseEnabled(Boolean.TRUE.equals(dto.paymentAutoCloseEnabled()));
        merchant.setPaymentAutoCloseMinutes(dto.paymentAutoCloseMinutes() != null && dto.paymentAutoCloseMinutes() > 0 ? dto.paymentAutoCloseMinutes() : 30);
        merchant.setPaymentSplitTableEnabled(Boolean.TRUE.equals(dto.paymentSplitTableEnabled()));
        merchant.setPaymentProfitSharingEnabled(Boolean.TRUE.equals(dto.paymentProfitSharingEnabled()));
        merchant.setPaymentCouponEnabled(Boolean.TRUE.equals(dto.paymentCouponEnabled()));
        merchant.setStatus(MerchantStatus.PENDING.getCode());
        merchantMapper.insert(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新商户
     *
     * @param id 标识
     * @param dto dto
     * @return 更新后的商户视图对象
     */
    public MerchantVO updateMerchant(Long id, MerchantDTO dto) {
        Merchant merchant = requireMerchant(id);
        BeanUtils.copyProperties(dto, merchant);
        merchant.setPaymentWalletEnabled(Boolean.TRUE.equals(dto.paymentWalletEnabled()));
        merchant.setPaymentAutoCloseEnabled(Boolean.TRUE.equals(dto.paymentAutoCloseEnabled()));
        merchant.setPaymentAutoCloseMinutes(dto.paymentAutoCloseMinutes() != null && dto.paymentAutoCloseMinutes() > 0 ? dto.paymentAutoCloseMinutes() : merchant.getPaymentAutoCloseMinutes());
        merchant.setPaymentSplitTableEnabled(Boolean.TRUE.equals(dto.paymentSplitTableEnabled()));
        merchant.setPaymentProfitSharingEnabled(Boolean.TRUE.equals(dto.paymentProfitSharingEnabled()));
        merchant.setPaymentCouponEnabled(Boolean.TRUE.equals(dto.paymentCouponEnabled()));
        merchantMapper.updateById(merchant);
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除商户
     *
     * @param id 标识
     * @return 是否删除成功
     */
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
    /**
     * 获取商户
     * @param id 长整数
     * @return 返回值
    */
    public MerchantVO getMerchant(Long id) {
        return convertToVO(requireMerchant(id));
    }

    @Override
    /**
     * 分页查询商户列表
     * @param merchantName 字符串
     * @param page 整数
     * @param size 获取数量
     * @param status 整数
     * @return 返回值
    */
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
    /**
     * 激活商户
     * @param id 标识
     * @return 返回值
    */
    public boolean activateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.ACTIVE.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 停用商户
     * @param id 标识
     * @return 返回值
    */
    public boolean deactivateMerchant(Long id) {
        Merchant merchant = requireMerchant(id);
        merchant.setStatus(MerchantStatus.DISABLED.getCode());
        return merchantMapper.updateById(merchant) > 0;
    }

    /**
     * 获取商户，不存在时抛出异常
     * @param id 标识
     * @return 返回值
    */
    private Merchant requireMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        return merchant;
    }

    /**
     * 生成商户号
     * @return 返回值
    */
    private String generateMerchantNo() {
        return "M" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 转换为视图对象
     *
     * @param merchant 商户
     * @return 转换后的商户视图对象
     */
    private MerchantVO convertToVO(Merchant merchant) {
        return new MerchantVO(
                merchant.getId(),
                merchant.getMerchantNo(),
                merchant.getMerchantName(),
                merchant.getContactName(),
                merchant.getContactPhone(),
                merchant.getContactEmail(),
                merchant.getBusinessLicense(),
                merchant.getLegalPerson(),
                merchant.getDefaultNotifyUrl(),
                merchant.getDefaultReturnUrl(),
                merchant.getPaymentWalletEnabled(),
                merchant.getPaymentAutoCloseEnabled(),
                merchant.getPaymentAutoCloseMinutes(),
                merchant.getPaymentSplitTableEnabled(),
                merchant.getPaymentProfitSharingEnabled(),
                merchant.getPaymentCouponEnabled(),
                merchant.getRemark(),
                merchant.getStatus(),
                MerchantStatus.descriptionOf(merchant.getStatus()),
                Math.toIntExact(merchantChannelMapper.selectCount(
                        new LambdaQueryWrapper<MerchantChannel>().eq(MerchantChannel::getMerchantId, merchant.getId())
                )),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt());
    }
}