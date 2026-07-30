package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceTestView;
import com.chua.starter.panel.model.PanelDriverUploadView;
import com.chua.starter.panel.model.PanelDatasourceView;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Panel 数据源服务。
 *
 * @author CH
 */
public interface PanelDatasourceService {

    /**
     * 查询全部数据源。
     *
     * @return 数据源列表
     */
    List<PanelDatasourceView> listAll();

    /**
     * 保存数据源。
     *
     * @param request 数据源参数
     * @return 保存后的数据源
     */
    PanelDatasourceView save(PanelDatasourceRequest request);

    /**
     * 测试数据源连通性。
     *
     * @param request 数据源参数
     * @return 测试结果
     */
    PanelDatasourceTestView test(PanelDatasourceRequest request);

    /**
     * 上传驱动文件并返回解析信息。
     *
     * @param panelDialectType 方言类型
     * @param file 驱动文件
     * @return 上传结果
     */
    PanelDriverUploadView uploadDriver(String panelDialectType, MultipartFile file);

    /**
     * 删除指定数据源。
     *
     * @param panelSourceId 数据源主键
     */
    void delete(String panelSourceId);
}
