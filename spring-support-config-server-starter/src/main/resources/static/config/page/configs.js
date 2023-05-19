
const config = function (profile) {
    return {
        url: "config-list?profile=" + profile,
        editable: !0,
        height: "100%",
        sidePagination: "server",           //分页方式：client客户端分页，server服务端分页（*）
        pageSize: 10,                     //每页的记录行数（*）
        pageList: [10, 25, 50, 100],        //可供选择的每页的行数（*）
        pagination: true,                   //是否显示分页（*）
        //得到查询的参数
        queryParams: function (params) {
            //这里的键的名字和控制器的变量名必须一直，这边改动，控制器也需要改成一样的
            return {
                pageSize: params.limit,                         //页面大小
                page: (params.offset / params.limit) + 1,   //页码
            };
        },
        onEditorSave: function (field, row, oldValue, $el) {
            $.ajax({
                url: 'config-update',
                type: 'post',
                data: {
                    'config-id': row['configId'],
                    'config-item': row['configItem'],
                    'config-value': row['configValue'],
                    'config-disable': row['disable'] == '是' ? 1 : (row['disable'] == '否' ? '0' : row['disable']),
                }
            }).done(xhr => {
                if(xhr && xhr['code'] == 200) {
                    toastr.info("修改成功");//提醒
                    return false;
                }
                toastr.error("修改失敗");//提醒
                return false;
            });
            return false;
        },
        responseHandler: function (res) {
            let data = res['data'] || {};
            return {
                "total": data.total || 0,
                "rows": data.data || []
            };
        },
        columns: [{
            field: 'configId',
            title: '序列'
        }, {
            field: 'configAppName',
            title: '配置名称'
        }, {
            field: 'configItem',
            title: '配置模块'
        }, {
            field: 'configName',
            title: '配置项'
        }, {
            field: 'configValue',
            title: '值',
            editable: !0
        }, {
            field: 'configDesc',
            title: '描述'
        }, {
            field: 'disable',
            title: '是否禁用',
            editable:{
                type:"select",
                options:{
                    items:[
                        "是","否"
                    ]
                }
            },
            formatter: function (row, data) {
                return data['disable'] == 0 ? '<span class="badge badge-primary">否</span>' : '<span class="badge badge-primary">是</span>';
            }
        }]
    }
}


function resetAmount(index, field, tdValue) {
    //更新单元格
    $('#exampleTable').bootstrapTable('updateCell', {
        index: index,
        field: field,
        value: tdValue
    })
}

$('#config-table-dev').bootstrapTable(config("dev"));
$('#config-table-prod').bootstrapTable(config("prod"));
$('#config-table-test').bootstrapTable(config("test"));