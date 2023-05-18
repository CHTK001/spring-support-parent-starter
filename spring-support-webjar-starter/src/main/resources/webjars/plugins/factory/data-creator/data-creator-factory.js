(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory();
	else if(typeof define === 'function' && define.amd)
		define([], factory);
	else if(typeof exports === 'object')
		exports["data-creator-factory"] = factory();
	else
		root["data-creator-factory"] = factory();
})(window, function() {
return /******/ (function(modules) { // webpackBootstrap
/******/ 	// The module cache
/******/ 	var installedModules = {};
/******/
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/
/******/ 		// Check if module is in cache
/******/ 		if(installedModules[moduleId]) {
/******/ 			return installedModules[moduleId].exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = installedModules[moduleId] = {
/******/ 			i: moduleId,
/******/ 			l: false,
/******/ 			exports: {}
/******/ 		};
/******/
/******/ 		// Execute the module function
/******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);
/******/
/******/ 		// Flag the module as loaded
/******/ 		module.l = true;
/******/
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/
/******/
/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = modules;
/******/
/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = installedModules;
/******/
/******/ 	// define getter function for harmony exports
/******/ 	__webpack_require__.d = function(exports, name, getter) {
/******/ 		if(!__webpack_require__.o(exports, name)) {
/******/ 			Object.defineProperty(exports, name, { enumerable: true, get: getter });
/******/ 		}
/******/ 	};
/******/
/******/ 	// define __esModule on exports
/******/ 	__webpack_require__.r = function(exports) {
/******/ 		if(typeof Symbol !== 'undefined' && Symbol.toStringTag) {
/******/ 			Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 		}
/******/ 		Object.defineProperty(exports, '__esModule', { value: true });
/******/ 	};
/******/
/******/ 	// create a fake namespace object
/******/ 	// mode & 1: value is a module id, require it
/******/ 	// mode & 2: merge all properties of value into the ns
/******/ 	// mode & 4: return value when already ns object
/******/ 	// mode & 8|1: behave like require
/******/ 	__webpack_require__.t = function(value, mode) {
/******/ 		if(mode & 1) value = __webpack_require__(value);
/******/ 		if(mode & 8) return value;
/******/ 		if((mode & 4) && typeof value === 'object' && value && value.__esModule) return value;
/******/ 		var ns = Object.create(null);
/******/ 		__webpack_require__.r(ns);
/******/ 		Object.defineProperty(ns, 'default', { enumerable: true, value: value });
/******/ 		if(mode & 2 && typeof value != 'string') for(var key in value) __webpack_require__.d(ns, key, function(key) { return value[key]; }.bind(null, key));
/******/ 		return ns;
/******/ 	};
/******/
/******/ 	// getDefaultExport function for compatibility with non-harmony modules
/******/ 	__webpack_require__.n = function(module) {
/******/ 		var getter = module && module.__esModule ?
/******/ 			function getDefault() { return module['default']; } :
/******/ 			function getModuleExports() { return module; };
/******/ 		__webpack_require__.d(getter, 'a', getter);
/******/ 		return getter;
/******/ 	};
/******/
/******/ 	// Object.prototype.hasOwnProperty.call
/******/ 	__webpack_require__.o = function(object, property) { return Object.prototype.hasOwnProperty.call(object, property); };
/******/
/******/ 	// __webpack_public_path__
/******/ 	__webpack_require__.p = "";
/******/
/******/
/******/ 	// Load entry module and return exports
/******/ 	return __webpack_require__(__webpack_require__.s = 0);
/******/ })
/************************************************************************/
/******/ ([
/* 0 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var Setting_1 = __webpack_require__(1);
var layout_none_1 = __webpack_require__(2);
var layout_bootstrap_1 = __webpack_require__(3);
var layout_waterfall_1 = __webpack_require__(4);
;
(function (callback, $, window, log, store) {
    callback($, window, log, store);
})(function ($, window, log, store) {
    $.fn.dataCreator = function (options, action) {
        msg("初始化数据加载插件", 'debug');
        var setting = initialOptions(options, action, this);
        initialDisplay(setting, action);
    };
    /**
     * 初始化显示方式
     * @param setting
     */
    var initialDisplay = function (setting, action) {
        var layout = setting.layout;
        if (!layout) {
            msg("布局异常", 'error');
            return;
        }
        //赋值
        layout.setSetting(setting);
        //渲染页面
        msg("渲染页面数据", 'debug');
        layout.render(action || 1);
    };
    /**
     * 初始化配置
     * @param options
     */
    var initialOptions = function (options, action, _this) {
        var param = new Setting_1.Setting();
        if (options === 'data') {
            param.data = action;
        }
        if (options === 'url') {
            param.url = action;
        }
        if (Object.prototype.toString.call(options).slice(8, -1) === "Array") {
            param.data = options;
        }
        if (Object.prototype.toString.call(options).slice(8, -1) === "Object") {
            param = Object.assign(true, param, options);
        }
        if (Object.prototype.toString.call(options.display).slice(8, -1) === "String") {
            param.layout = displayEntity(options.display);
        }
        param.selector = _this;
        if (!!log) {
            log.setLevel(param.level || 'info');
        }
        if (!!store) {
            param.store = store;
        }
        return param;
    };
    /**
     * 解释字符串
     * @param display
     */
    var displayEntity = function (display) {
        if (display === 'none') {
            return new layout_none_1.NoneLayout();
        }
        else if (display === 'table') {
            return new layout_bootstrap_1.TableLayout();
        }
        else if (display === 'waterfall') {
            return new layout_waterfall_1.WaterFallLayout();
        }
        return undefined;
    };
    /**
     *
     * @param log
     * @param msg
     */
    var msg = function (msg, level) {
        if (!!log) {
            if (!level) {
                level = 'info';
            }
            try {
                log[level](msg);
            }
            catch (e) {
            }
        }
    };
}, $, window, (function () {
    try {
        return log;
    }
    catch (e) {
        return undefined;
    }
})(), (function () {
    try {
        return store;
    }
    catch (e) {
        return undefined;
    }
})());


/***/ }),
/* 1 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 基础配置
 */
var layout_none_1 = __webpack_require__(2);
var Setting = /** @class */ (function () {
    function Setting() {
        this.pageLine = 2; //行数
        this.lineSize = 10; //一行数量
        this.cache = ''; //缓存名字
        this.layout = new layout_none_1.NoneLayout(); //展示方式
        this.itemCls = '.item-result'; //结果样式
        this.url = undefined; //数据来源
        this.data = undefined; //数据 data < url
        this.noData = '<div class="g-nodata g-text"><h4><small><img src="assets/image/i.png"> 未查询到匹配结果。</small></h4></div>'; //无数据
        this.failData = '<div class="g-nodata g-text"><h4><small><img src="assets/image/i.png"> 未查询到匹配结果。</small></h4></div>'; //无数据
        this.level = 'info'; //日志登记
        this.onPaginate = undefined; //点击分页回调
        this.dataFilter = undefined; //数据过滤
        this.dataFormat = undefined; //数据格式化
        this.destroy = function () {
            try {
                this.selector.bootstrapTable('destroy');
                this.selector.masonry('destroy');
                this.selector.off('scroll');
            }
            catch (e) {
            }
        }; //销毁display
        this.store = undefined; //缓存对象
    }
    return Setting;
}());
exports.Setting = Setting;


/***/ }),
/* 2 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var NoneLayout = /** @class */ (function () {
    function NoneLayout() {
    }
    Object.defineProperty(NoneLayout.prototype, "setting", {
        get: function () {
            return this._setting;
        },
        enumerable: true,
        configurable: true
    });
    NoneLayout.prototype.render = function (page) {
        var _this_1 = this;
        this._setting.destroy();
        this._setting.selector.empty();
        var $dataContent = $('<div class="data-creator-content data-creator-datas-grid"></div>');
        var $dataPaginate = $("\n             <div class=\"data-creator-paginate\" style=\"height: 45px\">\n                    <ul class=\"paginate\"></ul>\n                </div>\n            ");
        this._setting.selector.append($dataContent);
        this._setting.selector.append($dataPaginate);
        this._setting.dSelector = $dataContent;
        this._setting.pSelector = $dataPaginate.children('ul');
        if (!!this._setting.url) {
            this._setting.selector.loading({
                tl: 'double-ring'
            });
            $.when($.ajax({
                url: this._setting.url
            })).then(function (resolver, reject) {
                _this_1._setting.selector.loaded();
                _this_1.renderPage(resolver, page);
            }).fail(function () {
                _this_1._setting.selector.loaded();
                _this_1.renderFailPage();
            });
            return;
        }
        this.renderPage(this._setting.data, page);
    };
    NoneLayout.prototype.setSetting = function (setting) {
        this._setting = setting;
    };
    /**
     * 渲染页面
     * @param data
     */
    NoneLayout.prototype.renderPage = function (data, page) {
        if (!data) {
            this.renderNoData();
            return;
        }
        this._setting.dSelector.empty();
        this.setStoreBowerData(data);
        if (this._setting.dataFormat) {
            data = this._setting.dataFormat(data);
        }
        //数组即为数据
        if (Object.prototype.toString.call(data).slice(8, -1) === 'Array') {
            var sliceData = this.sliceCurrentPage(data, page);
            this.renderDataAndPaginate(sliceData['data'], page, sliceData['total']);
            return;
        }
        this.renderDataAndPaginate(data['data'], page, data['total'] || (data.length));
    };
    /**
     * 渲染数据和分页
     * @param data 数据
     * @param currentPage 当前页
     * @param totalNumber 最大总数
     */
    NoneLayout.prototype.renderDataAndPaginate = function (data, currentPage, totalNumber) {
        this.renderData(data, currentPage);
        this.renderPaginate(currentPage, totalNumber);
    };
    /**
     * 渲染数据
     * @param data
     * @param currentPage
     */
    NoneLayout.prototype.renderData = function (data, currentPage) {
        var selector = this._setting.dSelector;
        var lineSize = this._setting.lineSize;
        var pWidth = this._setting.selector.width();
        this.renderNotDataFilterData(data, currentPage);
        selector.css({
            "grid-template-columns": "repeat(" + lineSize + ", calc(100%/" + lineSize + "))",
            "margin": "5px 5px"
        });
        var $img = selector.find('img');
        $img.css({
            "max-width": Math.floor(pWidth / lineSize) + 'px',
            "padding": "5px 5px"
        });
    };
    /**
     * 渲染分页
     * @param currentPage
     * @param totalNumber
     */
    NoneLayout.prototype.renderPaginate = function (currentPage, totalNumber) {
        var lineSize = this._setting.lineSize;
        var pageLine = this._setting.pageLine;
        var pageSize = lineSize * pageLine;
        var onPaginate = this._setting.onPaginate;
        var _this = this;
        var options = {
            bootstrapMajorVersion: 3,
            //当前页数
            currentPage: currentPage,
            //总页数
            totalPages: Math.ceil(totalNumber / pageSize),
            itemTexts: function (type, page, current) {
                switch (type) {
                    case "first":
                        return "<<";
                    case "prev":
                        return "<";
                    case "next":
                        return ">";
                    case "last":
                        return ">>";
                    case "page":
                        return page;
                }
            },
            onPageChanged: function (event, oldPage, newPage) {
                var data = _this.getStoreBowerData();
                if (onPaginate) {
                    data = onPaginate(event, oldPage, newPage);
                }
                _this.renderPage(data, newPage);
            }
        };
        this._setting.pSelector.bootstrapPaginator(options);
        return;
    };
    /**
     * 渲染数据
     * @param data
     * @param currentPage
     */
    NoneLayout.prototype.renderNotDataFilterData = function (data, currentPage) {
        var lineSize = this._setting.lineSize;
        var pageLine = this._setting.pageLine;
        var len = data.length;
        var pageSize = lineSize * pageLine;
        var selector = this._setting.dSelector;
        var tl = this._setting.tl;
        var item = undefined;
        var $item = undefined;
        for (var index = 0; index < len; index++) {
            item = data[index];
            $item = $(template.compile(tl)(item));
            this.addStyle($item);
            selector.append($item);
        }
    };
    /**
     * 添加样式
     * @param $item
     */
    NoneLayout.prototype.addStyle = function ($item) {
        var itemCls = this._setting.itemCls;
        itemCls = !!itemCls ? itemCls.replace(".", "") : itemCls;
        $item.addClass(itemCls);
        return;
    };
    /**
     * 存储数据到浏览器
     * @param data
     */
    NoneLayout.prototype.setStoreBowerData = function (data) {
        if (!!this._setting.store && this._setting.cache) {
            this._setting.store.set(this._setting.cache, data);
        }
    };
    /**
     * 获取缓存数据
     * @param data
     */
    NoneLayout.prototype.getStoreBowerData = function () {
        if (!!this._setting.store && this._setting.cache) {
            return this._setting.store.get(this._setting.cache);
        }
        else {
            return this._setting.data;
        }
    };
    /**
     * 渲染没有数据加载页面
     */
    NoneLayout.prototype.renderNoData = function () {
        this._setting.selector.append(this._setting.noData);
    };
    /**
     * 渲染数据加载异常页面
     */
    NoneLayout.prototype.renderFailPage = function () {
        this._setting.selector.append(this._setting.failData);
    };
    /**
     * 裁剪数据
     * @param data 总数
     * @param page 页码
     */
    NoneLayout.prototype.sliceCurrentPage = function (data, currentPage) {
        var lineSize = this._setting.lineSize;
        var pageLine = this._setting.pageLine;
        var pageSize = lineSize * pageLine;
        var len = data ? data.length : 0;
        var dataFilter = this._setting.dataFilter;
        var startNumber = (currentPage - 1) * pageSize;
        var endNumber = Math.min(currentPage * pageSize, len);
        if (dataFilter) {
            return this.renderDataFilterData(data, currentPage);
        }
        if (len < pageSize) {
            return data;
        }
        return {
            data: data.slice(startNumber, endNumber),
            total: len
        };
    };
    /**
     * 渲染数据
     * @param data
     * @param currentPage
     */
    NoneLayout.prototype.renderDataFilterData = function (data, currentPage) {
        var lineSize = this._setting.lineSize;
        var pageLine = this._setting.pageLine;
        var len = data.length;
        var pageSize = lineSize * pageLine;
        var endNumber = currentPage * pageSize;
        var startNumber = (currentPage - 1) * pageSize;
        var item = undefined;
        var dataFilter = this._setting.dataFilter;
        var count = 0;
        var arr = [];
        for (var index = 0; index < len; index++) {
            item = data[index];
            if (dataFilter(item)) {
                if (count < endNumber && count >= startNumber) {
                    arr.push(item);
                }
                ++count;
            }
        }
        return {
            data: arr,
            total: count
        };
    };
    return NoneLayout;
}());
exports.NoneLayout = NoneLayout;


/***/ }),
/* 3 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var TableLayout = /** @class */ (function () {
    function TableLayout() {
    }
    TableLayout.prototype.render = function (page) {
        this.setting.destroy();
        this.setting.selector.empty();
        var pageSize = this.setting.pageLine * this.setting.lineSize;
        var columns = this.getColumns();
        this.tableSetting = Object.assign(true, {
            data: this.setting.data,
            columns: columns,
            pageSize: pageSize,
            search: !0,
            language: 'zh-CN',
            locale: 'zh-CN',
            cache: !1,
            sidePagination: 'client',
            pageNumber: 1,
            pagination: !0,
            pageList: [pageSize, pageSize * 2, pageSize * 3],
        }, this.setting);
        try {
            this.setting.selector.bootstrapTable('destroy');
            this.setting.selector.bootstrapTable(this.tableSetting);
            this.renderTable();
        }
        catch (e) {
            console.warn("请添加依赖插件");
        }
    };
    TableLayout.prototype.setSetting = function (setting) {
        this.setting = setting;
    };
    /**
     * 获取分页字段
     */
    TableLayout.prototype.getColumns = function () {
        var data = this.setting.data;
        if (!data || data.length == 0) {
            return [];
        }
        var one = data[0];
        var columns = [];
        for (var item in one) {
            columns.push({
                title: item,
                field: item,
                align: 'center',
                sortable: !0
            });
        }
        return columns;
    };
    /**
     * 渲染表格
     */
    TableLayout.prototype.renderTable = function () {
        var $thes = this.setting.selector.find('thead th');
        var thArray = [];
        for (var _i = 0, $thes_1 = $thes; _i < $thes_1.length; _i++) {
            var $th = $thes_1[_i];
            if (!$th.textContent) {
                continue;
            }
            thArray.push($th);
        }
        if (thArray.length == 0) {
            return;
        }
        var pWidth = this.setting.selector.width();
        var perWidth = pWidth / thArray.length;
        for (var _a = 0, thArray_1 = thArray; _a < thArray_1.length; _a++) {
            var th = thArray_1[_a];
            $(th).width(perWidth);
        }
    };
    return TableLayout;
}());
exports.TableLayout = TableLayout;


/***/ }),
/* 4 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 *
 */
var WaterFallLayout = /** @class */ (function () {
    function WaterFallLayout() {
        /**
         * 滚动渲染
         */
        this.renderScrollPage = function (count) {
            var selector = this.setting.selector;
            var data = this.setting.onPaginate(event, count, count + 1);
            if (!data || data.length == 0) {
                if (selector.children('.end-waterfall').length == 0) {
                    var noData = $(this.setting.noData);
                    var bottom = selector[0].scrollHeight + 30;
                    noData.addClass('end-waterfall');
                    noData.css({
                        'position': 'absolute',
                        'top': bottom + 'px',
                        'height': '30px',
                        'width': '100%'
                    });
                    selector.append(noData);
                    selector.scrollTo(bottom);
                }
                return;
            }
            if (Object.prototype.toString.call(data) === 'Object') {
                try {
                    data = data['data'];
                }
                catch (e) { }
            }
            this.renderDataMasonry(data);
        };
    }
    WaterFallLayout.prototype.render = function (page) {
        this.setting.destroy();
        var selector = this.setting.selector;
        selector.empty();
        var data1 = this.sliceData(page);
        this.renderPage(data1);
        selector.css({
            'max-height': '700px',
            'overflow': 'auto'
        });
        try {
            selector.masonry('destroy');
            selector.masonry({
                columnWidth: 80,
                percentPosition: true,
                itemSelector: this.setting.itemCls
            });
            if (!this.setting.onPaginate) {
                return;
            }
            var _this_1 = this;
            var count_1 = 1;
            selector.off('scroll');
            selector.on('scroll', function (event) {
                if (this.scrollHeight - this.scrollTop == this.clientHeight) {
                    _this_1.renderScrollPage(count_1++);
                }
            });
        }
        catch (e) {
        }
    };
    WaterFallLayout.prototype.setSetting = function (setting) {
        this.setting = setting;
        var data = setting.data;
        var pageLine = setting.pageLine;
        var lineSize = setting.lineSize;
        var pageSize = pageLine * lineSize;
        var len = data.length;
        setting.onPaginate = setting.onPaginate ? setting.onPaginate : function (event, oldPage, newPage) {
            var startNumber = oldPage * pageSize;
            var endNumber = Math.min(newPage * pageSize, len);
            return data.slice(startNumber, endNumber);
        };
    };
    /**
     * 渲染页面
     * @param page
     */
    WaterFallLayout.prototype.sliceData = function (page) {
        var data = this.setting.data;
        var pageLine = this.setting.pageLine;
        var lineSize = this.setting.lineSize;
        var pageSize = pageLine * lineSize;
        var startNumber = (page - 1) * pageSize;
        var endNumber = Math.min(pageSize * page, data.length);
        return data.slice(startNumber, endNumber);
    };
    /**
     * 渲染页面
     * @param data
     */
    WaterFallLayout.prototype.renderDataMasonry = function (data) {
        var len = data.length;
        var tl = this.setting.tl;
        var selector = this.setting.selector;
        var item = undefined;
        var $item = undefined;
        for (var index = 0; index < len; index++) {
            item = data[index];
            $item = $(template.compile(tl)(item));
            this.addStyle($item);
            selector.append($item).masonry('appended', $item);
        }
    };
    /**
     * 渲染页面
     * @param data
     */
    WaterFallLayout.prototype.renderPage = function (data) {
        var len = data.length;
        var tl = this.setting.tl;
        var selector = this.setting.selector;
        var item = undefined;
        var $item = undefined;
        for (var index = 0; index < len; index++) {
            item = data[index];
            $item = $(template.compile(tl)(item));
            this.addStyle($item);
            selector.append($item);
        }
    };
    /**
     * 添加样式
     * @param $item
     */
    WaterFallLayout.prototype.addStyle = function ($item) {
        var itemCls = this.setting.itemCls;
        itemCls = !!itemCls ? itemCls.replace(".", "") : itemCls;
        $item.addClass(itemCls);
        return;
    };
    return WaterFallLayout;
}());
exports.WaterFallLayout = WaterFallLayout;


/***/ })
/******/ ])["default"];
});