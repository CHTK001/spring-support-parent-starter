// [P3C BL-边界 豁免]

package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 自定义函数试跑入参。
 *
 * @param collectFunctionId 函数ID
 * @param arguments         位置实参，按闭包形参顺序填写，全部以文本传入
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "自定义函数试跑入参")
public record CollectFunctionTestDTO(
        @Schema(description = "函数ID")
        Integer collectFunctionId,
        @Schema(description = "位置实参")
        List<String> arguments) {

    /**
     * 规范化集合组件，阻断外部别名写入；保留 null 语义。
     */
    public CollectFunctionTestDTO {
        arguments = arguments == null ? null : List.copyOf(arguments);
    }
}
