package com.chua.starter.lowcode.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.lowcode.entity.LowFormVersion;
import com.chua.starter.lowcode.mapper.LowFormVersionMapper;
import com.chua.starter.lowcode.service.LowFormVersionService;
import com.chua.starter.lowcode.vo.LowFormVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 低代码-表单版本服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowFormVersionServiceImpl extends ServiceImpl<LowFormVersionMapper, LowFormVersion>
        implements LowFormVersionService {

    /**
     * 日期格式。
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<LowFormVersionVO> listByFormId(Integer formId) {
        List<LowFormVersion> versions = list(Wrappers.<LowFormVersion>lambdaQuery()
                .eq(LowFormVersion::getLowFormId, formId)
                .orderByDesc(LowFormVersion::getLowFormVersionNo));
        return versions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public LowFormVersionVO detail(Integer versionId) {
        return toVO(requireVersion(versionId));
    }

    /**
     * 按主键要版本，不存在直接拒绝。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    private LowFormVersion requireVersion(Integer versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("版本ID不能为空");
        }
        LowFormVersion version = getById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + versionId);
        }
        return version;
    }

    /**
     * 实体转出参。
     *
     * @param version 版本
     * @return 出参
     */
    private LowFormVersionVO toVO(LowFormVersion version) {
        LowFormVersionVO vo = new LowFormVersionVO();
        vo.setLowFormVersionId(version.getLowFormVersionId());
        vo.setLowFormId(version.getLowFormId());
        vo.setLowFormCode(version.getLowFormCode());
        vo.setLowFormVersionNo(version.getLowFormVersionNo());
        vo.setLowFormVersionSchema(version.getLowFormVersionSchema());
        vo.setLowFormVersionStatus(version.getLowFormVersionStatus());
        vo.setLowFormVersionPublishStatus(version.getLowFormVersionPublishStatus());
        vo.setLowFormVersionRemark(version.getLowFormVersionRemark());
        vo.setCreateTime(format(version.getCreateTime()));
        return vo;
    }

    /**
     * 格式化为字符串。
     *
     * @param time 时间
     * @return 字符串
     */
    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }
}
