package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.chua.common.support.base.bean.BeanUtils;
import com.chua.common.support.core.utils.CollectionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * page
 * @author CH
 */
@Schema(description = "分页信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    /**
     * 每页显示条数，默认 10
     */
    @Schema(description = "每页显示条数，默认 10")
    @Builder.Default
    private long pageSize = 10;
    /**
     * 总数
     */
    @Schema(description = "总数")
    @Builder.Default
    private long total = 10;

    /**
     * 当前页
     */
    @Schema(description = "当前页")
    @Builder.Default
    private long pageNo = 1;

    /**
     * 总页数
     */
    @Schema(description = "总页数")
    private long totalPage;
    /**
     * 查询数据列表
     */
    @Schema(description = "查询数据列表")
    @Builder.Default
    private List<T> records = Collections.emptyList();


    public static <T,O> PageResult<O> copyList(IPage<T> tiPage, Class<O> oClass){
        var result = new PageResult<O>();
        result.totalPage = tiPage.getPages();
        result.pageNo = tiPage.getCurrent();
        result.total = tiPage.getTotal();
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            List<O> ol = BeanUtils.copyPropertiesList(tiPage.getRecords(), oClass);
            result.records = ol;
        }
        return result;
    }


    public static <T> PageResult<T> copy(IPage<T> tiPage){
        var result = new PageResult<T>();
        result.totalPage = tiPage.getPages();
        result.pageNo = tiPage.getCurrent();
        result.total = tiPage.getTotal();
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            result.records = tiPage.getRecords();
        }
        return result;
    }
    public static <T> PageDTO<T> empty(){
        PageDTO<T> tPageDTO = new PageDTO<>();
        tPageDTO.setPages(0);
        tPageDTO.setRecords(Collections.emptyList());
        tPageDTO.setCurrent(0);
        tPageDTO.setTotal(0);
        return tPageDTO;
    }
}
