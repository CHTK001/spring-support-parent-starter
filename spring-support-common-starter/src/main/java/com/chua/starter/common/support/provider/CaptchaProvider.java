package com.chua.starter.common.support.provider;
import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.common.support.spi.ServiceProvider;
import com.wf.captcha.base.Captcha;
import com.chua.common.support.constant.CaptchaConstant;
import com.chua.common.support.annotation.Ignore;
import com.chua.common.support.utils.RandomUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* 校验码
*
* @author CH
* @since 4.0.0
 */
@RestController
@Tag(name = "校验码")
@Ignore
@RequestMapping("${plugin.captcha.context-path:/v1/}")
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
public class CaptchaProvider {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(CaptchaProvider.class);
        @Autowired
    /** Captcha属性 */
    private CaptchaProperties captchaProperties;

    /**
    * Producer
    *
    * @return producer的结果
    */
    public Captcha producer() {
        return createCaptcha(captchaProperties.getType(), captchaProperties.getLength());
    }

    /**
    * Producer
    *
    * @param typeEnum 类型enum
    * @return producer的结果
    */
    public Captcha producer(CaptchaTypeEnum typeEnum) {
        return createCaptcha(typeEnum, captchaProperties.getLength());
    }

    /**
    * Producer
    *
    * @param typeEnum 类型enum
    * @param length 长度
    * @return producer的结果
    */
    public Captcha producer(CaptchaTypeEnum typeEnum, int length) {
        return createCaptcha(typeEnum, length);
    }

    /**
    * 创建Captcha
    *
    * @param type 类型
    * @param length 长度
    * @return 创建captcha的结果
    */
    private Captcha createCaptcha(CaptchaTypeEnum type, int length) {
        int width = captchaProperties.getWidth();
        int height = captchaProperties.getHeight();
        String spiName = type.name().toLowerCase();
        CaptchaGenerator generator = ServiceProvider.of(CaptchaGenerator.class).getExtension(spiName);
        if (generator == null) {
            /**
            * runtime异常。
            * @param CaptchaTypeEnum captcha类型enum
            * @param spiName spi名称
            * @return runtime异常的结果
            */
            throw new RuntimeException("验证码配置信息错误！正确配置查看 CaptchaTypeEnum , 不支持的类型: " + spiName);
        }
        return generator.create(width, height, length);
    }


    /**
    * 创建Captcha
    *
    * @return 创建captcha的结果
    */
    public Captcha createCaptcha() {
        int randomInt = RandomUtils.randomInt(2, 3);
        CaptchaTypeEnum typeEnum = captchaProperties.getType();
        CaptchaTypeEnum captchaTypeEnum = typeEnum;
        if(typeEnum == CaptchaTypeEnum.RANDOM) {
            typeEnum = RandomUtils.randomEnum(CaptchaTypeEnum.class);
        }
        if (captchaTypeEnum == CaptchaTypeEnum.CHINESE || captchaTypeEnum == CaptchaTypeEnum.CHINESE_GIF) {
            randomInt = RandomUtils.randomInt(4, 5);
        } else if (captchaTypeEnum != CaptchaTypeEnum.ARITHMETIC) {
            // 缩短验证码长度为 4 位，避免前端输入框显示不全
            randomInt = 4;
        }
        return producer(captchaTypeEnum, randomInt);
    }
    /**
    * 校验码
    *
    * @param request  请求
    * @param response 响应
    */
    @Operation(summary = "获取验证码")
    @GetMapping("captcha.jpg")
    public void captcha(HttpServletRequest request, HttpServletResponse response) {
        Captcha captcha = createCaptcha();
        String captchaText = captcha.text();
        ServletOutputStream out = null;

        try {
            HttpSession session = request.getSession();
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");
            response.setContentType("image/jpeg");

            session.setAttribute(CaptchaConstant.CAPTCHA_SESSION_KEY, captchaText);
            response.setHeader("Access-Control-Captcha", Base64.getEncoder().encodeToString(captchaText.getBytes(StandardCharsets.UTF_8)));
            out = response.getOutputStream();
            captcha.out(out);
            out.flush();

        } catch (Exception e) {
            log.error("生成验证码失败", e);
        }
    }

    /**
    * 校验码
    *
    * @param request 请求
    * @param response 响应
    * @return captchaBase64的结果
    */
    @Operation(summary = "获取验证码")
    @GetMapping("captcha")
    public CaptchaResult captchaBase64(HttpServletRequest request, HttpServletResponse response) {
        Captcha captcha = createCaptcha();
        String captchaText = captcha.text();
        log.info("当前校验码 {}", captchaText);
        String captchaBase64 = captcha.toBase64();
        try {
            HttpSession session = request.getSession();
            session.setAttribute(CaptchaConstant.CAPTCHA_SESSION_KEY, captchaText);
            response.addHeader("Access-Control-Captcha", Base64.getEncoder().encodeToString(captchaText.getBytes(StandardCharsets.UTF_8)));
            CaptchaResult captchaResult = new CaptchaResult();
            captchaResult.setVerifyCodeBase64(captchaBase64);
            captchaResult.setVerifyCodeKey(captchaText);
            return captchaResult;
        } catch (Exception e) {
            throw e;
        }
    }


    /**
    * 验证码响应对象
    *
    * @author haoxr
    * @since 2023/03/24
    */
    public static class CaptchaResult {
        /** 验证代码密钥 */
        private String verifyCodeKey;
        /** 验证代码基础64 */
        private String verifyCodeBase64;

        /**
        * 获取验证编码钥匙
        *
        * @return 获取验证编码钥匙的结果
        */
        public String getVerifyCodeKey() {
            return verifyCodeKey;
        }

        /**
        * 设置验证编码钥匙
        *
        * @param verifyCodeKey 验证编码钥匙
        */
        public void setVerifyCodeKey(String verifyCodeKey) {
            this.verifyCodeKey = verifyCodeKey;
        }

        /**
        * 获取验证编码基础
        *
        * @return 获取验证编码base64的结果
        */
        public String getVerifyCodeBase64() {
            return verifyCodeBase64;
        }

        /**
        * 设置验证编码基础
        *
        * @param verifyCodeBase64 验证编码基础64
        */
        public void setVerifyCodeBase64(String verifyCodeBase64) {
            this.verifyCodeBase64 = verifyCodeBase64;
        }
    }
    /**
    * 获取 captcha属性
    *
    * @return captchaProperties
    */
    public CaptchaProperties getCaptchaProperties() {
        return captchaProperties;
    }

    /**
    * 设置 captcha属性
    *
    * @param captchaProperties captcha属性
    */
    public void setCaptchaProperties(CaptchaProperties captchaProperties) {
        this.captchaProperties = captchaProperties;
    }
}
