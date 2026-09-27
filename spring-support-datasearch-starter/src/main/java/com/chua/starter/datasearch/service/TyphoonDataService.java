package com.chua.starter.datasearch.service;

import com.chua.starter.datasearch.pojo.typhoon.TyphoonDetailVO;
import com.chua.starter.datasearch.pojo.typhoon.TyphoonItemVO;

import java.util.List;

/**
 * 顶栏台风数据服务。
 *
 * <p>把台风数据源（SPI）归一成本系统契约：风圈报文解析成四方位半径数组，
 * 多机构预报收敛成前端可直接绘制的路径通道。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface TyphoonDataService {

    /**
     * 当前活跃台风列表。
     *
     * @return 活跃台风列表（确无活跃台风时为空列表）；数据源不可达时返回 空
     */
    List<TyphoonItemVO> activeTyphoons();

    /**
     * 单个台风详情（历史路径 + 预报路径）。
     *
     * @param id 台风编号
     * @return 详情；数据源不可达或编号不存在时返回 空
     */
    TyphoonDetailVO detail(String id);
}
