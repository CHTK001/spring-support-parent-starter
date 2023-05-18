/**
 * 模块化
 */
const modulejs = {
	/**
	 * 配置
	 */
	options: {
		loggerJs: 'log4j/log4j.js',
		selector: '',//选择器
		async: !1,//是否异步
		source: '',//core.js源
		corePath: '',//core.js路径
		plugins: '',//插件
		firstHeader: '',//<head>
		pluginPath: '',//插件路径
		logger: undefined,//日志
		debug: !1,//调试模式
		tools: undefined,//工具
		css: "",//css路径
		time: undefined//时间
	},
	extension: {},
	/**
	 * 插件列表
	 */
	list: {},
	/**
	 * 缓存
	 */
	cache: {
		js: [],
		css: [],
		loadedJS: [],
		writeJsCache: {},
		writeCssCache: {}
	},
	/**
	 * 初始化
	 */
	initialize: function() {
		this.analysisCoreJs();
		this.analysisCoreConfig();
		this.analysisOptions();
	},
	/**
	 * 基础配置
	 */
	config: function() {
		return this;
	},
	/**
	 * 临时添加插件
	 * @param name
	 */
	install: function(name) {
		if(!name) {
			return undefined;
		}
		name = String(name);
		if(name.endsWith(".css")) {
			return this.utils.writeCss(name, this, !0);
		} else {
			return this.utils.writeJs(name, undefined, !1, this, !1);
		}
	},
	/**
	 * 卸载临时插件
	 * @param name
	 */
	uninstall: function(id) {
		let querySelector = document.querySelector("#" + id);
		querySelector.remove();
	},
	/**
	 * 查询插件
	 * @param name
	 */
	query: function(name) {
		let map = this.__config['trans'];
		let paths = this.__config['paths'];
		if(!map) {
			console.table(map);
		}
		let result = [];
		for(let plugin in map) {
			let reg = plugin.match(name);
			if(!reg) {
				let nameTrans = map[plugin];
				if(!nameTrans) {
					continue;
				}
				reg = nameTrans.match(name);
				if(!reg) {
					continue;
				}
			}
			let value = reg[0];
			if(!value) {
				continue;
			}
			let pluginInfo = map[plugin];
			result.push({
				plugin: plugin,
				name: pluginInfo,
				path: paths[plugin] + '.js'
			});
		}
		console.table(result);
	},
	/**
	 * 
	 */
	use: function(plugin, fn) {
		let _this = this;
		if(_this.status !== 'complete') {
			_this.plugins(undefined, undefined);
		}
		if(Object.prototype.toString.call(plugin).slice(8, -1) === 'String') {
			plugin = [plugin];
		}
		if (_this.options.async === 'seajs') {
			if (!fn) {
				let result = [];
				for(let index = 0, item; item = plugin[index ++]; ){
					try{
						result.push(seajs.use(plugin));
					} catch(e) {
						result.push(undefined);
					}
				}
				return !!result && result.length == 1 ? result[0] : result;
			} else {
				try {
					seajs.use(plugin, fn);
				} catch(e) {}
			}
		} else if (_this.options.async === 'requirejs') {
			if (!fn) {
				let result = [];
				for(let index = 0, item; item = plugin[index ++]; ){
					let fn = function() {};
					try{
						requirejs(plugin, fn);
						result.push(fn);
					} catch(e) {
						result.push(undefined);
					}
				}
				return !!result && result.length == 1 ? result[0] : result;
			} else {
				try {
					requirejs(plugin, fn);
				} catch(e) {}
			}
		} else {
			this.options.pluginsList = plugin;
			this.render();
		}
		
	},
	/**
	 * 解析核心js(core.js)
	 */
	analysisCoreJs: function() {
		var script = document.getElementsByTagName('script');
		var _core = undefined;
		for (var i = 0; i < script.length; i++) {
			if (script[i].getAttribute("data-core") != null) {
				_core = script[i];
				break;
			}
		}
		if (!!_core) {
			//css!
			this.options.css = "";
			//selector
			this.options.selector = _core;
			//是否异步
			this.options.async = _core.getAttribute('data-async');
			//head
			this.options.firstHeader = document.getElementsByTagName('head')[0];
			//基础配置
			this.options.innerText = _core.innerText;
			//源文件
			this.options.source = _core.src;
			//插件前缀
			this.options.pluginPath = _core.getAttribute('data-plugin-path');
			//配置文件目录
			this.options.mainjs = (_core.src ? _core.src.replace('/core.js', '') : '') + "/main.js";
			//是否调试模式(需要main支持)
			this.options.debug = !!_core.getAttribute('data-debug');
			//是否加载工具
			this.options.tools = !!_core.getAttribute('data-tools');
			//是否文件携带日期
			this.options.time = !!_core.getAttribute('data-time');
			//加载日志
			this.options.logger = _core.getAttribute('data-logger');
			//插件列表
			this.options.pluginsList = _core.getAttribute('data-plugins') || [];

			if (!this.options.plugins) {
				let src = _core.outerHTML;
				let index = src.indexOf('src=');
				src = src.substring(index + 5);
				index = src.indexOf("\"");
				if (index > -1) {
					src = src.substring(0, index);
				}
				this.options.pluginPath = src.replace('core.js', 'plugins/');
				//插件前缀
				this.options.corePath = src.replace('core.js', '')
			}

			if(!!this.options.innerText) {
				this.options.extension = eval(this.options.innerText);
			}

			if (!!this.options.async) {
				this.options.css = "../plugins/";
			}
		}

	},
	/**
	 * 渲染插件配置
	 */
	analysisCoreConfig: function() {
		this.utils.writeJs(this.options.corePath + "main.js", undefined, false, this);
	},
	/**
	 * 基础文件渲染
	 */
	analysisOptions: function() {
		if (!!this.options.logger) {
			this.utils.writeJs(this.options.loggerJs, undefined, true, this, !0);
			try {
				this.log = new Logger("core.js");
			} catch (e) {}

		}
		this.renderModuleJs();
	},
	/**
	 * 渲染插件
	 */
	renderModuleJs: function() {
		if (!this.options.pluginsList) {
			return;
		}
		this.render();
	},
	/**
	 * @param {Object} array 插件列表
	 * @param {Object} fn 回调
	 */
	plugins: function(array, fn) {
		if (!!array) {
			this.options.pluginsList = this.options.pluginsList.concat(array);
		}
		if (!!fn) {
			this.options.pluginsListCallback = fn;
		}
		
		this.renderPlugin();
	},

	/**
	 * 渲染
	 */
	render: function() {
		//异步方式(requirejs, seajs)
		if (!!this.options.async) {
			try {
				this[this.options.async].loader(this);
			} catch (e) {
				if (this.options.logger) {
					console.groupCollapsed('AMD加载失败')
					console.log('采用非AMD标准加载....');
					console.log('处理需要依赖utils.define模块或者插件Define.js');
					console.groupEnd();
				}
			}
			return;
		} else {
			this.commons.loader(this);
		}
	},
	/**
	 * 渲染插件
	 */
	renderPlugin: function() {
		if (!!this.options.async) {
			try {
				this[this.options.async].plugin(this);
			} catch (e) {
				if (this.options.logger) {
					console.groupCollapsed('AMD加载失败')
					console.log('采用非AMD标准加载....');
					console.log('处理需要依赖utils.define模块或者插件Define.js');
					console.groupEnd();
				}
		
			}
			return;
		} else {
			this.commons.plugin(this);
		}
	},
	/**
	 * 监听当main.js渲染完成
	 */
	completeConfig: function() {
		let plugins = this.__config['plugins'];
		if (!plugins) {
			return;
		}
		this.list = plugins;
		let paths = {},
			alias = {},
			shim = {},
			trans = {},
			ver = {};

		if(!!this.options.extension) {
			this.list = Object.assign(true, this.options.extension, this.list);
		}

		for (let key in this.list) {
			let item = this.list[key];

			let path, name, deps, vers;

			if (Object.prototype.toString.call(item).slice(8, -1) === 'Object') {
				path = item['path']; //插件路径
				name = item['name']; //插件名称
				deps = item['deps']; //插件依赖
				vers = item['ver']; //插件版本
			} else {
				path = item;
				name = key;
			}

			if (!path) {
				continue;
			}

			if (!!path && path.indexOf('css!') > -1) {
				path = path.replace('css!', 'css!' + this.options.css);
			}

			paths[key] = path;
			alias[key] = path.indexOf('css!') > -1 ? path : path + ".js";

			trans[key] = name || (key + '插件');
			if (!!vers) {
				for (let key1 in vers) {
					let item1 = vers[key1];
					paths[key + "-" + key1] = item1;
				}
			}
			ver[name] = vers;

			if (!deps) {
				continue;
			}
			let newDeps = [];
			for (let index = 0, item; item = deps[index++];) {
				if (!!item) {
					if(item.indexOf('css!') > -1) {
						item = item.replace('css!', 'css!' + this.options.css);
					} else if(item.indexOf('js!') > -1 && !this.list[item]){
						paths[item] =  item.replace('js!', '');
					}
				}
				newDeps.push(item);
			}

			shim[key] = {
				deps: newDeps,
				exports: key
			}

		}
		delete this.__config['plugins'];
		this.__config['baseUrl'] = this.options.pluginPath;
		this.__config['base'] = this.options.pluginPath;
		this.__config['paths'] = paths;
		this.__config['alias'] = alias;
		this.__config['shim'] = shim;
		this.__config['trans'] = trans;
		this.__config['ver'] = ver;
		if (!this.options.time) {
			delete this.__config['urlArgs'];
		}
	},
	/**
	 * 日志
	 */
	logger: function(msg) {
		let logger = this.log[this.options.logger];
		if (!!logger) {
			logger(msg);
		}
	},
	log: {
		DEFAULT: {
			level: 'INFO',
			trace: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
			debug: ['DEBUG', 'INFO', 'WARN', 'ERROR'],
			info: ['INFO', 'WARN', 'ERROR'],
			warn: ['WARN', 'ERROR'],
			error: ['ERROR'],
			module: '',
			config: {
				info: 'font-weight: bold; font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 10px; color: #444; padding: 8px 0; line-height: 10px;color: blue;text-align:center;',
				debug: 'color: black',
				trace: 'color:grey',
				error: 'font-weight: bold; font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 10px; padding: 8px 0; line-height: 10px;color: blue;text-align:center; color:red',
				warn: 'font-weight: bold; font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 10px; padding: 8px 0; line-height: 10px;color: blue;text-align:center;color: yellow'
			}
		},
		/**
		 * clear
		 */
		clear: function() {
			console.clear();
		},
		/**
		 * memory
		 */
		mem: function() {
			console.memory;
		},
		/**
		 * profile
		 */
		profile: function(title) {
			console.profile(title);
		},
		/**
		 * profileEnd
		 */
		profileEnd: function(title) {
			console.profileEnd(title);
		},

		/**
		 * time
		 */
		test: function(max) {
			for (var index = 0; index < max; index++) {

			}
		},
		/**
		 * time
		 */
		time: function(title) {
			console.time(title);
		},
		/**
		 * timeEnd
		 */
		timeEnd: function(title) {
			console.timeEnd(title);
		},
		/**
		 * group
		 */
		group: function(title) {
			console.groupCollepased(title);
		},
		/**
		 * groupEnd
		 */
		groupEnd: function() {
			console.groupEnd();
		},
		/**
		 * table
		 */
		table: function(params) {
			console.table(params);
		},
		/**
		 * info
		 */
		set: function(level) {
			this.options.level = level;
		},
		/**
		 * info
		 */
		info: function(msg) {
			this.log('INFO', this.adaptor(arguments));
		},
		/**
		 * debug
		 */
		debug: function(msg) {
			this.log('DEBUG', this.adaptor(arguments));
		},
		/**
		 * trace
		 */
		trace: function(msg) {
			console.trace(msg);
		},
		/**
		 * error
		 */
		error: function(msg) {
			this.log('ERROR', this.adaptor(arguments));
		},
		/**
		 * warn
		 */
		warn: function(msg) {
			this.log('WARN', this.adaptor(arguments));
		},
		/**
		 * 返回打印数据
		 */
		adaptor: function(_arguments) {
			if (_arguments.length == 0) {
				return "";
			} else if (_arguments.length == 1) {
				return _arguments[0];
			}
			return this.placeholder(_arguments);
		},
		/**
		 * 占位符
		 */
		placeholder: function(_arguments) {
			var level = this.options.level.toLocaleLowerCase();

			var msg = _arguments[0],
				cindex = 0;
			var valueLength = _arguments.length - 1;

			var array = msg.match(new RegExp(/{}/, 'g'));
			if (array) {
				for (var index = 0; index < array.length; index++) {
					msg = msg.replace('{}', valueLength < (index + 1) ? "" : _arguments[index + 1]);
				}
				return msg;
			}
			return _arguments;

		},
		/**
		 * 
		 */
		log: function(levelName, msg) {
			var level = this.options.level.toLocaleLowerCase();

			var currentLog = this.options[level];
			var module = this.options['module'];
			var _config = this.options.config[levelName.toLocaleLowerCase()];
			var clz = '';
			/* var obj = {};
			Error.captureStackTrace(obj, this);
			var line = function() {
				var stack = obj.stack || ""
				var matchResult = stack.match(/\(.*?\)/g) || []
				return matchResult[1] || ""
			}
			
			var lineStr = line(); */

			if (currentLog.indexOf(levelName) > -1) {
				var type = Object.prototype.toString.call(msg).slice(8, -1);
				if (!!module) {
					clz = module + ':';
				}
				if (type === 'Arguments') {
					msg[0] = clz + msg[0];
					console.log.apply(console, msg);
				} else if (type === 'Object') {
					console.log(clz + "　", msg);
				} else {
					console.log(clz + " %c" + msg, _config);
				}
			}
		}
	},
	//工具
	utils: {
		/**
		 *
		 * @param path 路径
		 * @param isNeedPreffix 是否添加过前缀
		 */
		getPath: function(path, isNeedPreffix, _this) {
			let newPath = path;
			if (!!_this.options.pluginPath && isNeedPreffix) {
				newPath = _this.options.pluginPath + path;
			}
			let oldPath = newPath.replace(_this.options.pluginPath, "");
			if(oldPath.startsWith("http")) {
				if(oldPath.indexOf("://") > -1) {
					newPath = oldPath;
				} else {
					newPath = oldPath.replace(":/", "://");
				}
			}
			return newPath;
		},
		/**
		 *
		 * @param path 路径
		 * @param needName
		 * @param isNeedPreffix 是否添加过前缀
		 * @param _this
		 * @param async
		 * @returns {*}
		 */
		writeJs: function(path, needName, isNeedPreffix, _this, async, id) {
			if(_this.cache.writeJsCache[path]) {
				return _this.cache.writeJsCache[path];
			}
			var newPath = this.getPath(path, isNeedPreffix, _this);

			let uuid = id || this.uuid();
			if (_this.cache.js.indexOf(newPath) == -1) {
				_this.cache.js.push(newPath);
				if (!!needName) {
					if (!!_this.options) {
						var elementScript = document.createElement("script");
						elementScript.src = newPath;
						elementScript.type = 'text/javascript';
						elementScript.charset = "utf-8"
						elementScript.async = true;
						elementScript.id = uuid;

						_this.options.selector.parentElement.insertBefore(elementScript, _this.options.selector.nextElementSibling);
						return elementScript;
					}
					return _this;
				}
				try {
					if (newPath.indexOf('undefined.js') == -1) {
						if (async) {
							document.write('<script type="text/javascript" id="'+ uuid +'" charset="utf-8" async src="' + newPath + '"></script>');
							return;
						}
						document.write('<script type="text/javascript" id="'+ uuid +'" charset="utf-8" src="' + newPath + '"></script>');

					}
				} catch (e) {
					if (_this.options.logger) {
						console.info("加载插件异常: %O, 地址: " + newPath, e);
					}
				}
			}
			_this.cache.writeJsCache[path] = uuid;
			return uuid;
		},
		/**
		 * 判断是否已存在
		 */
		hasJs: function(path) {
			var script = document.getElementsByTagName('script');
			var _core = undefined;
			for (var i = 0; i < script.length; i++) {
				if (this.jsName(script[i].getAttribute("src")) == this.jsName(path)) {
					return true;
				}
			}
			return false
		},
		/**
		 * js名字
		 * @param {Object} path
		 */
		jsName: function(path) {
			if (!!path) {
				var index = path.lastIndexOf("/");
				if (index > -1) {
					var js = path.substring(index + 1);
					return js;
				} else {
					return path;
				}
			} else {
				return path;
			}
		},
		writeCsses: function(arr, _this) {
			var styles = _this.__config.styles || {};
			var baseUrl = _this.__config.baseUrl;
			var trans = _this.__config.trans;
			var paths = _this.__config.paths || {};

			var style = undefined,
				link = undefined;

			var needTrans = false;

			for (var index = 0; index < arr.length; index++) {
				var oneCss = arr[index];
				if (oneCss.indexOf("css!") > -1) {
					link = style.replace('css!', '').replace('../styles/', '');
				} else {
					link = paths[oneCss];
					if (link.indexOf("css!") == -1) {
						continue;
					}
					link = link.replace('css!', '');
				}
				if (!link) {
					continue;
				}
				if (_this.cache.css.indexOf(link) == -1) {
					_this.cache.css.push(link);
					if (_this.options.logger) {
						console.groupCollapsed('%c加载自定义样式: ' + (trans[oneCss] || oneCss),
							'font-weight: bold; font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 10px; color: #444; padding: 8px 0; line-height: 10px;color: #F00;text-align:center;'
						);
					}
					try {
						_this.utils.writeCss(link, _this);
						if (_this.options.logger) {
							console.log('%c加载样式: ' + link, 'font-weight: bold; color: #F0F');
						}
					} catch (e) {}
					if (_this.options.logger) {
						console.groupEnd();
					}
				}
			}
		},
		/**
		 * 
		 * @param {Object} path
		 */
		writeCss: function(path, _this, notNeedPrefix, id) {
			if(_this.cache.writeCssCache[path]) {
				return _this.cache.writeCssCache[path];
			}
			const $$head = _this.options.firstHeader;
			let uuid = id || this.uuid();
			var elementStyle = document.createElement('link');
			if (!!_this.options.pluginPath && !notNeedPrefix) {
				elementStyle.href = _this.options.pluginPath + path;
			} else {
				elementStyle.href = path;
			}
			elementStyle.id = uuid;
			elementStyle.rel = 'stylesheet';
			try {
				$$head.appendChild(elementStyle);
			} catch (e) {
				if (_this.options.logger) {
					console.info("加载样式异常: %O, 地址: " + elementStyle.href, e);
				}
			}

			_this.cache.writeCssCache[path] = uuid;
			return uuid;
		},
		/**
		 * uuid
		 */
		uuid: function() {
			let chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');
			let uuid = [], i, len = 12;
			let radix = chars.length;

			if (len) {
				for (i = 0; i < len; i++) {
					uuid[i] = chars[0 | Math.random() * radix];
				}
			} else {
				let r;
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
		}
	},
	/**
	 * requirejs
	 */
	requirejs: {
		loader: function(_this) {
			this.writeRequire(_this);
		},
		/**
		 * @param {Object} _this
		 */
		plugin: function(_this) {
			_this.status = 'complete';
			try {
				requirejs.config(_this.__config);
				if (!!_this.options.pluginsList && !!_this.options.pluginsListCallback) {
					requirejs(_this.options.pluginsList, _this.options.pluginsListCallback);
				}
			} catch (e) {}
		},
		/**
		 * @param {Object} _this
		 */
		writeRequire: function(_this) {
			_this.utils.writeJs(_this.options.pluginPath + 'core/require.min.js', undefined, false, _this, 'main');
		},
	},
	/**
	 * seajs
	 */
	seajs: {
		loader: function(_this) {
			this.writeSeajs(_this);
		},
		/**
		 * @param {Object} _this
		 */
		plugin: function(_this) {
			_this.status = 'complete';
			try {
				seajs.config(_this.__config);
				if (!!_this.options.pluginsList && !!_this.options.pluginsListCallback) {
					seajs.use(_this.options.pluginsList, _this.options.pluginsListCallback);
			
				}
			} catch (e) {}
		},
		/**
		 * @param {Object} _this
		 */
		writeSeajs: function(_this) {
			_this.utils.writeJs(_this.options.pluginPath +'core/Seajs-v3.0.3.js', undefined, false, _this, 'seajs');
		},
	},
	/**
	 * 
	 */
	commons: {
		loader: function(_this) {
			if(!_this.__config) {
				return;
			}
			this.loadDefine(_this);
			this.loadJs(_this);
			this.loadCss(_this);
			_this.status = 'complete';
		},
		plugin: function(_this) {
			this.loader(_this);
		},
		/**
		 * @param {Object} _this
		 */
		loadDefine: function(_this) {
			let paths = _this.__config['paths'];
			_this.utils.writeJs(paths['utils.define'] + '.js', undefined, !0, _this, '', 'define');
		},
		/**
		 * @param {Object} _this
		 */
		loadJs: function(_this) {
			//this.core('utils.define');
			if (_this.options.logger) {
				console.groupEnd();
			}

			var map = _this.__config.map;
			let arrays = _this.options.pluginsList;

			let newArrays = [];

			var _allCss = map["*"];
			if (!!_allCss) {
				for (let item in _allCss) {
					newArrays.push(item);
				}
			}

			if (!!arrays) {
				newArrays = newArrays.concat(arrays);

				this.loadJses(newArrays, _this);
				if (_this.options.logger) {
					console.groupEnd();
				}
			}
		},
		/**
		 * 加载所有js
		 * @param {Object} arrays
		 * @param {Object} _this
		 */
		loadJses: function(arrays, _this) {
			var baseUrl = _this.__config.baseUrl;
			var path = _this.__config.paths || {};
			var shim = _this.__config.shim;
			var trans = _this.__config.trans;

			for (var index = 0; index < arrays.length; index++) {
				var onePlugin = arrays[index];
				var _path = path[onePlugin];
				if (!_path || _path.indexOf('css!') > -1) {
					continue;
				}

				if (_this.options.logger) {
					console.groupCollapsed('%c加载' + ((trans[onePlugin] || onePlugin || "")) + '组件',
						'font-weight: bold; font-family: "Helvetica Neue", Helvetica, Arial, sans-serif; font-size: 10px; color: #444; padding: 8px 0; line-height: 10px;color: #F00;text-align:center;'
					);
				}
				var deps = shim[onePlugin];
				if (!!deps) {
					this.shim(deps, _this);
				}

				if (!!_path) {
					var _finalPath = baseUrl + _path;
					_finalPath = _finalPath.indexOf(".js") > -1 ? _finalPath : _finalPath + ".js";
					_finalPath = _finalPath.replace("//", "/");
					if (_this.cache.loadedJS[_finalPath] == undefined) {
						if (_this.options.logger) {
							console.log('%c加载依赖: ' + (trans[_path] || _finalPath || "").replace(_this.options.pluginPath, ''),
								'font-weight: bold; color: #F0F');
						}
						_this.utils.writeJs(_finalPath, undefined, false, _this, '', onePlugin);
						_this.cache.loadedJS[_finalPath] = !0;
					} else {
						if (_this.options.logger) {
							console.log('%c加载缓存' + _finalPath, 'color: gray;');
						}
					}
				}
				if (_this.options.logger) {
					console.groupEnd();
				}
			}
		},

		/**
		 * 处理依赖
		 */
		shim: function(deps, _this) {
			var baseUrl = _this.__config.baseUrl;
			var path = _this.__config.paths || {};
			var shim = _this.__config.shim;
			var trans = _this.__config.trans;
			var map = _this.__config.map;
			for (var indej = 0; indej < deps.deps.length; indej++) {
				var one = deps.deps[indej];
				var _deps = shim[one];
				if (!!_deps) {
					this.shim(_deps, _this);
				}
				var _path = path[one] || "";
				if (one.indexOf('css!') > -1) {
					_this.utils.writeCss(one.replace('css!', '').replace('../plugins/', ''), _this, '', one +'_css');
					if (!!_path) {
						console.log('%c加载样式: ' + (trans[_path] || _path), 'font-weight: bold; color: #F0F');
					}
					continue;
				}
				let _finalPath = '';
				if(!_path.startsWith("http")) {
					_finalPath = baseUrl + _path;
					_finalPath = _finalPath.indexOf(".js") > -1 ? _finalPath : _finalPath + ".js";
					_finalPath = _finalPath.replace("//", "/");
				} else {
					_finalPath = _path;
				}


				if (!_this.utils.hasJs(_finalPath)) {
					if (_this.cache.loadedJS[_finalPath] == undefined) {
						if (_this.options.logger) {
							console.log('%c加载依赖: ' + (trans[_path] || _finalPath || "").replace(_this.options.pluginPath, ''),
								'font-weight: bold; color: #F0F');
						}
						_this.utils.writeJs(_finalPath, undefined, false, _this, '', one + "_js");
						_this.cache.loadedJS[_finalPath] = !0;
					} else {
						if (_this.options.logger) {
							console.log('%c从缓存中加载依赖: ' + (_finalPath || "").replace(_this.options.pluginPath, ''), 'color: gray;');
						}
					}
				} else {
					if (_this.options.logger) {
						console.log('%c从缓存中加载依赖: ' + (_finalPath || "").replace(_this.options.pluginPath, ''), 'color: gray;');
					}
				}
			}
		},

		/**
		 * @param {Object} _this
		 */
		loadCss: function(_this) {
			var map = _this.__config.map;
			var paths = _this.__config.paths;
			var styles = _this.__config.styles || {};

			let css = [];

			var _allCss = map["*"];
			if (!_allCss) {
				return;
			}
			for (let item in _allCss) {
				css.push(item);
			}

			if (css.length == 0) {
				return;
			}
			_allCss = css;
			var styleArrays = [];
			Object.values(_allCss).forEach(function(item) {
				styleArrays[styleArrays.length] = item;
			});
			_this.utils.writeCsses(styleArrays, _this);
		}
	}
}

modulejs.initialize();
