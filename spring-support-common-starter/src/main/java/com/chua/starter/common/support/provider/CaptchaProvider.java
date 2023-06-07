package com.chua.starter.common.support.provider;

import com.chua.common.support.discovery.Constants;
import com.chua.starter.common.support.properties.CaptchaProperties;
import com.wf.captcha.base.Captcha;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 校验码
 *
 * @author CH
 */
@RestController
@RequestMapping("${plugin.captcha.context-path:}")
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CaptchaProvider {

    private final Captcha producer;


    public CaptchaProvider(@Autowired(required = false) Captcha producer) {
        this.producer = producer;
    }

    /**
     * 校验码
     *
     * @param request  请求
     * @param response 响应
     */
    @RequestMapping("captcha.jpg")
    public void captcha(HttpServletRequest request, HttpServletResponse response) {
        String captchaText = producer.text();
        String captchaBase64 = producer.toBase64("data:image/png;base64,");
        ServletOutputStream out = null;
        try {
            HttpSession session = request.getSession();
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");
            response.setContentType("image/jpeg");

            session.setAttribute(Constants.CAPTCHA_SESSION_KEY, captchaText);
            response.setHeader("verifyCodeKey", Base64.getEncoder().encodeToString(captchaText.getBytes(StandardCharsets.UTF_8)));
            out = response.getOutputStream();
            String substring = captchaBase64.substring("data:image/png;base64,".length());
            byte[] decode = Base64.getDecoder().decode(substring);
            out.write(decode);
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
    @RequestMapping("captcha")
    public CaptchaResult captchaBase64(HttpServletRequest request) {
        String captchaText = producer.text();
        String captchaBase64 = producer.toBase64("data:image/png;base64,");
        try {
            HttpSession session = request.getSession();
            session.setAttribute(Constants.CAPTCHA_SESSION_KEY, captchaText);
            return CaptchaResult.builder()
                    .verifyCodeKey(captchaText)
                    .verifyCodeBase64(captchaBase64)
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
    @Data
    public static class CaptchaResult {

        private String verifyCodeKey;

        private String verifyCodeBase64;

    }
}
