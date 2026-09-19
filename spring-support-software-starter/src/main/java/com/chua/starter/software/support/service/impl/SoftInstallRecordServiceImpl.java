package com.chua.starter.software.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.enums.InstallStatus;
import com.chua.starter.software.support.mapper.SoftInstallRecordMapper;
import com.chua.starter.software.support.mapper.SoftSoftwareMapper;
import com.chua.starter.software.support.pojo.install.InstallDetailResponse;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.chua.starter.software.support.service.SoftInstallRecordService;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.SoftSourceService;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.chua.starter.software.support.spi.SoftwareSourceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 安装记录表 服务 实现
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftInstallRecordServiceImpl extends ServiceImpl<SoftInstallRecordMapper, SoftInstallRecord> implements SoftInstallRecordService {

    /**
     * 安装记录映射器
     */
    private final SoftInstallRecordMapper installRecordMapper;

    /**
     * 软件服务
     */
    private final SoftSoftwareService softwareService;

    /**
     * 软件映射器(用于更新安装计数)
     */
    private final SoftSoftwareMapper softwareMapper;

    /**
     * 软件源服务
     */
    private final SoftSourceService sourceService;

    /**
     * 软件源SPI列表
     */
    private final List<SoftwareSource> softwareSources;

    /**
     * 事务模板
     */
    private final TransactionTemplate transactionTemplate;

    @Override
    /**
     * Install
    */
    public ReturnResult<InstallDetailResponse> install(InstallRequest request) {
        SoftSoftware software = softwareService.getById(request.getSoftwareId());
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }
        // 检查是否已安装
        SoftInstallRecord exist = getInstalledRecord(request.getSoftwareId(), request.getUserId(), request.getDeviceId());
        if (exist != null && InstallStatus.INSTALLED.getValue() == exist.getInstallRecordStatus()) {
            return ReturnResult.illegal("软件已安装");
        }
        SoftSource source = sourceService.getById(software.getSoftwareSourceId());
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        // 获取安装源
        SoftwareSource installSource = getSoftwareSource(source);
        if (installSource == null) {
            return ReturnResult.illegal("不支持的软件源");
        }

        return transactionTemplate.execute(status -> {
            // 创建安装记录
            SoftInstallRecord record = new SoftInstallRecord();
            record.setInstallRecordId(IdUtils.fastUuid());
            record.setInstallRecordSoftwareId(request.getSoftwareId());
            record.setInstallRecordUserId(request.getUserId());
            record.setInstallRecordDeviceId(request.getDeviceId());
            record.setInstallRecordSourceId(software.getSoftwareSourceId());
            record.setInstallRecordVersion(software.getSoftwareVersion());
            record.setInstallRecordOperation("install");
            record.setInstallRecordStatus(InstallStatus.INSTALLING.getValue());
            record.setInstallRecordProgress(0);
            record.setInstallRecordStartTime(LocalDateTime.now());
            record.setInstallRecordInstallPath(request.getInstallPath());
            installRecordMapper.insert(record);

            try {
                // 模拟进度
                /**
                 * 更新进步。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                updateProgress(record.getInstallRecordId(), 30, "准备安装");
                SoftwareSourceResult result = installSource.install(source, software, request.getInstallPath());
                /**
                 * 更新进步。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                updateProgress(record.getInstallRecordId(), 100, result.getMessage());
                /**
                 * 完成install。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                completeInstall(record.getInstallRecordId());
                // 更新软件安装计数
                software.setSoftwareInstallCount(software.getSoftwareInstallCount() == null ? 1 : software.getSoftwareInstallCount() + 1);
                softwareMapper.updateById(software);
                return ReturnResult.ok(toDetailResponse(record, software));
            } catch (Exception e) {
                /**
                 * 失败install。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                failInstall(record.getInstallRecordId(), e.getMessage());
                return ReturnResult.illegal("安装失败: " + e.getMessage());
            }
        });
    }

    @Override
    /**
     * Uninstall
    */
    public ReturnResult<InstallDetailResponse> uninstall(String softwareId, String userId, String deviceId) {
        SoftSoftware software = softwareService.getById(softwareId);
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }
        SoftInstallRecord record = getInstalledRecord(softwareId, userId, deviceId);
        if (record == null || InstallStatus.INSTALLED.getValue() != record.getInstallRecordStatus()) {
            return ReturnResult.illegal("软件未安装");
        }
        SoftSource source = sourceService.getById(record.getInstallRecordSourceId());
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        SoftwareSource uninstallSource = getSoftwareSource(source);
        if (uninstallSource == null) {
            return ReturnResult.illegal("不支持的软件源");
        }

        return transactionTemplate.execute(status -> {
            record.setInstallRecordStatus(InstallStatus.UNINSTALLING.getValue());
            record.setInstallRecordOperation("uninstall");
            record.setInstallRecordStartTime(LocalDateTime.now());
            installRecordMapper.updateById(record);

            try {
                /**
                 * 更新进步。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                updateProgress(record.getInstallRecordId(), 50, "正在卸载");
                SoftwareSourceResult result = uninstallSource.uninstall(source, software, record.getInstallRecordInstallPath());
                /**
                 * 完成uninstall。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                completeUninstall(record.getInstallRecordId());
                return ReturnResult.ok(toDetailResponse(record, software));
            } catch (Exception e) {
                /**
                 * 失败uninstall。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                failUninstall(record.getInstallRecordId(), e.getMessage());
                return ReturnResult.illegal("卸载失败: " + e.getMessage());
            }
        });
    }

    @Override
    /**
     * 更新版本
    */
    public ReturnResult<InstallDetailResponse> updateVersion(String softwareId, String userId, String deviceId, String targetVersion) {
        SoftSoftware software = softwareService.getById(softwareId);
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }
        if (StringUtils.isBlank(targetVersion)) {
            return ReturnResult.illegal("目标版本不能为空");
        }
        SoftInstallRecord record = getInstalledRecord(softwareId, userId, deviceId);
        if (record == null || InstallStatus.INSTALLED.getValue() != record.getInstallRecordStatus()) {
            return ReturnResult.illegal("软件未安装");
        }
        SoftSource source = sourceService.getById(software.getSoftwareSourceId());
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        SoftwareSource softwareSourceImpl = getSoftwareSource(source);
        if (softwareSourceImpl == null) {
            return ReturnResult.illegal("不支持的软件源");
        }

        return transactionTemplate.execute(status -> {
            record.setInstallRecordStatus(InstallStatus.UPDATING.getValue());
            record.setInstallRecordOperation("update");
            record.setInstallRecordTargetVersion(targetVersion);
            record.setInstallRecordStartTime(LocalDateTime.now());
            installRecordMapper.updateById(record);

            try {
                /**
                 * 更新进步。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                updateProgress(record.getInstallRecordId(), 50, "正在更新");
                SoftwareSourceResult result = softwareSourceImpl.update(source, software, targetVersion, record.getInstallRecordInstallPath());
                /**
                 * 完成install。
                 * @param record.getInstallRecordId( record.获取installrecordid(
                 */
                completeInstall(record.getInstallRecordId());
                record.setInstallRecordVersion(targetVersion);
                installRecordMapper.updateById(record);
                return ReturnResult.ok(toDetailResponse(record, software));
            } catch (Exception e) {
                record.setInstallRecordStatus(InstallStatus.UPDATE_FAILED.getValue());
                record.setInstallRecordErrorMessage(e.getMessage());
                record.setInstallRecordEndTime(LocalDateTime.now());
                installRecordMapper.updateById(record);
                return ReturnResult.illegal("更新失败: " + e.getMessage());
            }
        });
    }

    @Override
    /**
     * 更新进步
    */
    public ReturnResult<Boolean> updateProgress(String recordId, Integer progress, String message) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        record.setInstallRecordProgress(progress);
        installRecordMapper.updateById(record);
        return ReturnResult.ok(true);
    }

    @Override
    /**
     * 完成install
    */
    public ReturnResult<Boolean> completeInstall(String recordId) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        record.setInstallRecordStatus(InstallStatus.INSTALLED.getValue());
        record.setInstallRecordProgress(100);
        record.setInstallRecordEndTime(LocalDateTime.now());
        long startEpoch = record.getInstallRecordStartTime().toEpochSecond(java.time.ZoneOffset.ofHours(8));
        long endEpoch = record.getInstallRecordEndTime().toEpochSecond(java.time.ZoneOffset.ofHours(8));
        record.setInstallRecordDuration((endEpoch - startEpoch) * 1000);
        installRecordMapper.updateById(record);
        return ReturnResult.ok(true);
    }

    @Override
    /**
     * 失败install
    */
    public ReturnResult<Boolean> failInstall(String recordId, String errorMessage) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        record.setInstallRecordStatus(InstallStatus.FAILED.getValue());
        record.setInstallRecordErrorMessage(errorMessage);
        record.setInstallRecordEndTime(LocalDateTime.now());
        installRecordMapper.updateById(record);
        return ReturnResult.ok(false);
    }

    @Override
    /**
     * 完成uninstall
    */
    public ReturnResult<Boolean> completeUninstall(String recordId) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        record.setInstallRecordStatus(InstallStatus.UNINSTALLED.getValue());
        record.setInstallRecordProgress(100);
        record.setInstallRecordEndTime(LocalDateTime.now());
        long startEpoch = record.getInstallRecordStartTime().toEpochSecond(java.time.ZoneOffset.ofHours(8));
        long endEpoch = record.getInstallRecordEndTime().toEpochSecond(java.time.ZoneOffset.ofHours(8));
        record.setInstallRecordDuration((endEpoch - startEpoch) * 1000);
        installRecordMapper.updateById(record);
        return ReturnResult.ok(true);
    }

    @Override
    /**
     * 失败uninstall
    */
    public ReturnResult<Boolean> failUninstall(String recordId, String errorMessage) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        record.setInstallRecordStatus(InstallStatus.FAILED.getValue());
        record.setInstallRecordErrorMessage(errorMessage);
        record.setInstallRecordEndTime(LocalDateTime.now());
        installRecordMapper.updateById(record);
        return ReturnResult.ok(false);
    }

    @Override
    /**
     * 列表bysoftwareid
    */
    public List<SoftInstallRecord> listBySoftwareId(String softwareId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordSoftwareId, softwareId)
                .orderByDesc(SoftInstallRecord::getInstallRecordCreateTime));
    }

    @Override
    /**
     * 列表by用户标识
    */
    public List<SoftInstallRecord> listByUserId(String userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordUserId, userId)
                .orderByDesc(SoftInstallRecord::getInstallRecordCreateTime));
    }

    @Override
    /**
     * 列表installedby用户标识
    */
    public List<SoftInstallRecord> listInstalledByUserId(String userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordUserId, userId)
                .eq(SoftInstallRecord::getInstallRecordStatus, InstallStatus.INSTALLED.getValue()));
    }

    @Override
    /**
     * 获取installedrecord
    */
    public SoftInstallRecord getInstalledRecord(String softwareId, String userId, String deviceId) {
        LambdaQueryWrapper<SoftInstallRecord> w = new LambdaQueryWrapper<>();
        w.eq(SoftInstallRecord::getInstallRecordSoftwareId, softwareId)
                .and(q -> q.eq(SoftInstallRecord::getInstallRecordUserId, userId)
                        .eq(SoftInstallRecord::getInstallRecordDeviceId, deviceId))
                .orderByDesc(SoftInstallRecord::getInstallRecordCreateTime);
        return baseMapper.selectOne(w);
    }

    @Override
    /**
     * 获取installedsoftware标识
    */
    public List<String> getInstalledSoftwareIds(String userId) {
        List<SoftInstallRecord> records = listInstalledByUserId(userId);
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(SoftInstallRecord::getInstallRecordSoftwareId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    /**
     * 获取installdetail
    */
    public ReturnResult<InstallDetailResponse> getInstallDetail(String recordId) {
        if (StringUtils.isBlank(recordId)) {
            return ReturnResult.illegal("记录ID不能为空");
        }
        SoftInstallRecord record = getById(recordId);
        if (record == null) {
            return ReturnResult.illegal("记录不存在");
        }
        SoftSoftware software = softwareService.getById(record.getInstallRecordSoftwareId());
        return ReturnResult.ok(toDetailResponse(record, software));
    }

    /**
     * 获取可用的软件源实现
     *
     * @param source 软件源配置
     * @return 软件源实现，如果没有可用实现则返回null
     */
    private SoftwareSource getSoftwareSource(SoftSource source) {
        for (SoftwareSource softwareSource : softwareSources) {
            if (softwareSource.support(source)) {
                return softwareSource;
            }
        }
        return null;
    }

    /**
     * 转换为详情响应对象
     *
     * @param record 安装记录
     * @param software 软件信息
     * @return 详情响应
     */
    private InstallDetailResponse toDetailResponse(SoftInstallRecord record, SoftSoftware software) {
        InstallDetailResponse response = new InstallDetailResponse();
        response.setInstallRecordId(record.getInstallRecordId());
        response.setInstallRecordSoftwareId(record.getInstallRecordSoftwareId());
        response.setInstallRecordUserId(record.getInstallRecordUserId());
        response.setInstallRecordDeviceId(record.getInstallRecordDeviceId());
        response.setInstallRecordSourceId(record.getInstallRecordSourceId());
        response.setInstallRecordVersion(record.getInstallRecordVersion());
        response.setInstallRecordTargetVersion(record.getInstallRecordTargetVersion());
        response.setInstallRecordOperation(record.getInstallRecordOperation());
        response.setInstallRecordStatus(record.getInstallRecordStatus());
        response.setInstallRecordProgress(record.getInstallRecordProgress());
        response.setInstallRecordStartTime(record.getInstallRecordStartTime());
        response.setInstallRecordEndTime(record.getInstallRecordEndTime());
        response.setInstallRecordDuration(record.getInstallRecordDuration());
        response.setInstallRecordErrorMessage(record.getInstallRecordErrorMessage());
        response.setInstallRecordErrorDetail(record.getInstallRecordErrorDetail());
        response.setInstallRecordLogPath(record.getInstallRecordLogPath());
        response.setInstallRecordDownloadPath(record.getInstallRecordDownloadPath());
        response.setInstallRecordInstallPath(record.getInstallRecordInstallPath());
        response.setInstallRecordRollbackVersion(record.getInstallRecordRollbackVersion());
        response.setInstallRecordExtra(record.getInstallRecordExtra());
        response.setInstallRecordCreateTime(record.getInstallRecordCreateTime());
        response.setInstallRecordUpdateTime(record.getInstallRecordUpdateTime());
        if (software != null) {
            response.setSoftwareName(software.getSoftwareName());
            response.setSoftwareVersion(software.getSoftwareVersion());
            response.setSoftwareIcon(software.getSoftwareIcon());
            response.setStatusText(InstallStatus.fromValue(record.getInstallRecordStatus()).getName());
        }
        return response;
    }
}
