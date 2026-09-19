package com.chua.starter.common.support.utils;

import com.chua.common.support.utils.StringUtils;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 本地工具
 *
 * @author CH
 * @since 4.0.0
 */
public class LocaleUtils {

    /**
     * 区域工具。
     */
    private LocaleUtils() {
    }

 // 最终 确保不可变引用，静态初始化块完成一次性配置；resourcebundle消息源 本身线程安全
    static final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    static{
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.toString());
        messageSource.setBasename("i18n/message");
    }
    /**
     * 获取 国际化后内容信息
     *
     * @param code 国际化Key
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

    /**
     * 获取Locate
     *
     * @return 获取locate的结果
     */
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

