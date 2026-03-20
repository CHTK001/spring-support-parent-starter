package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.configuration.PaymentProviderProperties;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.OnboardingStatus;
import com.chua.payment.support.enums.PaymentChannelType;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;
import com.chua.payment.support.vo.ProviderSpiOptionVO;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 商户渠道服务实现
 */
@Service
@RequiredArgsConstructor
public class MerchantChannelServiceImpl implements MerchantChannelService {

    private static final String AES_KEY = "PaymentSystem16";

    private final MerchantChannelMapper channelMapper;
    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;
    private final PaymentProviderProperties paymentProviderProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelVO createChannel(ChannelConfigDTO dto) {
        Merchant merchant = requireMerchant(dto.getMerchantId());
        MerchantChannel channel = new MerchantChannel();
        BeanUtils.copyProperties(dto, channel);
        channel.setExtConfig(normalizeExtConfig(channel.getChannelType(), mergeProviderSpi(dto.getExtConfig(), dto.getProviderSpi())));
        channel.setChannelName(StringUtils.hasText(dto.getChannelName()) ? dto.getChannelName() : buildDefaultChannelName(dto.getChannelType(), dto.getChannelSubType()));
        channel.setApiKey(encryptIfPresent(dto.getApiKey()));
        channel.setPrivateKey(encryptIfPresent(dto.getPrivateKey()));
        channel.setStatus(dto.getStatus() != null ? dto.getStatus() : ChannelStatus.DISABLED.getCode());
        channel.setSandboxMode(dto.getSandboxMode() != null ? dto.getSandboxMode() : 0);
        channel.setOnboardingStatus(StringUtils.hasText(dto.getOnboardingStatus()) ? dto.getOnboardingStatus() : defaultOnboardingStatus(dto.getChannelType()));
        PaymentMethodGuideVO guide = findGuide(dto.getChannelType(), dto.getChannelSubType());
        channel.setOnboardingLink(StringUtils.hasText(dto.getOnboardingLink()) ? dto.getOnboardingLink() : defaultGuideUrl(guide));
        channelMapper.insert(channel);
        return convertToVO(channel, merchant, guide);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelVO updateChannel(Long id, ChannelConfigDTO dto) {
        MerchantChannel channel = requireChannel(id);
        Merchant merchant = requireMerchant(channel.getMerchantId());
        BeanUtils.copyProperties(dto, channel, "apiKey", "privateKey");
        channel.setExtConfig(normalizeExtConfig(channel.getChannelType(), mergeProviderSpi(channel.getExtConfig(), dto.getProviderSpi())));
        channel.setChannelName(StringUtils.hasText(channel.getChannelName()) ? channel.getChannelName() : buildDefaultChannelName(channel.getChannelType(), channel.getChannelSubType()));
        if (StringUtils.hasText(dto.getApiKey())) {
            channel.setApiKey(encryptApiKey(dto.getApiKey()));
        }
        if (StringUtils.hasText(dto.getPrivateKey())) {
            channel.setPrivateKey(encryptApiKey(dto.getPrivateKey()));
        }
        if (dto.getStatus() == null && channel.getStatus() == null) {
            channel.setStatus(ChannelStatus.DISABLED.getCode());
        }
        if (dto.getSandboxMode() == null && channel.getSandboxMode() == null) {
            channel.setSandboxMode(0);
        }
        if (!StringUtils.hasText(channel.getOnboardingStatus())) {
            channel.setOnboardingStatus(defaultOnboardingStatus(channel.getChannelType()));
        }
        PaymentMethodGuideVO guide = findGuide(channel.getChannelType(), channel.getChannelSubType());
        if (!StringUtils.hasText(channel.getOnboardingLink())) {
            channel.setOnboardingLink(defaultGuideUrl(guide));
        }
        channelMapper.updateById(channel);
        return convertToVO(channel, merchant, guide);
    }

    @Override
    public MerchantChannelVO getChannel(Long id) {
        MerchantChannel channel = requireChannel(id);
        Merchant merchant = merchantMapper.selectById(channel.getMerchantId());
        return convertToVO(channel, merchant, findGuide(channel.getChannelType(), channel.getChannelSubType()));
    }

    @Override
    public List<MerchantChannelVO> listChannels(Long merchantId, String channelType, Integer status) {
        LambdaQueryWrapper<MerchantChannel> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(MerchantChannel::getMerchantId, merchantId);
        }
        if (StringUtils.hasText(channelType)) {
            wrapper.eq(MerchantChannel::getChannelType, channelType.toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            wrapper.eq(MerchantChannel::getStatus, status);
        }
        wrapper.orderByDesc(MerchantChannel::getCreatedAt);
        return channelMapper.selectList(wrapper).stream()
                .map(channel -> convertToVO(
                        channel,
                        merchantMapper.selectById(channel.getMerchantId()),
                        findGuide(channel.getChannelType(), channel.getChannelSubType())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enableChannel(Long id) {
        MerchantChannel channel = requireChannel(id);
        channel.setStatus(ChannelStatus.ENABLED.getCode());
        return channelMapper.updateById(channel) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableChannel(Long id) {
        MerchantChannel channel = requireChannel(id);
        channel.setStatus(ChannelStatus.DISABLED.getCode());
        return channelMapper.updateById(channel) > 0;
    }

    @Override
    public List<PaymentMethodGuideVO> listCatalog() {
        List<PaymentMethodGuideVO> list = new ArrayList<>();
        list.add(guide("WECHAT", "MINI_PROGRAM", "微信小程序支付开通", "微信支付", "https://pay.wechatpay.cn/", "https://pay.wechatpay.cn/static/applyment_guide/applyment_detail_miniapp.shtml", null,
                "完成微信支付商户开通、AppID 绑定与小程序收款接入。",
                List.of("营业执照", "法人身份信息", "结算账户", "小程序 AppID", "支付回调地址"),
                List.of("申请微信支付商户号", "在商户平台绑定小程序 AppID", "配置 APIv3 密钥和证书", "配置支付回调地址", "在系统内录入配置"),
                List.of("小程序支付要求前端具备 openId 获取链路", "商户号和 AppID 必须绑定")));
        list.add(guide("WECHAT", "JSAPI", "微信 JSAPI 支付开通", "微信支付", "https://pay.wechatpay.cn/", "https://pay.wechatpay.cn/doc/v3/merchant/4012791877", null,
                "适用于公众号、服务号或嵌入式微信环境支付。",
                List.of("营业执照", "法人信息", "微信公众号/服务号 AppID", "支付回调地址"),
                List.of("申请微信支付商户号", "绑定公众号或服务号 AppID", "开通 JSAPI 能力", "配置 APIv3 密钥与平台证书", "回填系统配置"),
                List.of("必须能获取用户 openId", "如涉及服务商模式需额外处理子商户授权")));
        list.add(guide("WECHAT", "H5", "微信 H5 支付开通", "微信支付", "https://pay.wechatpay.cn/", "https://pay.wechatpay.cn/doc/v3/merchant/4012791841", null,
                "适用于手机浏览器网页支付，需要申请 H5 支付权限。",
                List.of("营业执照", "域名", "ICP备案截图", "经营场景说明", "回调地址"),
                List.of("申请微信支付商户号", "在商户平台申请 H5 支付权限", "配置 H5 支付域名", "配置 APIv3 密钥", "录入系统配置"),
                List.of("H5 支付不是默认开通能力", "回调地址必须可公网访问")));
        list.add(guide("WECHAT", "APP", "微信 APP 支付开通", "微信支付", "https://pay.wechatpay.cn/", "https://pay.wechatpay.cn/", null,
                "适用于移动应用内支付。",
                List.of("营业执照", "法人信息", "移动应用 AppID", "应用包信息"),
                List.of("申请微信支付商户号", "绑定移动应用 AppID", "配置 APIv3 密钥和证书", "录入应用侧支付参数"),
                List.of("需要客户端配合拉起支付")));
        list.add(guide("WECHAT", "NATIVE", "微信 Native 支付开通", "微信支付", "https://pay.wechatpay.cn/", "https://pay.wechatpay.cn/", null,
                "适用于二维码主扫场景。",
                List.of("营业执照", "法人信息", "回调地址"),
                List.of("申请商户号", "配置 Native 支付参数", "生成码链接并展示给用户扫码"),
                List.of("Native 支付适合 PC 收银台和线下台卡")));

        list.add(guide("ALIPAY", "WEB", "支付宝电脑网站支付开通", "支付宝开放平台", "https://open.alipay.com/", "https://open.alipay.com/productDocument.htm", "https://open.alipay.com/support/supportCenter.htm",
                "适用于 PC 网站收款。",
                List.of("企业支付宝账号", "网站域名", "应用公私钥", "异步通知地址"),
                List.of("在开放平台创建应用", "申请电脑网站支付产品能力", "配置公私钥和回调地址", "系统内录入应用参数"),
                List.of("正式环境和沙箱环境网关不同")));
        list.add(guide("ALIPAY", "WAP", "支付宝手机网站支付开通", "支付宝开放平台", "https://open.alipay.com/", "https://open.alipay.com/productDocument.htm", "https://open.alipay.com/support/supportCenter.htm",
                "适用于手机浏览器网页支付。",
                List.of("企业支付宝账号", "H5 域名", "应用公私钥", "异步通知地址"),
                List.of("创建应用", "申请手机网站支付", "配置回调和公私钥", "系统内录入网关参数"),
                List.of("移动网页支付和电脑网站支付是不同产品能力")));
        list.add(guide("ALIPAY", "APP", "支付宝 APP 支付开通", "支付宝开放平台", "https://open.alipay.com/", "https://open.alipay.com/productDocument.htm", "https://open.alipay.com/support/supportCenter.htm",
                "适用于移动应用内拉起支付宝。",
                List.of("企业支付宝账号", "应用包信息", "应用公私钥", "回调地址"),
                List.of("创建应用", "申请 APP 支付产品", "配置应用签名", "录入系统配置"),
                List.of("需要客户端配合完成订单参数签名")));
        list.add(guide("ALIPAY", "FACE_TO_FACE", "支付宝当面付开通", "支付宝开放平台", "https://open.alipay.com/", "https://open.alipay.com/paymentServicer/paymentServicer.htm", "https://open.alipay.com/support/supportCenter.htm",
                "适用于扫码收款、门店台码、POS 场景。",
                List.of("企业支付宝账号", "门店经营信息", "应用公私钥", "回调地址"),
                List.of("创建应用", "申请当面付能力", "如为服务商模式完成商户签约", "录入系统配置"),
                List.of("服务商模式下还需要商户授权")));
        list.add(guide("ALIPAY", "SANDBOX", "支付宝沙箱调试", "支付宝开放平台", "https://open.alipay.com/", "https://open.alipay.com/support/supportCenter.htm", "https://os.alipayobjects.com/rmsportal/qzepRfDMREmzZQHMwXai.pdf",
                "用于联调阶段的支付、退款、异步通知测试。",
                List.of("开放平台测试应用", "沙箱密钥", "沙箱网关地址"),
                List.of("进入开放平台支持中心", "打开沙箱调试", "获取测试账号与测试应用参数", "系统内开启沙箱模式"),
                List.of("联调完成后必须切回正式网关")));

        list.add(guide("COMPOSITE", "AGGREGATE_ROUTE", "综合支付路由配置", "平台内部路由", null, null, null,
                "用于系统统一路由到多个第三方支付渠道。",
                List.of("路由标识", "签名密钥", "回调地址", "供应商对接信息"),
                List.of("确认已开通下游支付方式", "录入路由配置", "启用风控或权重规则"),
                List.of("该能力不对应单一官方开户平台")));
        list.add(guide("WALLET", "BALANCE", "站内钱包配置", "平台内部钱包", null, null, null,
                "用于账户余额支付、退款退回余额。",
                List.of("钱包开关", "充值策略", "风控规则"),
                List.of("启用钱包", "配置余额扣减和退款规则", "配置对账策略"),
                List.of("钱包能力不依赖第三方开户，但对账责任在平台自身")));
        return list;
    }

    @Override
    public List<ProviderSpiOptionVO> listProviderOptions(String channelType) {
        if (!StringUtils.hasText(channelType)) {
            return List.of();
        }

        String normalizedType = channelType.toUpperCase(Locale.ROOT);
        if (PaymentChannelType.ALIPAY.getCode().equalsIgnoreCase(normalizedType)) {
            return buildProviderOptions(normalizedType, ServiceProvider.of(AliyunAlipayGateway.class).getExtensions());
        }
        if (PaymentChannelType.WECHAT.getCode().equalsIgnoreCase(normalizedType)) {
            return buildProviderOptions(normalizedType, ServiceProvider.of(TencentWechatPayGateway.class).getExtensions());
        }
        return List.of();
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

    private Merchant requireMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new PaymentException("商户ID不能为空");
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        return merchant;
    }

    private MerchantChannel requireChannel(Long id) {
        MerchantChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new PaymentException("渠道配置不存在");
        }
        return channel;
    }

    private String encryptIfPresent(String value) {
        return StringUtils.hasText(value) ? encryptApiKey(value) : null;
    }

    private String normalizeExtConfig(String channelType, String extConfigText) {
        Map<String, Object> config = parseExtConfig(extConfigText);
        String providerSpi = firstText(
                config.get(AbstractMerchantPaymentChannel.EXT_PROVIDER_SPI),
                config.get("providerExtension"),
                config.get("spi"),
                paymentProviderProperties.resolveForChannelType(channelType));
        validateProviderSpi(channelType, providerSpi);
        config.put(AbstractMerchantPaymentChannel.EXT_PROVIDER_SPI, providerSpi);
        config.remove("providerExtension");
        config.remove("spi");
        return writeExtConfig(config);
    }

    private String mergeProviderSpi(String extConfigText, String providerSpi) {
        if (!StringUtils.hasText(providerSpi)) {
            return extConfigText;
        }
        Map<String, Object> config = parseExtConfig(extConfigText);
        config.put(AbstractMerchantPaymentChannel.EXT_PROVIDER_SPI, providerSpi);
        config.remove("providerExtension");
        config.remove("spi");
        return writeExtConfig(config);
    }

    private String buildDefaultChannelName(String channelType, String channelSubType) {
        String typeDesc = PaymentChannelType.descriptionOf(channelType);
        if (!StringUtils.hasText(typeDesc)) {
            typeDesc = channelType;
        }
        return StringUtils.hasText(channelSubType) ? typeDesc + "-" + channelSubType : typeDesc;
    }

    private String defaultOnboardingStatus(String channelType) {
        if (PaymentChannelType.WALLET.getCode().equalsIgnoreCase(channelType)
                || PaymentChannelType.COMPOSITE.getCode().equalsIgnoreCase(channelType)) {
            return OnboardingStatus.COMPLETED.getCode();
        }
        return OnboardingStatus.NOT_STARTED.getCode();
    }

    private String defaultGuideUrl(PaymentMethodGuideVO guide) {
        if (guide == null) {
            return null;
        }
        if (StringUtils.hasText(guide.getApplyUrl())) {
            return guide.getApplyUrl();
        }
        return guide.getOfficialUrl();
    }

    private PaymentMethodGuideVO findGuide(String channelType, String channelSubType) {
        return listCatalog().stream()
                .filter(item -> item.getChannelType().equalsIgnoreCase(channelType))
                .filter(item -> channelSubType == null || item.getChannelSubType().equalsIgnoreCase(channelSubType))
                .findFirst()
                .orElseGet(() -> listCatalog().stream()
                        .filter(item -> item.getChannelType().equalsIgnoreCase(channelType))
                        .findFirst()
                        .orElse(null));
    }

    private PaymentMethodGuideVO guide(String channelType,
                                       String channelSubType,
                                       String title,
                                       String officialName,
                                       String officialUrl,
                                       String applyUrl,
                                       String sandboxUrl,
                                       String summary,
                                       List<String> requiredMaterials,
                                       List<String> steps,
                                       List<String> tips) {
        PaymentMethodGuideVO vo = new PaymentMethodGuideVO();
        vo.setChannelType(channelType);
        vo.setChannelSubType(channelSubType);
        vo.setTitle(title);
        vo.setOfficialName(officialName);
        vo.setOfficialUrl(officialUrl);
        vo.setApplyUrl(applyUrl);
        vo.setSandboxUrl(sandboxUrl);
        vo.setSummary(summary);
        vo.setDefaultProviderSpi(paymentProviderProperties.resolveForChannelType(channelType));
        vo.setAvailableProviderSpis(listProviderOptions(channelType).stream()
                .map(ProviderSpiOptionVO::getExtensionName)
                .toList());
        vo.setRequiredMaterials(requiredMaterials);
        vo.setSteps(steps);
        vo.setTips(tips);
        return vo;
    }

    private MerchantChannelVO convertToVO(MerchantChannel channel, Merchant merchant, PaymentMethodGuideVO guide) {
        MerchantChannelVO vo = new MerchantChannelVO();
        BeanUtils.copyProperties(channel, vo);
        vo.setMerchantName(merchant != null ? merchant.getMerchantName() : null);
        vo.setApiKeyConfigured(StringUtils.hasText(channel.getApiKey()));
        vo.setPrivateKeyConfigured(StringUtils.hasText(channel.getPrivateKey()));
        vo.setPublicKeyConfigured(StringUtils.hasText(channel.getPublicKey()));
        vo.setCertConfigured(StringUtils.hasText(channel.getCertPath()));
        vo.setStatusDesc(ChannelStatus.descriptionOf(channel.getStatus()));
        vo.setOnboardingStatusDesc(OnboardingStatus.descriptionOf(channel.getOnboardingStatus()));
        vo.setProviderSpi(resolveProviderSpi(channel));
        if (guide != null) {
            vo.setGuideTitle(guide.getTitle());
            vo.setGuideUrl(defaultGuideUrl(guide));
        }
        return vo;
    }

    private List<ProviderSpiOptionVO> buildProviderOptions(String channelType, java.util.Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return List.of();
        }
        return extensions.stream()
                .sorted()
                .map(item -> {
                    ProviderSpiOptionVO vo = new ProviderSpiOptionVO();
                    vo.setChannelType(channelType);
                    vo.setExtensionName(item);
                    vo.setDefaultOption(paymentProviderProperties.resolveForChannelType(channelType).equalsIgnoreCase(item));
                    vo.setDescription(buildProviderDescription(channelType, item));
                    return vo;
                })
                .toList();
    }

    private String buildProviderDescription(String channelType, String extensionName) {
        if (PaymentChannelType.ALIPAY.getCode().equalsIgnoreCase(channelType)) {
            if ("default".equalsIgnoreCase(extensionName)) {
                return "支付宝默认 SPI 实现";
            }
            if ("alipay".equalsIgnoreCase(extensionName)) {
                return "支付宝官方网关实现";
            }
            if ("mock".equalsIgnoreCase(extensionName) || "alipay-mock".equalsIgnoreCase(extensionName)) {
                return "支付宝本地联调 mock 实现";
            }
        }
        if (PaymentChannelType.WECHAT.getCode().equalsIgnoreCase(channelType)) {
            if ("default".equalsIgnoreCase(extensionName)) {
                return "微信支付默认 SPI 实现";
            }
            if ("wechat-pay".equalsIgnoreCase(extensionName)) {
                return "微信支付官方网关实现";
            }
            if ("mock".equalsIgnoreCase(extensionName) || "wechat-mock".equalsIgnoreCase(extensionName)) {
                return "微信支付本地联调 mock 实现";
            }
        }
        return "支付 provider SPI 实现";
    }

    private String resolveProviderSpi(MerchantChannel channel) {
        if (channel == null || !StringUtils.hasText(channel.getExtConfig())) {
            return paymentProviderProperties.resolveForChannelType(channel.getChannelType());
        }
        try {
            var config = objectMapper.readValue(channel.getExtConfig(), new TypeReference<java.util.Map<String, Object>>() {
            });
            Object value = config.get(AbstractMerchantPaymentChannel.EXT_PROVIDER_SPI);
            if (value == null) {
                value = config.get("providerExtension");
            }
            if (value == null) {
                value = config.get("spi");
            }
            return value == null ? paymentProviderProperties.resolveForChannelType(channel.getChannelType()) : String.valueOf(value);
        } catch (Exception e) {
            return paymentProviderProperties.resolveForChannelType(channel.getChannelType());
        }
    }

    private void validateProviderSpi(String channelType, String providerSpi) {
        if (!StringUtils.hasText(channelType) || !StringUtils.hasText(providerSpi)) {
            return;
        }
        Set<String> available = listProviderOptions(channelType).stream()
                .map(ProviderSpiOptionVO::getExtensionName)
                .collect(java.util.stream.Collectors.toSet());
        if (!available.isEmpty() && !available.contains(providerSpi)) {
            throw new PaymentException("支付 provider SPI 不存在: " + providerSpi + ", channelType=" + channelType + ", 可选实现=" + available);
        }
    }

    private Map<String, Object> parseExtConfig(String extConfigText) {
        if (!StringUtils.hasText(extConfigText)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(extConfigText, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new PaymentException("支付方式扩展配置格式错误", e);
        }
    }

    private String writeExtConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new PaymentException("支付方式扩展配置序列化失败", e);
        }
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }
}
