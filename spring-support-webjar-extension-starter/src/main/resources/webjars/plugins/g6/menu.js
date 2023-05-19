(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory();
	else if(typeof define === 'function' && define.amd)
		define([], factory);
	else if(typeof exports === 'object')
		exports["Menu"] = factory();
	else
		root["Menu"] = factory();
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
/******/ 	return __webpack_require__(__webpack_require__.s = "./plugins/menu/index.js");
/******/ })
/************************************************************************/
/******/ ({

/***/ "./node_modules/@antv/util/lib/deep-mix.js":
/*!*************************************************!*\
  !*** ./node_modules/@antv/util/lib/deep-mix.js ***!
  \*************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var isPlainObject = __webpack_require__(/*! ./type/is-plain-object */ "./node_modules/@antv/util/lib/type/is-plain-object.js");
var isArray = __webpack_require__(/*! ./type/is-array */ "./node_modules/@antv/util/lib/type/is-array.js");

var MAX_MIX_LEVEL = 5;

function _deepMix(dist, src, level, maxLevel) {
  level = level || 0;
  maxLevel = maxLevel || MAX_MIX_LEVEL;
  for (var key in src) {
    if (src.hasOwnProperty(key)) {
      var value = src[key];
      if (value !== null && isPlainObject(value)) {
        if (!isPlainObject(dist[key])) {
          dist[key] = {};
        }
        if (level < maxLevel) {
          _deepMix(dist[key], value, level + 1, maxLevel);
        } else {
          dist[key] = src[key];
        }
      } else if (isArray(value)) {
        dist[key] = [];
        dist[key] = dist[key].concat(value);
      } else if (value !== undefined) {
        dist[key] = value;
      }
    }
  }
}

var deepMix = function deepMix() {
  var args = new Array(arguments.length);
  var length = args.length;
  for (var i = 0; i < length; i++) {
    args[i] = arguments[i];
  }
  var rst = args[0];
  for (var _i = 1; _i < length; _i++) {
    _deepMix(rst, args[_i]);
  }
  return rst;
};

module.exports = deepMix;

/***/ }),

/***/ "./node_modules/@antv/util/lib/dom/modify-css.js":
/*!*******************************************************!*\
  !*** ./node_modules/@antv/util/lib/dom/modify-css.js ***!
  \*******************************************************/
/*! no static exports found */
/***/ (function(module, exports) {


module.exports = function modifyCSS(dom, css) {
  if (dom) {
    for (var key in css) {
      if (css.hasOwnProperty(key)) {
        dom.style[key] = css[key];
      }
    }
  }
  return dom;
};

/***/ }),

/***/ "./node_modules/@antv/util/lib/each.js":
/*!*********************************************!*\
  !*** ./node_modules/@antv/util/lib/each.js ***!
  \*********************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var isObject = __webpack_require__(/*! ./type/is-object */ "./node_modules/@antv/util/lib/type/is-object.js");
var isArray = __webpack_require__(/*! ./type/is-array */ "./node_modules/@antv/util/lib/type/is-array.js");

var each = function each(elements, func) {
  if (!elements) {
    return;
  }
  var rst = void 0;
  if (isArray(elements)) {
    for (var i = 0, len = elements.length; i < len; i++) {
      rst = func(elements[i], i);
      if (rst === false) {
        break;
      }
    }
  } else if (isObject(elements)) {
    for (var k in elements) {
      if (elements.hasOwnProperty(k)) {
        rst = func(elements[k], k);
        if (rst === false) {
          break;
        }
      }
    }
  }
};

module.exports = each;

/***/ }),

/***/ "./node_modules/@antv/util/lib/event/wrap-behavior.js":
/*!************************************************************!*\
  !*** ./node_modules/@antv/util/lib/event/wrap-behavior.js ***!
  \************************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

/**
 * 封装事件，便于使用上下文this,和便于解除事件时使用
 * @protected
 * @param  {Object} obj   对象
 * @param  {String} action 事件名称
 * @return {Function}        返回事件处理函数
 */
function wrapBehavior(obj, action) {
  if (obj['_wrap_' + action]) {
    return obj['_wrap_' + action];
  }
  var method = function method(e) {
    obj[action](e);
  };
  obj['_wrap_' + action] = method;
  return method;
}

module.exports = wrapBehavior;

/***/ }),

/***/ "./node_modules/@antv/util/lib/type/is-array.js":
/*!******************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-array.js ***!
  \******************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var isType = __webpack_require__(/*! ./is-type */ "./node_modules/@antv/util/lib/type/is-type.js");

var isArray = Array.isArray ? Array.isArray : function (value) {
  return isType(value, 'Array');
};

module.exports = isArray;

/***/ }),

/***/ "./node_modules/@antv/util/lib/type/is-object-like.js":
/*!************************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-object-like.js ***!
  \************************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function (obj) { return typeof obj; } : function (obj) { return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; };

var isObjectLike = function isObjectLike(value) {
  /**
   * isObjectLike({}) => true
   * isObjectLike([1, 2, 3]) => true
   * isObjectLike(Function) => false
   * isObjectLike(null) => false
   */
  return (typeof value === 'undefined' ? 'undefined' : _typeof(value)) === 'object' && value !== null;
};

module.exports = isObjectLike;

/***/ }),

/***/ "./node_modules/@antv/util/lib/type/is-object.js":
/*!*******************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-object.js ***!
  \*******************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function (obj) { return typeof obj; } : function (obj) { return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; };

var isObject = function isObject(value) {
  /**
   * isObject({}) => true
   * isObject([1, 2, 3]) => true
   * isObject(Function) => true
   * isObject(null) => false
   */
  var type = typeof value === 'undefined' ? 'undefined' : _typeof(value);
  return value !== null && type === 'object' || type === 'function';
};

module.exports = isObject;

/***/ }),

/***/ "./node_modules/@antv/util/lib/type/is-plain-object.js":
/*!*************************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-plain-object.js ***!
  \*************************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var isObjectLike = __webpack_require__(/*! ./is-object-like */ "./node_modules/@antv/util/lib/type/is-object-like.js");
var isType = __webpack_require__(/*! ./is-type */ "./node_modules/@antv/util/lib/type/is-type.js");

var isPlainObject = function isPlainObject(value) {
  /**
   * isObjectLike(new Foo) => false
   * isObjectLike([1, 2, 3]) => false
   * isObjectLike({ x: 0, y: 0 }) => true
   * isObjectLike(Object.create(null)) => true
   */
  if (!isObjectLike(value) || !isType(value, 'Object')) {
    return false;
  }
  if (Object.getPrototypeOf(value) === null) {
    return true;
  }
  var proto = value;
  while (Object.getPrototypeOf(proto) !== null) {
    proto = Object.getPrototypeOf(proto);
  }
  return Object.getPrototypeOf(value) === proto;
};

module.exports = isPlainObject;

/***/ }),

/***/ "./node_modules/@antv/util/lib/type/is-type.js":
/*!*****************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-type.js ***!
  \*****************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

var toString = {}.toString;
var isType = function isType(value, type) {
  return toString.call(value) === '[object ' + type + ']';
};

module.exports = isType;

/***/ }),

/***/ "./plugins/base.js":
/*!*************************!*\
  !*** ./plugins/base.js ***!
  \*************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var deepMix = __webpack_require__(/*! @antv/util/lib/deep-mix */ "./node_modules/@antv/util/lib/deep-mix.js");

var each = __webpack_require__(/*! @antv/util/lib/each */ "./node_modules/@antv/util/lib/each.js");

var wrapBehavior = __webpack_require__(/*! @antv/util/lib/event/wrap-behavior */ "./node_modules/@antv/util/lib/event/wrap-behavior.js");

var PluginBase =
/*#__PURE__*/
function () {
  function PluginBase(cfgs) {
    this._cfgs = deepMix(this.getDefaultCfgs(), cfgs);
  }

  var _proto = PluginBase.prototype;

  _proto.getDefaultCfgs = function getDefaultCfgs() {
    return {};
  };

  _proto.initPlugin = function initPlugin(graph) {
    var self = this;
    self.set('graph', graph);
    var events = self.getEvents();
    var bindEvents = {};
    each(events, function (v, k) {
      var event = wrapBehavior(self, v);
      bindEvents[k] = event;
      graph.on(k, event);
    });
    this._events = bindEvents;
    this.init();
  };

  _proto.init = function init() {};

  _proto.getEvents = function getEvents() {
    return {};
  };

  _proto.get = function get(key) {
    return this._cfgs[key];
  };

  _proto.set = function set(key, val) {
    this._cfgs[key] = val;
  };

  _proto.destroy = function destroy() {};

  _proto.destroyPlugin = function destroyPlugin() {
    this.destroy();
    var graph = this.get('graph');
    var events = this._events;
    each(events, function (v, k) {
      graph.off(k, v);
    });
    this._events = null;
    this._cfgs = null;
    this.destroyed = true;
  };

  return PluginBase;
}();

module.exports = PluginBase;

/***/ }),

/***/ "./plugins/menu/index.js":
/*!*******************************!*\
  !*** ./plugins/menu/index.js ***!
  \*******************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

function _inheritsLoose(subClass, superClass) { subClass.prototype = Object.create(superClass.prototype); subClass.prototype.constructor = subClass; subClass.__proto__ = superClass; }

var Base = __webpack_require__(/*! ../base */ "./plugins/base.js");

var modifyCSS = __webpack_require__(/*! @antv/util/lib/dom/modify-css */ "./node_modules/@antv/util/lib/dom/modify-css.js");

var Menu =
/*#__PURE__*/
function (_Base) {
  _inheritsLoose(Menu, _Base);

  function Menu() {
    return _Base.apply(this, arguments) || this;
  }

  var _proto = Menu.prototype;

  _proto.getDefaultCfgs = function getDefaultCfgs() {
    return {
      createDOM: true,
      // 是否渲染 dom
      container: null,
      // menu 容器。若不指定就用 graph 的 container
      className: 'g6-analyzer-menu',
      // 指定 container css
      getContent: null,
      // 指定菜单内容，function(e) {...}
      onShow: function onShow() {},
      // 菜单展示事件
      onHide: function onHide() {} // 菜单隐藏事件

    };
  };

  _proto.getEvents = function getEvents() {
    return {
      contextmenu: 'onMenuShow'
    };
  };

  _proto.init = function init() {
    if (!this.get('createDOM')) {
      return;
    } // 如果指定组件生成 menu 内容,生成菜单 dom


    var menu = document.createElement('div');
    menu.className = this.get('className');
    modifyCSS(menu, {
      visibility: 'hidden'
    });
    var container = this.get('container');

    if (!container) {
      container = this.get('graph').get('container');
    }

    container.appendChild(menu);
    this.set('menu', menu);
  };

  _proto.onMenuShow = function onMenuShow(e) {
    var self = this;
    var menu = this.get('menu');
    var getContent = this.get('getContent');
    var onShow = this.get('onShow');

    if (getContent) {
      menu.innerHTML = getContent(e);
    }

    if (menu) {
      var graph = this.get('graph');
      var width = graph.get('width');
      var height = graph.get('height');
      var bbox = menu.getBoundingClientRect();
      var x = e.canvasX,
          y = e.canvasY; // 若菜单超出画布范围，反向

      if (x + bbox.width > width) {
        x = width - bbox.width;
        e.canvasX = x;
      }

      if (y + bbox.height > height) {
        y = height - bbox.height;
        e.canvasY = y;
      }

      if (!onShow || onShow(e) !== false) {
        modifyCSS(menu, {
          top: y,
          left: x,
          visibility: 'visible'
        });
      }
    } else {
      onShow(e);
    }

    var handler = function handler() {
      self.onMenuHide();
    }; // 如果在页面中其他任意地方进行click, 隐去菜单


    document.body.addEventListener('click', handler);
    this.set('handler', handler);
  };

  _proto.onMenuHide = function onMenuHide() {
    var menu = this.get('menu');

    if (this.get('onHide')() !== false) {
      menu && modifyCSS(menu, {
        visibility: 'hidden'
      }); // 隐藏菜单后需要移除事件监听

      document.body.removeEventListener('click', this.get('handler'));
    }
  };

  _proto.destroy = function destroy() {
    var menu = this.get('menu');
    var handler = this.get('handler');

    if (menu) {
      menu.parentNode.removeChild(menu);
    }

    if (handler) {
      document.body.removeEventListener('click', handler);
    }
  };

  return Menu;
}(Base);

module.exports = Menu;

/***/ })

/******/ });
});
//# sourceMappingURL=menu.js.map