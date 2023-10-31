package com.chua.starter.device.support.adaptor.pojo;

import com.chua.common.support.lang.date.DateTime;
import com.chua.starter.device.support.request.EventType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Date;

/**
 * 访问事件请求
 *
 * @author CH
 * @since 2023/10/27
 */
@Data
@Builder
public class AccessEventRequest {

    /**
     * 开始时间
     */
    @NonNull
    @Builder.Default
    private Date startTime = DateTime.now().minusHours(1).toDate();
    /**
     * 结束时间
     */
    @NonNull
    @Builder.Default
    private Date endTime = new Date();

    /**
     * 项目id
     */
    @NonNull
    @Builder.Default
    private String projectId = "";
    /**
     * 项目代码
     */
    @NonNull
    @Builder.Default
    private String projectCode = "";

    /**
     * 设备序列
     */
    private String deviceSerial;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 输入或输出
     * 进出方向 0:未知;1:进;2:出;
     */
    private String inOrOut;

    /**
     * 人员关键字(姓名、编号、证件号、手机号)模糊搜索，查询效率偏低
     */
    private String personKey;
    /**
     * 事件类型
     * ACCESS: 门禁
     */
    private EventType eventType;

    /**
     * 第页
     */
    @Builder.Default
    @NonNull
    private Integer pageNo = 1;

    /**
     * 页面大小
     */
    @Builder.Default
    @NonNull
    private Integer pageSize = 1000;


}
