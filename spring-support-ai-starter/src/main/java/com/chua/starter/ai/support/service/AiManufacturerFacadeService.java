package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiManufacturer;

import java.util.List;

public interface AiManufacturerFacadeService {

    List<SysAiManufacturer> list();

    SysAiManufacturer save(SysAiManufacturer manufacturer);

    boolean update(SysAiManufacturer manufacturer);

    boolean delete(Integer id);

    boolean deleteById(Integer id);
}
