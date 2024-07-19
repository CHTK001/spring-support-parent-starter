package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
/**
 * MonitorSysGenService接口定义了系统生成监控服务的行为。
 * 该接口继承自IService<MonitorSysGen>，表示它服务的对象是MonitorSysGen类型。
 * 主要功能包括对监控系统生成对象的更新操作。
 */
public interface MonitorSysGenService extends IService<MonitorSysGen>{

    /**
     * 更新监控系统生成对象的信息。
     * 此方法用于根据新的系统生成对象和旧的系统生成对象来更新数据库中的相关记录。
     * 参数newSysGen代表新生成的系统配置信息，oldSysGen代表原有的系统配置信息。
     * 通过比较新旧信息的差异，来确定并更新数据库中需要变更的部分。
     *
     * @param newSysGen 新的监控系统生成对象，包含需要更新的数据。
     * @param oldSysGen 旧的监控系统生成对象，用于对比和确定需要更新的数据。
     */
    void updateFor(MonitorSysGen newSysGen, MonitorSysGen oldSysGen);


    /**
     * 根据ID和旧的系统生成对象删除相关信息。
     *
     * 此方法用于根据提供的ID和旧的系统生成对象来删除特定的资源或信息。这可能是数据库中的一条记录，
     * 文件系统中的一个文件，或者其他任何形式的数据。方法的目的是为了在系统更新或迁移时，
     * 能够准确地删除旧的、不再需要的数据，以保持系统的整洁和性能。
     *
     * @param id 要删除的资源或信息的唯一标识符。这个ID是用来精确地定位要删除的特定资源或信息。
     * @param oldSysGen 旧的系统生成对象，这个对象包含了与要删除的资源或信息相关的一些元数据或配置信息。
     *                  通过这个对象，方法可能能够获取到一些额外的信息，以确定如何正确地执行删除操作。
     * @return 返回一个Boolean值，表示删除操作是否成功。如果删除成功，返回true；如果删除失败，返回false。
     */
    Boolean deleteFor(String id, MonitorSysGen oldSysGen);
}
