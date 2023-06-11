var connSuccess = false;
//测试连接
function testConn() {
    $("#mess2").html("连接测试中...");
    $.ajax({
        type : 'POST',
        contentType : 'application/json;charset=utf-8',
        url : baseUrl + "/system/permission/i/testConn",
        data : JSON.stringify({
            'databaseType' : $("#databaseType").val(),
            'databaseName' : $("#databaseName").val(),
            'userName' : $("#userName").val(),
            'password' : $("#password").val(),
            'port' : $("#port").val(),
            'ip' : $("#ip").val()
        }),
        datatype : "json",
        //成功返回之后调用的函数
        success : function(data) {
            var status = data.status;
            if (status == 'success') {
                $("#mess2").html(data.mess);
                connSuccess = true;
                //alert( data.mess );
            } else {
                connSuccess = false;
                $("#mess2").html("连接失败！");
            }
        }
    });
}
function configUpdate2() {
    var id = $('#id').val();
    var name = $('#name').val();
    var databaseType = $('#databaseType option:selected').val();
    var databaseName = $('#databaseName').val();
    var ip = $('#ip').val();
    var port = $('#port').val();
    var userName = $('#userName').val();
    var password = $('#password').val();
    var isdefault = $('#isdefault').val();
    var isValid = $("#mainform").form('validate');
    if (!isValid) {
        return isValid; // 返回false终止表单提交
    }
    $.ajax({
        type : 'POST',
        contentType : 'application/json;charset=utf-8',
        url : baseUrl + "/system/permission/i/configUpdate",
        data : JSON.stringify({
            'id' : id,
            'name' : name,
            'databaseType' : databaseType,
            'databaseName' : databaseName,
            'ip' : ip,
            'port' : port,
            'userName' : userName,
            'password' : password,
            'isdefault' : isdefault
        }),
        datatype : "json",
        //成功返回之后调用的函数
        success : function(data) {
            var status = data.status;
            if (status == 'success') {
                parent.$.messager.show({
                    title : "提示",
                    msg : data.mess,
                    position : "bottomRight"
                });
                setTimeout(function() {
                    config.panel('close');
                    $("#dg3").datagrid('reload');
                }, 1500);
                parent.init3();
            } else {
                parent.$.messager.show({
                    title : "提示",
                    msg : data.mess,
                    position : "bottomRight"
                });
            }
        }
    });
}
