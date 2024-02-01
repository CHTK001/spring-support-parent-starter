package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.device.support.entity.DeviceType;
import com.chua.starter.device.support.mapper.DeviceTypeMapper;
import com.chua.starter.device.support.service.DeviceTypeService;
import org.springframework.stereotype.Service;
@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService{

}
