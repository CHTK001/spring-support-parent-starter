package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.text.impl.DefaultTextCreator;
import com.google.code.kaptcha.util.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Random;

import static com.google.code.kaptcha.Constants.*;

/**
 * 校验码配置
 *
 * @author CH
 */
@ConditionalOnClass(name = {"com.google.code.kaptcha.impl.DefaultKaptcha"})
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfiguration {

    @Resource
    private CaptchaProperties captchaProperties;

    public DefaultKaptcha producer() {
        Properties properties = new Properties();
        properties.put(KAPTCHA_BORDER, captchaProperties.getBorder());
        properties.put(KAPTCHA_BORDER_COLOR, captchaProperties.getBorderColor());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_COLOR, captchaProperties.getText().getFont().getColor());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_NAMES, captchaProperties.getText().getFont().getNames());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_SIZE, captchaProperties.getText().getFont().getSize());
        properties.put(KAPTCHA_TEXTPRODUCER_CHAR_SPACE, captchaProperties.getText().getChars().getSpace());
        properties.put(KAPTCHA_TEXTPRODUCER_CHAR_STRING, captchaProperties.getText().getChars().getString());
        properties.put(KAPTCHA_NOISE_IMPL, captchaProperties.getNoise().getImpl());
        properties.put(KAPTCHA_NOISE_COLOR, captchaProperties.getNoise().getColor());
        properties.put(KAPTCHA_OBSCURIFICATOR_IMPL, captchaProperties.getObscurificator().getImpl());
        Config config = new Config(properties);
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }


    public DefaultKaptcha formula() {
        Properties properties = new Properties();
        properties.put(KAPTCHA_BORDER, captchaProperties.getBorder());
        properties.put(KAPTCHA_BORDER_COLOR, captchaProperties.getBorderColor());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_COLOR, captchaProperties.getText().getFont().getColor());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_NAMES, captchaProperties.getText().getFont().getNames());
        properties.put(KAPTCHA_TEXTPRODUCER_FONT_SIZE, captchaProperties.getText().getFont().getSize());
        properties.put(KAPTCHA_TEXTPRODUCER_CHAR_SPACE, captchaProperties.getText().getChars().getSpace());
        properties.put(KAPTCHA_TEXTPRODUCER_IMPL, CaptchaFormulaCreator.class.getTypeName());
        properties.put(KAPTCHA_NOISE_IMPL, captchaProperties.getNoise().getImpl());
        properties.put(KAPTCHA_NOISE_COLOR, captchaProperties.getNoise().getColor());
        properties.put(KAPTCHA_OBSCURIFICATOR_IMPL, captchaProperties.getObscurificator().getImpl());

        Config config = new Config(properties);
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }


    @Bean
    @ConditionalOnMissingBean
    public CaptchaProvider captchaProvider() {
        return new CaptchaProvider(producer(), formula());
    }

    /**
     * 公式
     */
    public static class CaptchaFormulaCreator extends DefaultTextCreator {
        private static final String[] CNUMBERS = "0,1,2,3,4,5,6,7,8,9,10".split(",");

        @Override
        public String getText() {
            int result = 0;
            Random random = new SecureRandom();
            int x = random.nextInt(10);
            int y = random.nextInt(10);
            StringBuilder suChinese = new StringBuilder();
            int randomoperands = (int) Math.round(Math.random() * 2);
            if (randomoperands == 0) {
                result = x * y;
                suChinese.append(CNUMBERS[x]);
                suChinese.append("*");
                suChinese.append(CNUMBERS[y]);
            } else if (randomoperands == 1) {
                if (!(x == 0) && y % x == 0) {
                    result = y / x;
                    suChinese.append(CNUMBERS[y]);
                    suChinese.append("/");
                    suChinese.append(CNUMBERS[x]);
                } else {
                    result = x + y;
                    suChinese.append(CNUMBERS[x]);
                    suChinese.append("+");
                    suChinese.append(CNUMBERS[y]);
                }
            } else if (randomoperands == 2) {
                if (x >= y) {
                    result = x - y;
                    suChinese.append(CNUMBERS[x]);
                    suChinese.append("-");
                    suChinese.append(CNUMBERS[y]);
                } else {
                    result = y - x;
                    suChinese.append(CNUMBERS[y]);
                    suChinese.append("-");
                    suChinese.append(CNUMBERS[x]);
                }
            } else {
                result = x + y;
                suChinese.append(CNUMBERS[x]);
                suChinese.append("+");
                suChinese.append(CNUMBERS[y]);
            }
            suChinese.append("=?@").append(result);
            return suChinese.toString();
        }
    }

}
