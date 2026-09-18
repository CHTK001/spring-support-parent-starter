package com.chua.starter.ai.support.model;

import lombok.Data;

/**
* 输入优化响应模型。
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class InputOptimizeResponse {

    /**
    * 原始输入
    */
    private String input;

    /**
    * 优化后的文本
    */
    private String optimized;

    /**
    * 实际使用的优化模型
    */
    private String model;

    /**
    * 优化耗时（毫秒）
    */
    private Long elapsedMillis;

    /**
    * 构建响应。
    *
    * @param input         原始输入
    * @param optimized     优化后文本
    * @param model         实际使用模型
    * @param elapsedMillis 耗时（毫秒）
    * @return 响应对象
    */
    public static InputOptimizeResponse of(String input, String optimized, String model, Long elapsedMillis) {
        InputOptimizeResponse response = new InputOptimizeResponse();
        response.setInput(input);
        response.setOptimized(optimized);
        response.setModel(model);
        response.setElapsedMillis(elapsedMillis);
        return response;
    }
}
