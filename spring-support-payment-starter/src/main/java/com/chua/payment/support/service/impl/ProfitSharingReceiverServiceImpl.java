package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.ProfitSharingReceiverDTO;
import com.chua.payment.support.entity.ProfitSharingReceiver;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.ProfitSharingReceiverMapper;
import com.chua.payment.support.service.ProfitSharingReceiverService;
import com.chua.payment.support.vo.ProfitSharingReceiverVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 分账接收方服务实现，负责接收方的维护、判重与查询。
 *
 * @author CH
 * @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class ProfitSharingReceiverServiceImpl implements ProfitSharingReceiverService {

    /**
     * 接收方启用状态
     */
    private static final String STATUS_ENABLED = "ENABLED";
    /**
     * 接收方停用状态
     */
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 分账接收方映射器
     */
    private final ProfitSharingReceiverMapper profitSharingReceiverMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfitSharingReceiverVO create(ProfitSharingReceiverDTO dto) {
        validate(dto);
        checkDuplicate(dto.merchantId(), dto.channelType(), dto.account(), null);
        ProfitSharingReceiver receiver = new ProfitSharingReceiver();
        applyDto(receiver, dto);
        receiver.setDeleted(0);
        profitSharingReceiverMapper.insert(receiver);
        return convertToVO(receiver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfitSharingReceiverVO update(Long id, ProfitSharingReceiverDTO dto) {
        if (id == null) {
            throw new PaymentException("接收方ID不能为空");
        }
        ProfitSharingReceiver receiver = requireReceiver(id);
        validate(dto);
        checkDuplicate(dto.merchantId(), dto.channelType(), dto.account(), id);
        receiver.setName(dto.name());
        receiver.setRatio(dto.ratio());
        receiver.setRelationType(dto.relationType());
        receiver.setRemark(dto.remark());
        profitSharingReceiverMapper.updateById(receiver);
        return convertToVO(receiver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        if (id == null) {
            throw new PaymentException("接收方ID不能为空");
        }
        ProfitSharingReceiver receiver = requireReceiver(id);
        receiver.setDeleted(1);
        return profitSharingReceiverMapper.updateById(receiver) > 0;
    }

    @Override
    public ProfitSharingReceiverVO get(Long id) {
        if (id == null) {
            throw new PaymentException("接收方ID不能为空");
        }
        return convertToVO(requireReceiver(id));
    }

    @Override
    public List<ProfitSharingReceiverVO> listByMerchant(Long merchantId, String channelType, String status) {
        if (merchantId == null) {
            throw new PaymentException("商户ID不能为空");
        }
        LambdaQueryWrapper<ProfitSharingReceiver> wrapper = new LambdaQueryWrapper<ProfitSharingReceiver>()
                .eq(ProfitSharingReceiver::getMerchantId, merchantId)
                .and(w -> w.isNull(ProfitSharingReceiver::getDeleted).or().eq(ProfitSharingReceiver::getDeleted, 0));
        if (StringUtils.hasText(channelType)) {
            wrapper.eq(ProfitSharingReceiver::getChannelType, channelType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProfitSharingReceiver::getStatus, status);
        }
        wrapper.orderByDesc(ProfitSharingReceiver::getCreatedAt);
        return profitSharingReceiverMapper.selectList(wrapper).stream().map(this::convertToVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfitSharingReceiverVO updateStatus(Long id, String status) {
        if (id == null) {
            throw new PaymentException("接收方ID不能为空");
        }
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new PaymentException("接收方状态不合法: " + status);
        }
        ProfitSharingReceiver receiver = requireReceiver(id);
        receiver.setStatus(status);
        profitSharingReceiverMapper.updateById(receiver);
        return convertToVO(receiver);
    }

    /**
     * 校验接收方参数
     *
     * @param dto 接收方参数
     */
    private void validate(ProfitSharingReceiverDTO dto) {
        if (dto == null) {
            throw new PaymentException("接收方参数不能为空");
        }
        if (dto.merchantId() == null) {
            throw new PaymentException("商户ID不能为空");
        }
        if (!StringUtils.hasText(dto.channelType())) {
            throw new PaymentException("渠道类型不能为空");
        }
        if (!StringUtils.hasText(dto.name())) {
            throw new PaymentException("接收方名称不能为空");
        }
        if (!StringUtils.hasText(dto.account())) {
            throw new PaymentException("接收方账号不能为空");
        }
        if (!StringUtils.hasText(dto.type())) {
            throw new PaymentException("账号类型不能为空");
        }
        if (dto.ratio() == null || dto.ratio().compareTo(java.math.BigDecimal.ZERO) < 0
                || dto.ratio().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new PaymentException("分账比例必须在 0~100 之间");
        }
    }

    /**
     * 同商户同渠道同账号判重
     *
     * @param merchantId  商户ID
     * @param channelType 渠道类型
     * @param account     接收方账号
     * @param excludeId   排除的接收方ID（更新时传自身ID）
     */
    private void checkDuplicate(Long merchantId, String channelType, String account, Long excludeId) {
        LambdaQueryWrapper<ProfitSharingReceiver> wrapper = new LambdaQueryWrapper<ProfitSharingReceiver>()
                .eq(ProfitSharingReceiver::getMerchantId, merchantId)
                .eq(ProfitSharingReceiver::getChannelType, channelType)
                .eq(ProfitSharingReceiver::getAccount, account)
                .and(w -> w.isNull(ProfitSharingReceiver::getDeleted).or().eq(ProfitSharingReceiver::getDeleted, 0));
        if (excludeId != null) {
            wrapper.ne(ProfitSharingReceiver::getId, excludeId);
        }
        if (profitSharingReceiverMapper.selectCount(wrapper) > 0) {
            throw new PaymentException("该渠道下已存在相同账号的分账接收方");
        }
    }

    /**
     * 将参数落到实体
     *
     * @param receiver 接收方实体
     * @param dto      接收方参数
     */
    private void applyDto(ProfitSharingReceiver receiver, ProfitSharingReceiverDTO dto) {
        receiver.setMerchantId(dto.merchantId());
        receiver.setChannelType(dto.channelType());
        receiver.setName(dto.name());
        receiver.setAccount(dto.account());
        receiver.setType(dto.type());
        receiver.setRelationType(dto.relationType());
        receiver.setRatio(dto.ratio());
        receiver.setStatus(STATUS_ENABLED);
        receiver.setRemark(dto.remark());
    }

    /**
     * 获取接收方，不存在时抛出异常
     *
     * @param id 接收方ID
     * @return 接收方实体
     */
    private ProfitSharingReceiver requireReceiver(Long id) {
        ProfitSharingReceiver receiver = profitSharingReceiverMapper.selectById(id);
        if (receiver == null || (receiver.getDeleted() != null && receiver.getDeleted() == 1)) {
            throw new PaymentException("分账接收方不存在: " + id);
        }
        return receiver;
    }

    /**
     * 转换为视图对象
     *
     * @param receiver 接收方实体
     * @return 接收方视图
     */
    private ProfitSharingReceiverVO convertToVO(ProfitSharingReceiver receiver) {
        String statusDesc = STATUS_ENABLED.equals(receiver.getStatus()) ? "启用" : "停用";
        return new ProfitSharingReceiverVO(
                receiver.getId(),
                receiver.getMerchantId(),
                receiver.getChannelType(),
                receiver.getName(),
                receiver.getAccount(),
                receiver.getType(),
                receiver.getRelationType(),
                receiver.getRatio(),
                receiver.getStatus(),
                statusDesc,
                receiver.getRemark(),
                receiver.getCreatedAt());
    }
}
