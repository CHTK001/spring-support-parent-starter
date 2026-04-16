package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证池 Mapper
 */
@Mapper
public interface SpiderCredentialMapper extends BaseMapper<SpiderCredential> {
}
