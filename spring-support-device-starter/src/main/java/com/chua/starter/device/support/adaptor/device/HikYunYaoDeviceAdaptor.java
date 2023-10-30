package com.chua.starter.device.support.adaptor.device;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Group;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.device.support.adaptor.HikYunYaoAdaptor;
import com.chua.starter.device.support.adaptor.client.pojo.HikYunYaoDeviceListResult;
import com.chua.starter.device.support.adaptor.pojo.LiveResult;
import com.chua.starter.device.support.adaptor.pojo.UploadResult;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceInfo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.chua.common.support.constant.NameConstant.STATUS;
import static com.chua.common.support.constant.NumberConstant.NUM_200;
import static com.chua.common.support.constant.NumberConstant.NUM_500;

/**
 * 海康云曜
 * @author CH
 */
@Group(value = "device", desc = "同步设备到云服务器", group = "device")
@Group(value = "device", desc = "设备同步", group = "service")
@Group(value = "camera", desc = "设备监控", group = "device")
@Spi("hai_kang_yun_yao")
public class HikYunYaoDeviceAdaptor
        extends HikYunYaoAdaptor
        implements DeviceAdaptor, DeviceUploadAdaptor, DeviceDownloadAdaptor, CameraDeviceAdaptor {

    public static final SimpleDateFormat FORMATTER  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+08:00");

    public HikYunYaoDeviceAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        super(deviceCloudPlatformConnector);
    }

    @Override
    public List<DeviceInfo> downloadFromCloud(int page, int pageSize) {
        HikYunYaoDeviceListResult deviceListResult = hikYunYaoClient.devicePage(page, pageSize, deviceCloudPlatformConnector.getDeviceConnectorProjectCode());
        if(null == deviceListResult) {
            return Collections.emptyList();
        }
        List<HikYunYaoDeviceListResult.ListDTO> list = deviceListResult.getList();
        List<DeviceInfo> deviceInfos = new ArrayList<>(list.size());
        for (HikYunYaoDeviceListResult.ListDTO listDTO : list) {
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

    @Override
    public LiveResult getLiveAddress(String deviceImsi, String deviceChannel) {
        JSONObject jsonObject = Json.fromJson(hikYunYaoClient.getLiveAddress(deviceCloudPlatformConnector.getDeviceConnectorProjectCode(), deviceImsi, Integer.valueOf(StringUtils.defaultString(deviceChannel, "1"))), JSONObject.class);
        if(null == jsonObject) {
            return new LiveResult();
        }
        LiveResult result = new LiveResult();
        if(NUM_200 == jsonObject.getIntValue(STATUS, NUM_500)) {
            result.setMsg(jsonObject.getString("msg"));
        }
        JSONObject data = jsonObject.getJSONObject("data");
        if(null != data) {
            result.setUrl(data.getString("url"));
        }

        return result;
    }

}
