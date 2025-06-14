/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.common.common.validator;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HtmlUtil;
import cn.keepbx.jpom.model.JsonMessage;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.utils.MapUtils;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.common.interceptor.HandlerMethodInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * 参数拦截器  验证参数是否正确  排序号是：-100
 * <p>
 * 配置方法
 *
 * @author bwcx_jzy
 * @since 2018/8/21.
 */
@Slf4j
@Configuration
public class ParameterInterceptor implements HandlerMethodInterceptor {
    /**
     * int 类型的数字输入最大长度  防止数据库字段溢出
     */
    public static int INT_MAX_LENGTH = 7;
    private final Interceptor interceptor = new DefaultInterceptor();

    /**
     * 获取值
     *
     * @param validatorConfig 验证规则
     * @param request         req
     * @param name            name
     * @param item            item
     * @param bodyParameters
     * @return val
     */
    private String getValue(ValidatorConfig validatorConfig, HttpServletRequest request, String name, MethodParameter item, Map<String, Object> bodyParameters) {
        // 获取值
        String value;
        // 指定name
        String configName = null;
        if (validatorConfig != null) {
            configName = validatorConfig.name();
        }
        if (StrUtil.isNotEmpty(configName)) {
            value = request.getParameter(configName);
        } else {
            value = request.getParameter(name);
        }

        if (null == value) {
            if (StrUtil.isNotEmpty(configName)) {
                value = Converter.convertIfNecessary(MapUtils.getObject(bodyParameters, configName), String.class);
            } else {
                value = Converter.convertIfNecessary(MapUtils.getObject(bodyParameters, name), String.class);
            }
        }
        // 默认值
        if (validatorConfig != null && !ValueConstants.DEFAULT_NONE.equals(validatorConfig.defaultVal())) {
            if (value == null && !validatorConfig.strEmpty()) {
                value = validatorConfig.defaultVal();
            }
            if (StrUtil.isEmpty(value) && validatorConfig.strEmpty()) {
                value = validatorConfig.defaultVal();
            }
        }
        return value;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
        String language = JakartaServletUtil.getHeader(request, Header.ACCEPT_LANGUAGE.getValue(), CharsetUtil.CHARSET_UTF_8);
        I18nMessageUtil.setLanguage(language);
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
//        String body = IoUtils.toString(request.getInputStream(), CharsetUtil.CHARSET_UTF_8);
//        Map<String, Object> bodyParameters = new HashMap<>();
//        if (null != body && body.startsWith("{") && body.endsWith("}")) {
//            try {
//                bodyParameters = Json.getJsonObject(body);
//            } catch (Exception ignored) {
//            }
//        }
        for (MethodParameter item : methodParameters) {
            ValidatorItem[] validatorItems;
            ValidatorConfig validatorConfig = item.getParameterAnnotation(ValidatorConfig.class);
            if (validatorConfig == null) {
                ValidatorItem validatorItem = item.getParameterAnnotation(ValidatorItem.class);
                if (validatorItem == null) {
                    continue;
                } else {
                    validatorItems = new ValidatorItem[]{validatorItem};
                }
            } else {
                validatorItems = validatorConfig.value();
            }
            String name = item.getParameterName();
            if (name == null) {
                continue;
            }
            String value = getValue(validatorConfig, request, name, item, Collections.emptyMap());
            // 验证每一项
            int errorCount = 0;
            for (int i = 0, len = validatorItems.length; i < len; i++) {
                ValidatorItem validatorItem = validatorItems[i];
                if (validatorItem.unescape()) {
                    value = HtmlUtil.unescape(value);
                }
                if (validatorConfig != null && validatorItem.value() == ValidatorRule.CUSTOMIZE) {
                    if (!customize(handlerMethod, item, validatorConfig, validatorItem, name, value, request, response)) {
                        return false;
                    }
                    // 自定义条件只识别一次
                    break;
                }
                boolean error = validator(validatorItem, value);
                if (validatorConfig == null) {
                    if (!error) {
                        //错误
                        interceptor.error(request, response, name, value, validatorItem);
                        return false;
                    }
                } else {
                    if (validatorConfig.errorCondition() == ErrorCondition.AND) {
                        if (!error) {
                            //错误
                            interceptor.error(request, response, name, value, validatorItem);
                            return false;
                        }
                    }
                    if (validatorConfig.errorCondition() == ErrorCondition.OR) {
                        if (error) {
                            break;
                        } else {
                            errorCount++;
                            if (i < len - 1) {
                                continue;
                            }
                            // 最后一项
                            if (i == len - 1 && errorCount == len) {
                                //错误
                                interceptor.error(request, response, name, value, validatorItem);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * 自定义参数效验
     *
     * @param handlerMethod   method
     * @param validatorConfig config
     * @param validatorItem   效验规则
     * @param methodParameter 参数对象
     * @param name            参数名
     * @param value           值
     * @return true 通过效验
     * @throws InvocationTargetException 反射异常
     * @throws IllegalAccessException    反射异常
     */
    private boolean customize(HandlerMethod handlerMethod, MethodParameter methodParameter, ValidatorConfig validatorConfig, ValidatorItem validatorItem, String name, String value,
                              HttpServletRequest request, HttpServletResponse response
    ) throws InvocationTargetException, IllegalAccessException {
        // 自定义验证
        Method method;
        try {
            method = ReflectUtil.getMethod(handlerMethod.getBeanType(), validatorConfig.customizeMethod(), MethodParameter.class, String.class);
        } catch (SecurityException s) {
            // 没有权限访问 直接拦截
            log.error(s.getMessage(), s);
            interceptor.error(request, response, name, value, validatorItem);
            return false;
        }
        if (method == null) {
            // 没有配置对应方法
            log.error(I18nMessageUtil.get("i18n.verification_method_not_configured.7358"), handlerMethod.getBeanType(), validatorConfig.customizeMethod());
            interceptor.error(request, response, name, value, validatorItem);
            return false;
        }
        Object obj = method.invoke(handlerMethod.getBean(), methodParameter, value);
        if (!Convert.toBool(obj, false)) {
            interceptor.error(request, response, name, value, validatorItem);
            return false;
        }
        return true;
    }

    /**
     * 获取长度范围
     *
     * @param range 范围
     * @return int数组
     */
    private int[] spiltRange(String range) {
        if (StrUtil.isEmpty(range)) {
            return null;
        }
        if (range.contains(StrUtil.COLON)) {
            // 范围
            String[] ranges = StrUtil.splitToArray(range, StrUtil.COLON);
            if (ranges != null && ranges.length == 2) {
                int start = Convert.toInt(ranges[0]);
                int end = Convert.toInt(ranges[1]);
                return new int[]{start, end};
            }
        } else {

            // 具体某个值
            int len = Convert.toInt(range);
            return new int[]{len};
        }
        return null;
    }

    /**
     * 拆分验证范围
     *
     * @param range 范围字符串
     * @return 数组
     */
    private Double[] spiltRangeDouble(String range) {
        if (StrUtil.isEmpty(range)) {
            return null;
        }
        Double[] doubles = new Double[3];
        if (range.contains(StrUtil.BRACKET_START) && range.endsWith(StrUtil.BRACKET_END)) {
            int start = range.indexOf(StrUtil.BRACKET_START);
            int end = range.indexOf(StrUtil.BRACKET_END);
            int len = Convert.toInt(range.substring(start + 1, end));
            doubles[2] = (double) len;
            range = range.substring(0, start);
        }
        if (range.contains(StrUtil.COLON)) {
            String[] ranges = StrUtil.splitToArray(range, StrUtil.COLON);
            if (ranges != null && ranges.length == 2) {
                doubles[0] = Convert.toDouble(ranges[0]);
                doubles[1] = Convert.toDouble(ranges[1]);
            }
        } else {
            doubles[0] = Convert.toDouble(range);
        }
        return doubles;
    }

    private boolean validator(final ValidatorItem validatorItem, String value) {
        ValidatorRule validatorRule = validatorItem.value();
        switch (validatorRule) {
            case EMPTY:
                if (Validator.isNotEmpty(value)) {
                    return false;
                }
                break;
            case NOT_EMPTY:
            case NOT_BLANK: {
                if (validatorRule == ValidatorRule.NOT_EMPTY) {
                    if (Validator.isEmpty(value)) {
                        return false;
                    }
                } else {
                    if (StrUtil.isBlank(value)) {
                        return false;
                    }
                }
                if (value == null) {
                    return false;
                }
                int valLen = value.length();
                int[] ranges = spiltRange(validatorItem.range());
                if (ranges != null) {
                    if (ranges.length == 1) {
                        if (ranges[0] != valLen) {
                            return false;
                        }
                    } else {
                        if (valLen < ranges[0] || valLen > ranges[1]) {
                            return false;
                        }
                    }
                }
            }
            break;
            case GENERAL: {
                int[] ranges = spiltRange(validatorItem.range());
                if (ranges == null) {
                    if (!Validator.isGeneral(value)) {
                        return false;
                    }
                } else if (ranges.length == 1) {
                    if (!Validator.isGeneral(value, ranges[0])) {
                        return false;
                    }
                } else {
                    if (!Validator.isGeneral(value, ranges[0], ranges[1])) {
                        return false;
                    }
                }
            }
            break;
            case DECIMAL:
            case NUMBERS:
                if (!validatorNumber(validatorItem, value)) {
                    return false;
                }
                break;
            case POSITIVE_INTEGER:
            case NON_ZERO_INTEGERS:
                String reg = validatorRule == ValidatorRule.POSITIVE_INTEGER ? "^\\+?[0-9]*$" : "^\\+?[1-9][0-9]*$";
                if (!Validator.isMatchRegex(reg, value)) {
                    return false;
                }
                // 强制现在整数不能超过7位
                if (value.length() > INT_MAX_LENGTH) {
                    return false;
                }
                if (!validatorNumber(validatorItem, value)) {
                    return false;
                }
                break;
            default:
                break;
        }
        return validator2(validatorItem, value);
    }

    /**
     * 数字类型的
     *
     * @param validatorItem 规则
     * @param value         值
     * @return true 正确的
     */
    private boolean validatorNumber(final ValidatorItem validatorItem, String value) {
        Double[] douRange = spiltRangeDouble(validatorItem.range());
        if (douRange != null && douRange[2] != null) {
            int len = douRange[2].intValue();
            // 小数
            if (!Validator.isMatchRegex("\\d+\\.\\d{" + len + "}$", value)) {
                return false;
            }
        } else if (!Validator.isNumber(value)) {
            return false;
        }
        if (douRange != null) {
            if (douRange[1] == null && douRange[0] != null) {
                // 具体某个值
                Double doubleVal = Convert.toDouble(value);
                return douRange[0].equals(doubleVal);
            } else if (douRange[1] != null && douRange[0] != null) {
                // 范围
                if (douRange[0] <= douRange[1]) {
                    Double doubleVal = Convert.toDouble(value);
                    return doubleVal <= douRange[1] && doubleVal >= douRange[0];
                }
            }
        }
        return true;
    }

    /**
     * 普通的验证规则
     *
     * @param validatorItem 规则item
     * @param value         值
     * @return true通过
     */
    private boolean validator2(final ValidatorItem validatorItem, String value) {
        ValidatorRule validatorRule = validatorItem.value();
        switch (validatorRule) {
            case EMAIL:
                if (!Validator.isEmail(value)) {
                    return false;
                }
                break;
            case MOBILE:
                if (!Validator.isMobile(value)) {
                    return false;
                }
                break;
            case URL:
                if (!Validator.isUrl(value)) {
                    return false;
                }
                break;
            case WORD:
                if (!Validator.isWord(value)) {
                    return false;
                }
                break;
            case CHINESE:
                if (!Validator.isChinese(value)) {
                    return false;
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        I18nMessageUtil.clearLanguage();
    }

    /**
     * 验证拦截器
     */
    public interface Interceptor {
        /**
         * 拦截到
         *
         * @param request       ree
         * @param response      res
         * @param parameterName 参数名
         * @param value         值
         * @param validatorItem 验证规则
         */
        void error(final HttpServletRequest request, final HttpServletResponse response, final String parameterName, final String value, final ValidatorItem validatorItem);

        /**
         * 获取参数
         *
         * @param request       req
         * @param parameterName 参数名
         * @return 值
         */
        String getParameter(final HttpServletRequest request, final String parameterName);
    }

    /**
     * 默认的参数拦截
     */
    public static class DefaultInterceptor implements Interceptor {
        @Override
        public void error(HttpServletRequest request, HttpServletResponse response, String parameterName, String value, ValidatorItem validatorItem) {
            String msg = validatorItem.msg();
            if (StrUtil.isEmpty(msg)) {
                msg = I18nMessageUtil.get("i18n.parameter_validation_failed.f0a1");
            } else {
                try {
                    msg = I18nMessageUtil.get(msg);
                } catch (Exception ignored) {
                }
            }
            JsonMessage<String> jsonMessage = new JsonMessage<>(validatorItem.code(), msg);
            log.warn("{} {} {} {} {}", request.getRequestURI(), parameterName, value, validatorItem.value(), jsonMessage);
            JakartaServletUtil.write(response, jsonMessage.toString(), MediaType.APPLICATION_JSON_VALUE);
        }

        @Override
        public String getParameter(HttpServletRequest request, String parameterName) {
            return null;
        }
    }
}
