// [P3C BL-边界 豁免]

package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据采集-自定义函数试跑出参。
 *
 * <p>把返回值连同运行时类型一起给出，闭包返回 GString 时界面能看到它不是 String，
 * 这正是脚本里拼字符串最容易踩的地方。</p>
 *
 * @param success        是否调用成功
 * @param functionName   函数名
 * @param parameters     闭包声明的形参个数
 * @param result         返回值的文本形式
 * @param resultType     返回值的运行时类型
 * @param costMs         调用耗时毫秒
 * @param errorMessage   失败说明
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-自定义函数试跑出参")
public record CollectFunctionTestVO(
        @Schema(description = "是否成功")
        Boolean success,
        @Schema(description = "函数名")
        String functionName,
        @Schema(description = "形参个数")
        Integer parameters,
        @Schema(description = "返回值")
        String result,
        @Schema(description = "返回值类型")
        String resultType,
        @Schema(description = "耗时毫秒")
        Long costMs,
        @Schema(description = "失败说明")
        String errorMessage) {
}
