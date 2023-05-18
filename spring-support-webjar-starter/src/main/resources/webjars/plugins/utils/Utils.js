(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory();
	else if(typeof define === 'function' && define.amd)
		define([], factory);
	else if(typeof exports === 'object')
		exports["Utils"] = factory();
	else
		root["Utils"] = factory();
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
var ObjectHelper_1 = __webpack_require__(1);
var StringsHelper_1 = __webpack_require__(2);
var DateHelper_1 = __webpack_require__(4);
var BooleanHelper_1 = __webpack_require__(3);
var ArrayHelper_1 = __webpack_require__(5);
var MapHelper_1 = __webpack_require__(6);
var ScriptHelper_1 = __webpack_require__(7);
var Utils = {
    Objects: new ObjectHelper_1.default(),
    Strings: new StringsHelper_1.default(),
    Dates: new DateHelper_1.DatesHelper(),
    Booleans: new BooleanHelper_1.BooleanHelper(),
    Arrays: new ArrayHelper_1.ArrayHelper(),
    Maps: new MapHelper_1.MapHelper(),
    Scripts: new ScriptHelper_1.ScriptHelper(),
    time: function (source) {
        var uuid = "任务ID:[" + this.Strings.uuid() + "]耗时";
        console.time(uuid);
        ;
        (function (source, _this) {
            if (_this.Objects.isFunction(source)) {
                source();
            }
            else {
                source;
            }
        })(source, this);
        console.timeEnd(uuid);
    },
    table: function (source) {
        console.table(source);
    }
};
exports.Utils = Utils;


/***/ }),
/* 1 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 对象处理工具类
 */
var ObjectHelper = /** @class */ (function () {
    function ObjectHelper() {
    }
    /**
     * 是否是字符串
     * @param value
     */
    ObjectHelper.prototype.isString = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'String';
    };
    /**
     * 是否是数字
     * @param {Object} vl
     */
    ObjectHelper.prototype.isNumber = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Number';
    };
    /**
     * 是否是对象
     * @param value
     */
    ObjectHelper.prototype.isObject = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Object';
    };
    /**
     * 是否是数组
     * @param value
     */
    ObjectHelper.prototype.isArray = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Array';
    };
    /**
     * 是否是布尔
     * @param {Object} vl
     */
    ObjectHelper.prototype.isBoolean = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Boolean';
    };
    /**
     * 是否是函数
     * @param {Object} vl
     */
    ObjectHelper.prototype.isFunction = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Function';
    };
    /**
     * 是否是日期
     * @param {Object} vl
     */
    ObjectHelper.prototype.isDate = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Date';
    };
    /**
     * 是否是null
     * @param {Object} vl
     */
    ObjectHelper.prototype.isNull = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Null';
    };
    /**
     * 是否是undefined
     * @param {Object} vl
     */
    ObjectHelper.prototype.isUndefined = function (value) {
        return Object.prototype.toString.call(value).slice(8, -1) === 'Undefined';
    };
    /**
     * 是否是False
     * @param {Object} vl
     */
    ObjectHelper.prototype.isFalse = function (value) {
        return !value || value === 'null' || value === 'undefined' || value === 'false' || value === 'NaN';
    };
    /**
     * 是否是True
     * @param {Object} vl
     */
    ObjectHelper.prototype.isTrue = function (value) {
        return !this.isFalse(value);
    };
    return ObjectHelper;
}());
exports.default = ObjectHelper;


/***/ }),
/* 2 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 字符串处理类
 */
var BooleanHelper_1 = __webpack_require__(3);
var StringsHelper = /** @class */ (function () {
    function StringsHelper() {
        this.booleans = new BooleanHelper_1.BooleanHelper();
        this.noMatch = /(.)^/;
        this.defaultSetting = {
            evaluate: /<%([\s\S]+?)%>/g,
            interpolate: /<%=([\s\S]+?)%>/g,
            escape: /<%-([\s\S]+?)%>/g,
            variable: ""
        };
        this.escapes = {
            "'": "'",
            '\\': '\\',
            '\r': 'r',
            '\n': 'n',
            '\u2028': 'u2028',
            '\u2029': 'u2029'
        };
        this.escapeRegExp = /\\|'|\r|\n|\u2028|\u2029/g;
        this.escapeChar = function (match) {
            return '\\' + this.escapes[match];
        };
    }
    /**
     * 获取浏览器url中的参数值
     * @param {Object} name
     */
    StringsHelper.prototype.toUrlParam = function (name) {
        return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)', "ig").exec(location.search) || [, ""])[1].replace(/\+/g, '%20')) || null;
    };
    /**
     * 驼峰命名
     * @param value
     * @param symbol
     */
    StringsHelper.prototype.hump = function (value) {
        if (this.isBlank(value) || value.length == 0) {
            return value;
        }
        var symbol = '-';
        if (arguments.length > 1) {
            symbol = arguments[1];
        }
        symbol = this.ifEmptyDefault(symbol, '-');
        var strings = value.split(symbol);
        var stringBuffer = "";
        for (var _i = 0, strings_1 = strings; _i < strings_1.length; _i++) {
            var string = strings_1[_i];
            stringBuffer += this.firstUpperCase(string);
        }
        return this.firstLowerCase(stringBuffer);
    };
    /**
     * 首字符大写
     */
    StringsHelper.prototype.firstUpperCase = function (value) {
        if (this.isBlank(value) || value.length == 0) {
            return value;
        }
        var first = value.substring(0, 1);
        var end = this.substring(value, 1, value.length);
        return first.toUpperCase() + end;
    };
    /**
     * 首字符小
     */
    StringsHelper.prototype.firstLowerCase = function (value) {
        if (this.isBlank(value) || value.length == 0) {
            return value;
        }
        var first = value.substring(0, 1);
        var end = this.substring(value, 1, value.length);
        return first.toLowerCase() + end;
    };
    /**
     * 分割
     * @param value 值
     * @param start 开始位置
     * @param end 结束位置
     */
    StringsHelper.prototype.substring = function (value, start, end) {
        if (this.isBlank(value) || value.length == 0) {
            return value;
        }
        var len = value.length;
        return value.substring(start, end);
    };
    /**
     * 字符串是否为空/undefined
     * @param value
     */
    StringsHelper.prototype.isEmpty = function (value) {
        return value === null || value === undefined;
    };
    /**
     * 字符串是否为空/undefined/''
     * @param value
     */
    StringsHelper.prototype.isBlank = function (value) {
        return value === null || value === undefined || value === '';
    };
    /**
     * 字符串是否为空/undefined返回默认值
     * @param value
     */
    StringsHelper.prototype.ifEmptyDefault = function (value, defaultValue) {
        return this.isEmpty(value) ? defaultValue : value;
    };
    /**
     * 是否为数字
     * @param value
     */
    StringsHelper.prototype.isNumber = function (value) {
        return this.isEmpty(value) ? !1 : /^(?:[1-9]\d*|0)(?:\.\d+)?$/.test(value);
    };
    /**
     * 模板
     * @param value
     */
    StringsHelper.prototype.template = function (value, data) {
        try {
            return template.compile(value)(data);
        }
        catch (e) {
            console.warn("依赖插件[template-web]");
        }
        return value;
    };
    StringsHelper.prototype.guid = function () {
        function S4() {
            return (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);
        }
        return (S4() + S4() + "-" + S4() + "-" + S4() + "-" + S4() + "-" + S4() + S4() + S4());
    };
    /**
     * uuid
     */
    StringsHelper.prototype.uuid = function () {
        var chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');
        var uuid = [], i, len = 12;
        var radix = chars.length;
        if (len) {
            for (i = 0; i < len; i++) {
                uuid[i] = chars[0 | Math.random() * radix];
            }
        }
        else {
            var r = void 0;
            uuid[8] = uuid[13] = uuid[18] = uuid[23] = '-';
            uuid[14] = '4';
            for (i = 0; i < 36; i++) {
                if (!uuid[i]) {
                    r = 0 | Math.random() * 16;
                    uuid[i] = chars[(i == 19) ? (r & 0x3) | 0x8 : r];
                }
            }
        }
        return uuid.join('');
    };
    return StringsHelper;
}());
exports.default = StringsHelper;


/***/ }),
/* 3 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * boolean处理类
 */
var ObjectHelper_1 = __webpack_require__(1);
var BooleanHelper = /** @class */ (function () {
    function BooleanHelper() {
        this.objects = new ObjectHelper_1.default();
    }
    /**
     * 是否有长度
     */
    BooleanHelper.prototype.hasLength = function (value) {
        if (this.objects.isArray(value)) {
            return !!value && value.length > 0;
        }
        else if (this.objects.isObject(value)) {
            var count = 0;
            for (var _i = 0, value_1 = value; _i < value_1.length; _i++) {
                var element = value_1[_i];
                count = 1;
                break;
            }
            return count > 0;
        }
        return !!value;
    };
    /**
     * 是否是中文
     * @param {Object} vl
     */
    BooleanHelper.prototype.isCgChinese = function (vl) {
        return /^[\u4E00-\u9FA5]+$/.test(vl);
    };
    /**
     * 是否是图片
     * @param {Object} vl
     */
    BooleanHelper.prototype.isImage = function (vl) {
        return /(gif|jpg|jpeg|png|GIF|JPG|PNG)$/ig.test(vl);
    };
    /**
     * 是否是视频
     * @param {Object} vl
     */
    BooleanHelper.prototype.isVideo = function (vl) {
        return /(mp4|mp3|flv|wav)$/ig.test(vl);
    };
    return BooleanHelper;
}());
exports.BooleanHelper = BooleanHelper;


/***/ }),
/* 4 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 日期处理类
 */
var DatesHelper = /** @class */ (function () {
    function DatesHelper() {
    }
    /**
     * 前几天
     * @param {Object} len
     */
    DatesHelper.prototype.nearlyDay = function (len) {
        return this.formatTime(this.nearly(new Date().getTime(), len, 'D', -1), "yyyy-MM-dd HH:mm:ss");
    };
    /**
     * 前几月
     * @param {Object} len
     */
    DatesHelper.prototype.nearlyMonth = function (len) {
        return this.formatTime(this.nearly(new Date().getTime(), len, 'M', -1), "yyyy-MM-dd HH:mm:ss");
    };
    /**
     * 获取近几天|日|月
     * @param {Object} time 时间戳
     * @param {Object} len 间隔时间
     * @param {Object} type = [Y|M|D|H|I|S] 类型
     * @param {Object} direction = [-1|1] 前后
     */
    DatesHelper.prototype.nearly = function (time, len, type, direction) {
        var _timeLength = (time + '').length;
        if (_timeLength < 13) {
            time *= Math.pow(10, 13 - _timeLength);
        }
        var dateTime = new Date(time);
        var formatObj = {
            y: dateTime.getFullYear(),
            m: dateTime.getMonth() + 1,
            d: dateTime.getDate(),
            h: dateTime.getHours(),
            i: dateTime.getMinutes(),
            s: dateTime.getSeconds(),
            a: dateTime.getDay()
        };
        direction = isNaN(direction) ? -1 : direction, len = isNaN(len) ? 0 : len, type = type.toUpperCase();
        switch (type) {
            case 'Y':
                dateTime.setFullYear(formatObj.y + len * direction);
                break;
            case 'M':
                dateTime.setMonth(formatObj.m + len * direction - 1);
                break;
            case 'D':
                dateTime.setDate(formatObj.d + len * direction);
                break;
            case 'H':
                dateTime.setHours(formatObj.h + len * direction);
                break;
            case 'I':
                dateTime.setMinutes(formatObj.i + len * direction);
                break;
            case 'S':
                dateTime.setSeconds(formatObj.s + len * direction);
                break;
        }
        return dateTime.getTime();
    };
    /**
     * 当前时间
     */
    DatesHelper.prototype.nowDate = function () {
        return this.formatTime(new Date().getTime(), "yyyy-MM-dd HH:mm:ss");
    };
    /**
     * 当前时间
     */
    DatesHelper.prototype.nowTime = function () {
        return new Date().getTime();
    };
    /**
     * 获取时间戳
     * @param {Date | string} date 时间/时间字符串
     */
    DatesHelper.prototype.getTime = function (date) {
        var dataTime = new Date(date);
        return dataTime.getTime();
    };
    /**
     * 格式化时间
     * time为number类型补全13位时间戳
     * @param {number | Date | string} time
     * @param {string} format 时间格式
     */
    DatesHelper.prototype.formatTime = function (time, dateFormat) {
        if (!time) {
            return undefined;
        }
        var dateTime = undefined;
        if (typeof time === 'number') {
            var _timeLength = (time + '').length;
            if (_timeLength < 13) {
                time *= Math.pow(10, 13 - _timeLength);
            }
            dateTime = new Date(+time);
        }
        else {
            dateTime = new Date(time);
        }
        dateFormat = dateFormat || "yyyy-MM-dd hh:mm:ss";
        var o = {
            "M+": dateTime.getMonth() + 1,
            "d+": dateTime.getDate(),
            "h+": dateTime.getHours(),
            "H+": dateTime.getHours(),
            "m+": dateTime.getMinutes(),
            "s+": dateTime.getSeconds(),
            "q+": Math.floor((dateTime.getMonth() + 3) / 3),
            "S": dateTime.getMilliseconds()
        };
        if (/(y+)/.test(dateFormat)) {
            dateFormat = dateFormat.replace(RegExp.$1, (dateTime.getFullYear() + "").substr(4 - RegExp.$1.length));
        }
        for (var k in o) {
            if (new RegExp("(" + k + ")").test(dateFormat)) {
                dateFormat = dateFormat.replace(RegExp.$1, RegExp.$1.length == 1 ? o[k] : ("00" + o[k]).substr(("" + o[k]).length));
            }
        }
        return dateFormat;
    };
    /**
     * 转换日期对象为日期字符串
     * @param {Object} date - 时间
     * @param {Object} pattern - 格式字符串,例如：yyyy-MM-dd hh:mm:ss
     * @return 符合要求的日期字符串
     */
    DatesHelper.prototype.getFormatDate = function (date, pattern) {
        if (date == undefined) {
            date = new Date();
        }
        if (pattern == undefined) {
            pattern = "yyyy-MM-dd hh:mm:ss";
        }
        return this.formatTime(date.getTime(), pattern);
    };
    return DatesHelper;
}());
exports.DatesHelper = DatesHelper;


/***/ }),
/* 5 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (_) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 集合处理
 */
var ObjectHelper_1 = __webpack_require__(1);
var ArrayHelper = /** @class */ (function () {
    function ArrayHelper() {
        this.objects = new ObjectHelper_1.default();
        this.forEach = this.each;
    }
    /**
     * @param array
     */
    ArrayHelper.prototype.generatorFunction = function (array) {
        if (!array) {
            return undefined;
        }
        if (!this.objects.isArray(array)) {
            return undefined;
        }
        var length = array.length;
        var result = function () {
            var index;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        index = 0;
                        _a.label = 1;
                    case 1:
                        if (!(index < length)) return [3 /*break*/, 4];
                        return [4 /*yield*/, array[index]];
                    case 2:
                        _a.sent();
                        _a.label = 3;
                    case 3:
                        index++;
                        return [3 /*break*/, 1];
                    case 4: return [2 /*return*/];
                }
            });
        };
        return result;
    };
    /**
     * 遍历集合
     * @param array 素组
     * @param callback 回调 function(index, item): void {}
     */
    ArrayHelper.prototype.each = function (array, callback) {
        if (!array || !callback) {
            return undefined;
        }
        if (!this.objects.isArray(array)) {
            return undefined;
        }
        var length = array.length;
        for (var index = 0; index < length; index++) {
            callback(index, array[index]);
        }
    };
    return ArrayHelper;
}());
exports.ArrayHelper = ArrayHelper;


/***/ }),
/* 6 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (_) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
var ObjectHelper_1 = __webpack_require__(1);
var StringsHelper_1 = __webpack_require__(2);
/**
 * 2元集合处理
 */
var MapHelper = /** @class */ (function () {
    function MapHelper() {
        this.objects = new ObjectHelper_1.default();
        this.strings = new StringsHelper_1.default();
        this.forEach = this.each;
    }
    /**
     *
     * 是否包含key
     * @param key
     * @param source
     */
    MapHelper.prototype.container = function (key, source) {
        if (!key || !source) {
            return !1;
        }
        return this.strings.isEmpty(source[key]);
    };
    /**
     * 获取值
     * @param key
     * @param source
     */
    MapHelper.prototype.get = function (key, source) {
        if (!source) {
            return undefined;
        }
        return source[key];
    };
    /**
     *
     * @param source
     */
    MapHelper.prototype.generatorFunction = function (source) {
        if (!source) {
            return undefined;
        }
        if (!this.objects.isObject(source)) {
            return undefined;
        }
        var keys = Object.keys(source);
        var length = keys.length;
        var key = undefined;
        var result = function () {
            var index;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        index = 0;
                        _a.label = 1;
                    case 1:
                        if (!(index < length)) return [3 /*break*/, 4];
                        key = keys[index];
                        return [4 /*yield*/, {
                                key: key,
                                value: source[key]
                            }];
                    case 2:
                        _a.sent();
                        _a.label = 3;
                    case 3:
                        index++;
                        return [3 /*break*/, 1];
                    case 4: return [2 /*return*/];
                }
            });
        };
        return result;
    };
    /**
     * 遍历集合
     * @param array 素组
     * @param callback 回调 function(index, item): void {}
     */
    MapHelper.prototype.each = function (array, callback) {
        if (!array || !callback) {
            return undefined;
        }
        if (!this.objects.isObject(array)) {
            return undefined;
        }
        var keys = Object.keys(array);
        var length = keys.length;
        var key = undefined;
        for (var index = 0; index < length; index++) {
            key = keys[index];
            callback(key, array[key]);
        }
    };
    return MapHelper;
}());
exports.MapHelper = MapHelper;


/***/ }),
/* 7 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * style/script处理
 */
var StringsHelper_1 = __webpack_require__(2);
var ObjectHelper_1 = __webpack_require__(1);
var MapHelper_1 = __webpack_require__(6);
var ScriptHelper = /** @class */ (function () {
    function ScriptHelper() {
        this.strings = new StringsHelper_1.default();
        this.objects = new ObjectHelper_1.default();
        this.maps = new MapHelper_1.MapHelper();
    }
    /**
     * 添加css文件
     * @param link
     */
    ScriptHelper.prototype.addScript = function (link) {
        if (this.strings.isBlank(link)) {
            return;
        }
        if (this.objects.isString(link)) {
            var elementScript = document.createElement("script");
            elementScript.src = link;
            elementScript.type = 'text/javascript';
            elementScript.charset = "utf-8";
        }
        else if (this.objects.isObject(link)) {
            if (this.maps.container('src', link)) {
                return;
            }
            elementScript.charset = link['charset'] || "utf-8";
            var elementScript = document.createElement("script");
            elementScript.src = link['src'];
            elementScript.type = link['type'] || 'text/javascript';
            elementScript.async = !!link['async'];
            if (this.maps.container('crossOrigin', link)) {
                elementScript.crossOrigin = link['crossOrigin'];
            }
            if (this.maps.container('defer', link)) {
                elementScript.defer = link['defer'];
            }
        }
        document.write(elementScript.outerHTML);
        return;
    };
    /**
     * 添加css文件
     * @param link
     */
    ScriptHelper.prototype.addLink = function (link) {
        if (this.strings.isBlank(link)) {
            return;
        }
        var $$head = document.getElementsByTagName('head')[0];
        var elementStyle = document.createElement('link');
        elementStyle.href = link;
        elementStyle.rel = 'stylesheet';
        elementStyle.charset = 'UTF-8';
        try {
            $$head.appendChild(elementStyle);
        }
        catch (e) { }
    };
    /**
     * 添加样式字符串
     */
    ScriptHelper.prototype.insertStyle = function (styles) {
        if (!styles) {
            return;
        }
        var $$head = document.getElementsByTagName('head')[0];
        var styleElement = document.createElement("style");
        styleElement.innerText = styles;
        $$head.appendChild(styleElement);
    };
    /**
     * 添加样式
     */
    ScriptHelper.prototype.addStyle = function (dom, styles) {
        if (!dom) {
            return;
        }
        if (dom.length == 0) {
            return;
        }
        dom = dom[0];
        for (var style in styles) {
            dom.style[this.strings.hump(style)] = styles[style];
        }
    };
    /**
     * 查询节点
     * @param name
     */
    ScriptHelper.prototype.querySelector = function (name) {
        return this.strings.isBlank(name) ? undefined : document.querySelector(name);
    };
    /**
     * 获取element信息
     */
    ScriptHelper.prototype.element = function (element) {
        if (this.strings.isBlank(element)) {
            return {};
        }
        try {
            element = element[0];
            return {
                width: element.clientWidth,
                height: element.clientHeight,
                left: element.style.left,
                top: element.style.top,
                offsetLeft: element.offsetLeft,
                offsetTop: element.offsetTop
            };
        }
        catch (e) {
            console.error(e);
        }
    };
    /**
     * 获取document信息
     */
    ScriptHelper.prototype.document = function () {
        var documentElement = document.documentElement;
        return {
            width: documentElement.clientWidth,
            height: documentElement.clientHeight,
            left: documentElement.style.left,
            top: documentElement.style.top,
            offsetLeft: documentElement.offsetLeft,
            offsetTop: documentElement.offsetTop
        };
    };
    /**
     * 获取screen信息
     */
    ScriptHelper.prototype.screen = function () {
        var documentElement = document.documentElement;
        return {
            width: screen.availWidth,
            height: screen.availHeight
        };
    };
    return ScriptHelper;
}());
exports.ScriptHelper = ScriptHelper;


/***/ })
/******/ ])["Utils"];
});