/**
 * 分页插件
 */
;(function(fn, $) {
	fn($);
})(function($) {
	$ = $ || window.$;
	
	/**
	 * 分頁插件
	 * @param {Object} options
	 */
	$.fn.paginate = function() {
		/**
		 * 默认配置
		 */
		var DEFAULT_OPTIONS = {
			/**
			 * 页面显示配置
			 */
			config: {
				/**
				 * 默认每页条数
				 */
				size: 20,
				/**
				 * 类型
				 */
				type: 0,
				/**
				 * 当前页
				 */
				offset: 1,
				/**
				 * 最大显示页数
				 */
				max: 5
			},
			/**
			 * 预回调加载
			 */
			preCallback: function(offset) {
				console.log(offset);
			},
			/**
			 * 点击回调
			 * @param {Object} offset
			 */
			callback: function(offset) {
				console.log(offset);
			}
			
		}
		/**
	 	* 分页对象
	 	*/
		var PaginateFactory = {
			/**
			 * 初始化
			 * @param {Object} options
			 */
			inital: function(options) {
				options = options || {};
				
				PaginateFactory.opts = $.extend(true, DEFAULT_OPTIONS, options);
				PaginateFactory.opts.cache = {};
				PaginateFactory.type = options.type || DEFAULT_OPTIONS.config.type;
				PaginateFactory._this = $(this);
				PaginateFactory.selector = $(this);
				
				
				PaginateFactory.initalPage();
				
				return PaginateFactory;
				
			},
			/**
			 * 初始化页面组件
			 */
			initalPage: function() {
				/**
				 * 默认加载bootstrap-paginator
				 */
				if(0 === this.type || 1 === this.type) {
					var length_ = this._this.find('ul').length;
					if(length_ === 0) {
						this._this.append('<ul class="pagination"></ul>');
						this.selector = this._this;
						this._this = this._this.find('ul');
					}
				} 
				if(0 === this.type) {
					this.bootstrap(this.opts.config.offset, this.opts.config.total, this.opts.callback);
				} else if(1 === this.type) {
					this.custom(this.opts.config.offset, this.opts.config.total, this.opts.config.max, this.opts.callback);
				} else {
				
				}
			},
			/**
			 * 
			 */
			render: function(offset, total, callback) {
				if(offset > 0) {
					this.opts.config.offset = offset;
				}
				if(total > 0) {
					this.opts.config.total = total;
				}
				if(typeof callback === 'function') {
					this.opts.callback = callback;
				}
				if(this.opts.config.type == 0) {
					this.bootstrap(this.opts.config.offset, this.opts.config.total, this.opts.callback);
				} else {
					this.custom(this.opts.config.offset, this.opts.config.total, this.opts.config.max, this.opts.callback);
				}
			},
			/**
			 * 自定义
			 * @param {Object} offset
			 * @param {Object} total
			 * @param {Object} callback
			 */
			custom: function(newPage, totalPage, max, callback) {
				
				var $$pagination = this._this;
				var _globalThis = this;
				var $$option = this.selector.find('#toSelect');
				var $$toPage = this.selector.find('.toPage > select');
				
				var _prev = '<li class="page-button" id="page-prev"><a href="javascript:void(0)" aria-label="Previous"><span aria-hidden="true"><</span></a></li>';
				var _next = '<li class="page-button" id="page-next"><a href="javascript:void(0)" aria-label="Next"><span aria-hidden="true">></span></a></li>';
				var _page = function(page) {
					return '<li class="page-num" data-value="'+ page +'" id="page-'+ page +'"> <a href="javascript:void(0)" >'+ page +'</a> </li>';
				}
				
				if($$option.length > 0) {
					for (var int = 1; int < (totalPage + 1); int ++) {
						$$toPage.append('<option value="'+ int +'">'+ int +'</option>');
					}
					$$option.off('change');
					var maxLimit = totalPage - max + 1;
					$$option.on('change', function() {
						var _vl = + this.value;
						if(_vl <= maxLimit) {
							mkpagination(_vl);
						} else {
							mkpagination(maxLimit, _vl);
						}
						
						_globalThis.opts.callback(+_vl, 'select');
					});
				}
				
				var mkpagination = function(where, active) {
					$$pagination.find(' li').remove();
					if(totalPage <= max) {
						for (var i = 0; i < totalPage; i ++) {
							$$pagination.append(_page(i + 1));
						}
					} else {
						var minPage = where || newPage;
						var showPage = max;
						
						for (var i = minPage; i < (minPage + max); i ++) {
							$$pagination.append(_page(i));
						}
						if(minPage > 1) {
							$$pagination.prepend(_prev);
						} 
						if((showPage + minPage) <= totalPage){
							$$pagination.append(_next);
						}
					}
					
					var _active = active || minPage;
					
					$$pagination.find('#page-' + _active).addClass('active');
					
					listener_li();
					listener_next();
					listener_prev();
					
				} 
				
				var listener_active = function(page) {
					$$pagination.find('#page-' + page).addClass('active');
				}
				
				var listener_li = function() {
					var $$li = $$pagination.find('li').filter('.page-num');
					if($$li.length > 0) {
						$$li.off('click');
						$$li.on('click', function() {
							var _this = $(this);
							if(!_this.hasClass('active')) {
								$$li.removeClass('active');
								_this.addClass('active');
								var value = _this.attr('data-value') || 0;
								window.scrollTo(window.screenLeft, window.screen.availHeight);
								_globalThis.opts.callback(+value);
							}
						});
					}
				}
				
				var listener_prev = function() {
					var $$prev = $$pagination.find('#page-prev');
					if($$prev.length > 0) {
						$$prev.off('click');
						$$prev.on('click', function() {
							//下一页
							var $$next = $$pagination.find('#page-next');
							//获取最新li集合
							var $$li = $$pagination.find('li').filter('.page-num');
							//点击对象
							var _this = $(this);
							//选取选中项
							var $$active = $$li.filter('.active');
							//选取当前页码
							var _activeNum = $$active.attr('data-value') || 0;
							//待查询页码
							var _newPage = +_activeNum - 1;
							//获取第一项
							var $$first_li = $$li.first();
							//获取第一一项页码
							var value = +$$first_li.attr('data-value') || 0;
							if(value > 1) {
								//移除最后一项
								$$li.last().remove();
								//最后页码
								var _fristPage = +value - 1;
								//添加新页码
								_this.after(_page(_fristPage));
								//删除上一页
								if(_fristPage == 1) {
									$$pagination.find('#page-prev').remove();
								}
								if($$next.length == 0) {
									$$pagination.find('li:last').after(_next);
									listener_next();
								}
								//重新绑定
								listener_li();
								//移除样式
								$$li.removeClass('active');
								//绑定前一项样式
								$$pagination.find('#page-' + _newPage).addClass('active');
								//重置当前页
								$$pagination.find('#nowPage').text(_newPage);
								//查询数据
								_globalThis.opts.callback(_newPage);
							}
						});
					}
				}
				var listener_next = function() {
					var $$next = $$pagination.find('#page-next');
					$$next.off('click');
					$$next.on('click', function() {
						//上一页
						var $$prev = $$pagination.find('#page-prev');
						//获取最新li集合
						var $$li = $$pagination.find('li').filter('.page-num');
						//点击对象
						var _this = $(this);
						//选取选中项
						var $$active = $$li.filter('.active');
						//选取当前页码
						var _activeNum = $$active.attr('data-value') || 0;
						//待查询页码
						var _newPage = +_activeNum + 1;
						//获取最后一项
						var $$last_li = $$li.last();
						//获取最后一项页码
						var value = +$$last_li.attr('data-value') || 0;
						if(value < totalPage) {
							//移除第一项
							$$li.first().remove();
							//最后页码
							var _lastPage = +value + 1;
							
							//添加新页码
							_this.before(_page(_lastPage));
							//删除下一页
							if(_lastPage == totalPage) {
								$$pagination.find('#page-next').remove();
							}
							if($$prev.length == 0) {
								$$pagination.find('li:first').before(_prev);
								listener_prev();
							}
							//重新绑定
							listener_li();
							//移除样式
							$$li.removeClass('active');
							//绑定后一项样式
							$$pagination.find('#page-' + _newPage).addClass('active');
							//重置当前页
							$$pagination.find('#nowPage').text(_newPage);
							//查询数据
							_globalThis.opts.callback(_newPage);
						} 
					});
				}
				
				mkpagination(null, newPage);
			
			},
			/**
			 * 
			 * @param {Object} offset 當前位置
			 * @param {Object} total 最大數量
			 * @param {Object} callback 回調
			 */
			bootstrap: function(offset, total, callback) {
				if(!!total && total > 1 && (!this.opts.cache.currentOffset || this.opts.cache.currentOffset != offset)) {
					this.opts.cache.currentOffset = offset;
					var options = {
							bootstrapMajorVersion: 3,
							//当前页数
							currentPage: offset, 
							//总页数
							totalPages: total, 
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
							},//点击事件，用于通过Ajax来刷新整个list列表
							onPageChanged:function (event, oldPage, newPage) {
								this.currentPage = newPage;
								this.oldPage = oldPage;
								if(!!callback) {
									callback(newPage);
								} else {
									this.opts.callback(newPage);
								}
							}
					};
					this.currentPage = offset;
					this.initalPage(options);
					
					$(this._this).bootstrapPaginator(options);
				} else {
					$(this._this).find('div').remove();
				}
			},
			/**
			 * 分页加载完毕回调
			 * @param {Object} fn
			 */
			pageDone: function(callback) {
				this.opts.callback = callback;
				return this;
			},
			/**
			 * 最大页数
			 */
			pageTotal: function(offset) {
				this.opts.config.offset = offset;
			},
			/**
			 * 获取当前页码
			 */
			getCurrentPage: function() {
				return this.currentPage;
			}
		}
		
		return PaginateFactory.inital.apply(this, arguments);
	}
	
}, $);
