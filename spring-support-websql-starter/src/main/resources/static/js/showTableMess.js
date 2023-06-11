
var dgg;
var tableName;
var databaseName;
var databaseConfigId;
$(function(){
    databaseName = $("#databaseName").val();
    tableName = $("#tableName").val();
    databaseConfigId = $("#databaseConfigId").val();
    initData();
});


function initData(){

    dgg=$('#dg3').datagrid({
        method: "get",
        url: baseUrl + '/system/permission/i/viewTableMess/'+tableName+'/'+databaseName+'/'+databaseConfigId,
        fit : true,
        fitColumns : true,
        border : false,
        idField : 'treeSoftPrimaryKey',
        striped:true,
        pagination:true,
        rownumbers:true,
        pageNumber:1,
        pageSize : 20,
        pageList : [ 10, 20, 30, 40, 50 ],
        singleSelect:false,
        columns:[[
            {field:'TREESOFTPRIMARYKEY',checkbox:true},
            {field:'propName',title:'名称', width:30   },
            {field:'propValue',title:'信息',width:50   }
        ]],
        checkOnSelect:true,
        selectOnCheck:true,
        extEditing:false,
        toolbar:'#tb3',
        autoEditing: true,     //该属性启用双击行时自定开启该行的编辑状态
        singleEditing: true


    });

}


function refresh(){
    $('#dg3').datagrid('reload');
    $("#dg3").datagrid('clearSelections').datagrid('clearChecked');
}