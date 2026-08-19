package com.chua.starter.software.support.service.impl.local;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.mapper.SoftSoftwareMapper;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.SoftSourceService;
import com.chua.starter.software.support.service.local.LocalSoftwareService;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.chua.starter.software.support.spi.SoftwareSourceResult;
import com.chua.starter.software.support.spi.local.LocalSoftwareSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.chua.starter.software.support.enums.SourceType.LOCAL;

/**
 * 本地软件服务实现
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Service
public class LocalSoftwareServiceImpl implements LocalSoftwareService {

    /** Transaction模板 */
    private final TransactionTemplate transactionTemplate;
    /** Soft来源服务 */
    private final SoftSourceService softSourceService;
    /** Softsoftware服务 */
    private final SoftSoftwareService softSoftwareService;
    /** Softsoftwaremapper */
    private final SoftSoftwareMapper softSoftwareMapper;
    /** 本地software来源 */
    private final LocalSoftwareSource localSoftwareSource;
    /** Softwaresources */
    private final List<SoftwareSource> softwareSources;

    @Autowired
    public LocalSoftwareServiceImpl(TransactionTemplate transactionTemplate,
                                    SoftSourceService softSourceService,
                                    SoftSoftwareService softSoftwareService,
                                    SoftSoftwareMapper softSoftwareMapper,
                                    LocalSoftwareSource localSoftwareSource,
                                    List<SoftwareSource> softwareSources) {
        this.transactionTemplate = transactionTemplate;
        this.softSourceService = softSourceService;
        this.softSoftwareService = softSoftwareService;
        this.softSoftwareMapper = softSoftwareMapper;
        this.localSoftwareSource = localSoftwareSource;
        this.softwareSources = softwareSources;
    }

    @Override
    public int scanLocalSoftware(String sourceId) {
        if (StringUtils.isBlank(sourceId)) {
            sourceId = getLocalSourceId();
        }
        SoftSource source = softSourceService.getById(sourceId);
        if (source == null) {
            log.error("本地软件源不存在，sourceId={}", sourceId);
            return 0;
        }

        String finalSourceId = sourceId;
        return transactionTemplate.execute(status -> {
            // 1. 先软删除之前的所有本地软件
            int deleted = softSoftwareMapper.updateStatusBySourceId(finalSourceId, 1);
            log.info("本次清空已删除 {} 条旧本地软件", deleted);

            // 2. 调用 SPI 采集
            List<SoftSoftware> collected = localSoftwareSource.fetchSoftwareList(source);
            if (CollectionUtils.isEmpty(collected)) {
                log.info("本次扫描未发现新本地软件");
                return 0;
            }

            int count = 0;
            for (SoftSoftware software : collected) {
                // 必填字段 set
                software.setSoftwareSourceId(finalSourceId);
                software.setSoftwareStatus(0);
                software.setSoftwareCreateTime(LocalDateTime.now());
                software.setSoftwareUpdateTime(LocalDateTime.now());
                software.setSoftwareIsOfficial(false);
                software.setSoftwareIsRecommended(false);
                software.setSoftwareIsFree(true);

                // 根据 code 判断是否已存在
                SoftSoftware exist = softSoftwareService.getByCode(software.getSoftwareCode());
                if (exist != null && exist.getSoftwareStatus() == 0) {
                    software.setSoftwareId(exist.getSoftwareId());
                    softSoftwareMapper.updateById(software);
                } else {
                    softSoftwareMapper.insert(software);
                }
                count++;
            }
            log.info("本次扫描共保存 {} 个本地软件", count);
            return count;
        });
    }

    @Override
    public boolean clearLocalSoftware(String sourceId) {
        if (StringUtils.isBlank(sourceId)) {
            sourceId = getLocalSourceId();
        }
        int rows = softSoftwareMapper.updateStatusBySourceId(sourceId, 1);
        return rows > 0;
    }

    @Override
    public ReturnResult<Object> installLocalSoftware(InstallRequest request) {
        // 从本地软件列表安装，本质上是检查软件是否存在于本地源，然后创建安装记录
        SoftSoftware software = softSoftwareService.getById(request.getSoftwareId());
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }

        // 检查是否属于本地源
        String localSourceId = getLocalSourceId();
        if (!localSourceId.equals(software.getSoftwareSourceId())) {
            return ReturnResult.illegal("软件不属于本地源");
        }

        // 直接创建安装记录（状态为已安装）
        // TODO: 调用真正的安装流程（需要执行可执行文件等），这里仅记录管理状态
        // 使用 SoftInstallRecordService 的逻辑?
        log.info("管理员手动安装本地软件: softwareId={}, userId={}, installPath={}",
                request.getSoftwareId(), request.getUserId(), request.getInstallPath());
        return ReturnResult.ok("安装成功（模拟）");
    }

    @Override
    public ReturnResult<Object> uninstallLocalSoftware(String softwareId, String userId, String deviceId) {
        SoftSoftware software = softSoftwareService.getById(softwareId);
        if (software == null) {
            return ReturnResult.illegal("软件不存在");
        }

        // 软删除软件记录
        software.setSoftwareStatus(1);
        software.setSoftwareUpdateTime(LocalDateTime.now());
        softSoftwareService.updateSoftware(software);
        log.info("管理员从管理系统卸载本地软件: softwareId={}, userId={}", softwareId, userId);
        return ReturnResult.ok("卸载成功（标记删除）");
    }

    @Override
    public ReturnResult<SoftSoftware> addLocalSoftware(SoftSoftware software) {
        if (StringUtils.isBlank(software.getSoftwareName())) {
            return ReturnResult.illegal("软件名称不能为空");
        }
        // 自动赋值本地源
        String localSourceId = getLocalSourceId();
        software.setSoftwareSourceId(localSourceId);
        software.setSoftwareStatus(0);
        software.setSoftwareIsOfficial(false);
        software.setSoftwareIsRecommended(false);
        software.setSoftwareIsFree(true);
        String code = software.getSoftwareCode();
        if (StringUtils.isBlank(code)) {
            code = software.getSoftwareName().replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "");
            software.setSoftwareCode(code);
        }
        return softSoftwareService.saveSoftware(software);
    }

    @Override
    public List<SoftSoftware> listLocalSoftware(String keyword) {
        String localSourceId = getLocalSourceId();
        LambdaQueryWrapper<SoftSoftware> wrapper = new LambdaQueryWrapper<SoftSoftware>()
                .eq(SoftSoftware::getSoftwareSourceId, localSourceId)
                .eq(SoftSoftware::getSoftwareStatus, 0)
                .like(StringUtils.isNotBlank(keyword), SoftSoftware::getSoftwareName, keyword)
                .orderByAsc(SoftSoftware::getSoftwareName);
        return softSoftwareService.list(wrapper);
    }

    @Override
    public String getLocalSourceId() {
        // 查询默认本地源
        SoftSource localSource = softSourceService.getDefaultLocalSource();
        if (localSource == null) {
            // 没有则创建默认本地源
            SoftSource source = new SoftSource();
            source.setSourceName("本地系统源");
            source.setSourceCode("LOCAL_SYSTEM_SOURCE");
            source.setSourceType(LOCAL.getValue());
            source.setSourceIsEnabled(true);
            source.setSourceIsDefault(true);
            source.setSourcePriority(0);
            source.setSourceAutoSync(false);
            source.setSourceSyncInterval(60);
            source.setSourceStatus(0);
            softSourceService.saveSource(source);
            return source.getSourceId();
        }
        return localSource.getSourceId();
    }
}