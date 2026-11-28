package com.chua.starter.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.entity.system.SysUserThirdParty;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户第三方绑定信息 映射器
 *
 * @since 2024/12/11
 * @author CH
 */
@Mapper
public interface SysUserThirdPartyMapper extends BaseMapper<SysUserThirdParty> {
}
