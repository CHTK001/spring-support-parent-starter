package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.service.MerchantChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 商户渠道服务实现
 *
 * @author CH
 * @since 2026-03-18
 */
@Service
@RequiredArgsConstructor
public class MerchantChannelServiceImpl implements MerchantChannelService {

    private final MerchantChannelMapper channelMapper;
    private static final String AES_KEY = "PaymentSystem16"; // 16字节密钥

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannel createChannel(ChannelConfigDTO dto) {
        MerchantChannel channel = new MerchantChannel();
        BeanUtils.copyProperties(dto, channel);
        
        // 加密敏感信息
        if (dto.getApiKey() != null) {
            channel.setApiKey(encryptApiKey(dto.getApiKey()));
        }
        if (dto.getPrivateKey() != null) {
            channel.setPrivateKey(encryptApiKey(dto.getPrivateKey()));
        }
        
        channel.setStatus(1); // 默认启用
        channelMapper.insert(channel);
        return channel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannel updateChannel(Long id, ChannelConfigDTO dto) {
        MerchantChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new PaymentException("渠道配置不存在");
        }
        
        BeanUtils.copyProperties(dto, channel);
        
        // 加密敏感信息
        if (dto.getApiKey() != null) {
            channel.setApiKey(encryptApiKey(dto.getApiKey()));
        }
        if (dto.getPrivateKey() != null) {
            channel.setPrivateKey(encryptApiKey(dto.getPrivateKey()));
        }
        
        channelMapper.updateById(channel);
        return channel;
    }

    @Override
    public MerchantChannel getChannel(Long id) {
        return channelMapper.selectById(id);
    }

    @Override
    public List<MerchantChannel> listChannels(Long merchantId) {
        LambdaQueryWrapper<MerchantChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantChannel::getMerchantId, merchantId);
        return channelMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enableChannel(Long id) {
        MerchantChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new PaymentException("渠道配置不存在");
        }
        channel.setStatus(1);
        return channelMapper.updateById(channel) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableChannel(Long id) {
        MerchantChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new PaymentException("渠道配置不存在");
        }
        channel.setStatus(0);
        return channelMapper.updateById(channel) > 0;
    }

    @Override
    public String encryptApiKey(String apiKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new PaymentException("加密失败", e);
        }
    }

    @Override
    public String decryptApiKey(String encryptedKey) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedKey));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PaymentException("解密失败", e);
        }
    }
}
