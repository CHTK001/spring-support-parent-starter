package com.chua.starter.common.support.utils;

import com.chua.common.support.utils.StringUtils;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 本地Utils
 *
 * @author CH
 */
public class LocaleUtils {

    static ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    static{
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.toString());
        messageSource.setBasename("i18n/message");
    }
    /**
     * 获取 国际化后内容信息
     *
     * @param code 国际化key
     * @return 国际化后内容信息
     */
    public static String getMessage(String code) {
        Locale locale = getLocate();
        try {
            return messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            return "请联系管理员";
        }
    }

    public static Locale getLocate() {
        HttpServletRequest request = RequestUtils.getRequest();
        if(null == request) {
            return Locale.getDefault();
        }

        String language = request.getHeader("Accept-Language");

        if(StringUtils.isNotBlank(language)) {
            Locale locale = null;
            try {
                locale =Locale.forLanguageTag(language.split(",")[0]);
            } catch (Exception ignored) {
            }
            if(null != locale) {
                return locale;
            }
        }

        language = request.getHeader("accept-language");
        if(StringUtils.isNotBlank(language)) {
            Locale locale = null;
            try {
                locale = Locale.forLanguageTag(language);
            } catch (Exception ignored) {
            }
            if(null != locale) {
                return locale;
            }
        }

        language = request.getParameter("lang");
        if(StringUtils.isNotBlank(language)) {
            Locale locale = null;
            try {
                locale = Locale.forLanguageTag(language);
            } catch (Exception ignored) {
            }
            if(null != locale) {
                return locale;
            }
        }

        return Locale.getDefault();
    }
}
