(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory();
	else if(typeof define === 'function' && define.amd)
		define([], factory);
	else if(typeof exports === 'object')
		exports["Grid"] = factory();
	else
		root["Grid"] = factory();
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
/******/ 	return __webpack_require__(__webpack_require__.s = "./plugins/grid/index.js");
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

/***/ "./node_modules/@antv/util/lib/dom/create-dom.js":
/*!*******************************************************!*\
  !*** ./node_modules/@antv/util/lib/dom/create-dom.js ***!
  \*******************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

/**
 * 创建DOM 节点
 * @param  {String} str Dom 字符串
 * @return {HTMLElement}  DOM 节点
 */
var TABLE = document.createElement('table');
var TABLE_TR = document.createElement('tr');
var FRAGMENT_REG = /^\s*<(\w+|!)[^>]*>/;
var CONTAINERS = {
  tr: document.createElement('tbody'),
  tbody: TABLE,
  thead: TABLE,
  tfoot: TABLE,
  td: TABLE_TR,
  th: TABLE_TR,
  '*': document.createElement('div')
};

module.exports = function createDom(str) {
  var name = FRAGMENT_REG.test(str) && RegExp.$1;
  if (!(name in CONTAINERS)) {
    name = '*';
  }
  var container = CONTAINERS[name];
  str = str.replace(/(^\s*)|(\s*$)/g, '');
  container.innerHTML = '' + str;
  var dom = container.childNodes[0];
  container.removeChild(dom);
  return dom;
};

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

/***/ "./plugins/grid/index.js":
/*!*******************************!*\
  !*** ./plugins/grid/index.js ***!
  \*******************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

function _inheritsLoose(subClass, superClass) { subClass.prototype = Object.create(superClass.prototype); subClass.prototype.constructor = subClass; subClass.__proto__ = superClass; }

var Base = __webpack_require__(/*! ../base */ "./plugins/base.js");

var createDOM = __webpack_require__(/*! @antv/util/lib/dom/create-dom */ "./node_modules/@antv/util/lib/dom/create-dom.js");

var modifyCSS = __webpack_require__(/*! @antv/util/lib/dom/modify-css */ "./node_modules/@antv/util/lib/dom/modify-css.js");

var GRID = 'url(data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGRlZnM+PHBhdHRlcm4gaWQ9ImdyaWQiIHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTSAwIDEwIEwgNDAgMTAgTSAxMCAwIEwgMTAgNDAgTSAwIDIwIEwgNDAgMjAgTSAyMCAwIEwgMjAgNDAgTSAwIDMwIEwgNDAgMzAgTSAzMCAwIEwgMzAgNDAiIGZpbGw9Im5vbmUiIHN0cm9rZT0iI2UwZTBlMCIgb3BhY2l0eT0iMC4yIiBzdHJva2Utd2lkdGg9IjEiLz48cGF0aCBkPSJNIDQwIDAgTCAwIDAgMCA0MCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjZTBlMGUwIiBzdHJva2Utd2lkdGg9IjEiLz48L3BhdHRlcm4+PC9kZWZzPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9InVybCgjZ3JpZCkiLz48L3N2Zz4=)';

var Grid =
/*#__PURE__*/
function (_Base) {
  _inheritsLoose(Grid, _Base);

  function Grid() {
    return _Base.apply(this, arguments) || this;
  }

  var _proto = Grid.prototype;

  _proto.init = function init() {
    var graph = this.get('graph');
    var minZoom = graph.get('minZoom');
    var graphContainer = graph.get('container');
    var canvas = graph.get('canvas').get('el');
    var width = graph.get('width');
    var height = graph.get('height');
    var container = createDOM('<div style="position: absolute; left:0;top:0;right:0;bottom:0;overflow: hidden;z-index: -1;"></div>');
    var gridContainer = createDOM('<div class="g6-grid" style="position:absolute;transform-origin: 0% 0% 0px; background-image: ' + GRID + '"></div>');
    container.appendChild(gridContainer);
    modifyCSS(gridContainer, {
      width: width / minZoom + 'px',
      height: height / minZoom + 'px',
      left: 0,
      top: 0
    });
    graphContainer.insertBefore(container, canvas);
    this.set('container', container);
    this.set('gridContainer', gridContainer);
  };

  _proto.getEvents = function getEvents() {
    return {
      viewportchange: 'updateGrid'
    };
  };

  _proto.updateGrid = function updateGrid(e) {
    var gridContainer = this.get('gridContainer');
    var matrix = e.matrix;
    var transform = 'matrix(' + matrix[0] + ',' + matrix[1] + ',' + matrix[3] + ',' + matrix[4] + ',' + 0 + ',' + 0 + ')';
    modifyCSS(gridContainer, {
      transform: transform
    });
  };

  _proto.getContainer = function getContainer() {
    return this.get('container');
  };

  _proto.destroy = function destroy() {
    var graphContainer = this.get('graph').get('container');
    graphContainer.removeChild(this.get('container'));
  };

  return Grid;
}(Base);

module.exports = Grid;

/***/ })

/******/ });
});
//# sourceMappingURL=grid.js.map