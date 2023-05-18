modulejs.moduleCacheManager = {};
modulejs.loadModules = [];
modulejs.cacheModules = {};
modulejs.undefinedDefined = [];
const define = function() {

	var current = document.currentScript;
	var currentSrc = current.getAttribute('src');
	currentSrc = currentSrc.replace(prex, '');

	if (modulejs.moduleCacheManager[currentSrc]) {
		return;
	}
	var prex = '',
		logger = undefined,
		prexScript = undefined;

	var scripts = document.getElementsByTagName('script');
	var initalScript = [];
	for (var index = 0; index < scripts.length; index++) {
		var oneScript = scripts[index];
		if (oneScript.getAttribute('src') && oneScript.getAttribute('src').indexOf('Define.js') == -1) {
			initalScript[initalScript.length] = scripts[index];
		}
	}

	try{
		prex = modulejs.options.pluginPath;
		logger = modulejs.options.logger;
	}catch(e){
	}

	var functionArray = [], jsFunction;

	/**
	 * 依賴插件函數
	 */
	var shimFunction = function() {
		jsFunction = arguments[1];

		var jsSimArray = arguments[0];

		if(Object.prototype.toString.call(jsSimArray).slice(8, -1) === 'String') {
			jsSimArray = [jsSimArray];
		}
		
		modulejs.commons.loadJses(jsSimArray, modulejs);
	}
	var _length = arguments.length;
	var type = typeof arguments[0] === 'function';
	//无依赖
	if (type) {
		Define.releaseFunction.apply(this, arguments);
	} else {
		//加载依赖
		shimFunction.apply(this, arguments);

		current.remove();

		if (arguments.length > 2) {
			arguments[0] = arguments[1];
			arguments[1] = arguments[2];
		} else {
			arguments[0] = arguments[1];
			arguments[1] = undefined;
		}
		try{
			Define.releaseFunction.apply(this, arguments);
		}catch(e){
			if(!modulejs.undefinedDefined) {
				modulejs.undefinedDefined = [];
			}
			modulejs.undefinedDefined[modulejs.undefinedDefined.length] = arguments;
			
		}
		var temp = document.open();
		temp.write('<script type="text/javascript" charset="utf-8" src="' + (currentSrc) + '"></script>');
		temp.close();
		modulejs.moduleCacheManager[currentSrc] = !0;

		if (!!jsFunction) {
			modulejs.loadModules[modulejs.loadModules.length] = jsFunction;
		}
		//console.groupEnd();
	}
}

window.onload = function() {
	if(!!modulejs.undefinedDefined) {
		for (var i = 0; i < modulejs.undefinedDefined.length; i++) {
			var item = modulejs.undefinedDefined[i];
			Define.releaseFunction.apply(this, item);
		};
	}
	delete modulejs.undefinedDefined;
};

var Define = {
	/**
	 * 一個函數參數
	 */
	releaseFunction: function() {
		var jsFunction = arguments[0];
		var jsModuleName = arguments[1];
		var evalFunction = undefined;
	
		if (typeof jsFunction === 'function') {
			evalFunction = jsFunction();
			var moduleName = (evalFunction && evalFunction.getFunctionName) ? evalFunction.getFunctionName() : '_';
	
			var obj = {};
			if (!!jsModuleName) {
				if (!modulejs[jsModuleName]) {
					modulejs[jsModuleName] = function() {};
					obj = Object.assign({}, evalFunction);
				} else {
					obj = Object.assign(modulejs[jsModuleName] || {}, evalFunction);
				}
				modulejs[jsModuleName] = obj;
			} else {
				if (!modulejs.U) {
					modulejs.U = function() {};
					obj = Object.assign({}, evalFunction);
				} else {
					obj = Object.assign(modulejs.U[moduleName] || {}, evalFunction);
				}
				modulejs.U[moduleName] = obj;
			}
		}
	},
	/**
	 * 基础配置文件
	 */
	config: function(conf) {
		if (!define) {
			define = {};
		}
		if (!define.env) {
			define.env = {};
		}
		if (conf) {
			var presuffix = conf['presuffix'] || '';
			var support = ['css', 'less', 'sass'];

			for (var index = 0; index < support.length; index++) {
				define.env[support[index]] = presuffix + (conf[support[index]] || "");
			}
		}
	},
	/**
	 * 设置工作间
	 */
	workSpace: function(space, presuffix) {
		presuffix = presuffix || '';

		var _current = presuffix + space;

		this.config({
			css: _current,
			less: _current,
			sass: _current
		});
	},
	/**
	 * 设置工作间
	 */
	currentWorkSpace: function(presuffix) {
		presuffix = presuffix || '';

		var _current = presuffix + __config.baseUrl + "/";

		this.config({
			css: _current,
			less: _current,
			sass: _current
		});
	},
	/**
	 * 预处理
	 */
	preprocessor: function() {
		if (!!modulejs.loadModules) {
			for (var index = 0; index < modulejs.loadModules.length; index++) {
				if (!cacheModules[index]) {
					modulejs.loadModules[index]();
					cacheModules[index] = !0;
				}
			}
		}
		return this;
	},
	/**
	 * 添加样式
	 * @param {Object} path
	 */
	addStyle: function(path) {
		var suff = '';
		if (define && define.env && define.env['css']) {
			suff = define.env['css'];
		}
		const $$head = document.getElementsByTagName('head')[0];
		var elementStyle = document.createElement('link');
		elementStyle.href = suff + path;
		elementStyle.rel = 'stylesheet';
		elementStyle.type = 'text/css';
		try {
			$$head.appendChild(elementStyle);
		} catch (e) {
			console.log(e);
		}
		return this;
	},
	/**
	 * 添加less样式
	 * @param {Object} path
	 */
	addLess: function(path) {
		var suff = '';
		if (define && define.env && define.env['less']) {
			suff = define.env['less'];
		}
		const $$head = document.getElementsByTagName('head')[0];
		var elementStyle = document.createElement('link');
		elementStyle.href = suff + path;
		elementStyle.rel = 'stylesheet/less';
		elementStyle.type = 'text/css';
		try {
			$$head.appendChild(elementStyle);
		} catch (e) {
			console.log(e);
		}
		return this;
	},
	/**
	 * 添加sass样式
	 * @param {Object} path
	 */
	addSass: function(path) {
		var suff = '';
		if (define && define.env && define.env['sass']) {
			suff = define.env['sass'];
		}
		const $$head = document.getElementsByTagName('head')[0];
		var elementStyle = document.createElement('link');
		elementStyle.href = suff + path;
		elementStyle.rel = 'stylesheet/sass';
		elementStyle.type = 'text/css';
		try {
			$$head.appendChild(elementStyle);
		} catch (e) {
			console.log(e);
		}
		return this;
	},
	/**
	 * 插件列表
	 */
	list: function() {
		var paths = __config.paths;
		var map = {};
		for (var ky in paths) {
			map['name'] = ky;
			map['desc'] = __config.trans[ky] || ky;
		}
		return map;
	}
}
