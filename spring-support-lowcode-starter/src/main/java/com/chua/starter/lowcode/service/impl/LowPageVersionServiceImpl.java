package com.chua.starter.lowcode.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.lowcode.entity.LowFormPageVersion;
import com.chua.starter.lowcode.mapper.LowFormPageVersionMapper;
import com.chua.starter.lowcode.service.LowPageVersionService;
import com.chua.starter.lowcode.vo.LowFormPageVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 低代码-页面版本服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowPageVersionServiceImpl extends ServiceImpl<LowFormPageVersionMapper, LowFormPageVersion>
        implements LowPageVersionService {

    /**
     * 日期格式。
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<LowFormPageVersionVO> listByPageId(Integer pageId) {
        List<LowFormPageVersion> versions = list(Wrappers.<LowFormPageVersion>lambdaQuery()
                .eq(LowFormPageVersion::getLowPageId, pageId)
                .orderByDesc(LowFormPageVersion::getLowPageVersionNo));
        return versions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public LowFormPageVersionVO detail(Integer versionId) {
        return toVO(requireVersion(versionId));
    }

    /**
     * 按主键要版本，不存在直接拒绝。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    private LowFormPageVersion requireVersion(Integer versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("版本ID不能为空");
        }
        LowFormPageVersion version = getById(versionId);
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
    private LowFormPageVersionVO toVO(LowFormPageVersion version) {
        LowFormPageVersionVO vo = new LowFormPageVersionVO();
        vo.setLowPageVersionId(version.getLowPageVersionId());
        vo.setLowPageId(version.getLowPageId());
        vo.setLowPageCode(version.getLowPageCode());
        vo.setLowPageVersionNo(version.getLowPageVersionNo());
        vo.setLowPageVersionSchema(version.getLowPageVersionSchema());
        vo.setLowPageVersionStatus(version.getLowPageVersionStatus());
        vo.setLowPageVersionPublishStatus(version.getLowPageVersionPublishStatus());
        vo.setLowPageVersionRemark(version.getLowPageVersionRemark());
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
