package com.chua.starter.device.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.device.support.mapper.DeviceTypeMapper;
import com.chua.starter.device.support.entity.DeviceType;
import com.chua.starter.device.support.service.DeviceTypeService;
@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService{

}
