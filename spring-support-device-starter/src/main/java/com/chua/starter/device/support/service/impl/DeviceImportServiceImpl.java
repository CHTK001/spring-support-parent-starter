// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.service.impl;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.model.DeviceImportResult;
import com.chua.starter.device.support.model.DeviceImportRow;
import com.chua.starter.device.support.service.DeviceImportService;
import com.chua.starter.device.support.util.DeviceExcelImportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备台账 Excel 导入实现。
 *
 * <p>三种模式共用同一套校验，区别只在「按设备ID 匹配到什么」：</p>
 * <ul>
 *   <li>{@code create}：ID 留空则新增；ID 已存在直接报错，避免误覆盖。</li>
 *   <li>{@code update}：必须填已存在的 ID，只更新填了值的字段。</li>
 *   <li>{@code upsert}：ID 存在就更新、不存在就新增。</li>
 * </ul>
 *
 * <p>干模拟（{@code dryRun=true}）只做校验与动作判定，不写库；前端先预览再确认写入。
 * 逐行失败只影响该行，其它行照常导入，最终在结果里逐行说明。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportServiceImpl implements DeviceImportService {

    /**
     * 新增
     */
    private static final String MODE_CREATE = "create";

    /**
     * 更新
     */
    private static final String MODE_UPDATE = "update";

    /**
     * 新增或更新
     */
    private static final String MODE_UPSERT = "upsert";

    /**
     * 动作：新增
     */
    private static final String ACTION_CREATE = "create";

    /**
     * 动作：更新
     */
    private static final String ACTION_UPDATE = "update";

    /**
     * 动作：跳过
     */
    private static final String ACTION_SKIP = "skip";

    /**
     * 动作：错误
     */
    private static final String ACTION_ERROR = "error";

    /**
     * 单次导入上限：与前端提示保持一致，超过让用户拆分
     */
    private static final int MAX_ROWS = 5000;

    /**
     * 新增设备的默认离线阈值（秒）
     */
    private static final int DEFAULT_OFFLINE_THRESHOLD = 60;

    /**
     * 设备映射器
     */
    private final DeviceInfoMapper deviceInfoMapper;

    @Override
    public DeviceImportResult importDevices(MultipartFile file, String mode, boolean dryRun) throws IOException {
        DeviceImportResult result = new DeviceImportResult();
        if (file == null || file.isEmpty()) {
            result.add(DeviceImportRow.of(1, ACTION_ERROR, null, null, "没有收到上传文件"));
            return result;
        }
        String normalizedMode = normalizeMode(mode);
        List<Map<String, String>> rows;
        try (InputStream in = file.getInputStream()) {
            rows = DeviceExcelImportUtil.readRows(in);
        }
        result.setTotal(rows.size());
        if (rows.isEmpty()) {
            result.add(DeviceImportRow.of(1, ACTION_ERROR, null, null,
                    "没有可导入的数据：请使用模板表头，并保证「设备名称」列有值"));
            return result;
        }
        if (rows.size() > MAX_ROWS) {
            result.add(DeviceImportRow.of(1, ACTION_ERROR, null, null,
                    "本次读取 " + rows.size() + " 行，超过单次上限 " + MAX_ROWS + " 行，请拆分后再导入"));
            return result;
        }

        Set<String> seenIds = new HashSet<>();
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            importRow(rowNumber, row, normalizedMode, dryRun, seenIds, result);
        }
        log.info("设备台账导入完成: mode={}, dryRun={}, total={}, created={}, updated={}, skipped={}, error={}",
                normalizedMode, dryRun, result.getTotal(), result.getCreateCount(),
                result.getUpdateCount(), result.getSkipCount(), result.getErrorCount());
        return result;
    }

    /**
     * 处理单行。
     *
     * @param rowNumber 行号
     * @param row       行数据
     * @param mode      导入模式
     * @param dryRun    是否干模拟
     * @param seenIds   本文件已出现的设备 ID
     * @param result    结果容器
     */
    private void importRow(int rowNumber, Map<String, String> row, String mode, boolean dryRun,
                           Set<String> seenIds, DeviceImportResult result) {
        String name = value(row, "name");
        String deviceId = value(row, "deviceId");
        if (name.isEmpty()) {
            result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, deviceId, null, "设备名称为空"));
            return;
        }
        if (!deviceId.isEmpty() && !seenIds.add(deviceId)) {
            result.add(DeviceImportRow.of(rowNumber, ACTION_SKIP, deviceId, name, "同一文件内设备ID重复，已跳过"));
            return;
        }

        DeviceInfo existing = deviceId.isEmpty() ? null : deviceInfoMapper.selectById(deviceId);
        String action = resolveAction(mode, deviceId, existing, rowNumber, name, result);
        if (action == null) {
            return;
        }
        try {
            DeviceInfo device = buildDevice(row, existing, ACTION_CREATE.equals(action));
            if (!dryRun) {
                if (ACTION_CREATE.equals(action)) {
                    deviceInfoMapper.insert(device);
                } else {
                    deviceInfoMapper.updateById(device);
                }
            }
            String message = dryRun
                    ? "校验通过（干模拟，未写库）"
                    : (ACTION_CREATE.equals(action) ? "已新增" : "已更新");
            result.add(DeviceImportRow.of(rowNumber, action, device.getDeviceInfoId(), name, message));
        } catch (IllegalArgumentException ex) {
            result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, deviceId, name, ex.getMessage()));
        } catch (RuntimeException ex) {
            log.warn("台账导入第 {} 行写库失败: {}", rowNumber, ex.getMessage());
            result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, deviceId, name, "写库失败：" + ex.getMessage()));
        }
    }

    /**
     * 按模式判断这一行是新增、更新还是错误。
     *
     * @param mode      模式
     * @param deviceId  设备 ID
     * @param existing  库中已存在的设备，可空
     * @param rowNumber 行号
     * @param name      设备名称
     * @param result    结果容器
     * @return create / update；校验不通过返回 null（错误已写入结果）
     */
    private String resolveAction(String mode, String deviceId, DeviceInfo existing,
                                 int rowNumber, String name, DeviceImportResult result) {
        switch (mode) {
            case MODE_UPDATE:
                if (deviceId.isEmpty()) {
                    result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, null, name, "更新模式必须填写设备ID"));
                    return null;
                }
                if (existing == null) {
                    result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, deviceId, name, "设备不存在，无法更新"));
                    return null;
                }
                return ACTION_UPDATE;
            case MODE_CREATE:
                if (existing != null) {
                    result.add(DeviceImportRow.of(rowNumber, ACTION_ERROR, deviceId, name,
                            "设备ID已存在（新增模式请留空，或改用「新增/更新」）"));
                    return null;
                }
                return ACTION_CREATE;
            default:
                return existing == null ? ACTION_CREATE : ACTION_UPDATE;
        }
    }

    /**
     * 组装设备实体：新增写全字段，更新只覆盖填了值的字段。
     *
     * @param row     行数据
     * @param existing 已存在的实体（更新时用），可空
     * @param create  是否新增
     * @return 待写入实体
     */
    private DeviceInfo buildDevice(Map<String, String> row, DeviceInfo existing, boolean create) {
        DeviceInfo device = create || existing == null ? new DeviceInfo() : existing;
        LocalDateTime now = LocalDateTime.now();

        String name = value(row, "name");
        if (!name.isEmpty()) {
            device.setDeviceInfoName(name);
        }
        setIfPresent(device::setDeviceInfoVendor, value(row, "vendor"));
        setIfPresent(device::setDeviceInfoModel, value(row, "model"));
        setIfPresent(device::setDeviceInfoCategory, value(row, "category"));
        setIfPresent(device::setDeviceInfoSubCategory, value(row, "subCategory"));
        setIfPresent(device::setDeviceInfoIp, value(row, "ip"));
        setIfPresent(device::setDeviceInfoSerial, value(row, "serial"));
        setIfPresent(device::setDeviceInfoFirmware, value(row, "firmware"));
        setIfPresent(device::setDeviceInfoAddress, value(row, "address"));

        Integer port = parseInt(value(row, "port"), "端口");
        if (port != null) {
            device.setDeviceInfoPort(port);
        }
        Integer channelCount = parseInt(value(row, "channelCount"), "通道数");
        if (channelCount != null) {
            device.setDeviceInfoChannelCount(channelCount);
        }
        BigDecimal latitude = parseDecimal(value(row, "latitude"), "纬度");
        if (latitude != null) {
            device.setDeviceInfoLatitude(latitude);
        }
        BigDecimal longitude = parseDecimal(value(row, "longitude"), "经度");
        if (longitude != null) {
            device.setDeviceInfoLongitude(longitude);
        }

        device.setDeviceInfoUpdatedAt(now);
        if (create) {
            String requestedId = value(row, "deviceId");
            // 台账里给了 ID 就沿用（干模拟预览也能看到最终 ID）；留空交给 MP 生成
            device.setDeviceInfoId(requestedId.isEmpty() ? null : requestedId);
            device.setDeviceInfoOnline(Boolean.FALSE);
            device.setDeviceInfoSource("local");
            device.setDeviceInfoStatus("normal");
            device.setDeviceInfoOfflineThreshold(DEFAULT_OFFLINE_THRESHOLD);
            device.setDeviceInfoCreatedAt(now);
            if (device.getDeviceInfoChannelCount() == null) {
                device.setDeviceInfoChannelCount(0);
            }
        }
        return device;
    }

    /**
     * 非空才写：更新模式下留空 = 不修改。
     *
     * @param setter 赋值方法
     * @param text   文本值
     */
    private static void setIfPresent(java.util.function.Consumer<String> setter, String text) {
        if (!text.isEmpty()) {
            setter.accept(text);
        }
    }

    /**
     * 读取并去空白。
     *
     * @param row 行数据
     * @param key 字段名
     * @return 文本（非 null）
     */
    private static String value(Map<String, String> row, String key) {
        String text = row.get(key);
        return text == null ? "" : text.trim();
    }

    /**
     * 解析整数；空值返回 null。
     *
     * @param text  文本
     * @param label 字段名（报错用）
     * @return 整数或 null
     */
    private static Integer parseInt(String text, String label) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是整数，当前值：" + text);
        }
    }

    /**
     * 解析小数；空值返回 null。
     *
     * @param text  文本
     * @param label 字段名（报错用）
     * @return 小数或 null
     */
    private static BigDecimal parseDecimal(String text, String label) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是数字，当前值：" + text);
        }
    }

    /**
     * 归一化模式：未知值按新增处理，避免误更新。
     *
     * @param mode 原始模式
     * @return create / update / upsert
     */
    private static String normalizeMode(String mode) {
        if (MODE_UPDATE.equalsIgnoreCase(mode)) {
            return MODE_UPDATE;
        }
        if (MODE_UPSERT.equalsIgnoreCase(mode)) {
            return MODE_UPSERT;
        }
        return MODE_CREATE;
    }
}
