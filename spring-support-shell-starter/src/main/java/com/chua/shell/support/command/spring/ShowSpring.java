package com.chua.shell.support.command.spring;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.ansi.AnsiColor;
import com.chua.common.support.ansi.AnsiOutput;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.*;
import java.util.stream.Collectors;

/**
 * invoke
 *
 * @author CH
 */
public class ShowSpring {
    /**
     * 执行
     *
     * @param command 命令
     * @return 结果
     */
    public static String execute(List<String> command) {
        if (command.size() > 1) {
            return "无效参数";
        }

        String s = command.get(0);
        if ("bean".equalsIgnoreCase(s)) {
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("head", new String[]{"序列", "Bean", "类"});
            List<Object[]> tpl = new LinkedList<>();
            rs.put("rows", tpl);
            for (String beanDefinitionName : beanDefinitionNames) {
                tpl.add(new String[]{beanDefinitionName, applicationContext.getBean(beanDefinitionName).getClass().getTypeName()});

            }
            return "@table " + JSON.toJSONString(rs);

        }
        if ("mapping".equalsIgnoreCase(s)) {
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            RequestMappingHandlerMapping requestMappingHandlerMapping;
            try {
                requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            } catch (BeansException e) {
                return "无法解析";
            }
            Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("head", new String[]{"方法", "路径", "响应类型", "处理方法"});
            List<Object[]> tpl = new LinkedList<>();
            rs.put("rows", tpl);

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo entryKey = entry.getKey();
                HandlerMethod handlerMethod = entry.getValue();
                tpl.add(new String[]{
                        AnsiOutput.toString(AnsiColor.GREEN, Arrays.toString(entryKey.getMethodsCondition().getMethods().stream().map(Enum::name).toArray(String[]::new))),
                        AnsiOutput.toString(AnsiColor.BLUE, Arrays.toString(entryKey.getPathPatternsCondition().getPatterns().stream().map(PathPattern::getPatternString).toArray(String[]::new))),
                        entryKey.getProducesCondition().getProducibleMediaTypes().stream().map(MediaType::toString).collect(Collectors.joining("\r\n")),
                        handlerMethod.toString()
                });
            }
            return "@table " + JSON.toJSONString(rs);

        }

        return "";
    }
}
