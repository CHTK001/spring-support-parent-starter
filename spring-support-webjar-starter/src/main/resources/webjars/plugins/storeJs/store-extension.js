(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory();
	else if(typeof define === 'function' && define.amd)
		define([], factory);
	else if(typeof exports === 'object')
		exports["storeJs"] = factory();
	else
		root["storeJs"] = factory();
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
var SessionStorage_1 = __webpack_require__(1);
var LocalStorage_1 = __webpack_require__(4);
var OldIEUserDataStorage_1 = __webpack_require__(5);
var OldFFGlobalStorage_1 = __webpack_require__(6);
var MemoryStorage_1 = __webpack_require__(7);
var CookieStorage_1 = __webpack_require__(8);
store.store = function (name) {
    var storeSetting = {};
    var storage = null;
    var level = 9;
    if (arguments.length > 1) {
        level = +arguments[1];
        level = Math.min(level, 9);
    }
    if (name === 'SessionStorage') {
        storage = new SessionStorage_1.SessionStorage();
    }
    else if (name === 'LocalStorage') {
        storage = new LocalStorage_1.LocalStorage();
    }
    else if (name === 'OldIEUserDataStorage') {
        storage = new OldIEUserDataStorage_1.OldIEUserDataStorage();
    }
    else if (name === 'OldFFGlobalStorage') {
        storage = new OldFFGlobalStorage_1.OldFFGlobalStorage();
    }
    else if (name === 'MemoryStorage') {
        storage = new MemoryStorage_1.MemoryStorage();
    }
    else if (name === 'CookieStorage') {
        storage = new CookieStorage_1.CookieStorage();
    }
    storeSetting['name'] = name;
    storeSetting['read'] = storage.read;
    storeSetting['clearAll'] = storage.clearAll;
    storeSetting['each'] = storage.each;
    storeSetting['remove'] = storage.remove;
    storeSetting['storage'] = storage.storage;
    var storageUtils = storeSetting['storageUtils'] = storage['storageUtils'] || {};
    storeSetting['write'] = storage.write;
    storeSetting['memoryStorage'] = storage['memoryStorage'];
    var storeSettings, pakoSetting;
    if (!!storageUtils && (!!(storeSettings = storageUtils['storeSetting'])) && (!!(pakoSetting = storeSettings['pakoSetting']))) {
        pakoSetting['level'] = level;
    }
    var store1 = store.createStore(storeSetting);
    store1.storage = storeSetting;
    return store1;
};


/***/ }),
/* 1 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * sessionStorage
 */
var SessionStorage = /** @class */ (function () {
    function SessionStorage() {
        this.storageUtils = new StorageUtils_1.StorageUtils();
    }
    SessionStorage.prototype.clearAll = function () {
        return this.storage().clear();
    };
    SessionStorage.prototype.each = function (fn) {
        for (var i = this.storage().length - 1; i >= 0; i--) {
            var key = this.storage().key(i);
            fn(this.read(key), key);
        }
    };
    SessionStorage.prototype.read = function (key) {
        var item = this.storage().getItem(key);
        return this.storageUtils.inGet(item);
    };
    SessionStorage.prototype.remove = function (key) {
        return this.storage().removeItem(key);
    };
    SessionStorage.prototype.storage = function () {
        try {
            return Global.sessionStorage;
        }
        catch (e) {
            return sessionStorage;
        }
    };
    SessionStorage.prototype.write = function (key, data) {
        data = this.storageUtils.deGet(data);
        this.storage().setItem(key, data);
    };
    return SessionStorage;
}());
exports.SessionStorage = SessionStorage;


/***/ }),
/* 2 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 处理值
 */
var StoreSetting_1 = __webpack_require__(3);
var StorageUtils = /** @class */ (function () {
    function StorageUtils() {
        this.storeSetting = new StoreSetting_1.StoreSetting();
    }
    /**
     * 处理值
     * @param data
     */
    StorageUtils.prototype.inGet = function (data) {
        var pakoSetting = this.storeSetting.pakoSetting;
        if (pakoSetting['level'] < 0) {
            return data;
        }
        try {
            return pako.inflate(data || "", pakoSetting);
        }
        catch (e) {
            return data;
        }
    };
    /**
     * 处理值
     * @param data
     */
    StorageUtils.prototype.deGet = function (data) {
        var pakoSetting = this.storeSetting.pakoSetting;
        if (pakoSetting['level'] < 0) {
            return data;
        }
        try {
            return pako.deflate(data || "", pakoSetting);
        }
        catch (e) {
            return data;
        }
    };
    return StorageUtils;
}());
exports.StorageUtils = StorageUtils;


/***/ }),
/* 3 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
/**
 *
 */
var StoreSetting = /** @class */ (function () {
    function StoreSetting() {
        this.pakoSetting = {
            level: 9,
            to: 'string'
        };
    }
    return StoreSetting;
}());
exports.StoreSetting = StoreSetting;


/***/ }),
/* 4 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * LocalStorage
 */
var LocalStorage = /** @class */ (function () {
    function LocalStorage() {
        this.storageUtils = new StorageUtils_1.StorageUtils();
    }
    LocalStorage.prototype.clearAll = function () {
        return this.storage().clear();
    };
    LocalStorage.prototype.each = function (fn) {
        for (var i = this.storage().length - 1; i >= 0; i--) {
            var key = this.storage().key(i);
            fn(this.read(key), key);
        }
    };
    LocalStorage.prototype.read = function (key) {
        var item = this.storage().getItem(key);
        return this.storageUtils.inGet(item);
    };
    LocalStorage.prototype.remove = function (key) {
        return this.storage().removeItem(key);
    };
    LocalStorage.prototype.storage = function () {
        try {
            return Global.localStorage;
        }
        catch (e) {
            return localStorage;
        }
    };
    LocalStorage.prototype.write = function (key, data) {
        data = this.storageUtils.deGet(data);
        this.storage().setItem(key, data);
    };
    return LocalStorage;
}());
exports.LocalStorage = LocalStorage;


/***/ }),
/* 5 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * oldIE-userDataStorage
 */
var OldIEUserDataStorage = /** @class */ (function () {
    function OldIEUserDataStorage() {
        this.storageName = 'storejs';
        this.doc = Global.document;
        this._withStorageEl = this._makeIEStorageElFunction();
        this.disable = (Global.navigator ? Global.navigator.userAgent : '').match(/ (MSIE 8|MSIE 9|MSIE 10)\./); // MSIE 9.x, MSIE 10.x
        this.forbiddenCharsRegex = new RegExp("[!\"#$%&'()*+,/\\\\:;<=>?@[\\]^`{|}~]", "g");
        this.storageUtils = new StorageUtils_1.StorageUtils();
    }
    OldIEUserDataStorage.prototype.clearAll = function () {
        this._withStorageEl(function (storageEl) {
            var attributes = storageEl.XMLDocument.documentElement.attributes;
            storageEl.load(this.storageName);
            for (var i = attributes.length - 1; i >= 0; i--) {
                storageEl.removeAttribute(attributes[i].name);
            }
            storageEl.save(this.storageName);
        });
    };
    OldIEUserDataStorage.prototype.each = function (fn) {
        this._withStorageEl(function (storageEl) {
            var attributes = storageEl.XMLDocument.documentElement.attributes;
            for (var i = attributes.length - 1; i >= 0; i--) {
                var attr = attributes[i];
                fn(storageEl.getAttribute(attr.name), attr.name);
            }
        });
    };
    OldIEUserDataStorage.prototype.read = function (key) {
        if (this.disable) {
            return;
        }
        var fixedKey = this.fixKey(key);
        var res = null;
        this._withStorageEl(function (storageEl) {
            res = storageEl.getAttribute(fixedKey);
        });
        return this.storageUtils.inGet(res);
    };
    OldIEUserDataStorage.prototype.remove = function (key) {
        var fixedKey = this.fixKey(key);
        this._withStorageEl(function (storageEl) {
            storageEl.removeAttribute(fixedKey);
            storageEl.save(this.storageName);
        });
    };
    OldIEUserDataStorage.prototype.storage = function () {
    };
    OldIEUserDataStorage.prototype.write = function (key, data) {
        if (this.disable) {
            return;
        }
        var fixedKey = this.fixKey(key);
        this._withStorageEl(function (storageEl) {
            data = this.storageUtils.deGet(data);
            storageEl.setAttribute(fixedKey, data);
            storageEl.save(this.storageName);
        });
    };
    OldIEUserDataStorage.prototype.fixKey = function (key) {
        return key.replace(/^\d/, '___$&').replace(this.forbiddenCharsRegex, '___');
    };
    /**
     *
     * @private
     */
    OldIEUserDataStorage.prototype._makeIEStorageElFunction = function () {
        if (!this.doc || !this.doc.documentElement || !this.doc.documentElement.addBehavior) {
            return null;
        }
        var scriptTag = 'script', storageOwner, storageContainer, storageEl;
        try {
            storageContainer = new ActiveXObject('htmlfile');
            storageContainer.open();
            storageContainer.write('<' + scriptTag + '>document.w=window</' + scriptTag + '><iframe src="/favicon.ico"></iframe>');
            storageContainer.close();
            storageOwner = storageContainer.w.frames[0].document;
            storageEl = storageOwner.createElement('div');
        }
        catch (e) {
            storageEl = this.doc.createElement('div');
            storageOwner = this.doc.body;
        }
        return function (storeFunction) {
            var args = [].slice.call(arguments, 0);
            args.unshift(storageEl);
            storageOwner.appendChild(storageEl);
            storageEl.addBehavior('#default#userData');
            storageEl.load(this.storageName);
            storeFunction.apply(this, args);
            storageOwner.removeChild(storageEl);
            return;
        };
    };
    return OldIEUserDataStorage;
}());
exports.OldIEUserDataStorage = OldIEUserDataStorage;


/***/ }),
/* 6 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * oldFF-globalStorage
 */
var OldFFGlobalStorage = /** @class */ (function () {
    function OldFFGlobalStorage() {
        this.storageUtils = new StorageUtils_1.StorageUtils();
    }
    OldFFGlobalStorage.prototype.clearAll = function () {
        this.each(function (key, _) {
            delete this.globalStorage[key];
        });
    };
    OldFFGlobalStorage.prototype.each = function (fn) {
        for (var i = this.storage().length - 1; i >= 0; i--) {
            var key = this.storage().key(i);
            fn(this.storage()[key], key);
        }
    };
    OldFFGlobalStorage.prototype.read = function (key) {
        var res = this.storage()[key];
        return this.storageUtils.inGet(res);
    };
    OldFFGlobalStorage.prototype.remove = function (key) {
        return this.storage().removeItem(key);
    };
    OldFFGlobalStorage.prototype.storage = function () {
        return Global.globalStorage;
    };
    OldFFGlobalStorage.prototype.write = function (key, data) {
        data = this.storageUtils.deGet(data);
        this.storage()[key] = data;
    };
    return OldFFGlobalStorage;
}());
exports.OldFFGlobalStorage = OldFFGlobalStorage;


/***/ }),
/* 7 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * MemoryStorage
 */
var MemoryStorage = /** @class */ (function () {
    function MemoryStorage() {
        this.storageUtils = new StorageUtils_1.StorageUtils();
        this.memoryStorage = {};
    }
    MemoryStorage.prototype.clearAll = function () {
        this.memoryStorage = {};
    };
    MemoryStorage.prototype.each = function (fn) {
        for (var key in this.memoryStorage) {
            if (this.memoryStorage.hasOwnProperty(key)) {
                fn(this.memoryStorage[key], key);
            }
        }
    };
    MemoryStorage.prototype.read = function (key) {
        var item = this.memoryStorage[key];
        return this.storageUtils.inGet(item);
    };
    MemoryStorage.prototype.remove = function (key) {
        delete this.memoryStorage[key];
    };
    MemoryStorage.prototype.storage = function () {
    };
    MemoryStorage.prototype.write = function (key, data) {
        data = this.storageUtils.deGet(data);
        this.memoryStorage[key] = data;
    };
    return MemoryStorage;
}());
exports.MemoryStorage = MemoryStorage;


/***/ }),
/* 8 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var StorageUtils_1 = __webpack_require__(2);
/**
 * CookieStorage
 */
var CookieStorage = /** @class */ (function () {
    function CookieStorage() {
        this.storageUtils = new StorageUtils_1.StorageUtils();
    }
    CookieStorage.prototype.clearAll = function () {
        this.each(function (_, key) {
            this.remove(key);
        });
    };
    CookieStorage.prototype.each = function (fn) {
        var cookies = this.storage().cookie.split(/; ?/g);
        for (var i = cookies.length - 1; i >= 0; i--) {
            if (!String.prototype.trim.call(cookies[i])) {
                continue;
            }
            var kvp = cookies[i].split('=');
            var key = unescape(kvp[0]);
            var val = unescape(kvp[1]);
            fn(val, key);
        }
    };
    CookieStorage.prototype.read = function (key) {
        if (!key || !this._has(key)) {
            return null;
        }
        var regexpStr = "(?:^|.*;\\s*)" +
            escape(key).replace(/[\-\.\+\*]/g, "\\$&") +
            "\\s*\\=\\s*((?:[^;](?!;))*[^;]?).*";
        var item = unescape(this.storage().cookie.replace(new RegExp(regexpStr), "$1"));
        return this.storageUtils.inGet(item);
    };
    CookieStorage.prototype.remove = function (key) {
        if (!key || !this._has(key)) {
            return;
        }
        this.storage().cookie = escape(key) + "=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    };
    CookieStorage.prototype.storage = function () {
        try {
            return Global.document;
        }
        catch (e) {
            return document;
        }
    };
    CookieStorage.prototype.write = function (key, data) {
        if (!key) {
            return;
        }
        data = escape(key) + "=" + escape(data) + "; expires=Tue, 19 Jan 2038 03:14:07 GMT; path=/";
        data = this.storageUtils.deGet(data);
        this.storage().cookie = data;
    };
    CookieStorage.prototype._has = function (key) {
        return (new RegExp("(?:^|;\\s*)" + escape(key).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=")).test(this.storage().cookie);
    };
    return CookieStorage;
}());
exports.CookieStorage = CookieStorage;


/***/ })
/******/ ])["storeJs"];
});