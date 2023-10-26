package com.chua.starter.device.support.adaptor.device;

import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.starter.device.support.adaptor.YunYaoAdaptor;
import com.chua.starter.device.support.adaptor.client.pojo.DeviceListResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.pojo.UploadResult;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author CH
 */
@Group(value = "device", desc = "同步设备到云服务器", group = "device")
@Group(value = "device", desc = "设备同步", group = "service")
@Spi("hai_kang_yun_yao")
public class HikYunYaoDeviceAdaptor extends
        YunYaoAdaptor implements DeviceAdaptor, DeviceUploadAdaptor, DeviceDownloadAdaptor {

    private static final SimpleDateFormat FORMATTER  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+0800");

    public HikYunYaoDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceInfo> downloadFromCloud(int page, int pageSize) {
        DeviceListResult deviceListResult = hikYunYaoClient.devicePage(page, pageSize, deviceCloudPlatformConnector.getDeviceConnectorProjectCode());
        if(null == deviceListResult) {
            return Collections.emptyList();
        }
        List<DeviceListResult.ListDTO> list = deviceListResult.getList();
        List<DeviceInfo> deviceInfos = new ArrayList<>(list.size());
        for (DeviceListResult.ListDTO listDTO : list) {
            DeviceInfo item = new DeviceInfo();
            try {
                item.setCreateTime(FORMATTER.parse(listDTO.getCreateTime()));
            } catch (ParseException ignored) {
            }
            item.setDeviceModel(listDTO.getModel());
            item.setDeviceConnectorId(deviceCloudPlatformConnector.getDeviceConnectorId() + "");
            item.setDeviceName(listDTO.getDeviceName());
            item.setDeviceImsi(listDTO.getDeviceSerial());
            item.setDeviceValidateCode(listDTO.getValidateCode());
            item.setDeviceOrgCode(listDTO.getDeviceOrgId());
            item.setDeviceVersion(listDTO.getDeviceVersion());
            item.setDeviceStatus(1 == listDTO.getOnlineStatus() ? "online" : "offline");
            item.setDeviceInCloud("cloud");
            item.setDeviceTypeName(listDTO.getModelTypeStr());
            item.setDeviceTypeCode(listDTO.getModelType());
            item.setDeviceNetAddress(listDTO.getNetAddress());

            deviceInfos.add(item);
        }
        return deviceInfos;
    }

    @Override
    public UploadResult uploadToCloud(DeviceInfo deviceInfo) {
        return null;
    }
}
