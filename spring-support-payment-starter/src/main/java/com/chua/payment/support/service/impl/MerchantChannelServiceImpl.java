package com.chua.payment.support.service.impl;

import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.configuration.PaymentCipherService;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.PaymentChannelType;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商户渠道服务实现
 */
@Service
@RequiredArgsConstructor
public class MerchantChannelServiceImpl implements MerchantChannelService {

    private final MerchantChannelMapper channelMapper;
    private final MerchantMapper merchantMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final ObjectMapper objectMapper;
    private final PaymentCipherService paymentCipherService;
    @Lazy
    private final PaymentChannelRegistry paymentChannelRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelVO createChannel(ChannelConfigDTO dto) {
        Merchant merchant = requireMerchant(dto.getMerchantId());
        MerchantChannel channel = new MerchantChannel();
        BeanUtils.copyProperties(dto, channel);
        channel.setExtConfig(normalizeExtConfig(dto.getExtConfig()));
        channel.setChannelName(StringUtils.hasText(dto.getChannelName()) ? dto.getChannelName() : buildDefaultChannelName(dto.getChannelType(), dto.getChannelSubType()));
        channel.setApiKey(encryptIfPresent(dto.getApiKey()));
        channel.setPrivateKey(encryptIfPresent(dto.getPrivateKey()));
        channel.setStatus(dto.getStatus() != null ? dto.getStatus() : ChannelStatus.DISABLED.getCode());
        channel.setSandboxMode(dto.getSandboxMode() != null ? dto.getSandboxMode() : 0);
        PaymentMethodGuideVO guide = findGuide(dto.getChannelType(), dto.getChannelSubType());
        validateEnabledChannelSupport(channel);
        channelMapper.insert(channel);
        return convertToVO(channel, merchant, guide);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelVO updateChannel(Long id, ChannelConfigDTO dto) {
        MerchantChannel channel = requireChannel(id);
        Merchant merchant = requireMerchant(channel.getMerchantId());
        BeanUtils.copyProperties(dto, channel, "apiKey", "privateKey");
        channel.setExtConfig(normalizeExtConfig(channel.getExtConfig()));
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
        PaymentMethodGuideVO guide = findGuide(channel.getChannelType(), channel.getChannelSubType());
        validateEnabledChannelSupport(channel);
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
        validateEnabledChannelSupport(channel);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteChannel(Long id) {
        MerchantChannel channel = requireChannel(id);
        if (Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            throw new PaymentException("启用中的支付方式不允许删除，请先禁用");
        }

        Long relatedOrderCount = paymentOrderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getChannelId, id)
                .ne(PaymentOrder::getDeleted, 1));
        if (relatedOrderCount != null && relatedOrderCount > 0) {
            throw new PaymentException("该支付方式已关联订单，不允许删除");
        }
        return channelMapper.deleteById(id) > 0;
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

        list.add(guide("WALLET", "BALANCE", "站内钱包配置", "平台内部钱包", null, null, null,
                "用于账户余额支付、退款退回余额。",
                List.of("钱包开关", "充值策略", "风控规则"),
                List.of("启用钱包", "配置余额扣减和退款规则", "配置对账策略"),
                List.of("钱包能力不依赖第三方开户，但对账责任在平台自身")));
        list.add(guide("EPAY", "JSAPI", "易支付微信 JSAPI", "易支付", "https://www.ezfpy.cn/", "https://www.ezfpy.cn/", null,
                "通过易支付网关承接微信 JSAPI 场景，系统自动路由到易支付 SPI。",
                List.of("商户号", "应用 ID", "API 密钥", "支付回调地址"),
                List.of("开通易支付商户", "配置微信通道参数", "录入系统并联调回调"),
                List.of("前台无需再选择 SPI，后端自动处理")));
        list.add(guide("EPAY", "MINI_PROGRAM", "易支付微信小程序", "易支付", "https://www.ezfpy.cn/", "https://www.ezfpy.cn/", null,
                "适用于通过易支付通道发起微信小程序支付。",
                List.of("商户号", "小程序 AppID", "API 密钥", "支付回调地址"),
                List.of("开通易支付商户", "绑定小程序参数", "联调支付与回调"),
                List.of("回调默认复用支付全局配置或商户默认配置")));
        list.add(guide("EPAY", "H5", "易支付 H5", "易支付", "https://www.ezfpy.cn/", "https://www.ezfpy.cn/", null,
                "适用于浏览器和移动端 H5 拉起支付。",
                List.of("商户号", "API 密钥", "支付回调地址", "返回地址"),
                List.of("开通易支付商户", "配置 H5 通道", "联调浏览器回跳和异步回调"),
                List.of("建议同时配置统一回跳地址")));

        list.add(guide("UNIONPAY", "APP", "云闪付APP支付", "中国银联", "https://open.unionpay.com", "https://open.unionpay.com/tjweb/acproduct/list", "https://open.unionpay.com/tjweb/acproduct/sandbox",
                "适用于APP内拉起云闪付客户端完成支付。",
                List.of("AppId", "AppKey", "商户号", "回调地址", "返回地址"),
                List.of("注册银联开放平台账号", "创建应用获取AppId", "签约商户号", "配置回调地址", "下载SDK集成"),
                List.of("支持沙箱环境测试", "需要配置商户私钥和银联公钥")));

        list.add(guide("UNIONPAY", "H5", "云闪付H5支付", "中国银联", "https://open.unionpay.com", "https://open.unionpay.com/tjweb/acproduct/list", "https://open.unionpay.com/tjweb/acproduct/sandbox",
                "适用于手机浏览器内跳转云闪付完成支付。",
                List.of("AppId", "AppKey", "商户号", "回调地址", "返回地址"),
                List.of("注册银联开放平台账号", "创建应用获取AppId", "签约商户号", "配置回调地址"),
                List.of("支持沙箱环境测试", "需要配置商户私钥和银联公钥")));

        return list;
    }

    @Override
    public String encryptApiKey(String apiKey) {
        return paymentCipherService.encrypt(apiKey);
    }

    @Override
    public String decryptApiKey(String encryptedKey) {
        return paymentCipherService.decrypt(encryptedKey);
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

    private void validateEnabledChannelSupport(MerchantChannel channel) {
        if (channel == null || !Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            return;
        }
        if (!paymentChannelRegistry.supports(channel.getChannelType(), channel.getChannelSubType())) {
            throw new PaymentException("当前版本未提供可启用的支付渠道实现: "
                    + channel.getChannelType()
                    + (StringUtils.hasText(channel.getChannelSubType()) ? "/" + channel.getChannelSubType() : ""));
        }
        validateRequiredConfig(channel);
    }

    private void validateRequiredConfig(MerchantChannel channel) {
        String channelType = upper(channel.getChannelType());
        String channelSubType = upper(channel.getChannelSubType());
        if ("WECHAT".equals(channelType)) {
            requireText(channel.getAppId(), "微信 AppId 不能为空");
            requireText(channel.getMerchantNo(), "微信商户号不能为空");
            requireText(channel.getPrivateKey(), "微信商户私钥不能为空");
            requireText(channel.getApiKey(), "微信 APIv3 Key 不能为空");
            requireText(extText(parseExtConfig(channel.getExtConfig()), "merchantSerialNumber"),
                    "微信商户证书序列号不能为空，请在 extConfig 中提供 merchantSerialNumber");
            return;
        }
        if ("ALIPAY".equals(channelType)) {
            requireText(channel.getAppId(), "支付宝应用ID不能为空");
            requireText(channel.getPrivateKey(), "支付宝私钥不能为空");
            requireText(channel.getPublicKey(), "支付宝公钥不能为空");
            return;
        }
        if ("EPAY".equals(channelType)) {
            requireText(channel.getMerchantNo(), "易支付商户号不能为空");
            requireText(channel.getApiKey(), "易支付 API 密钥不能为空");
            return;
        }
        if ("COMPOSITE".equals(channelType) && "AGGREGATE_ROUTE".equals(channelSubType)) {
            Long targetChannelId = resolveRouteChannelId(channel.getExtConfig());
            if (targetChannelId == null) {
                throw new PaymentException("直营网关配置必须在 extConfig 中提供 targetChannelId 或 defaultChannelId");
            }
            if (channel.getId() != null && channel.getId().equals(targetChannelId)) {
                throw new PaymentException("直营网关配置不能指向自身");
            }
            MerchantChannel targetChannel = channelMapper.selectById(targetChannelId);
            if (targetChannel == null) {
                throw new PaymentException("直营网关目标渠道不存在: " + targetChannelId);
            }
            if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
                throw new PaymentException("直营网关配置不能嵌套指向 COMPOSITE 渠道");
            }
            if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
                throw new PaymentException("直营网关目标渠道当前版本不可执行: "
                        + targetChannel.getChannelType()
                        + "/" + targetChannel.getChannelSubType());
            }
        }
    }

    private String normalizeExtConfig(String extConfigText) {
        return writeExtConfig(parseExtConfig(extConfigText));
    }

    private String buildDefaultChannelName(String channelType, String channelSubType) {
        String typeDesc = PaymentChannelType.descriptionOf(channelType);
        if (!StringUtils.hasText(typeDesc)) {
            typeDesc = channelType;
        }
        return StringUtils.hasText(channelSubType) ? typeDesc + "-" + channelSubType : typeDesc;
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
        vo.setChannelTypeDesc(PaymentChannelType.descriptionOf(channel.getChannelType()));
        vo.setEffectiveNotifyUrl(firstText(channel.getNotifyUrl(), merchant != null ? merchant.getDefaultNotifyUrl() : null));
        vo.setEffectiveReturnUrl(firstText(channel.getReturnUrl(), merchant != null ? merchant.getDefaultReturnUrl() : null));
        if (guide != null) {
            vo.setGuideTitle(guide.getTitle());
            vo.setGuideUrl(defaultGuideUrl(guide));
        }
        return vo;
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

    private Long resolveRouteChannelId(String extConfigText) {
        Map<String, Object> extConfig = parseExtConfig(extConfigText);
        return parseLong(extConfig.get("targetChannelId"), extConfig.get("defaultChannelId"));
    }

    private Long parseLong(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                    throw new PaymentException("渠道扩展配置中的路由 channelId 格式错误: " + text);
                }
            }
        }
        return null;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String extText(Map<String, Object> extConfig, String key) {
        Object value = extConfig.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(message);
        }
        return value;
    }

    @Override
    public MerchantChannel getWalletChannel(Long merchantId) {
        if (merchantId == null) {
            throw new PaymentException("商户ID不能为空");
        }
        MerchantChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getMerchantId, merchantId)
                .eq(MerchantChannel::getChannelType, "WALLET")
                .eq(MerchantChannel::getChannelSubType, "BALANCE")
                .eq(MerchantChannel::getStatus, 1)
                .last("limit 1"));
        if (channel == null) {
            throw new PaymentException("商户未开通钱包渠道");
        }
        return channel;
    }
}
