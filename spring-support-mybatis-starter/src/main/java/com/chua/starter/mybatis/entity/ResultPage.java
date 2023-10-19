package com.chua.starter.mybatis.entity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.CollectionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * page
 * @author CH
 */
@Schema(description = "分页信息")
@Data
public class ResultPage<T>{
    /**
     * 每页显示条数，默认 10
     */
    @Schema(description = "每页显示条数，默认 10")
    private long size = 10;
    /**
     * 总数
     */
    @Schema(description = "总数")
    private long total = 10;

    /**
     * 当前页
     */
    @Schema(description = "当前页")
    private long current = 1;

    /**
     * 总页数
     */
    @Schema(description = "总页数")
    private long totalPage;
    /**
     * 查询数据列表
     */
    @Schema(description = "查询数据列表")
    private List<T> records = Collections.emptyList();


    public static <T,O> ResultPage<O> copyList(IPage<T> tiPage, Class<O> oClass){
        ResultPage<O> oPageDTO = new ResultPage<>();
        oPageDTO.setTotalPage(tiPage.getPages());
        oPageDTO.setCurrent(tiPage.getCurrent());
        oPageDTO.setTotal(tiPage.getTotal());
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            List<O> ol = BeanUtils.copyPropertiesList(tiPage.getRecords(), oClass);
            oPageDTO.setRecords(ol);
        }
        return oPageDTO;
    }


    public static <T> ResultPage<T> copy(IPage<T> tiPage){
        ResultPage<T> oPageDTO = new ResultPage<>();
        oPageDTO.setTotalPage(tiPage.getPages());
        oPageDTO.setCurrent(tiPage.getCurrent());
        oPageDTO.setTotal(tiPage.getTotal());
        if (CollectionUtils.isNotEmpty(tiPage.getRecords())){
            oPageDTO.setRecords(tiPage.getRecords());
        }
        return oPageDTO;
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
