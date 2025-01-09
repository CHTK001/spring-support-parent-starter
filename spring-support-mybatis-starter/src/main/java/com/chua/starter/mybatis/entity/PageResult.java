package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.CollectionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

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
        PageResult.PageResultBuilder<O> oPageDTO = PageResult.<O>builder();
        oPageDTO.totalPage(tiPage.getPages());
        oPageDTO.pageNo(tiPage.getCurrent());
        oPageDTO.total(tiPage.getTotal());
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            List<O> ol = BeanUtils.copyPropertiesList(tiPage.getRecords(), oClass);
            oPageDTO.records(ol);
        }
        return oPageDTO.build();
    }


    public static <T> PageResult<T> copy(IPage<T> tiPage){
        PageResult.PageResultBuilder<T> oPageDTO = PageResult.<T>builder();
        oPageDTO.totalPage(tiPage.getPages());
        oPageDTO.pageNo(tiPage.getCurrent());
        oPageDTO.total(tiPage.getTotal());
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            oPageDTO.records(tiPage.getRecords());
        }
        return oPageDTO.build();
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
