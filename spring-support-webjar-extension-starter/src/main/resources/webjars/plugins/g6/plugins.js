(function webpackUniversalModuleDefinition(root, factory) {
	if(typeof exports === 'object' && typeof module === 'object')
		module.exports = factory(require("@antv/g6"));
	else if(typeof define === 'function' && define.amd)
		define(["@antv/g6"], factory);
	else if(typeof exports === 'object')
		exports["plugins"] = factory(require("@antv/g6"));
	else
		root["plugins"] = factory(root["G6"]);
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
/******/ 	return __webpack_require__(__webpack_require__.s = "./plugins/index.js");
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

/***/ "./plugins/bundling/index.js":
/*!***********************************!*\
  !*** ./plugins/bundling/index.js ***!
  \***********************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

function _inheritsLoose(subClass, superClass) { subClass.prototype = Object.create(superClass.prototype); subClass.prototype.constructor = subClass; subClass.__proto__ = superClass; }

var Base = __webpack_require__(/*! ../base */ "./plugins/base.js");

function getEucliDis(pointA, pointB, eps) {
  var vx = pointA.x - pointB.x;
  var vy = pointA.y - pointB.y;

  if (!eps || Math.abs(vx) > eps || Math.abs(vy) > eps) {
    return Math.sqrt(vx * vx + vy * vy);
  }

  return eps;
}

function getDotProduct(ei, ej) {
  return ei.x * ej.x + ei.y * ej.y;
}

function projectPointToEdge(p, e) {
  var k = (e.source.y - e.target.y) / (e.source.x - e.target.x);
  var x = (k * k * e.source.x + k * (p.y - e.source.y) + p.x) / (k * k + 1);
  var y = k * (x - e.source.x) + e.source.y;
  return {
    x: x,
    y: y
  };
}

var Bundling =
/*#__PURE__*/
function (_Base) {
  _inheritsLoose(Bundling, _Base);

  function Bundling() {
    return _Base.apply(this, arguments) || this;
  }

  var _proto = Bundling.prototype;

  _proto.getDefaultCfgs = function getDefaultCfgs() {
    return {
      edgeBundles: [],
      // |edges| arrays, each one stores the related edges' id
      edgePoints: [],
      // |edges| * divisions edge points
      K: 0.1,
      // 边的强度
      lambda: 0.1,
      // 初始步长
      divisions: 1,
      // 初始切割点数
      divRate: 2,
      // subdivision rate increase
      cycles: 6,
      // number of cycles to perform
      iterations: 90,
      // 每个 cycle 初始迭代次数
      iterRate: 0.6666667,
      // 迭代下降率
      bundleThreshold: 0.6,
      eps: 1e-6,
      onLayoutEnd: function onLayoutEnd() {},
      // 布局完成回调
      onTick: function onTick() {} // 每一迭代布局回调

    };
  };

  _proto.init = function init() {
    var graph = this.get('graph');
    var onTick = this.get('onTick');

    var tick = function tick() {
      onTick && onTick();
      graph.refreshPositions();
    };

    this.set('tick', tick);
  };

  _proto.bundling = function bundling(data) {
    var self = this;
    self.set('data', data); // 如果正在布局，忽略布局请求

    if (self.isTicking()) {
      return;
    }

    var edges = data.edges;
    var nodes = data.nodes;
    var nodeIdMap = new Map();
    var error = false;
    nodes.forEach(function (node) {
      if (node.x === null || !node.y === null || node.x === undefined || !node.y === undefined) {
        error = true;
      }

      nodeIdMap.set(node.id, node);
    });
    if (error) throw new Error('please layout the graph or assign x and y for nodes first');
    self.set('nodeIdMap', nodeIdMap); // subdivide each edges

    var divisions = self.get('divisions');
    var divRate = self.get('divRate');
    var edgePoints = self.divideEdges(divisions);
    self.set('edgePoints', edgePoints); // compute the bundles

    var edgeBundles = self.getEdgeBundles();
    self.set('edgeBundles', edgeBundles); // iterations

    var C = self.get('cycles');
    var iterations = self.get('iterations');
    var iterRate = self.get('iterRate');
    var lambda = self.get('lambda');

    for (var i = 0; i < C; i++) {
      var _loop = function _loop(j) {
        var forces = [];
        edges.forEach(function (e, k) {
          if (e.source === e.target) return;
          var source = nodeIdMap.get(e.source);
          var target = nodeIdMap.get(e.target);
          forces[k] = self.getEdgeForces({
            source: source,
            target: target
          }, k, divisions, lambda);

          for (var p = 0; p < divisions + 1; p++) {
            edgePoints[k][p].x += forces[k][p].x;
            edgePoints[k][p].y += forces[k][p].y;
          }
        });
      };

      for (var j = 0; j < iterations; j++) {
        _loop(j);
      } // parameters for nex cycle


      lambda = lambda / 2;
      divisions *= divRate;
      iterations *= iterRate;
      edgePoints = self.divideEdges(divisions);
      self.set('edgePoints', edgePoints);
    } // change the edges according to edgePoints


    edges.forEach(function (e, i) {
      if (e.source === e.target) return;
      e.shape = 'polyline';
      e.controlPoints = edgePoints[i].slice(1, edgePoints[i].length - 1);
    });
    var graph = self.get('graph');
    graph.refresh();
  };

  _proto.updateBundling = function updateBundling(cfg) {
    var self = this;
    var data = cfg.data;

    if (data) {
      self.set('data', data);
    }

    if (self.get('ticking')) {
      self.set('ticking', false);
    }

    Object.keys(cfg).forEach(function (key) {
      self.set(key, cfg[key]);
    });

    if (cfg.onTick) {
      self.set('tick', function () {
        cfg.onTick();
        self.graph.refresh();
      });
    }

    self.bundling(data);
  };

  _proto.divideEdges = function divideEdges(divisions) {
    var self = this;
    var edges = self.get('data').edges;
    var nodeIdMap = self.get('nodeIdMap');
    var edgePoints = self.get('edgePoints');
    if (!edgePoints || edgePoints === undefined) edgePoints = [];
    edges.forEach(function (edge, i) {
      if (!edgePoints[i] || edgePoints[i] === undefined) edgePoints[i] = [];
      var source = nodeIdMap.get(edge.source);
      var target = nodeIdMap.get(edge.target);

      if (divisions === 1) {
        edgePoints[i].push({
          x: source.x,
          y: source.y
        }); // source

        edgePoints[i].push({
          x: 0.5 * (source.x + target.x),
          y: 0.5 * (source.y + target.y)
        }); // mid

        edgePoints[i].push({
          x: target.x,
          y: target.y
        }); // target
      } else {
        var edgeLength = 0;

        if (!edgePoints[i] || edgePoints[i] === []) {
          // it is a straight line
          edgeLength = getEucliDis(source, target);
        } else edgeLength = self.getEdgeLength(edgePoints[i]);

        var divisionLength = edgeLength / (divisions + 1);
        var currentDivisonLength = divisionLength;
        var newEdgePoints = [{
          x: source.x,
          y: source.y
        }]; // source

        edgePoints[i].forEach(function (ep, j) {
          if (j === 0) return;
          var oriDivisionLength = getEucliDis(ep, edgePoints[i][j - 1]);

          while (oriDivisionLength > currentDivisonLength) {
            var ratio = currentDivisonLength / oriDivisionLength;
            var edgePoint = {
              x: edgePoints[i][j - 1].x,
              y: edgePoints[i][j - 1].y
            };
            edgePoint.x += ratio * (ep.x - edgePoints[i][j - 1].x);
            edgePoint.y += ratio * (ep.y - edgePoints[i][j - 1].y);
            newEdgePoints.push(edgePoint);
            oriDivisionLength -= currentDivisonLength;
            currentDivisonLength = divisionLength;
          }

          currentDivisonLength -= oriDivisionLength;
        });
        newEdgePoints.push({
          x: target.x,
          y: target.y
        }); // target

        edgePoints[i] = newEdgePoints;
      }
    });
    return edgePoints;
  };

  _proto.getEdgeLength = function getEdgeLength(points) {
    var length = 0;
    points.forEach(function (p, i) {
      if (i === 0) return;
      length += getEucliDis(p, points[i - 1]);
    });
    return length;
  };

  _proto.getEdgeBundles = function getEdgeBundles() {
    var self = this;
    var data = self.get('data');
    var edges = data.edges;
    var bundleThreshold = self.get('bundleThreshold');
    var nodeIdMap = self.get('nodeIdMap');
    var edgeBundles = self.get('edgeBundles');
    if (!edgeBundles) edgeBundles = [];
    edges.forEach(function (e, i) {
      if (!edgeBundles[i] || edgeBundles[i] === undefined) edgeBundles[i] = [];
    });
    edges.forEach(function (ei, i) {
      var iSource = nodeIdMap.get(ei.source);
      var iTarget = nodeIdMap.get(ei.target);
      edges.forEach(function (ej, j) {
        if (j <= i) return;
        var jSource = nodeIdMap.get(ej.source);
        var jTarget = nodeIdMap.get(ej.target);
        var score = self.getBundleScore({
          source: iSource,
          target: iTarget
        }, {
          source: jSource,
          target: jTarget
        });

        if (score >= bundleThreshold) {
          edgeBundles[i].push(j);
          edgeBundles[j].push(i);
        }
      });
    });
    return edgeBundles;
  };

  _proto.getBundleScore = function getBundleScore(ei, ej) {
    var self = this;
    ei.vx = ei.target.x - ei.source.x;
    ei.vy = ei.target.y - ei.source.y;
    ej.vx = ej.target.x - ej.source.x;
    ej.vy = ej.target.y - ej.source.y;
    ei.length = getEucliDis({
      x: ei.source.x,
      y: ei.source.y
    }, {
      x: ei.target.x,
      y: ei.target.y
    });
    ej.length = getEucliDis({
      x: ej.source.x,
      y: ej.source.y
    }, {
      x: ej.target.x,
      y: ej.target.y
    }); // angle score

    var aScore = self.getAngleScore(ei, ej); // scale score

    var sScore = self.getScaleScore(ei, ej); // position score

    var pScore = self.getPosisionScore(ei, ej); // visibility socre

    var vScore = self.getVisibilityScore(ei, ej);
    return aScore * sScore * pScore * vScore;
  };

  _proto.getAngleScore = function getAngleScore(ei, ej) {
    var dotProduct = getDotProduct({
      x: ei.vx,
      y: ei.vy
    }, {
      x: ej.vx,
      y: ej.vy
    });
    return dotProduct / (ei.length * ej.length);
  };

  _proto.getScaleScore = function getScaleScore(ei, ej) {
    var aLength = (ei.length + ej.length) / 2;
    var score = 2 / (aLength / Math.min(ei.length, ej.length) + Math.max(ei.length, ej.length) / aLength);
    return score;
  };

  _proto.getPosisionScore = function getPosisionScore(ei, ej) {
    var aLength = (ei.length + ej.length) / 2;
    var iMid = {
      x: (ei.source.x + ei.target.x) / 2,
      y: (ei.source.y + ei.target.y) / 2
    };
    var jMid = {
      x: (ej.source.x + ej.target.x) / 2,
      y: (ej.source.y + ej.target.y) / 2
    };
    var distance = getEucliDis(iMid, jMid);
    return aLength / (aLength + distance);
  };

  _proto.getVisibilityScore = function getVisibilityScore(ei, ej) {
    var self = this;
    var vij = self.getEdgeVisibility(ei, ej);
    var vji = self.getEdgeVisibility(ej, ei);
    return vij < vji ? vij : vji;
  };

  _proto.getEdgeVisibility = function getEdgeVisibility(ei, ej) {
    var ps = projectPointToEdge(ej.source, ei);
    var pt = projectPointToEdge(ej.target, ei);
    var pMid = {
      x: (ps.x + pt.x) / 2,
      y: (ps.y + pt.y) / 2
    };
    var iMid = {
      x: (ei.source.x + ei.target.x) / 2,
      y: (ei.source.y + ei.target.y) / 2
    };
    return Math.max(0, 1 - 2 * getEucliDis(pMid, iMid) / getEucliDis(ps, pt));
  };

  _proto.getEdgeForces = function getEdgeForces(e, eidx, divisions, lambda) {
    var self = this;
    var edgePoints = self.get('edgePoints');
    var K = self.get('K');
    var kp = K / (getEucliDis(e.source, e.target) * (divisions + 1));
    var edgePointForces = [{
      x: 0,
      y: 0
    }];

    for (var i = 1; i < divisions; i++) {
      var force = {
        x: 0,
        y: 0
      };
      var spring = self.getSpringForce({
        pre: edgePoints[eidx][i - 1],
        cur: edgePoints[eidx][i],
        next: edgePoints[eidx][i + 1]
      }, kp);
      var electrostatic = self.getElectrostaticForce(i, eidx);
      force.x = lambda * (spring.x + electrostatic.x);
      force.y = lambda * (spring.y + electrostatic.y);
      edgePointForces.push(force);
    }

    edgePointForces.push({
      x: 0,
      y: 0
    });
    return edgePointForces;
  };

  _proto.getSpringForce = function getSpringForce(divisions, kp) {
    var x = divisions.pre.x + divisions.next.x - 2 * divisions.cur.x;
    var y = divisions.pre.y + divisions.next.y - 2 * divisions.cur.y;
    x *= kp;
    y *= kp;
    return {
      x: x,
      y: y
    };
  };

  _proto.getElectrostaticForce = function getElectrostaticForce(pidx, eidx) {
    var self = this;
    var eps = self.get('eps');
    var edgeBundles = self.get('edgeBundles');
    var edgePoints = self.get('edgePoints');
    var edgeBundle = edgeBundles[eidx];
    var resForce = {
      x: 0,
      y: 0
    };
    edgeBundle.forEach(function (eb) {
      var force = {
        x: edgePoints[eb][pidx].x - edgePoints[eidx][pidx].x,
        y: edgePoints[eb][pidx].y - edgePoints[eidx][pidx].y
      };

      if (Math.abs(force.x) > eps || Math.abs(force.y) > eps) {
        var length = getEucliDis(edgePoints[eb][pidx], edgePoints[eidx][pidx]);
        var diff = 1 / length;
        resForce.x += force.x * diff;
        resForce.y += force.y * diff;
      }
    });
    return resForce;
  };

  _proto.isTicking = function isTicking() {
    return this.get('ticking');
  };

  _proto.getSimulation = function getSimulation() {
    return this.get('forceSimulation');
  };

  _proto.destroy = function destroy() {
    if (this.get('ticking')) {
      this.getSimulation().stop();
    }

    _Base.prototype.destroy.call(this);
  };

  return Bundling;
}(Base);

module.exports = Bundling;

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

/***/ }),

/***/ "./plugins/index.js":
/*!**************************!*\
  !*** ./plugins/index.js ***!
  \**************************/
/*! no static exports found */
/***/ (function(module, exports, __webpack_require__) {

var G6Plugins = {
  Minimap: __webpack_require__(/*! ./minimap */ "./plugins/minimap/index.js"),
  Grid: __webpack_require__(/*! ./grid */ "./plugins/grid/index.js"),
  Menu: __webpack_require__(/*! ./menu */ "./plugins/menu/index.js"),
  Bundling: __webpack_require__(/*! ./bundling */ "./plugins/bundling/index.js")
};
module.exports = G6Plugins;

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
//# sourceMappingURL=plugins.js.map