<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"/>
    <title>配置中心</title>

    <#-- favicon -->
    <link rel="icon" href="${request.contextPath}/favicon.ico"/>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <!-- Tell the browser to be responsive to screen width -->
    <meta content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" name="viewport">
    <!-- Bootstrap 3.3.5 -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/bootstrap/css/bootstrap.min.css">
    <!-- Font Awesome -->
    <!-- <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.5.0/css/font-awesome.min.css"> -->
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/font-awesome-4.5.0/css/font-awesome.min.css">
    <!-- Ionicons -->
    <!-- <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/ionicons/2.0.1/css/ionicons.min.css"> -->
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/ionicons-2.0.1/css/ionicons.min.css">
    <!-- Theme style -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/dist/css/AdminLTE-local.min.css">
    <!-- AdminLTE Skins. Choose a skin from the css/skins folder instead of downloading all of them to reduce the load. -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/dist/css/skins/_all-skins.min.css">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <link rel="stylesheet" href="${request.contextPath}/static/adminlte/plugins/iCheck/square/blue.css">

    <link rel="stylesheet" href="${request.contextPath}/static/plugins/bootstrap-table/bootstrap-table.min.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/x-editable/jquery-editable/css/jquery-editable.css">
    <link rel="stylesheet" href="${request.contextPath}/static/plugins/toastr/toastr.min.css">
    <style>
        /*- scrollbar -*/
        ::-webkit-scrollbar {
            width: 5px;
            height: 5px;
        }

        ::-webkit-scrollbar-thumb {
            background-color: #999;
            -webkit-border-radius: 5px;
            border-radius: 5px;
        }

        ::-webkit-scrollbar-thumb:vertical:hover {
            background-color: #666;
        }

        ::-webkit-scrollbar-thumb:vertical:active {
            background-color: #333;
        }

        ::-webkit-scrollbar-button {
            display: none;
        }

        ::-webkit-scrollbar-track {
            background-color: #f1f1f1;
        }
    </style>
</head>
<body>

<div>
    <ul class="nav nav-tabs">
        <li class="nav-item" role="presentation">
            <a class="nav-link active" id="home-dev" data-toggle="tab" href="#dev" role="tab" aria-controls="dev" aria-selected="true">dev</a>
        </li>
        <li class="nav-item" role="presentation">
            <a class="nav-link" id="home-prod" data-toggle="tab" href="#prod" role="tab" aria-controls="prod" aria-selected="true">prod</a>
        </li>
        <li class="nav-item" role="presentation">
            <a class="nav-link" id="home-test" data-toggle="tab" href="#test" role="tab" aria-controls="test" aria-selected="true">test</a>
        </li>
    </ul>

    <div class="tab-content">
        <div class="tab-pane active" id="dev" role="tabpanel" aria-labelledby="home-dev">
            <table id="config-table-dev" class="table table-striped table-bordered"></table>
        </div>
        <div class="tab-pane" id="prod" role="tabpanel" aria-labelledby="home-prod">
            <table id="config-table-prod" class="table table-striped table-bordered"></table>
        </div>
        <div class="tab-pane" id="test" role="tabpanel" aria-labelledby="home-test">
            <table id="config-table-test" class="table table-striped table-bordered"></table>
        </div>
    </div>
</div>
<!-- jQuery 2.1.4 -->
<script src="${request.contextPath}/static/adminlte/plugins/jQuery/jquery-2.2.3.min.js"></script>
<!-- Bootstrap 3.3.5 -->
<script src="${request.contextPath}/static/plugins/popper/popper.min.js"></script>
<script src="${request.contextPath}/static/adminlte/bootstrap/js/bootstrap.min.js"></script>
<script src="${request.contextPath}/static/plugins/bootstrap-table/bootstrap-table.min.js"></script>
<script src="${request.contextPath}/static/plugins/bootstrap-table/extensions/editable/bootstrap-table-editor.js"></script>
<script src="${request.contextPath}/static/plugins/bootstrap-table/locale/bootstrap-table-zh-CN.min.js"></script>
<!-- FastClick -->
<script src="${request.contextPath}/static/adminlte/plugins/fastclick/fastclick.min.js"></script>
<!-- AdminLTE App -->
<script src="${request.contextPath}/static/adminlte/dist/js/app.min.js"></script>
<#-- jquery.slimscroll -->
<script src="${request.contextPath}/static/adminlte/plugins/slimScroll/jquery.slimscroll.min.js"></script>

<script src="${request.contextPath}/static/plugins/toastr/toastr.min.js"></script>
<script src="${request.contextPath}/static/adminlte/plugins/iCheck/icheck.min.js"></script>
<script src="${request.contextPath}/static/config/page/config.js"></script>
</body>