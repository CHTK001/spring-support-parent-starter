package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelRemarkRequest;
import com.chua.starter.panel.model.PanelRemarkView;

import java.util.List;

/**
 * 面板备注服务。
 *
 * @author CH
 */
public interface PanelRemarkService {

    /**
     * 按连接标识查询备注列表。
     *
     * @param panelConnectionId 连接标识
     * @return 备注列表
     */
    List<PanelRemarkView> listByConnectionId(String panelConnectionId);

    /**
     * 保存备注。
     *
     * @param request 备注参数
     * @return 保存后的备注
     */
    PanelRemarkView saveRemark(PanelRemarkRequest request);
}
