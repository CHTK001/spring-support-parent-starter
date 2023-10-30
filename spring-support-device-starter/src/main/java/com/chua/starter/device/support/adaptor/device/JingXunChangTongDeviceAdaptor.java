package com.chua.starter.device.support.adaptor.device;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.util.List;

/**
 * 精讯畅通
 * @author CH
 */
@Spi("JING_XUN_CHANG_TONG")
public class JingXunChangTongDeviceAdaptor implements DeviceDownloadAdaptor {
    @Override
    public List<DeviceInfo> downloadFromCloud(int page, int pageSize) {

        return null;
    }
}
