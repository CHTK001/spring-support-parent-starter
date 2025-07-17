package com.chua.starter.plugin.filter;

import com.chua.starter.plugin.service.XssProtectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * XSS防护请求包装器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final XssProtectionService xssProtectionService;
    private final Map<String, String[]> filteredParameterMap;

    public XssHttpServletRequestWrapper(HttpServletRequest request, XssProtectionService xssProtectionService) {
        super(request);
        this.xssProtectionService = xssProtectionService;
        this.filteredParameterMap = new HashMap<>();
        
        // 预处理所有参数
        preprocessParameters();
    }

    /**
     * 预处理所有参数
     */
    private void preprocessParameters() {
        Map<String, String[]> originalParams = super.getParameterMap();
        
        for (Map.Entry<String, String[]> entry : originalParams.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();
            
            // 检查是否需要过滤此参数
            if (!xssProtectionService.shouldCheckParameter(paramName)) {
                filteredParameterMap.put(paramName, paramValues);
                continue;
            }
            
            // 过滤参数值
            String[] filteredValues = new String[paramValues.length];
            for (int i = 0; i < paramValues.length; i++) {
                try {
                    filteredValues[i] = xssProtectionService.filterXssContent(
                        paramValues[i], paramName, this);
                } catch (XssFilter.XssAttackException e) {
                    // 根据配置决定是抛出异常还是继续处理
                    if (xssProtectionService.isRejectMode()) {
                        throw e;
                    } else {
                        // 过滤模式，使用过滤后的内容
                        filteredValues[i] = e.getMessage(); // 这里应该是过滤后的安全内容
                    }
                }
            }
            
            filteredParameterMap.put(paramName, filteredValues);
        }
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        return filteredParameterMap.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return new HashMap<>(filteredParameterMap);
    }

    /**
     * 获取原始参数值（未过滤）
     * 
     * @param name 参数名
     * @return 原始参数值
     */
    public String getOriginalParameter(String name) {
        return super.getParameter(name);
    }

    /**
     * 获取原始参数值数组（未过滤）
     * 
     * @param name 参数名
     * @return 原始参数值数组
     */
    public String[] getOriginalParameterValues(String name) {
        return super.getParameterValues(name);
    }

    /**
     * 获取原始参数映射（未过滤）
     * 
     * @return 原始参数映射
     */
    public Map<String, String[]> getOriginalParameterMap() {
        return super.getParameterMap();
    }

    /**
     * 检查参数是否被过滤
     * 
     * @param name 参数名
     * @return 是否被过滤
     */
    public boolean isParameterFiltered(String name) {
        String[] original = super.getParameterValues(name);
        String[] filtered = filteredParameterMap.get(name);
        
        if (original == null && filtered == null) {
            return false;
        }
        
        if (original == null || filtered == null) {
            return true;
        }
        
        if (original.length != filtered.length) {
            return true;
        }
        
        for (int i = 0; i < original.length; i++) {
            if (!original[i].equals(filtered[i])) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 获取被过滤的参数名列表
     * 
     * @return 被过滤的参数名数组
     */
    public String[] getFilteredParameterNames() {
        return filteredParameterMap.entrySet().stream()
            .filter(entry -> isParameterFiltered(entry.getKey()))
            .map(Map.Entry::getKey)
            .toArray(String[]::new);
    }
}
