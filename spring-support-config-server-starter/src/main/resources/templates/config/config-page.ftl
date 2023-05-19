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
<script type="text/javascript" src="/webjars/core.js" data-core data-logger="true"></script>
<script type="text/javascript">
    modulejs.plugins(['popper', 'bootstrap-table', 'bootstrap-table-editor', 'toastr', "icheck"]);
</script>
<script src="${request.contextPath}/static/config/page/config.js"></script>
</body>