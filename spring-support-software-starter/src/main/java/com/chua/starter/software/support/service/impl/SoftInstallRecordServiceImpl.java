// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.software.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
import com.chua.starter.software.support.realtime.InstallProgressDispatcher;
import com.chua.starter.software.support.realtime.InstallProgressEvent;
import com.chua.starter.software.support.realtime.InstallProgressPublisher;
import com.chua.starter.software.support.service.SoftInstallRecordService;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.SoftSourceService;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.chua.starter.software.support.spi.SoftwareSourceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
                public class SoftInstallRecordServiceImpl extends ServiceImpl<SoftInstallRecordMapper, SoftInstallRecord
                    > implements SoftInstallRecordService {

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

      /**
       * 安装进度发布器
       *
       * <p>用 {@link ObjectProvider} 延迟解析：本模块可脱离实时通道独立启动（如单独跑
       * {@code SoftStarterApplication}），缺 Bean 时退化为静默跳过，不允许因推送失败打断安装。</p>
       */
    private final ObjectProvider<InstallProgressPublisher> progressPublisherProvider;

    @Override
      /**
       * Install
       * @param request 参数
       * @return 返回值
      */
    public ReturnResult<InstallDetailResponse> install(InstallRequest request) {
        SoftSoftware software = softwareService.getById(request.softwareId());
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }
          // 检查是否已安装
        SoftInstallRecord exist = getInstalledRecord(request.softwareId(), request.userId(), request.deviceId());
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
            record.setInstallRecordSoftwareId(request.softwareId());
            record.setInstallRecordUserId(request.userId());
            record.setInstallRecordDeviceId(request.deviceId());
            record.setInstallRecordSourceId(software.getSoftwareSourceId());
            record.setInstallRecordVersion(software.getSoftwareVersion());
            record.setInstallRecordOperation("install");
            record.setInstallRecordStatus(InstallStatus.INSTALLING.getValue());
            record.setInstallRecordProgress(0);
            record.setInstallRecordStartTime(LocalDateTime.now());
            record.setInstallRecordInstallPath(request.installPath());
            installRecordMapper.insert(record);
              // 先播一帧 0%：发起人的页面可能在请求返回前就已经在等进度，
              // 不必等到第一个 updateProgress 才开始显示
            publishProgress(record, "开始安装");

            try {
                  // 模拟进度
                  /**
                   * 更新进步。
                   * @param record.getInstallRecordId( record.获取installrecordid(
                   */
                updateProgress(record.getInstallRecordId(), 30, "准备安装");
                SoftwareSourceResult result = installSource.install(source, software, request.installPath());
                  /**
                   * 更新进步。
                   * @param record.getInstallRecordId( record.获取installrecordid(
                   */
                updateProgress(record.getInstallRecordId(), 100, result.message());
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
       * @param deviceId 字符串
       * @param softwareId 字符串
       * @param userId 字符串
       * @return 返回值
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
            record.setInstallRecordProgress(0);
            record.setInstallRecordStartTime(LocalDateTime.now());
            record.setInstallRecordErrorMessage(null);
            installRecordMapper.updateById(record);
            publishProgress(record, "开始卸载");

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
       * @param deviceId 字符串
       * @param softwareId 字符串
       * @param targetVersion 字符串
       * @param userId 字符串
       * @return 返回值
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
            record.setInstallRecordProgress(0);
            record.setInstallRecordStartTime(LocalDateTime.now());
            record.setInstallRecordErrorMessage(null);
            installRecordMapper.updateById(record);
            publishProgress(record, "开始更新");

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
                publishProgress(record, e.getMessage());
                return ReturnResult.illegal("更新失败: " + e.getMessage());
            }
        });
    }

    @Override
      /**
       * 更新进步
       * @param message 字符串
       * @param progress 整数
       * @param recordId 字符串
       * @return 返回值
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
        publishProgress(record, message);
        return ReturnResult.ok(true);
    }

      /**
       * 推送一帧安装进度
       *
       * <p>推送失败只意味着页面看不到这一帧，不能让已经落库的状态被回滚或把接口变成 500，
       * 因此这里吞掉所有异常（分发器内部另有兜底，这里再兜一层是防止 SPI 实现违规抛出）。</p>
       *
       * <p><b>为什么等到事务提交再推</b>：安装流程跑在 {@code transactionTemplate} 里，
       * 如果在事务内直接广播「安装完成 100%」，而随后的落库语句失败导致事务回滚，
       * 前端会看到一个数据库里并不存在的成功状态，刷新列表又变回失败——这种前后矛盾
       * 比晚几百毫秒更糟。因此在事务活跃时把事件快照挂到提交回调上，
       * 回滚则整帧丢弃，不做「先说成功再撤回」。</p>
       *
       * @param record  安装记录（进度推送的载荷来源）
       * @param message 进度说明，可为 {@code null}
       */
    private void publishProgress(SoftInstallRecord record, String message) {
        if (record == null) {
            return;
        }
        InstallProgressPublisher publisher;
        try {
            publisher = progressPublisherProvider.getIfAvailable();
        } catch (Exception e) {
              // 容器里出现了多个 InstallProgressPublisher（业务方自建实现与本模块兜底并存）时，
              // getIfAvailable 会抛 NoUniqueBeanDefinitionException。这里退化为「这一帧不发」，
              // 而不是让一次可用的安装因为装配歧义变成接口 500。
            log.warn("[Software][Progress] 进度发布器装配歧义，跳过本帧: recordId={}, reason={}",
                    record.getInstallRecordId(), e.getMessage());
            return;
        }
        if (publisher == null) {
            return;
        }
        InstallProgressEvent event;
        try {
              // 先快照：事务提交前记录对象还可能被后续 set 改掉（如完成安装时回填版本号）
            event = InstallProgressEvent.of(record, message);
        } catch (Exception e) {
            log.warn("[Software][Progress] 进度事件构造失败: recordId={}, reason={}",
                    record.getInstallRecordId(), e.getMessage());
            return;
        }
        new InstallProgressDispatcher(publisher).dispatch(event);
    }

    @Override
      /**
       * 完成install
       * @param recordId 字符串
       * @return 返回值
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
        publishProgress(record, "安装完成");
        return ReturnResult.ok(true);
    }

    @Override
      /**
       * 失败install
       * @param errorMessage 字符串
       * @param recordId 字符串
       * @return 返回值
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
        publishProgress(record, errorMessage);
        return ReturnResult.ok(false);
    }

    @Override
      /**
       * 完成uninstall
       * @param recordId 字符串
       * @return 返回值
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
        publishProgress(record, "卸载完成");
        return ReturnResult.ok(true);
    }

    @Override
      /**
       * 失败uninstall
       * @param errorMessage 字符串
       * @param recordId 字符串
       * @return 返回值
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
        publishProgress(record, errorMessage);
        return ReturnResult.ok(false);
    }

    @Override
      /**
       * 列表bysoftwareid
       * @param softwareId 字符串
       * @return 返回值
      */
    public List<SoftInstallRecord> listBySoftwareId(String softwareId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordSoftwareId, softwareId)
                .orderByDesc(SoftInstallRecord::getInstallRecordCreateTime));
    }

    @Override
      /**
       * 列表by用户标识
       * @param userId 字符串
       * @return 返回值
      */
    public List<SoftInstallRecord> listByUserId(String userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordUserId, userId)
                .orderByDesc(SoftInstallRecord::getInstallRecordCreateTime));
    }

    @Override
      /**
       * 列表installedby用户标识
       * @param userId 字符串
       * @return 返回值
      */
    public List<SoftInstallRecord> listInstalledByUserId(String userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftInstallRecord>()
                .eq(SoftInstallRecord::getInstallRecordUserId, userId)
                .eq(SoftInstallRecord::getInstallRecordStatus, InstallStatus.INSTALLED.getValue()));
    }

    @Override
      /**
       * 获取installedrecord
       * @param deviceId 字符串
       * @param softwareId 字符串
       * @param userId 字符串
       * @return 返回值
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
       * @param userId 字符串
       * @return 返回值
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
       * @param recordId 字符串
       * @return 返回值
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