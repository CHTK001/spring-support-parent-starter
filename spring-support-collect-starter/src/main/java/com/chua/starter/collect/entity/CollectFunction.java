package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集自定义函数。
 *
 * <p>一条记录就是一个 Groovy 闭包，保存时编译校验，运行期同时暴露给脚本里的
 * {@code fn.名字(...)} 与参数表达式里的 {@code ${名字(...)}}。形参个数由闭包自身推导，
 * 存进 {@code collectFunctionParameters} 只为界面提示，不是第二份真相。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_function")
@Schema(description = "数据采集-自定义函数")
public class CollectFunction extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_function_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectFunctionId;

    /**
     * 函数名，调用时用的标识符。
     */
    @Schema(description = "函数名")
    private String collectFunctionName;

    /**
     * 闭包字面量，形如 { a, b -> ... }。
     */
    @Schema(description = "闭包字面量 { a, b -> ... }")
    private String collectFunctionScript;

    /**
     * 形参个数，保存时从编译结果回填。
     */
    @Schema(description = "形参个数")
    private Integer collectFunctionParameters;

    /**
     * 用途说明。
     */
    @Schema(description = "用途说明")
    private String collectFunctionDescription;

    /**
     * 是否启用，0 停用 1 启用。
     */
    @Schema(description = "是否启用 0停用 1启用")
    private Integer collectFunctionEnabled;

    /**
     * 最近一次试跑结果。
     */
    @Schema(description = "最近一次试跑结果")
    private String collectFunctionLastResult;

    /**
     * 最近一次试跑时间。
     */
    @Schema(description = "最近一次试跑时间")
    private LocalDateTime collectFunctionLastTime;
}
