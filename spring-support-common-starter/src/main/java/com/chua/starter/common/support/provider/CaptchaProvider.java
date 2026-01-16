package com.chua.starter.common.support.provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.constant.Constants;
import com.chua.common.support.utils.RandomUtils;
import com.chua.starter.common.support.constant.CaptchaTypeEnum;
import com.chua.starter.common.support.properties.CaptchaProperties;
import com.wf.captcha.*;
import com.wf.captcha.base.Captcha;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 校验码
 *
 * @author CH
 */
@RestController
@Tag(name = "校验码")
@Ignore
@RequestMapping("${plugin.captcha.context-path:/v1/}")
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
public class CaptchaProvider {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptchaProvider.class);


    @Autowired
    private CaptchaProperties captchaProperties;

    public Captcha producer() {
        return createCaptcha(captchaProperties.getType(), captchaProperties.getLength());
    }

    public Captcha producer(CaptchaTypeEnum typeEnum) {
        return createCaptcha(typeEnum, captchaProperties.getLength());
    }

    public Captcha producer(CaptchaTypeEnum typeEnum, int length) {
        return createCaptcha(typeEnum, length);
    }

    private Captcha createCaptcha(CaptchaTypeEnum type, int length) {
        Captcha captcha;
        int width = captchaProperties.getWidth();
        int height = captchaProperties.getHeight();
        String fontName = captchaProperties.getFontName();

        switch (type) {
            case ARITHMETIC:
                captcha = new ArithmeticCaptcha(width, height);
                //固定设置为两位，图片为算数运算表达式
                captcha.setLen(length);
                break;
            case CHINESE:
                captcha = new ChineseCaptcha(width, height);
                captcha.setLen(length);
                break;
            case CHINESE_GIF:
                captcha = new ChineseGifCaptcha(width, height);
                captcha.setLen(length);
                break;
            case GIF:
                captcha = new GifCaptcha(width, height);
                captcha.setLen(length);
                break;
            case SPEC:
                captcha = new SpecCaptcha(width, height);
                captcha.setCharType(Captcha.TYPE_DEFAULT);
                captcha.setLen(length);
                break;
            default:
                throw new RuntimeException("验证码配置信息错误！正确配置查看 CaptchaTypeEnum ");
        }
        try {
            //captcha.setFont(Captcha.FONT_1, captchaProperties.getFontSize());
        } catch (Exception ignored) {
        }

        return captcha;
    }


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
            randomInt = RandomUtils.randomInt(6, 7);
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

            session.setAttribute(Constants.CAPTCHA_SESSION_KEY, captchaText);
            response.setHeader("Access-Control-Captcha", Base64.getEncoder().encodeToString(captchaText.getBytes(StandardCharsets.UTF_8)));
            out = response.getOutputStream();
            captcha.out(out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 校验码
     *
     * @param request 请求
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
            session.setAttribute(Constants.CAPTCHA_SESSION_KEY, captchaText);
            response.addHeader("Access-Control-Captcha", Base64.getEncoder().encodeToString(captchaText.getBytes(StandardCharsets.UTF_8)));
            return CaptchaResult.builder()
                    .verifyCodeBase64(captchaBase64)
                    .verifyCodeKey(captchaText)
                    .build();
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
    @Builder
        public static class CaptchaResult {

        private String verifyCodeKey;

        private String verifyCodeBase64;

    }
    /**
     * 获取 captchaProperties
     *
     * @return captchaProperties
     */
    public CaptchaProperties getCaptchaProperties() {
        return captchaProperties;
    }

    /**
     * 设置 captchaProperties
     *
     * @param captchaProperties captchaProperties
     */
    public void setCaptchaProperties(CaptchaProperties captchaProperties) {
        this.captchaProperties = captchaProperties;
    }

    /**
     * 获取 captcha
     *
     * @return captcha
     */
    public Captcha getCaptcha() {
        return captcha;
    }

    /**
     * 设置 captcha
     *
     * @param captcha captcha
     */
    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }

    /**
     * 获取 width
     *
     * @return width
     */
    public int getWidth() {
        return width;
    }

    /**
     * 设置 width
     *
     * @param width width
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * 获取 height
     *
     * @return height
     */
    public int getHeight() {
        return height;
    }

    /**
     * 设置 height
     *
     * @param height height
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * 获取 fontName
     *
     * @return fontName
     */
    public String getFontName() {
        return fontName;
    }

    /**
     * 设置 fontName
     *
     * @param fontName fontName
     */
    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

            /**
     * 获取 randomInt
     *
     * @return randomInt
     */
    public int getRandomInt() {
        return randomInt;
    }

    /**
     * 设置 randomInt
     *
     * @param randomInt randomInt
     */
    public void setRandomInt(int randomInt) {
        this.randomInt = randomInt;
    }

    /**
     * 获取 typeEnum
     *
     * @return typeEnum
     */
    public CaptchaTypeEnum getTypeEnum() {
        return typeEnum;
    }

    /**
     * 设置 typeEnum
     *
     * @param typeEnum typeEnum
     */
    public void setTypeEnum(CaptchaTypeEnum typeEnum) {
        this.typeEnum = typeEnum;
    }

    /**
     * 获取 captchaTypeEnum
     *
     * @return captchaTypeEnum
     */
    public CaptchaTypeEnum getCaptchaTypeEnum() {
        return captchaTypeEnum;
    }

    /**
     * 设置 captchaTypeEnum
     *
     * @param captchaTypeEnum captchaTypeEnum
     */
    public void setCaptchaTypeEnum(CaptchaTypeEnum captchaTypeEnum) {
        this.captchaTypeEnum = captchaTypeEnum;
    }

    /**
     * 获取 captcha
     *
     * @return captcha
     */
    public Captcha getCaptcha() {
        return captcha;
    }

    /**
     * 设置 captcha
     *
     * @param captcha captcha
     */
    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }

    /**
     * 获取 captchaText
     *
     * @return captchaText
     */
    public String getCaptchaText() {
        return captchaText;
    }

    /**
     * 设置 captchaText
     *
     * @param captchaText captchaText
     */
    public void setCaptchaText(String captchaText) {
        this.captchaText = captchaText;
    }

    /**
     * 获取 out
     *
     * @return out
     */
    public ServletOutputStream getOut() {
        return out;
    }

    /**
     * 设置 out
     *
     * @param out out
     */
    public void setOut(ServletOutputStream out) {
        this.out = out;
    }

    /**
     * 获取 session
     *
     * @return session
     */
    public HttpSession getSession() {
        return session;
    }

    /**
     * 设置 session
     *
     * @param session session
     */
    public void setSession(HttpSession session) {
        this.session = session;
    }

    /**
     * 获取 captcha
     *
     * @return captcha
     */
    public Captcha getCaptcha() {
        return captcha;
    }

    /**
     * 设置 captcha
     *
     * @param captcha captcha
     */
    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }

    /**
     * 获取 captchaText
     *
     * @return captchaText
     */
    public String getCaptchaText() {
        return captchaText;
    }

    /**
     * 设置 captchaText
     *
     * @param captchaText captchaText
     */
    public void setCaptchaText(String captchaText) {
        this.captchaText = captchaText;
    }

    /**
     * 获取 captchaBase64
     *
     * @return captchaBase64
     */
    public String getCaptchaBase64() {
        return captchaBase64;
    }

    /**
     * 设置 captchaBase64
     *
     * @param captchaBase64 captchaBase64
     */
    public void setCaptchaBase64(String captchaBase64) {
        this.captchaBase64 = captchaBase64;
    }

    /**
     * 获取 session
     *
     * @return session
     */
    public HttpSession getSession() {
        return session;
    }

    /**
     * 设置 session
     *
     * @param session session
     */
    public void setSession(HttpSession session) {
        this.session = session;
    }

    /**
     * 获取 e
     *
     * @return e
     */
    public throw getE() {
        return e;
    }

    /**
     * 设置 e
     *
     * @param e e
     */
    public void setE(throw e) {
        this.e = e;
    }

    /**
     * 获取 verifyCodeKey
     *
     * @return verifyCodeKey
     */
    public String getVerifyCodeKey() {
        return verifyCodeKey;
    }

    /**
     * 设置 verifyCodeKey
     *
     * @param verifyCodeKey verifyCodeKey
     */
    public void setVerifyCodeKey(String verifyCodeKey) {
        this.verifyCodeKey = verifyCodeKey;
    }

    /**
     * 获取 verifyCodeBase64
     *
     * @return verifyCodeBase64
     */
    public String getVerifyCodeBase64() {
        return verifyCodeBase64;
    }

    /**
     * 设置 verifyCodeBase64
     *
     * @param verifyCodeBase64 verifyCodeBase64
     */
    public void setVerifyCodeBase64(String verifyCodeBase64) {
        this.verifyCodeBase64 = verifyCodeBase64;
    }


}

