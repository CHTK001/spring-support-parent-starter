package com.chua.starter.common.support.provider;

import com.chua.starter.common.support.properties.CaptchaProperties;
import com.google.code.kaptcha.Constants;
import com.google.code.kaptcha.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.Random;

/**
 * 校验码
 *
 * @author CH
 */
@RestController
@RequestMapping("${plugin.captcha.context-path:}")
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CaptchaProvider {

    private final Producer producer;

    private final Producer formula;

    public CaptchaProvider( @Autowired(required = false)Producer producer,  @Autowired(required = false)Producer formula) {
        this.producer = producer;
        this.formula = formula;
    }

    /**
     * 校验码
     *
     * @param request  请求
     * @param response 响应
     */
    @RequestMapping("captcha.jpg")
    public void captcha(HttpServletRequest request, HttpServletResponse response) {
        ServletOutputStream out = null;
        try {
            HttpSession session = request.getSession();
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");
            response.setContentType("image/jpeg");
            String capStr = null;
            String code = null;
            BufferedImage bi = null;
            Random random = new SecureRandom();
            int nextInt = random.nextInt(2);

            if (nextInt < 1) {
                String capText = formula.createText();
                capStr = capText.substring(0, capText.lastIndexOf("@"));
                code = capText.substring(capText.lastIndexOf("@") + 1);
                bi = formula.createImage(capStr);
            } else {
                capStr = code = producer.createText();
                bi = producer.createImage(capStr);
            }
            session.setAttribute(Constants.KAPTCHA_SESSION_KEY, code);

            out = response.getOutputStream();
            ImageIO.write(bi, "png", out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
