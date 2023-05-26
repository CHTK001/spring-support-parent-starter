<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>代码生成器</title>

    <#-- favicon -->
    <link rel="icon" href="${request.contextPath}/favicon.ico"/>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <!-- Tell the browser to be responsive to screen width -->
    <meta content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" name="viewport">
    <link href="/webjars/bootstrap/css/bootstrap.min.css" rel="stylesheet">
    <link href="/webjars/bootstrap-table/bootstrap-table.min.css" rel="stylesheet">
    <link href="/webjars/style.css" rel="stylesheet">
    <style>
        .modal-backdrop {
            opacity: 0 !important;
            filter: alpha(opacity=0) !important;
        }

        .form-check input {
            margin-left: 20px !important;
        }
    </style>
</head>
<body>
<select id="selectLeo" class="form-control form-control-placeholder col-md-6" style="width: 100px">
</select>

<div id="download" class="btn btn-default" style="width: 100px">
    下载
</div>
<div>
    <table id="table" class="table table-striped table-bordered"></table>
</div>

<!-- 模态框（Modal） -->
<div class="modal  modal-dialog-centered" data-backdrop="static" data-keyboard="false"
     id="identifier" tabindex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                <h4 class="modal-title" id="myModalLabel">导出</h4>
            </div>
            <div class="modal-body">

            </div>
        </div>
    </div>
</div>

<script type="text/javascript" src="/webjars/jquery-3.2.1.min.js"></script>
<script type="text/javascript" src="/webjars/bootstrap/js/bootstrap.bundle.min.js"></script>
<script type="text/javascript" src="/webjars/jquery.easing.min.js"></script>
<script type="text/javascript" src="/webjars/bootstrap-table/bootstrap-table.min.js"></script>
<script type="text/javascript" src="/webjars/bootstrap-table/locale/bootstrap-table-zh-CN.min.js"></script>
<script type="text/javascript" src="/webjars/script.js"></script>
<script>
    var selectionIds = [];	//保存选中ids

    const $table = $('#table');
    const $download = $('#download');

    $download.on("click", () => {
        let bootstrapTable = $table.bootstrapTable('getSelections');
        if (bootstrapTable.length == 0) {
            alert('请选择表');
            return false;
        }

        if (bootstrapTable.length == 1 && bootstrapTable[0]['id'] == 'table') {
            alert('请选择数据源');
            return false;
        }
        let base = '';
        let tables = '';
        bootstrapTable.forEach(item => {
            tables += ',' + item['Name'];
            base += '<div class="form-group" style="overflow: auto">' +
                '<label class="col-md-6" for="name">' + item['Name'] + '</label>' +
                '<label class="col-md-6" for="name">' + item['Comment'] + '</label>' +
                '</div>';
        })
        let tablesEle = '<label id="tables" class="hidden"> ' + tables + '</label>'

        $('.modal-body').empty();
        $('.modal-body').append(`

            <div class="row">
                    <div>
                        <form id="msform"  class="col-md-12" style="min-height: 400px">
                            <!-- progressbar -->
                            <ul id="progressbar">
                                <li class="active">选中信息</li>
                                <li>基本信息</li>
                                <li>包信息</li>
                            </ul>
                            <!-- fieldsets -->
                            <fieldset>
                                <h2 class="fs-title">基础信息</h2>
                                <h3 class="fs-subtitle">选中信息</h3>` +
            base + tablesEle
            + `
                                <input type="button" name="next" class="next action-button" value="下一步"/>
                            </fieldset>
                            <fieldset>
                                <h2 class="fs-title">基本信息</h2>
                                <h3 class="fs-subtitle">基本信息</h3>
                                <table class="table table-hover table-condensed table-sm">
                                    <tbody>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">作者</td>
                                            <td class="text-left"><input type="text" id="author" placeholder="CH" value="CH" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">swagger</td>
                                            <td class="text-left form-check form-check-inline"><label class="checkbox-inline"><input type="checkbox" id="swagger"  />开启</label></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">springdoc</td>
                                            <td class="text-left form-check form-check-inline"><label class="checkbox-inline"><input type="checkbox" id="springdoc"  />开启</label></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">kotlin</td>
                                            <td class="text-left form-check form-check-inline"><label class="checkbox-inline"><input type="checkbox" id="kotlin"  />开启</label></td>
                                        </tr>
                                    </tbody>
                                </table>
                                <input type="button" name="previous" class="previous action-button-previous" value="上一步"/>
                                <input type="button" name="next" class="next action-button" value="下一步"/>
                            </fieldset>
                            <fieldset>
                                <h2 class="fs-title">包信息</h2>
                                <h3 class="fs-subtitle">包信息</h3>
                                <table class="table table-hover table-condensed table-sm">
                                    <tbody>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">父包名</td>
                                            <td class="text-left"><input type="text" id="parent" placeholder="com.chua" value="com.chua" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Entity包名</td>
                                            <td class="text-left"><input type="text" id="entity" placeholder="entity" value="entity" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Service包名</td>
                                            <td class="text-left"><input type="text" id="service" placeholder="service" value="service" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Service Impl包名</td>
                                            <td class="text-left"><input type="text" id="serviceImpl" placeholder="serviceImpl" value="service.impl" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Mapper包名</td>
                                            <td class="text-left"><input type="text" id="mapper" placeholder="mapper" value="mapper" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Mapper XML包名</td>
                                            <td class="text-left"><input type="text" id="xml" placeholder="mapper" value="mapper" /></td>
                                        </tr>
                                        <tr>
                                            <td class="text-right" style="vertical-align: middle !important;">Controller包名</td>
                                            <td class="text-left"><input type="text" id="controller" placeholder="controller" value="controller" /></td>
                                        </tr>
                                    </tbody>
                                </table>
                                <input type="button" name="previous" class="previous action-button-previous" value="上一步"/>
                                <input type="submit" name="submit" class="submit action-button" value="提交"/>
                            </fieldset>
                        </form>

                    </div>
                </div>
        `);
        initial();
        $('#identifier').modal('show');
        return false;
    })
    //选中事件操作数组
    var union = function (array, ids) {
        $.each(ids, function (i, id) {
            if ($.inArray(id, array) == -1) {
                array[array.length] = id;
            }
        });
        return array;
    };
    //取消选中事件操作数组
    var difference = function (array, ids) {
        $.each(ids, function (i, id) {
            var index = $.inArray(id, array);
            if (index != -1) {
                array.splice(index, 1);
            }
        });
        return array;
    };

    //表格分页之前处理多选框数据
    function responseHandler(res) {
        $.each(res.rows, function (i, row) {
            row.checkStatus = $.inArray(row.id, selectionIds) != -1;	//判断当前行的数据id是否存在与选中的数组，存在则将多选框状态变为true
        });
        return res['data'] || [];
    }

    var _ = {"union": union, "difference": difference};
    //绑定选中事件、取消事件、全部选中、全部取消
    $table.on('check.bs.table check-all.bs.table uncheck.bs.table uncheck-all.bs.table', function (e, rows) {
        var ids = $.map(!$.isArray(rows) ? [rows] : rows, function (row) {
            return row.Name;
        });
        func = $.inArray(e.type, ['check', 'check-all']) > -1 ? 'union' : 'difference';
        selectionIds = _[func](selectionIds, ids);
    });
    $.ajax({
        url: "db"
    }).done(xhr => {
        let $selectLeo = $("#selectLeo");
        (xhr['data'] || []).forEach(item => {
            $selectLeo.append('<option value="-1">' + item + '</option>');
        })
        $selectLeo.find('option:eq(0)').prop('selected', true)

        let selectionIds = [];    //保存选中ids
        $selectLeo.off('change')
        let opt = function() {
            return {
                url: "find?dataSource=" + $selectLeo.find('option:selected').text(),
                editable: !0,
                height: "100%",
                singleSelect: !1,
                clickToSelect: !0,
                maintainSelected: true,
                locale: "zh-CN",
                sidePagination: "client",           //分页方式：client客户端分页，server服务端分页（*）
                pageSize: 10,                     //每页的记录行数（*）
                pageList: [10, 25, 50, 100],        //可供选择的每页的行数（*）
                pagination: true,                   //是否显示分页（*）
                responseHandler: responseHandler, //在渲染页面数据之前执行的方法，此配置很重要!!!!!!!
                columns: [{
                    field: 'checkStatus', checkbox: true,
                }, {
                    field: '',
                    title: '序列',
                    formatter: function (value, row, index) {
                        return index + 1;
                    }
                }, {
                    field: 'Name',
                    title: '表名'
                }, {
                    field: 'Comment',
                    title: '描述'
                }, {
                    field: 'Create_time',
                    title: '创建时间',
                    formatter: function (row, data, v) {
                        let date = new Date(~~data['Create_time'] * 1000);
                        let year = date.getFullYear();    //  返回的是年份
                        let month = date.getMonth() + 1;  //  返回的月份上个月的月份，记得+1才是当月
                        let dates = date.getDate();
                        if (month < 10) month = "0" + month;
                        if (date < 10) date = "0" + date;
                        return year + "-" + month + "-" + dates
                    }
                }, {
                    field: 'Update_time',
                    title: '更新时间'
                }]
            };
        }
        let initialTable = function () {
            $table.bootstrapTable(opt());
        }
        initialTable();
        $selectLeo.on('change', (e) => {
            $table.bootstrapTable("refresh", opt());
        })
    });
</script>

</body>