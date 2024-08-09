package com.chua.starter.mybatis.entity;

import com.chua.starter.common.support.annotations.RequestParamMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * page 查询
 * @author CH
 */
@Schema(description ="分页信息")
@Data
public class Query<T>{
    private static final Integer PAGE_NO = 1;
    private static final Integer PAGE_SIZE = 10;

    /**
     * 每页条数 - 不分页
     *
     * 例如说，导出接口，可以设置 {@link #pageSize} 为 -1 不分页，查询所有数据。
     */
    public static final Integer PAGE_SIZE_NONE = -1;

    @Schema(description = "页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED,example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    @RequestParamMapping({"page", "pageNo", "current"})
    private Integer page = PAGE_NO;

    @Schema(description = "每页条数，最大值为 100", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = 100, message = "每页条数最大值为 100")
    @RequestParamMapping({"pageSize", "size", "count"})
    private Integer pageSize = PAGE_SIZE;

    /**
     * 初始化分页
     *
     * @return {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page}<{@link T}>
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> createPage() {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<T>(page, pageSize);
    }
}
