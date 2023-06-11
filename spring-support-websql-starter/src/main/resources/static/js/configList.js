var dgg;
var config;
$(function(){
    initData();
});
function initData(){
    dgg=$('#dg3').datagrid({
        method: "get",
        url: baseUrl + '/system/permission/i/configList/'+Math.random(),
        fit : true,
        fitColumns : true,
        border : false,
        idField : 'id',
        striped:true,
        pagination:true,
        rownumbers:true,
        pageNumber:1,
        pageSize : 20,
        pageList : [ 10, 20, 30, 40, 50 ],
        singleSelect:false,
        columns:[[
            {field:'TREESOFTPRIMARYKEY',checkbox:true},
            {field:'id',title:'id',sortable:true,width:30},
            {field:'name',title:'名称',width:100 },
            {field:'databaseType',title:'数据库类型',sortable:true,width:100 },
            {field:'databaseName',title:'默认数据库',sortable:true,width:100 },
            {field:'ip',title:'IP',sortable:true,width:100},
            {field:'port',title:'端口',sortable:true,width:100},
            {field:'userName',title:'用户名',sortable:true,width:100},
            {field:'isdefault',title:'是否默认',sortable:true,formatter: function(value,row,index){
                    if (row.isdefault=='1'){
                        return '是';
                    } else {
                        return '';
                    }
                }}
        ] ],
        checkOnSelect:true,
        selectOnCheck:true,
        extEditing:false,
        toolbar:'#tb3',
        autoEditing: false,     //该属性启用双击行时自定开启该行的编辑状态
        singleEditing: false,
        onDblClickRow: function (rowIndex, rowData) {
            var id =rowData.id  ;
            config = $("#dlgg").dialog({
                title: '查看',
                width: 350,
                height: 320,
                href:baseUrl + '/system/permission/i/editConfigForm/'+id,
                maximizable:true,
                modal:true,
                buttons:[
                    {
                        text:'测试',
                        iconCls:'icon-search',
                        handler:function(){
                            testConn();
                        }
                    },
                    {
                        text:'取消',
                        iconCls:'icon-cancel',
                        handler:function(){
                            config.panel('close');
                        }
                    }]
            });
        }
    });
}
//打开 新增 编辑 对话框
function addConfigForm(){
    config = $("#dlgg").dialog({
        title: ' 新增',
        width: 350,
        height: 320,
        href:baseUrl +'/system/permission/i/addConfigForm',
        maximizable:true,
        modal:true,
        buttons:[{
            text:'测试',
            iconCls:'icon-search',
            handler:function(){
                testConn();
            }
        },
            {
                text:'保存',
                iconCls:'icon-ok',
                handler:function(){
                    configUpdate2();
                    //$("#mainform").submit();
                }
            },{
                text:'取消',
                iconCls:'icon-cancel',
                handler:function(){
                    config.panel('close');
                }
            }]
    });
}
function editConfigForm(){
    var checkedItems = $('#dg3').datagrid('getChecked');
    if(checkedItems.length == 0 ){
        parent.$.messager.show({ title : "提示",msg: "请先选择一行数据！", position: "bottomRight" });
        return;
    }
    if(checkedItems.length >1 ){
        parent.$.messager.show({ title : "提示",msg: "请选择一行数据！", position: "bottomRight" });
        return;
    }
    var id = checkedItems[0]['id']  ;
    config= $("#dlgg").dialog({
        title: '编辑',
        width: 350,
        height: 320,
        href:baseUrl + '/system/permission/i/editConfigForm/'+id,
        maximizable:true,
        modal:true,
        buttons:[{
            text:'测试',
            iconCls:'icon-search',
            handler:function(){
                testConn();
            }
        },
            {
                text:'保存',
                iconCls:'icon-ok',
                handler:function(){
                    configUpdate2();
                    //$("#mainform").submit();
                }
            },{
                text:'取消',
                iconCls:'icon-cancel',
                handler:function(){
                    config.panel('close');
                }
            }]
    });
}
//删除
function deleteConfig(){
    var checkedItems = $('#dg3').datagrid('getChecked');
    var length = checkedItems.length;
    var data2=$('#dg3').datagrid('getData');
    var totalLength = data2.total;
    // alert('总数据量:'+data.total)
    if(totalLength - length <= 0 ){
        parent.$.messager.show({ title : "提示",msg: "必须保留一行配置信息！", position: "bottomRight" });
        return ;
    }
    if(checkedItems.length == 0 ){
        parent.$.messager.show({ title : "提示",msg: "请先选择一行数据！", position: "bottomRight" });
        return ;
    }
    var ids = [];
    $.each( checkedItems, function(index, item){
        ids.push( item["id"] );
    });
    $.easyui.messager.confirm("操作提示", "您确定要删除"+length+"行数据吗？", function (c) {
        if (c) {
            $.ajax({
                type:'POST',
                contentType:'application/json;charset=utf-8',
                url:baseUrl + "/system/permission/i/deleteConfig",
                data: JSON.stringify( { 'ids':ids } ),
                datatype: "json",
                //成功返回之后调用的函数
                success:function(data){
                    var status = data.status ;
                    if(status == 'success' ){
                        $('#dg3').datagrid('reload');
                        $("#dg3").datagrid('clearSelections').datagrid('clearChecked');
                        parent.$.messager.show({ title : "提示",msg: data.mess, position: "bottomRight" });
                    }else{
                        parent.$.messager.show({ title : "提示",msg: data.mess, position: "bottomRight" });
                    }
                }
            });
        }
    });
}
function refresh(){
    $('#dg3').datagrid('reload');
    $("#dg3").datagrid('clearSelections').datagrid('clearChecked');
}

