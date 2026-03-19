package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantMapper;
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
 *
 * @author CH
 * @since 2026-03-18
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantMapper merchantMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO createMerchant(MerchantDTO dto) {
        // 生成商户号
        String merchantNo = generateMerchantNo();
        
        // 创建商户实体
        Merchant merchant = new Merchant();
        BeanUtils.copyProperties(dto, merchant);
        merchant.setMerchantNo(merchantNo);
        merchant.setStatus(0); // 待审核
        
        // 保存到数据库
        merchantMapper.insert(merchant);
        
        // 转换为VO返回
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO updateMerchant(Long id, MerchantDTO dto) {
        // 查询商户
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        
        // 更新商户信息
        BeanUtils.copyProperties(dto, merchant);
        merchantMapper.updateById(merchant);
        
        // 转换为VO返回
        return convertToVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMerchant(Long id) {
        // 查询商户
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        
        // 删除商户
        return merchantMapper.deleteById(id) > 0;
    }

    @Override
    public MerchantVO getMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        return convertToVO(merchant);
    }

    @Override
    public Page<MerchantVO> listMerchants(int page, int size, String merchantName, Integer status) {
        // 构建查询条件
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(merchantName)) {
            wrapper.like(Merchant::getMerchantName, merchantName);
        }
        if (status != null) {
            wrapper.eq(Merchant::getStatus, status);
        }
        wrapper.orderByDesc(Merchant::getCreatedAt);
        
        // 分页查询
        Page<Merchant> merchantPage = merchantMapper.selectPage(new Page<>(page, size), wrapper);
        
        // 转换为VO
        Page<MerchantVO> voPage = new Page<>(page, size, merchantPage.getTotal());
        voPage.setRecords(merchantPage.getRecords().stream()
                .map(this::convertToVO)
                .toList());
        
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activateMerchant(Long id) {
        // 查询商户
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        
        // 激活商户
        merchant.setStatus(1); // 已激活
        return merchantMapper.updateById(merchant) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deactivateMerchant(Long id) {
        // 查询商户
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        
        // 停用商户
        merchant.setStatus(2); // 已停用
        return merchantMapper.updateById(merchant) > 0;
    }

    /**
     * 生成商户号
     *
     * @return 商户号
     */
    private String generateMerchantNo() {
        return "M" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 转换为VO
     *
     * @param merchant 商户实体
     * @return 商户VO
     */
    private MerchantVO convertToVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        BeanUtils.copyProperties(merchant, vo);
        return vo;
    }
}
