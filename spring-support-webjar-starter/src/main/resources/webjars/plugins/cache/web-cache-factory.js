define([], function() {
	"use strict"

	var DEFAULT_OPTION = {
		storage: 'session', //缓存类型
		log: 'info', //日志级别
		compress: !1, //是否压缩
		privacy: !1, //是否隐私
		cache: {
			msg: 0,
			pako: 0,
			privacy: 0
		}
	}


	var JCacheFactory = function(options) {
		this.options = Object.assign(true, DEFAULT_OPTION, options || {});
		this.loggerFactory = !!Logger ? new Logger("", {
			level: this.options.log
		}) : null;
		this.initial();
	}

	JCacheFactory.prototype = {
		constructor: JCacheFactory,
		/**
		 * 初始化
		 */
		initial: function() {
			if (window.openDatabase && this.options.storage == 'websql') {
				this.db = openDatabase("temp.db", "1.0", null, 20000);
				this.db.transaction(function(tr) {
					tr.executeSql('create table jCacheFactory(_id text, _value text)');
				});
			}
		},
		/**
		 * 加/解密
		 * @param {type} value 值
		 * @param {type} privacy :true加密
		 */
		privacyFactory: function(value, privacy) {
			if (this.options.cache.privacy == 0) {
				console.log('Security issues are not encrypted!');
				this.options.cache.privacy = 1;
			}
			return value;
		},
		/**
		 * 解/压缩
		 * @param {type} value 值
		 * @param {type} compress :true压缩
		 */
		compressFactory: function(value, compress) {
			if (this.options.compress) {
				if (window.pako) {
					return !!compress ? pako.gzip(value) : pako.ungzip(value);
				} else {
					if (this.options.cache.pako == 0) {
						this.options.cache.pako = 1;
						this.logger("warn", 'Please add plugin pako.js support!');
					}
					return value;
				}
			}
			return value;
		},
		/**
		 * 存储key-value
		 */
		setItem: function(key, value, expire) {
			if (!key) {
				this.logger("warn", "The key cannot be empty!");
				return;
			}
			if (value == undefined) {
				this.logger("warn", "The value cannot be undefined!");
				return;
			}
			var newValue = {
				cache: null == value ? "" : value
			};
			if (!!expire) {
				newValue['expire'] = expire;
				newValue['cacheTime'] = new Date().getTime();
			}
			this.getOrSet('set', key, newValue);
		},
		/**
		 * 存储key-value
		 */
		getItem: function(key) {
			if (!key) {
				this.logger("warn", "The key cannot be empty!");
				return;
			}
			var result = this.getOrSet('get', key);
			return undefined == result ? undefined : (result['cache'] ? result['cache'] : result);
		},
		/**
		 * 存储key-value
		 */
		removeItem: function(key) {
			if (!key) {
				this.logger("warn", "The key cannot be empty!");
				return;
			}
			this.getOrSet('remove', key);
		},
		/**
		 * 存储key-value
		 */
		removeAll: function() {
			this.getOrSet('removeAll', key);
		},
		/**
		 * 重新设置存储位置
		 */
		setStorage: function(storage) {
			this.options.storage = storage;
			console.log('reset successfully !');
		},
		/**
		 * 
		 */
		keys: function() {
			return this.getOrSet('keys', '1');
		},
		/**
		 * 获取或者存储
		 */
		getOrSet: function(getOrSetParam, key, value) {
			if (!key) {
				this.logger("warn", "The key cannot be empty!");
				return;
			}
			var result = undefined;
			var storage = this.options.storage;
			if ('session' == storage) {
				result = this.sessionStorageFactory(getOrSetParam, key, value);
			} else if ('local' == storage) {
				result = this.localStorageFactory(getOrSetParam, key, value);
			} else if ('websql' == storage) {
				result = this.webSqlFactory(getOrSetParam, key, value);
			}

			if ('get' == getOrSetParam && undefined != result) {
				var expire = result['expire'];
				if (!!expire) {
					var cacheTime = result['cacheTime'];
					if (new Date().getTime() - cacheTime > expire) {
						this.removeItem(key);
						return undefined;
					}
				}
			}

			return result;
		},
		/**
		 * session操作
		 */
		sessionStorageFactory: function(getOrSetParam, key, value) {
			if ('get' == getOrSetParam) {
				var getValue = sessionStorage.getItem(key);
				if (getValue == undefined) {
					this.logger('debug', key + " no cache!");
					return undefined;
				}
				try {
					return JSON.parse(this.privacyFactory(this.compressFactory(getValue, !1), !1));
				} catch (e) {
					this.logger("error", e);
					return undefined;
				}
			} else if ('set' == getOrSetParam) {
				try {
					sessionStorage.setItem(key, JSON.stringify(this.compressFactory(this.privacyFactory(value, !0), !0)));
				} catch (e) {
					this.logger("error", e);
					return !1;
				}
				return !0;
			} else if ('remove' == getOrSetParam) {
				sessionStorage.removeItem(key);
				return key;
			} else if ('removeAll' == getOrSetParam) {
				sessionStorage.clear();
				return !0;
			} else if ('keys' == getOrSetParam) {
				return sessionStorage.key();
			}
		},
		/**
		 * local操作
		 */
		localStorageFactory: function(getOrSetParam, key, value) {
			if ('get' == getOrSetParam) {
				var getValue = localStorage.getItem(key);
				if (getValue == undefined) {
					return undefined;
				}
				try {
					return JSON.parse(this.privacyFactory(this.compressFactory(getValue, !1), !1));
				} catch (e) {
					this.logger("error", e);
					return undefined;
				}
			} else if ('set' == getOrSetParam) {
				try {
					localStorage.setItem(key, JSON.stringify(this.compressFactory(this.privacyFactory(value, !0), !0)));
				} catch (e) {
					this.logger("error", e);
					return !1;
				}
				return !0;
			} else if ('remove' == getOrSetParam) {
				localStorage.removeItem(key);
				return key;
			} else if ('removeAll' == getOrSetParam) {
				localStorage.clear();
				return !0;
			} else if ('keys' == getOrSetParam) {
				return localStorage.key();
			}
		},
		/**
		 * @param {Object} getOrSetParam
		 * @param {Object} key
		 * @param {Object} value
		 */
		webSqlFactory: function(getOrSetParam, key, value) {
			if (this.db) {
				var $this = this;
				var result = undefined;
				if ('get' == getOrSetParam) {
					var promise = new Promise(function(resolve, reject) {
						$this.db.transaction(function(tr) {
							tr.executeSql('select * from jCacheFactory where _id = ?', [key], function(tr, result) {
								resolve(result.rows);
							});
						});
					});
				}

				promise.then(function(getValue) {
					if (getValue == undefined || getValue.length < 1) {
						return undefined;
					}
					try {
						result = JSON.parse($this.privacyFactory($this.compressFactory(getValue[0]['_value'], !1), !1));
					} catch (e) {
						$this.logger("error", e);
						result = undefined;
					}
				});
			} else if ('set' == getOrSetParam) {
				value = JSON.stringify(value);
				try {
					$this.db.transaction(function(tr) {
						tr.executeSql('insert into jCacheFactory values(?, ?)', [key, value]);
					});
				} catch (e) {
					$this.logger("error", e);
					return !1;
				}
				return !0;
			} else if ('remove' == getOrSetParam) {
				try {
					$this.db.transaction(function(tr) {
						tr.executeSql('delete from jCacheFactory where _id = ?', [key]);
					});
				} catch (e) {
					$this.logger("error", e);
					return !1;
				}
				return key;
			} else if ('removeAll' == getOrSetParam) {
				try {
					$this.db.transaction(function(tr) {
						tr.executeSql('delete from jCacheFactory', []);
					});
				} catch (e) {
					$this.logger("error", e);
					return !1;
				}
				return !0;
			} else if ('keys' == getOrSetParam) {
				debugger
				var promise = function() {
					return new Promise(function(resolve, reject) {
						$this.db.transaction(function(tr) {
							tr.executeSql('select * from jCacheFactory', [], function(tr, result) {
								resolve(result.rows);
							});
						});
					});
				}

				promise.then(function(getValue) {
					if (getValue == undefined) {
						return undefined;
					}
					try {
						return JSON.parse($this.privacyFactory($this.compressFactory(getValue, !1), !1));
					} catch (e) {
						$this.logger("error", e);
						return undefined;
					}
				});
			}
		},
		/**
		 * 输出日志
		 * @param {Object} info 信息
		 */
		logger: function(level, info) {
			if (!!this.loggerFactory) {
				if ('error' == level) {
					this.loggerFactory.error(info);
				} else if ('info' == level) {
					this.loggerFactory.info(info);
				} else if ('debug' == level) {
					this.loggerFactory.debug(info);
				} else if ('trace' == level) {
					this.loggerFactory.trace(info);
				} else if ('warn' == level) {
					this.loggerFactory.warn(info);
				}
				return;
			} else {
				if (this.options.cache.msg == 0) {
					console.log('Please add plugin Log4j.js support!');
					this.options.cache.msg = 1;
				}
				return;
			}
		}
	}

	window.JCacheFactory = JCacheFactory;
});
