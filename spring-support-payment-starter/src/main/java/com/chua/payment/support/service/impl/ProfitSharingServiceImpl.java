package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.IdUtils;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.dto.ProfitSharingOrderQueryDTO;
import com.chua.payment.support.dto.ProfitSharingReturnDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.ProfitSharingOrder;
import com.chua.payment.support.entity.ProfitSharingReceiver;
import com.chua.payment.support.entity.ProfitSharingReturn;
import com.chua.payment.support.enums.ProfitSharingOrderStatus;
import com.chua.payment.support.enums.ProfitSharingReturnStatus;
import com.chua.payment.support.event.PaymentSuccessEvent;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.mapper.ProfitSharingOrderMapper;
import com.chua.payment.support.mapper.ProfitSharingReceiverMapper;
import com.chua.payment.support.mapper.ProfitSharingReturnMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.ProfitSharingService;
import com.chua.payment.support.vo.ProfitSharingOrderVO;
import com.chua.payment.support.vo.ProfitSharingReturnVO;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatProfitSharingRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatProfitSharingResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatProfitSharingReturnRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatProfitSharingReturnResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分账服务实现：支付成功后按商户分账配置登记分账单并调用渠道网关执行，支持分账查询与回退。
 *
 * <p>分账金额口径：以订单实付金额为基数，按接收方比例（%）计算各接收方分账金额（四舍五入两位小数），
 * 分账总金额为各接收方金额之和。同一订单重复支付成功事件幂等（复用已存在的分账单）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Service
public class ProfitSharingServiceImpl extends AbstractMerchantPaymentChannel implements ProfitSharingService {

    /**
     * 分账单号前缀
     */
    private static final String SHARING_NO_PREFIX = "PFS";
    /**
     * 回退单号前缀
     */
    private static final String RETURN_NO_PREFIX = "PFR";
    /**
     * 接收方启用状态
     */
    private static final String RECEIVER_STATUS_ENABLED = "ENABLED";
    /**
     * 渠道类型：微信
     */
    private static final String CHANNEL_TYPE_WECHAT = "WECHAT";
    /**
     * 渠道类型：支付宝
     */
    private static final String CHANNEL_TYPE_ALIPAY = "ALIPAY";

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
    /**
     * 分账单映射器
     */
    private final ProfitSharingOrderMapper profitSharingOrderMapper;
    /**
     * 分账接收方映射器
     */
    private final ProfitSharingReceiverMapper profitSharingReceiverMapper;
    /**
     * 分账回退映射器
     */
    private final ProfitSharingReturnMapper profitSharingReturnMapper;
    /**
     * 支付渠道网关注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
     * 构造分账服务实现。
     *
     * @param merchantChannelService   商户渠道服务
     * @param objectMapper             对象映射器
     * @param merchantMapper           商户映射器
     * @param merchantChannelMapper    商户渠道映射器
     * @param paymentOrderMapper       支付订单映射器
     * @param profitSharingOrderMapper 分账单映射器
     * @param profitSharingReceiverMapper 分账接收方映射器
     * @param profitSharingReturnMapper   分账回退映射器
     * @param providerGatewayRegistry  支付渠道网关注册表
     */
    public ProfitSharingServiceImpl(MerchantChannelService merchantChannelService,
                                    ObjectMapper objectMapper,
                                    MerchantMapper merchantMapper,
                                    MerchantChannelMapper merchantChannelMapper,
                                    PaymentOrderMapper paymentOrderMapper,
                                    ProfitSharingOrderMapper profitSharingOrderMapper,
                                    ProfitSharingReceiverMapper profitSharingReceiverMapper,
                                    ProfitSharingReturnMapper profitSharingReturnMapper,
                                    PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantMapper = merchantMapper;
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.profitSharingOrderMapper = profitSharingOrderMapper;
        this.profitSharingReceiverMapper = profitSharingReceiverMapper;
        this.profitSharingReturnMapper = profitSharingReturnMapper;
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfitSharingOrderVO shareForOrder(PaymentSuccessEvent event) {
        if (event == null || event.getMerchantId() == null || !StringUtils.hasText(event.getOrderNo())) {
            return null;
        }
        Merchant merchant = merchantMapper.selectById(event.getMerchantId());
        if (merchant == null || !Boolean.TRUE.equals(merchant.getPaymentProfitSharingEnabled())) {
            return null;
        }
        ProfitSharingOrder existing = findByOrderNo(event.getOrderNo());
        if (existing != null) {
            return convertToVO(existing);
        }
        List<ProfitSharingReceiver> receivers = listEnabledReceivers(event.getMerchantId(), event.getChannelType());
        if (receivers.isEmpty()) {
            log.info("商户未配置启用的分账接收方，跳过分账: orderNo={}, channelType={}",
                    event.getOrderNo(), event.getChannelType());
            return null;
        }
        if (!CHANNEL_TYPE_WECHAT.equalsIgnoreCase(event.getChannelType())
                && !CHANNEL_TYPE_ALIPAY.equalsIgnoreCase(event.getChannelType())) {
            log.info("渠道类型不支持分账，跳过: orderNo={}, channelType={}", event.getOrderNo(), event.getChannelType());
            return null;
        }

        ProfitSharingOrder order = insertSharingOrder(event, receivers);

        MerchantChannel channel = resolveChannel(event.getOrderId());
        if (channel == null) {
            markOrderFailed(order, "支付渠道不存在或未启用");
            return convertToVO(order);
        }
        order.setChannelId(channel.getId());
        profitSharingOrderMapper.updateById(order);
        try {
            ProfitSharingOrderStatus status;
            String channelOrderNo;
            if (CHANNEL_TYPE_WECHAT.equalsIgnoreCase(order.getChannelType())) {
                TencentWechatProfitSharingResponse response = executeWechatSharing(channel, order, receivers);
                if (!response.success()) {
                    markOrderFailed(order, response.message());
                    return convertToVO(order);
                }
                status = mapWechatOrderState(response.state());
                channelOrderNo = response.orderId();
            } else {
                AliyunAlipayProfitSharingResponse response = executeAlipaySharing(channel, order, receivers);
                if (!response.success()) {
                    markOrderFailed(order, response.message());
                    return convertToVO(order);
                }
                status = ProfitSharingOrderStatus.SUCCEEDED;
                channelOrderNo = response.settleNo();
            }
            order.setStatus(status.getCode());
            order.setChannelOrderNo(channelOrderNo);
            if (status == ProfitSharingOrderStatus.SUCCEEDED || status == ProfitSharingOrderStatus.FAILED) {
                order.setFinishAt(LocalDateTime.now());
            }
            profitSharingOrderMapper.updateById(order);
            log.info("分账执行完成: sharingOrderNo={}, status={}", order.getSharingOrderNo(), order.getStatus());
        } catch (Exception e) {
            log.error("分账执行异常: sharingOrderNo={}", order.getSharingOrderNo(), e);
            markOrderFailed(order, e.getMessage());
        }
        return convertToVO(order);
    }

    @Override
    public Page<ProfitSharingOrderVO> pageOrders(ProfitSharingOrderQueryDTO dto) {
        int pageNum = dto == null || dto.pageNum() == null || dto.pageNum() < 1 ? 1 : dto.pageNum();
        int pageSize = dto == null || dto.pageSize() == null || dto.pageSize() < 1 ? 10 : dto.pageSize();
        LambdaQueryWrapper<ProfitSharingOrder> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.merchantId() != null) {
                wrapper.eq(ProfitSharingOrder::getMerchantId, dto.merchantId());
            }
            if (StringUtils.hasText(dto.sharingOrderNo())) {
                wrapper.eq(ProfitSharingOrder::getSharingOrderNo, dto.sharingOrderNo());
            }
            if (StringUtils.hasText(dto.orderNo())) {
                wrapper.eq(ProfitSharingOrder::getOrderNo, dto.orderNo());
            }
            if (StringUtils.hasText(dto.status())) {
                wrapper.eq(ProfitSharingOrder::getStatus, dto.status());
            }
        }
        wrapper.orderByDesc(ProfitSharingOrder::getCreatedAt);
        Page<ProfitSharingOrder> orderPage = profitSharingOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ProfitSharingOrderVO> voPage = new Page<>(pageNum, pageSize, orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public ProfitSharingOrderVO getBySharingOrderNo(String sharingOrderNo) {
        ProfitSharingOrder order = requireBySharingOrderNo(sharingOrderNo);
        return convertToVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfitSharingReturnVO returnSharing(ProfitSharingReturnDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.sharingOrderNo())) {
            throw new PaymentException("分账单号不能为空");
        }
        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("回退金额必须大于 0");
        }
        ProfitSharingOrder order = requireBySharingOrderNo(dto.sharingOrderNo());
        if (!ProfitSharingOrderStatus.SUCCEEDED.getCode().equals(order.getStatus())) {
            throw new PaymentException("仅分账成功的分账单可发起回退，当前状态: " + order.getStatus());
        }
        if (dto.amount().compareTo(order.getSharingAmount()) > 0) {
            throw new PaymentException("回退金额不能超过分账总金额");
        }
        String receiverAccount = resolveReturnReceiverAccount(order, dto.receiverAccount());

        ProfitSharingReturn shareReturn = new ProfitSharingReturn();
        shareReturn.setReturnNo(IdUtils.createOrderNo(RETURN_NO_PREFIX));
        shareReturn.setMerchantId(order.getMerchantId());
        shareReturn.setSharingOrderId(order.getId());
        shareReturn.setSharingOrderNo(order.getSharingOrderNo());
        shareReturn.setOrderNo(order.getOrderNo());
        shareReturn.setAmount(dto.amount());
        shareReturn.setReceiverAccount(receiverAccount);
        shareReturn.setReason(dto.reason());
        shareReturn.setStatus(ProfitSharingReturnStatus.PENDING.getCode());
        shareReturn.setDeleted(0);
        profitSharingReturnMapper.insert(shareReturn);

        MerchantChannel channel = requireChannel(order);
        try {
            String status;
            String channelReturnNo;
            if (CHANNEL_TYPE_WECHAT.equalsIgnoreCase(order.getChannelType())) {
                TencentWechatPayProperties properties = buildWechatProperties(channel);
                TencentWechatProfitSharingReturnRequest request = new TencentWechatProfitSharingReturnRequest(
                        channel.getAppId(),
                        order.getSharingOrderNo(),
                        shareReturn.getReturnNo(),
                        receiverAccount,
                        yuanToFen(dto.amount()),
                        firstNonBlank(dto.reason(), "分账回退"),
                        null);
                TencentWechatProfitSharingReturnResponse response =
                        wechatGateway().returnProfitSharing(properties, request);
                if (!response.success()) {
                    markReturnFailed(shareReturn, response.message());
                    return convertToVO(shareReturn);
                }
                status = mapWechatReturnState(response.result());
                channelReturnNo = response.returnId();
            } else {
                AliyunAlipayProperties properties = buildAlipayProperties(channel);
                AliyunAlipayProfitSharingReturnRequest request = new AliyunAlipayProfitSharingReturnRequest(
                        shareReturn.getReturnNo(),
                        order.getThirdPartyOrderNo(),
                        receiverAccount,
                        dto.amount(),
                        firstNonBlank(dto.reason(), "分账回退"),
                        null);
                AliyunAlipayProfitSharingReturnResponse response =
                        alipayGateway().returnProfitSharing(properties, request);
                if (!response.success()) {
                    markReturnFailed(shareReturn, response.message());
                    return convertToVO(shareReturn);
                }
                status = ProfitSharingReturnStatus.SUCCEEDED.getCode();
                channelReturnNo = response.returnNo();
            }
            shareReturn.setStatus(status);
            shareReturn.setChannelReturnNo(channelReturnNo);
            if (ProfitSharingReturnStatus.SUCCEEDED.getCode().equals(status)
                    || ProfitSharingReturnStatus.FAILED.getCode().equals(status)) {
                shareReturn.setFinishAt(LocalDateTime.now());
            }
            profitSharingReturnMapper.updateById(shareReturn);
            if (ProfitSharingReturnStatus.SUCCEEDED.getCode().equals(status)) {
                order.setStatus(ProfitSharingOrderStatus.RETURNED.getCode());
                order.setFinishAt(LocalDateTime.now());
                profitSharingOrderMapper.updateById(order);
            }
            log.info("分账回退完成: returnNo={}, status={}", shareReturn.getReturnNo(), shareReturn.getStatus());
        } catch (Exception e) {
            log.error("分账回退异常: returnNo={}", shareReturn.getReturnNo(), e);
            markReturnFailed(shareReturn, e.getMessage());
        }
        return convertToVO(shareReturn);
    }

    @Override
    public List<ProfitSharingReturnVO> listReturns(String sharingOrderNo) {
        if (!StringUtils.hasText(sharingOrderNo)) {
            throw new PaymentException("分账单号不能为空");
        }
        LambdaQueryWrapper<ProfitSharingReturn> wrapper = new LambdaQueryWrapper<ProfitSharingReturn>()
                .eq(ProfitSharingReturn::getSharingOrderNo, sharingOrderNo)
                .and(w -> w.isNull(ProfitSharingReturn::getDeleted).or().eq(ProfitSharingReturn::getDeleted, 0))
                .orderByDesc(ProfitSharingReturn::getCreatedAt);
        return profitSharingReturnMapper.selectList(wrapper).stream().map(this::convertToVO).toList();
    }

    /**
     * 登记分账单（PENDING），并按接收方比例计算分账明细
     *
     * @param event     支付成功事件
     * @param receivers 启用的接收方列表
     * @return 已落库的分账单
     */
    private ProfitSharingOrder insertSharingOrder(PaymentSuccessEvent event, List<ProfitSharingReceiver> receivers) {
        BigDecimal paidAmount = event.getPaidAmount() == null ? BigDecimal.ZERO : event.getPaidAmount();
        List<Map<String, Object>> details = new ArrayList<>(receivers.size());
        BigDecimal sharingAmount = BigDecimal.ZERO;
        for (ProfitSharingReceiver receiver : receivers) {
            BigDecimal amount = paidAmount
                    .multiply(receiver.getRatio() == null ? BigDecimal.ZERO : receiver.getRatio())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            sharingAmount = sharingAmount.add(amount);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", receiver.getName());
            detail.put("account", receiver.getAccount());
            detail.put("type", receiver.getType());
            detail.put("relationType", receiver.getRelationType());
            detail.put("amount", amount);
            detail.put("description", "订单分账:" + event.getOrderNo());
            details.add(detail);
        }

        ProfitSharingOrder order = new ProfitSharingOrder();
        order.setSharingOrderNo(IdUtils.createOrderNo(SHARING_NO_PREFIX));
        order.setMerchantId(event.getMerchantId());
        order.setChannelType(event.getChannelType());
        order.setOrderId(event.getOrderId());
        order.setOrderNo(event.getOrderNo());
        order.setThirdPartyOrderNo(event.getThirdPartyOrderNo());
        order.setTotalAmount(paidAmount);
        order.setSharingAmount(sharingAmount);
        order.setReceivers(toJson(details));
        order.setStatus(ProfitSharingOrderStatus.PENDING.getCode());
        order.setDeleted(0);
        profitSharingOrderMapper.insert(order);
        return order;
    }

    /**
     * 调用微信网关执行分账
     *
     * @param channel   支付渠道
     * @param order     分账单
     * @param receivers 启用的接收方列表
     * @return 微信分账响应
     */
    private TencentWechatProfitSharingResponse executeWechatSharing(MerchantChannel channel, ProfitSharingOrder order,
                                                                    List<ProfitSharingReceiver> receivers) {
        TencentWechatPayProperties properties = buildWechatProperties(channel);
        List<Map<String, Object>> apiReceivers = new ArrayList<>(receivers.size());
        for (ProfitSharingReceiver receiver : receivers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", receiver.getType());
            item.put("account", receiver.getAccount());
            item.put("amount", yuanToFen(order.getTotalAmount()
                    .multiply(receiver.getRatio() == null ? BigDecimal.ZERO : receiver.getRatio())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)));
            item.put("description", "订单分账:" + order.getOrderNo());
            apiReceivers.add(item);
        }
        TencentWechatProfitSharingRequest request = new TencentWechatProfitSharingRequest(
                channel.getAppId(),
                order.getThirdPartyOrderNo(),
                order.getSharingOrderNo(),
                apiReceivers,
                null,
                null);
        return wechatGateway().createProfitSharingOrder(properties, request);
    }

    /**
     * 调用支付宝网关执行分账
     *
     * @param channel   支付渠道
     * @param order     分账单
     * @param receivers 启用的接收方列表
     * @return 支付宝分账响应
     */
    private AliyunAlipayProfitSharingResponse executeAlipaySharing(MerchantChannel channel, ProfitSharingOrder order,
                                                                   List<ProfitSharingReceiver> receivers) {
        AliyunAlipayProperties properties = buildAlipayProperties(channel);
        List<Map<String, Object>> royaltyParameters = new ArrayList<>(receivers.size());
        for (ProfitSharingReceiver receiver : receivers) {
            BigDecimal amount = order.getTotalAmount()
                    .multiply(receiver.getRatio() == null ? BigDecimal.ZERO : receiver.getRatio())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("trans_in", receiver.getAccount());
            item.put("amount", AliyunAlipayProfitSharingRequest.amountText(amount));
            item.put("desc", "订单分账:" + order.getOrderNo());
            royaltyParameters.add(item);
        }
        AliyunAlipayProfitSharingRequest request = new AliyunAlipayProfitSharingRequest(
                order.getSharingOrderNo(),
                order.getThirdPartyOrderNo(),
                royaltyParameters,
                "订单分账:" + order.getOrderNo(),
                null);
        return alipayGateway().createProfitSharingOrder(properties, request);
    }

    /**
     * 构建微信支付配置属性
     *
     * @param channel 渠道
     * @return 微信支付配置属性
     */
    private TencentWechatPayProperties buildWechatProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(extText(channel, "merchantSerialNumber"), "微信商户证书序列号不能为空"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }

    /**
     * 构建支付宝配置属性
     *
     * @param channel 渠道
     * @return 支付宝配置属性
     */
    private AliyunAlipayProperties buildAlipayProperties(MerchantChannel channel) {
        AliyunAlipayProperties properties = new AliyunAlipayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "支付宝应用ID不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "支付宝私钥不能为空"));
        properties.setAlipayPublicKey(requiredText(channel.getPublicKey(), "支付宝公钥不能为空"));
        properties.setSandbox(channel.getSandboxMode() != null && channel.getSandboxMode() == 1);
        properties.setServerUrl(extText(channel, "serverUrl"));
        properties.setCharset(firstNonBlank(extText(channel, "charset"), "UTF-8"));
        properties.setFormat(firstNonBlank(extText(channel, "format"), "json"));
        properties.setSignType(firstNonBlank(extText(channel, "signType"), "RSA2"));
        return properties;
    }

    /**
     * 获取微信支付网关
     *
     * @return 微信支付网关
     */
    private TencentWechatPayGateway wechatGateway() {
        return providerGatewayRegistry.tencentWechatPayGateway();
    }

    /**
     * 获取支付宝网关
     *
     * @return 支付宝网关
     */
    private AliyunAlipayGateway alipayGateway() {
        return providerGatewayRegistry.aliyunAlipayGateway();
    }

    /**
     * 按支付订单号查询分账单（幂等判定）
     *
     * @param orderNo 支付订单号
     * @return 分账单；不存在返回 {@code null}
     */
    private ProfitSharingOrder findByOrderNo(String orderNo) {
        LambdaQueryWrapper<ProfitSharingOrder> wrapper = new LambdaQueryWrapper<ProfitSharingOrder>()
                .eq(ProfitSharingOrder::getOrderNo, orderNo)
                .and(w -> w.isNull(ProfitSharingOrder::getDeleted).or().eq(ProfitSharingOrder::getDeleted, 0));
        List<ProfitSharingOrder> orders = profitSharingOrderMapper.selectList(wrapper);
        return orders.isEmpty() ? null : orders.get(0);
    }

    /**
     * 获取分账单，不存在时抛出异常
     *
     * @param sharingOrderNo 分账单号
     * @return 分账单
     */
    private ProfitSharingOrder requireBySharingOrderNo(String sharingOrderNo) {
        LambdaQueryWrapper<ProfitSharingOrder> wrapper = new LambdaQueryWrapper<ProfitSharingOrder>()
                .eq(ProfitSharingOrder::getSharingOrderNo, sharingOrderNo)
                .and(w -> w.isNull(ProfitSharingOrder::getDeleted).or().eq(ProfitSharingOrder::getDeleted, 0));
        List<ProfitSharingOrder> orders = profitSharingOrderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            throw new PaymentException("分账单不存在: " + sharingOrderNo);
        }
        return orders.get(0);
    }

    /**
     * 查询商户在指定渠道下启用的分账接收方
     *
     * @param merchantId  商户ID
     * @param channelType 渠道类型
     * @return 启用的接收方列表
     */
    private List<ProfitSharingReceiver> listEnabledReceivers(Long merchantId, String channelType) {
        if (!StringUtils.hasText(channelType)) {
            return List.of();
        }
        LambdaQueryWrapper<ProfitSharingReceiver> wrapper = new LambdaQueryWrapper<ProfitSharingReceiver>()
                .eq(ProfitSharingReceiver::getMerchantId, merchantId)
                .eq(ProfitSharingReceiver::getChannelType, channelType)
                .eq(ProfitSharingReceiver::getStatus, RECEIVER_STATUS_ENABLED)
                .and(w -> w.isNull(ProfitSharingReceiver::getDeleted).or().eq(ProfitSharingReceiver::getDeleted, 0));
        return profitSharingReceiverMapper.selectList(wrapper);
    }

    /**
     * 解析支付渠道：优先按支付订单的渠道ID取，取不到返回 {@code null}
     *
     * @param orderId 支付订单ID
     * @return 支付渠道；不存在返回 {@code null}
     */
    private MerchantChannel resolveChannel(Long orderId) {
        if (orderId == null) {
            return null;
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(orderId);
        if (paymentOrder == null || paymentOrder.getChannelId() == null) {
            return null;
        }
        MerchantChannel channel = merchantChannelMapper.selectById(paymentOrder.getChannelId());
        if (channel == null) {
            return null;
        }
        // 钱包等内部渠道不参与渠道侧分账
        if (!CHANNEL_TYPE_WECHAT.equalsIgnoreCase(channel.getChannelType())
                && !CHANNEL_TYPE_ALIPAY.equalsIgnoreCase(channel.getChannelType())) {
            return null;
        }
        return channel;
    }

    /**
     * 获取分账单关联的支付渠道，不存在时抛出异常
     *
     * @param order 分账单
     * @return 支付渠道
     */
    private MerchantChannel requireChannel(ProfitSharingOrder order) {
        if (order.getChannelId() == null) {
            throw new PaymentException("分账单未关联支付渠道");
        }
        MerchantChannel channel = merchantChannelMapper.selectById(order.getChannelId());
        if (channel == null) {
            throw new PaymentException("支付渠道不存在: " + order.getChannelId());
        }
        return channel;
    }

    /**
     * 解析回退接收方账号：入参为空且仅一个接收方时取该接收方账号
     *
     * @param order           分账单
     * @param receiverAccount 入参接收方账号
     * @return 回退接收方账号
     */
    private String resolveReturnReceiverAccount(ProfitSharingOrder order, String receiverAccount) {
        if (StringUtils.hasText(receiverAccount)) {
            return receiverAccount;
        }
        List<Map<String, Object>> details = parseReceivers(order.getReceivers());
        if (details.size() == 1) {
            Object account = details.get(0).get("account");
            if (account != null && StringUtils.hasText(String.valueOf(account))) {
                return String.valueOf(account);
            }
        }
        throw new PaymentException("回退接收方账号不能为空");
    }

    /**
     * 解析分账单接收方明细 JSON
     *
     * @param receiversJson 接收方明细 JSON
     * @return 明细列表；解析失败返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseReceivers(String receiversJson) {
        if (!StringUtils.hasText(receiversJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(receiversJson, List.class);
        } catch (Exception e) {
            log.warn("分账接收方明细解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将微信分账状态映射为本域分账单状态
     *
     * @param wechatState 微信分账状态
     * @return 本域分账单状态
     */
    private ProfitSharingOrderStatus mapWechatOrderState(String wechatState) {
        if (wechatState == null) {
            return ProfitSharingOrderStatus.PROCESSING;
        }
        return switch (wechatState.toUpperCase()) {
            case "SUCCESS" -> ProfitSharingOrderStatus.SUCCEEDED;
            case "FAILED" -> ProfitSharingOrderStatus.FAILED;
            default -> ProfitSharingOrderStatus.PROCESSING;
        };
    }

    /**
     * 将微信回退状态映射为本域回退单状态
     *
     * @param wechatResult 微信回退状态
     * @return 本域回退单状态码
     */
    private String mapWechatReturnState(String wechatResult) {
        if (wechatResult == null) {
            return ProfitSharingReturnStatus.PENDING.getCode();
        }
        return switch (wechatResult.toUpperCase()) {
            case "SUCCESS" -> ProfitSharingReturnStatus.SUCCEEDED.getCode();
            case "FAILED" -> ProfitSharingReturnStatus.FAILED.getCode();
            default -> ProfitSharingReturnStatus.PENDING.getCode();
        };
    }

    /**
     * 将分账单标记为失败
     *
     * @param order  分账单
     * @param reason 失败原因
     */
    private void markOrderFailed(ProfitSharingOrder order, String reason) {
        order.setStatus(ProfitSharingOrderStatus.FAILED.getCode());
        order.setFailReason(reason);
        order.setFinishAt(LocalDateTime.now());
        profitSharingOrderMapper.updateById(order);
    }

    /**
     * 将回退单标记为失败
     *
     * @param shareReturn 回退单
     * @param reason      失败原因
     */
    private void markReturnFailed(ProfitSharingReturn shareReturn, String reason) {
        shareReturn.setStatus(ProfitSharingReturnStatus.FAILED.getCode());
        shareReturn.setFailReason(reason);
        shareReturn.setFinishAt(LocalDateTime.now());
        profitSharingReturnMapper.updateById(shareReturn);
    }

    /**
     * 转换为分账单视图
     *
     * @param order 分账单
     * @return 分账单视图
     */
    private ProfitSharingOrderVO convertToVO(ProfitSharingOrder order) {
        String statusDesc;
        ProfitSharingOrderStatus status = ProfitSharingOrderStatus.valueOf(order.getStatus());
        statusDesc = switch (status) {
            case PENDING -> "待分账";
            case PROCESSING -> "分账中";
            case SUCCEEDED -> "分账成功";
            case FAILED -> "分账失败";
            case RETURNED -> "已回退";
        };
        return new ProfitSharingOrderVO(
                order.getId(),
                order.getSharingOrderNo(),
                order.getMerchantId(),
                order.getChannelId(),
                order.getChannelType(),
                order.getOrderId(),
                order.getOrderNo(),
                order.getThirdPartyOrderNo(),
                order.getTotalAmount(),
                order.getSharingAmount(),
                order.getReceivers(),
                order.getStatus(),
                statusDesc,
                order.getChannelOrderNo(),
                order.getFailReason(),
                order.getFinishAt(),
                order.getRemark(),
                order.getCreatedAt());
    }

    /**
     * 转换为回退单视图
     *
     * @param shareReturn 回退单
     * @return 回退单视图
     */
    private ProfitSharingReturnVO convertToVO(ProfitSharingReturn shareReturn) {
        String statusDesc;
        ProfitSharingReturnStatus status = ProfitSharingReturnStatus.valueOf(shareReturn.getStatus());
        statusDesc = switch (status) {
            case PENDING -> "处理中";
            case SUCCEEDED -> "回退成功";
            case FAILED -> "回退失败";
        };
        return new ProfitSharingReturnVO(
                shareReturn.getId(),
                shareReturn.getReturnNo(),
                shareReturn.getMerchantId(),
                shareReturn.getSharingOrderId(),
                shareReturn.getSharingOrderNo(),
                shareReturn.getOrderNo(),
                shareReturn.getAmount(),
                shareReturn.getReceiverAccount(),
                shareReturn.getReason(),
                shareReturn.getStatus(),
                statusDesc,
                shareReturn.getChannelReturnNo(),
                shareReturn.getFailReason(),
                shareReturn.getFinishAt(),
                shareReturn.getCreatedAt());
    }
}
