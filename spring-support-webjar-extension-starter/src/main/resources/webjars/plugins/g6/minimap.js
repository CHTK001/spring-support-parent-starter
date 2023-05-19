(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory(require("@antv/g6"));
	else if(typeof define === 'function' && define.amd)
		define(["@antv/g6"], factory);
	else if(typeof exports === 'object')
		exports["Minimap"] = factory(require("@antv/g6"));
	else
		root["Minimap"] = factory(root["G6"]);
})(window, function(__WEBPACK_EXTERNAL_MODULE__antv_g6__) {
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
/******/ 	return __webpack_require__(__webpack_require__.s = "./plugins/minimap/index.js");
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

/***/ "./node_modules/@antv/util/lib/type/is-nil.js":
/*!****************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-nil.js ***!
  \****************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

// isFinite,
var isNil = function isNil(value) {
  /**
   * isNil(null) => true
   * isNil() => true
   */
  return value === null || value === undefined;
};

module.exports = isNil;

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

/***/ "./node_modules/@antv/util/lib/type/is-string.js":
/*!*******************************************************!*\
  !*** ./node_modules/@antv/util/lib/type/is-string.js ***!
  \*******************************************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var isType = __webpack_require__(/*! ./is-type */ "./node_modules/@antv/util/lib/type/is-type.js");

var isString = function isString(str) {
  return isType(str, 'String');
};

module.exports = isString;

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

/***/ "./plugins/minimap/index.js":
/*!**********************************!*\
  !*** ./plugins/minimap/index.js ***!
  \**********************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

function _extends() { _extends = Object.assign || function (target) { for (var i = 1; i < arguments.length; i++) { var source = arguments[i]; for (var key in source) { if (Object.prototype.hasOwnProperty.call(source, key)) { target[key] = source[key]; } } } return target; }; return _extends.apply(this, arguments); }

function _inheritsLoose(subClass, superClass) { subClass.prototype = Object.create(superClass.prototype); subClass.prototype.constructor = subClass; subClass.__proto__ = superClass; }

var G = __webpack_require__(/*! @antv/g6 */ "@antv/g6").G;

var Base = __webpack_require__(/*! ../base */ "./plugins/base.js");

var isString = __webpack_require__(/*! @antv/util/lib/type/is-string */ "./node_modules/@antv/util/lib/type/is-string.js");

var isNil = __webpack_require__(/*! @antv/util/lib/type/is-nil */ "./node_modules/@antv/util/lib/type/is-nil.js");

var createDOM = __webpack_require__(/*! @antv/util/lib/dom/create-dom */ "./node_modules/@antv/util/lib/dom/create-dom.js");

var modifyCSS = __webpack_require__(/*! @antv/util/lib/dom/modify-css */ "./node_modules/@antv/util/lib/dom/modify-css.js");

var each = __webpack_require__(/*! @antv/util/lib/each */ "./node_modules/@antv/util/lib/each.js");

var max = Math.max;
var DEFAULT_MODE = 'default';
var KEYSHAPE_MODE = 'keyShape';
var DELEGATE_MODE = 'delegate';

var Minimap =
/*#__PURE__*/
function (_Base) {
  _inheritsLoose(Minimap, _Base);

  function Minimap() {
    return _Base.apply(this, arguments) || this;
  }

  var _proto = Minimap.prototype;

  _proto.init = function init() {
    this.initContainer();
  };

  _proto.getDefaultCfgs = function getDefaultCfgs() {
    return {
      container: null,
      className: 'g6-minimap',
      viewportClassName: 'g6-minimap-viewport',
      type: 'default',
      // 可选 default, delegate, keyShape
      size: [200, 120],
      delegateStyle: {
        fill: '#40a9ff',
        stroke: '#096dd9'
      },
      refresh: true
    };
  };

  _proto.getEvents = function getEvents() {
    return {
      beforepaint: 'updateCanvas',
      beforeanimate: 'disableRefresh',
      afteranimate: 'enableRefresh',
      viewportchange: 'disableOneRefresh'
    };
  } // 若是正在进行动画，不刷新缩略图
  ;

  _proto.disableRefresh = function disableRefresh() {
    this.set('refresh', false);
  };

  _proto.enableRefresh = function enableRefresh() {
    this.set('refresh', true);
    this.updateCanvas();
  };

  _proto.disableOneRefresh = function disableOneRefresh() {
    this.set('viewportChange', true);
  };

  _proto.initContainer = function initContainer() {
    var self = this;
    var graph = self.get('graph');
    var size = self.get('size');
    var className = self.get('className');
    var parentNode = self.get('container');
    var container = createDOM('<div class="' + className + '" style="width:' + size[0] + 'px; height:' + size[1] + 'px"></div>');

    if (isString(parentNode)) {
      parentNode = document.getElementById(parentNode);
    }

    if (parentNode) {
      parentNode.appendChild(container);
    } else {
      graph.get('container').appendChild(container);
    }

    self.set('container', container);
    var containerDOM = createDOM('<div class="g6-minimap-container"></div>');
    container.appendChild(containerDOM);
    var canvas = new G.Canvas({
      containerDOM: containerDOM,
      width: size[0],
      height: size[1],
      pixelRatio: graph.get('pixelRatio')
    });
    self.set('canvas', canvas);
    self.updateCanvas();
  };

  _proto.initViewport = function initViewport() {
    var _this = this;

    var cfgs = this._cfgs;
    var size = cfgs.size;
    var graph = cfgs.graph;
    var canvas = this.get('canvas');
    var containerDOM = canvas.get('containerDOM');
    var viewport = createDOM('<div class="' + cfgs.viewportClassName + '" style="position:absolute;left:0;top:0;box-sizing:border-box;border: 2px solid #1980ff"></div>');
    var x, // 计算拖拽水平方向距离
    y, // 计算拖拽垂直方向距离
    dragging, // 是否在拖拽minimap的视口
    left, // 缓存viewport当前对于画布的x
    top, // 缓存viewport当前对于画布的y
    width, // 缓存viewport当前宽度
    height; // 缓存viewport当前高度

    containerDOM.addEventListener('mousedown', function (e) {
      cfgs.refresh = false;

      if (e.target !== viewport) {
        return;
      } // 如果视口已经最大了，不需要拖拽


      var style = viewport.style;
      left = parseInt(style.left, 10);
      top = parseInt(style.top, 10);
      width = parseInt(style.width, 10);
      height = parseInt(style.height, 10);

      if (width >= size[0] || height >= size[1]) {
        return;
      }

      dragging = true;
      x = e.clientX;
      y = e.clientY;
    }, false);
    containerDOM.addEventListener('mousemove', function (e) {
      if (!dragging || isNil(e.clientX) || isNil(e.clientY)) {
        return;
      }

      var dx = x - e.clientX;
      var dy = y - e.clientY; // 若视口移动到最左边或最右边了,仅移动到边界

      if (left - dx < 0) {
        dx = left;
      } else if (left - dx + width > size[0]) {
        dx = left + width - size[0];
      } // 若视口移动到最上或最下边了，仅移动到边界


      if (top - dy < 0) {
        dy = top;
      } else if (top - dy + height > size[1]) {
        dy = top + height - size[1];
      }

      left -= dx;
      top -= dy; // 先移动视口，避免移动到边上以后出现视口闪烁

      modifyCSS(viewport, {
        left: left + 'px',
        top: top + 'px'
      });

      var ratio = _this.get('ratio');

      graph.translate(dx / ratio, dy / ratio);
      x = e.clientX;
      y = e.clientY;
    }, false);
    containerDOM.addEventListener('mouseleave', function () {
      dragging = false;
      cfgs.refresh = true;
    }, false);
    containerDOM.addEventListener('mouseup', function () {
      dragging = false;
      cfgs.refresh = true;
    }, false);
    this.set('viewport', viewport);
    containerDOM.appendChild(viewport);
  };

  _proto.updateCanvas = function updateCanvas() {
    // 如果是在动画，则不刷新视图
    if (!this.get('refresh')) {
      return;
    } // 如果是视口变换，也不刷新视图，但是需要重置视口大小和位置


    if (this.get('viewportChange')) {
      this.set('viewportChange', false);

      this._updateViewport();
    }

    var size = this.get('size');
    var graph = this.get('graph');
    var canvas = this.get('canvas');
    var type = this.get('type');

    switch (type) {
      case DEFAULT_MODE:
        this._updateGraphShapes();

        break;

      case KEYSHAPE_MODE:
        this._updateKeyShapes();

        break;

      case DELEGATE_MODE:
        this._updateDelegateShapes();

        break;

      default:
        this._updateGraphShapes();

    }

    var bbox = canvas.getBBox(); // 刷新后bbox可能会变，需要重置画布矩阵以缩放到合适的大小

    var width = max(bbox.width, graph.get('width'));
    var height = max(bbox.height, graph.get('height'));
    var pixelRatio = canvas.get('pixelRatio');
    var ratio = Math.min(size[0] / width, size[1] / height);
    canvas.resetMatrix(); // 如果bbox为负，先平移到左上角

    var minX = -(bbox.minX > 0 ? 0 : bbox.minX);
    var minY = -(bbox.minY > 0 ? 0 : bbox.minY);
    canvas.translate(minX, minY);
    canvas.scale(ratio * pixelRatio, ratio * pixelRatio); // 缩放到适合视口后, 平移到画布中心

    var dx = (size[0] - width * ratio) / 2;
    var dy = (size[1] - height * ratio) / 2;
    canvas.translate(dx * pixelRatio, dy * pixelRatio);
    canvas.draw(); // 更新minimap视口

    this.set('ratio', ratio);
    this.set('dx', dx + minX * ratio);
    this.set('dy', dy + minY * ratio);

    this._updateViewport();
  } // 仅在minimap上绘制keyShape
  // FIXME 如果用户自定义绘制了其他内容，minimap上就无法画出
  ;

  _proto._updateKeyShapes = function _updateKeyShapes() {
    var graph = this._cfgs.graph;
    var canvas = this.get('canvas');
    var group = canvas.get('children')[0];

    if (!group) {
      group = canvas.addGroup();
      group.setMatrix(graph.get('group').getMatrix());
    }

    var nodes = graph.getNodes();
    group.clear();

    this._getGraphEdgeKeyShape(group); // 节点需要group配合keyShape


    each(nodes, function (node) {
      if (node.isVisible()) {
        var parent = group.addGroup();
        parent.setMatrix(node.get('group').attr('matrix'));
        parent.add(node.get('keyShape').clone());
      }
    });
  } // 将主图上的图形完全复制到小图
  ;

  _proto._updateGraphShapes = function _updateGraphShapes() {
    var graph = this.get('graph');
    var canvas = this.get('canvas');
    var graphGroup = graph.get('group');
    var clonedGroup = graphGroup.clone();
    clonedGroup.resetMatrix();
    canvas.get('children')[0] = clonedGroup;
  } // 将主图上的node用
  ;

  _proto._updateDelegateShapes = function _updateDelegateShapes() {
    var graph = this._cfgs.graph;
    var canvas = this.get('canvas');
    var group = canvas.get('children')[0] || canvas.addGroup();
    var delegateStyle = this.get('delegateStyle');
    group.clear();

    this._getGraphEdgeKeyShape(group);

    each(graph.getNodes(), function (node) {
      if (node.isVisible()) {
        var bbox = node.getBBox();
        group.addShape('rect', {
          attrs: _extends({
            x: bbox.minX,
            y: bbox.minY,
            width: bbox.width,
            height: bbox.height
          }, delegateStyle)
        });
      }
    });
  };

  _proto._getGraphEdgeKeyShape = function _getGraphEdgeKeyShape(group) {
    var graph = this.get('graph');
    each(graph.getEdges(), function (edge) {
      if (edge.isVisible()) {
        group.add(edge.get('keyShape').clone());
      }
    });
  } // 绘制minimap视口
  ;

  _proto._updateViewport = function _updateViewport() {
    var ratio = this.get('ratio');
    var dx = this.get('dx');
    var dy = this.get('dy');
    var graph = this.get('graph');
    var size = this.get('size');
    var graphWidth = graph.get('width');
    var graphHeight = graph.get('height');
    var topLeft = graph.getPointByCanvas(0, 0);
    var bottomRight = graph.getPointByCanvas(graphWidth, graphHeight);
    var viewport = this.get('viewport');

    if (!viewport) {
      this.initViewport();
    } // viewport宽高,左上角点的计算


    var width = (bottomRight.x - topLeft.x) * ratio;
    var height = (bottomRight.y - topLeft.y) * ratio;
    var left = topLeft.x * ratio + dx;
    var top = topLeft.y * ratio + dy;

    if (width > size[0]) {
      width = size[0];
    }

    if (height > size[1]) {
      height = size[1];
    } // 缓存目前缩放比，在移动 minimap 视窗时就不用再计算大图的移动量


    this.set('ratio', ratio);
    modifyCSS(viewport, {
      left: left > 0 ? left + 'px' : 0,
      top: top > 0 ? top + 'px' : 0,
      width: width + 'px',
      height: height + 'px'
    });
  }
  /**
   * 获取minimap的画布
   * @return {object} G的canvas实例
   */
  ;

  _proto.getCanvas = function getCanvas() {
    return this.get('canvas');
  }
  /**
   * 获取minimap的窗口
   * @return {object} 窗口的dom实例
   */
  ;

  _proto.getViewport = function getViewport() {
    return this.get('viewport');
  }
  /**
   * 获取minimap的容器dom
   * @return {object} dom
   */
  ;

  _proto.getContainer = function getContainer() {
    return this.get('container');
  };

  _proto.destroy = function destroy() {
    this.get('canvas').destroy();
    var container = this.get('container');
    container.parentNode.removeChild(container);
  };

  return Minimap;
}(Base);

module.exports = Minimap;

/***/ }),

/***/ "@antv/g6":
/*!********************************************************************************************!*\
  !*** external {"root":"G6","commonjs2":"@antv/g6","commonjs":"@antv/g6","amd":"@antv/g6"} ***!
  \********************************************************************************************/
/*! no static exports found */
/***/ (function(module, exports) {

module.exports = __WEBPACK_EXTERNAL_MODULE__antv_g6__;

/***/ })

/******/ });
});
//# sourceMappingURL=minimap.js.map