package com.chua.starter.pay.support.wechat;

import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 微信支付签名证书
 *
 * @author CH
 * @since 2024/12/31
 */
public class WechatPaySignatureCertificate {


    private final PayMerchantConfigWechat payMerchantConfigWechat;

    public WechatPaySignatureCertificate(PayMerchantConfigWechat payMerchantConfigWechat) {
        this.payMerchantConfigWechat = payMerchantConfigWechat;
    }

    /**
     * app生成带签名支付信息
     *
     * @param timestamp 时间戳
     * @param nonceStr  随机数
     * @param prepayId  预付单
     * @return 支付信息
     * @throws Exception
     */
    public String appPaySign(String timestamp, String nonceStr, String prepayId) throws Exception {
        //上传私钥
        PrivateKey privateKey = getPrivateKey();
        String signatureStr = Stream.of(payMerchantConfigWechat.getPayMerchantConfigWechatAppId(), timestamp, nonceStr, prepayId)
                .collect(Collectors.joining("\n", "", "\n"));
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sign.sign());
    }

    /**
     * 小程序及其它支付生成带签名支付信息
     *
     * @param timestamp 时间戳
     * @param nonceStr  随机数
     * @param prepayId  预付单
     * @return 支付信息
     * @throws Exception
     */
    public String jsApiPaySign(String timestamp, String nonceStr, String prepayId) throws Exception {
        //上传私钥
        PrivateKey privateKey = getPrivateKey();
        String signatureStr = Stream.of(payMerchantConfigWechat.getPayMerchantConfigWechatAppId(), timestamp, nonceStr, "prepay_id=" + prepayId)
                .collect(Collectors.joining("\n", "", "\n"));
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sign.sign());
    }


    /**
     * 获取私钥。
     * String filename 私钥文件路径  (required)
     *
     * @return 私钥对象
     */
    public PrivateKey getPrivateKey() throws IOException {
        String content = Files.readString(Paths.get(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath()));
        try {
            String privateKey = content.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的密钥格式");
        }
    }
}
