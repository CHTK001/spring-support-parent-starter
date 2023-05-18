;
(function(fn, $, window) {
	fn($);
})(function($) {

	var measurements = [
		'paddingLeft',
		'paddingRight',
		'paddingTop',
		'paddingBottom',
		'marginLeft',
		'marginRight',
		'marginTop',
		'marginBottom',
		'borderLeftWidth',
		'borderRightWidth',
		'borderTopWidth',
		'borderBottomWidth'
	];

	var measurementsLength = measurements.length;

	var DEFAULT_OPTIONS = {
		id: '',
		name: '',
		solder: 'xy',
		handler: 'handler', //拖动位置
		content: '', //内容
		inBody: !1,
		size: [800, 600],
		offset: ['100px', '50px'],
		salverBar: '.popup-salver-bar',
		tl: (function() {
			return `
				<div class="popup-window popup-window-fixed popup-fiex popup-fiex-column">
					<div class="popup-window-bar popup-fiex popup-fiex-row" >
						<div class="popup-window-bar-title popup-flex-center popup-fiex popup-fiex-row">
							<div class="title-icon popup-flex-center popup-fiex ">
								<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" class="icon popup-icon" viewBox="0 0 1024 1024" id="icon">
									<path d="M853.333333 1024L170.666667 1024c-93.866667 0-170.666667-76.8-170.666667-170.666667L0 170.666667c0-93.866667 76.8-170.666667 170.666667-170.666667l682.66666699 0c93.866667 0 170.666667 76.8 170.66666701 170.666667l0 682.66666699C1024 947.2 947.2 1024 853.333333 1024zM170.666667 85.333333C123.733333 85.333333 85.333333 123.733333 85.333333 170.666667l0 682.66666699c0 46.933333 38.4 85.333333 85.33333301 85.33333301l682.66666699 0c46.933333 0 85.333333-38.4 85.33333301-85.33333301L938.666667 170.666667c0-46.933333-38.4-85.333333-85.33333301-85.33333301L170.666667 85.333333z" p-id="1156"></path><path d="M981.333333 341.333333L42.666667 341.333333C17.066667 341.333333 0 324.266667 0 298.666667s17.066667-42.666667 42.666667-42.666667l938.66666699 0c25.6 0 42.666667 17.066667 42.66666701 42.666667S1006.933333 341.333333 981.333333 341.333333z" p-id="1157"></path><path d="M170.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1158"></path><path d="M170.666667 234.666667C136.533333 234.666667 106.666667 204.8 106.666667 170.666667S136.533333 106.666667 170.666667 106.666667 234.666667 136.533333 234.666667 170.666667 204.8 234.666667 170.666667 234.666667zM170.666667 149.333333C157.866667 149.333333 149.333333 157.866667 149.333333 170.666667S157.866667 192 170.666667 192 192 183.466667 192 170.666667 183.466667 149.333333 170.666667 149.333333z" p-id="1159"></path><path d="M298.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1160"></path><path d="M298.666667 234.666667C264.533333 234.666667 234.666667 204.8 234.666667 170.666667S264.533333 106.666667 298.666667 106.666667 362.666667 136.533333 362.666667 170.666667 332.8 234.666667 298.666667 234.666667zM298.666667 149.333333C285.866667 149.333333 277.333333 157.866667 277.333333 170.666667S285.866667 192 298.666667 192 320 183.466667 320 170.666667 311.466667 149.333333 298.666667 149.333333z" p-id="1161"></path><path d="M426.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1162"></path><path d="M426.666667 234.666667C392.533333 234.666667 362.666667 204.8 362.666667 170.666667S392.533333 106.666667 426.666667 106.666667s64 29.866667 64 64S460.8 234.666667 426.666667 234.666667zM426.666667 149.333333C413.866667 149.333333 405.333333 157.866667 405.333333 170.666667S413.866667 192 426.666667 192s21.333333-8.533333 21.333333-21.333333S439.466667 149.333333 426.666667 149.333333z" p-id="1163"></path>
								</svg>
							</div>
							<div class="title-message popup-flex-center popup-fiex-column popup-fiex">
								<label class="title-message-label">sdsadsdsd</label>
							</div>
						</div>
						<div class="popup-window-bar-handler popup-flex"></div>
						<div class="popup-window-bar-tools popup-fiex popup-flex-center popup-fiex-row">
							<div class="popup-icon tools-min popup-tools  popup-fiex popup-fiex-column popup-flex-center">
								<i class="glyphicon glyphicon-minus" style="top: -3px;" />
							</div>
							<div class="popup-icon tools-close popup-tools popup-fiex popup-fiex-column popup-flex-center">
								<i class="glyphicon glyphicon-remove" style="top: -3px;" />
							</div>
						</div>
					</div>
					<div class="popup-window-content popup-flex">
					</div>
				</div>
			`;
		})(),
		style: {
			'background': 'rgb(255, 255, 255)',
			'border': '1px solid rgb(59, 172, 237)',
			'border-radius': '4px',
			'box-shadow': 'rgba(0, 0, 0, 0.3) 1px 1px 24px',
			'position': 'fixed',
			'border-radius': '10px'
		},
		barpopup: (function() {
			return `
				<div class="popup-fiex popup-fiex-column popup-flex-center">
					<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" class="icon popup-icon" viewBox="0 0 1024 1024" id="icon">
						<path d="M853.333333 1024L170.666667 1024c-93.866667 0-170.666667-76.8-170.666667-170.666667L0 170.666667c0-93.866667 76.8-170.666667 170.666667-170.666667l682.66666699 0c93.866667 0 170.666667 76.8 170.66666701 170.666667l0 682.66666699C1024 947.2 947.2 1024 853.333333 1024zM170.666667 85.333333C123.733333 85.333333 85.333333 123.733333 85.333333 170.666667l0 682.66666699c0 46.933333 38.4 85.333333 85.33333301 85.33333301l682.66666699 0c46.933333 0 85.333333-38.4 85.33333301-85.33333301L938.666667 170.666667c0-46.933333-38.4-85.333333-85.33333301-85.33333301L170.666667 85.333333z" p-id="1156"></path><path d="M981.333333 341.333333L42.666667 341.333333C17.066667 341.333333 0 324.266667 0 298.666667s17.066667-42.666667 42.666667-42.666667l938.66666699 0c25.6 0 42.666667 17.066667 42.66666701 42.666667S1006.933333 341.333333 981.333333 341.333333z" p-id="1157"></path><path d="M170.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1158"></path><path d="M170.666667 234.666667C136.533333 234.666667 106.666667 204.8 106.666667 170.666667S136.533333 106.666667 170.666667 106.666667 234.666667 136.533333 234.666667 170.666667 204.8 234.666667 170.666667 234.666667zM170.666667 149.333333C157.866667 149.333333 149.333333 157.866667 149.333333 170.666667S157.866667 192 170.666667 192 192 183.466667 192 170.666667 183.466667 149.333333 170.666667 149.333333z" p-id="1159"></path><path d="M298.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1160"></path><path d="M298.666667 234.666667C264.533333 234.666667 234.666667 204.8 234.666667 170.666667S264.533333 106.666667 298.666667 106.666667 362.666667 136.533333 362.666667 170.666667 332.8 234.666667 298.666667 234.666667zM298.666667 149.333333C285.866667 149.333333 277.333333 157.866667 277.333333 170.666667S285.866667 192 298.666667 192 320 183.466667 320 170.666667 311.466667 149.333333 298.666667 149.333333z" p-id="1161"></path><path d="M426.666667 170.666667m-42.666667 0a1 1 0 1 0 85.333333 0 1 1 0 1 0-85.333333 0Z" p-id="1162"></path><path d="M426.666667 234.666667C392.533333 234.666667 362.666667 204.8 362.666667 170.666667S392.533333 106.666667 426.666667 106.666667s64 29.866667 64 64S460.8 234.666667 426.666667 234.666667zM426.666667 149.333333C413.866667 149.333333 405.333333 157.866667 405.333333 170.666667S413.866667 192 426.666667 192s21.333333-8.533333 21.333333-21.333333S439.466667 149.333333 426.666667 149.333333z" p-id="1163"></path>
					</svg>
				</div>
			`;
		})(),
		pointer: undefined
	};

	$.popup = function(setting) {
		let settings = initialOption(setting, this);
		initialPageSelector(settings);
		return settings.id;
	};

	/**
	 * 初始化页面节点
	 * @param {Object} settings
	 */
	let initialPageSelector = function(settings) {
		initialPageSalverBar(settings);
		//initialPagePopoverShadow(settings);
		initialPagePopup(settings);
	};
	/**
	 * 弹出层阴影
	 */
	let initialPagePopoverShadow = function(settings) {
		let documentHeight = settings.documentHeight;
		let documentWidth = settings.documentWidth;
		let $body = settings.$body;
		let $popupWindowShadow = $body.children('.popup-window-shadow');
		if(!$popupWindowShadow || $popupWindowShadow.length == 0) {
			$body.append('<div class="popup-window-shadow" style="width: '+ documentWidth +'px; height: '+ documentHeight +'px; z-index: -999"></div>');
		}
		
	};

	/**
	 * 初始化页面弹出框节点
	 * @param {Object} settings
	 */
	let initialPagePopup = function(settings) {
		let id = settings.id;
		let selectorId = 'popup-window-' + id;
		
		let exist = testPopup(settings);

		if(!exist) {
			renderPopup(settings);
		}
		
		let $selector = $('#' + selectorId);

		events($selector, settings);
		dragStart($selector, settings);
		//dragMove($selector, settings);
		dragEnd($selector, settings);
	};
	/**
	 * 渲染不存在的弹框
	 */
	let renderPopup = function(settings) {
		let id = settings.id;
		let $tl = $(settings.tl);
		let size = settings.size;
		let offset = settings.offset;
		let style = settings.style;
		let $salverBarHandler = $tl.find('.popup-window-bar-handler');
		let selectorId = 'popup-window-' + id;
		let rLeft = settings.initLeft;
		let rTop = settings.initTop;
		let $body = settings.$body;
		let $popupWindowShadow = $body//.children('.popup-window-shadow')
		let $popupWindowContent = $tl.find('.popup-window-content');
		
		if (!!settings.handler) {
			$salverBarHandler.addClass(settings.handler);
		}
		
		style.left = offset ? offset[1] : 0;
		style.top = offset ? offset[0] : 0;
		
		$tl.attr('wid', id).attr('id', selectorId);
		$tl.width(size ? size[0] : 0);
		$tl.height(size ? size[1] : 0);
		$tl.css(style);
		$popupWindowContent.append($(settings.content).clone());
		
		$popupWindowShadow.append($tl);
		
		$selector = $tl;
		$selector.find('.title-message-label').text(id);
		$selector.draggabilly({
			containment: settings.inBody,
			handle: '.popup-window-bar-handler'
		});
	};
	/**
	 * 尝试打开已有的弹框
	 */
	let testPopup = function(settings) {
		let selectorId = settings.id;
		let solder = settings.solder;
		let $selector = $('#popup-window-' + selectorId);
		if ($selector.length > 0) {
			if(solder === 'bar') {
				let $salverBar = $(settings.salverBar);
				$selector.show();
				$salverBar.children().removeClass('salver-button-active');
				$salverBar.children('[popup-id=' + selectorId + ']').addClass('salver-button-active');
			} else if(solder === 'xy') {
				let rect = getRect($selector);
				if(rect.startLeft !== undefined) {
					let startLeft = ~~rect.startLeft;
					let startTop = ~~rect.startTop;
						
					$selector.css({
						left: startLeft + 'px',
						top: startTop + 'px'
					});
				}
				$selector.show();
				$('[popup-id='+ selectorId +']').remove();
			}
		}
		
		return $selector && $selector.length > 0;
	};
	
	/**
	 * @param {Object} $selector
	 * @param {Object} settings
	 */
	let dragMove = function($selector, settings) {
		let pointerCall = settings.pointer;
		let $popupWindowBarTitle = $('.popup-window-bar-title');
		let _titleWidth = $popupWindowBarTitle.width();
		let _titleHeight = $popupWindowBarTitle.height();
		
		$selector.on('dragMove', function(event, pointer) {
			var _this = $(this);
			let rectInfo = getRect(_this);
			rectInfo.offsetX = pointer.offsetX;
			rectInfo.offsetY = pointer.offsetY;
			rectInfo.clientX = pointer.clientX;
			rectInfo.clientY = pointer.clientY;
			rectInfo.titleWidth = _titleWidth;
			rectInfo.titleHeight = _titleHeight;
			
			rectInfo = getPointerRect(rectInfo);
			
			if(pointerCall) {
				pointerCall(rectInfo);
			}
			if(!rectInfo.xNotPage && rectInfo.yNotPage) {
				if(rectInfo.y) {
					_this.css('top', 0);
					return; 
				}
				_this.css('bottom', 0);
				return;
			} else if(rectInfo.xNotPage && !rectInfo.yNotPage) {
				if(rectInfo.x) {
					_this.css('left', 0);
					return; 
				}
				_this.css('right', 0);
				return;
			} else if(rectInfo.xNotPage && rectInfo.yNotPage) {
				if(rectInfo.x && rectInfo.y) {
					_this.css({
						'left': 0,
						'top': 0
					});
					return; 
				} else if(rectInfo.x && !rectInfo.y) {
					_this.css({
						'left': 0,
						'bottom': 0
					});
					return; 
				} if(!rectInfo.x && rectInfo.y) {
					_this.css({
						'right': 0,
						'top': 0
					});
					return; 
				}
				_this.css({
					'right': 0,
					'bottom': 0
				});
				return;
			}
			
		});
	};
	
	/**
	 * @param {Object} rectInfo
	 */
	let getPointerRect = function(rectInfo) {
		rectInfo.left = rectInfo.clientX - rectInfo.offsetX - rectInfo.titleWidth;
		rectInfo.top = rectInfo.clientY - rectInfo.offsetY - rectInfo.titleHeight;
		rectInfo.xNotPage = rectInfo.left <= 0 || rectInfo.left + rectInfo.width >= rectInfo.parentWidth,
		rectInfo.yNotPage = rectInfo.top <= 0 || rectInfo.top + rectInfo.height >= rectInfo.parentHeight,
		rectInfo.x = rectInfo.left <= 0,
		rectInfo.y = rectInfo.top <= 0
		return rectInfo;
	};
	/**
	 * @param {Object} $selector
	 * @param {Object} settings
	 */
	let dragStart = function($selector, settings) {
		if(settings.solder === 'bar') {
			return !1;
		}
		
		$selector.on('dragStart', function(event, pointer) {
			var _this = $(this);
			let rectInfo = getRect(_this);
			let startLeft = rectInfo.left;
			let startTop = rectInfo.top;
	
			_this.attr('start-left', startLeft).attr('start-top', startTop);
		});
	};
	/**
	 * @param {Object} $selector
	 * @param {Object} settings
	 */
	let dragEnd = function($selector, settings) {
		if(settings.solder === 'bar') {
			return !1;
		}
		
		$selector.on('dragEnd', function(event, pointer) {
			var _this = $(this);
			let rectInfo = getRect(_this);
			
			if(!rectInfo.xNotPage && rectInfo.yNotPage) {
				yMoveInPage($selector, rectInfo, settings);
			} else if(rectInfo.xNotPage && !rectInfo.yNotPage) {
				xMoveInPage($selector, rectInfo, settings);
			} else if(rectInfo.xNotPage && rectInfo.yNotPage) {
				xyMoveInPage($selector, rectInfo, settings);
			}

		});
	};
	/**
	 * x移动回页面
	 * @param {Object} $selector
	 */
	let xyMoveInPage = function($selector, rectInfo, settings) {
		if(rectInfo.x) {
			xMoveInPage($selector, rectInfo, settings);
		} else {
			yMoveInPage($selector, rectInfo, settings);
		}
	};
	/**
	 * x移动回页面
	 * @param {Object} $selector
	 */
	let xMoveInPage = function($selector, rectInfo, settings) {
		//左侧
		let $barpopup = _xyMinEvent($selector, rectInfo, settings);
		if(rectInfo.x) {
			$barpopup.css({
				left: '0px',
				top: (rectInfo.top < 0 ? 0 : rectInfo.top) + 'px'
			});
			mouseoutAndOver($barpopup, 'popup-salver-button-left-1', 'popup-salver-button-left', 'popup-salver-right salver-right-active');
			return;
		}
		$barpopup.css({
			right: '0px',
			top: (rectInfo.top < 0 ? 0 : rectInfo.top) + 'px'
		});
		mouseoutAndOver($barpopup, 'popup-salver-button-right-1', 'popup-salver-button-right', 'popup-salver-left salver-left-active');
		
		return;
	};
	/**
	 * @param {Object} _this
	 */
	let _xyMinEvent = function($selector, rectInfo, settings) {
		let selectorId = settings.id;
		let $body = settings.$body;
		
		let $barpopup = _minEvent($selector, null, settings, function(_this) {
			let rect = getRect($selector);
				
			if(rect.startLeft !== undefined) {
				let startLeft = ~~rect.startLeft;
				let startTop = ~~rect.startTop;
					
				$selector.css({
					left: startLeft + 'px',
					top: startTop + 'px'
				});
			}
			$('[popup-id='+ selectorId +']').remove();
			return;
		});
		
		
		$body.append($barpopup);
		
		return $barpopup;
	};
	/**
	 * y移动回页面
	 * @param {Object} $selector
	 */
	let yMoveInPage = function($selector, rectInfo, settings) {
		//左侧
		let $barpopup = _xyMinEvent($selector, rectInfo, settings);
		let _left = (rectInfo.left + rectInfo.width / 2);
		if(rectInfo.y) {
			$barpopup.css({
				left: (_left < 0 ? 0 : _left) + 'px',
				top: '0px'
			});
			mouseoutAndOver($barpopup, 'popup-salver-button-top-1', 'popup-salver-button-top', 'popup-salver-bottom salver-bottom-active');
			return;
		}
		$barpopup.css({
			left: (_left < 0 ? 0 : _left)   + 'px',
			bottom: '0px'
		});
		mouseoutAndOver($barpopup, 'popup-salver-button-bottom-1', 'popup-salver-button-bottom', 'popup-salver-top salver-top-active');
		
		return;
	};
	
	/**
	 * 鼠标事件
	 * @param {Object} $barpopup
	 * @param {Object} show
	 * @param {Object} hide
	 */
	let mouseoutAndOver = function($barpopup, show, hide, classes) {
		if(!$barpopup || $barpopup.length == 0) {
			return !1;
		}
		
		$barpopup.addClass(show).addClass(classes);
		
		$barpopup.off('mouseover');
		$barpopup.on('mouseover', function() {
			$barpopup.removeClass(show).addClass(hide);
		});
				
		$barpopup.off('mouseout');
		$barpopup.on('mouseout', function() {
			$barpopup.removeClass(hide).addClass(show);
		});
		
	};
	
	/**坐标区域
	 * @param {Object} _this
	 */
	let getRect = function(_this) {
		var left = parseInt(_this.css('left'));
		var top = parseInt(_this.css('top'));
		var width = _this.width();
		var height = _this.height();
		let parentWidth = $(document).width();
		let parentHeight = $(document).height();
		let startLeft = _this.attr('start-left');
		let startTop = _this.attr('start-top');
		
		var paddingWidth = parseInt(_this.css('padding-left')) + parseInt(_this.css('padding-right'));
		var paddingHeight = parseInt(_this.css('padding-top')) + parseInt(_this.css('padding-bottom'));
		var marginWidth = parseInt(_this.css('margin-left')) + parseInt(_this.css('margin-right'));
		var marginHeight = parseInt(_this.css('margin-top')) + parseInt(_this.css('margin-bottom'));
		var borderWidth = parseInt(_this.css('border-left-width')) + parseInt(_this.css('border-right-width'));
		var borderHeight = parseInt(_this.css('border-top-width')) + parseInt(_this.css('border-bottom-width'));
		
		width = width + paddingWidth + marginWidth + borderWidth;
		height = height + paddingHeight + marginHeight + borderHeight;
		
		return {
			left: left,
			top: top,
			startLeft: startLeft,
			startTop: startTop,
			width: width,
			pWidth: parentWidth,
			pHeight: parentHeight,
			height: height,
			xNotPage: left <= 0 || left + width >= parentWidth,
			yNotPage: top <= 0 || top + height >= parentHeight,
			x: left <= 0,
			y: top <= 0
		}
	};
	
	/**
	 * 关闭事件
	 */
	let closeEvent = function($selector, $salverBar, id) {
		let $close = $selector.find('.tools-close');
		if ($close.length > 0) {
			$close.off('click');
			$close.on('click', function() {
				$selector.remove();
				if(!!$salverBar && $salverBar.length > 0) {
					$salverBar.find('[popup-id=' + id + ']').remove();
				}
			});
		}
	};
	
	/**
	 * 关闭事件
	 */
	let minEvent = function($selector, $salverBar, settings) {
		let $min = $selector.find('.tools-min');
		if(!$salverBar || $salverBar.length == 0) {
			$min.hide();
		}
		if ($salverBar && $min.length > 0) {
			$min.off('click');
			$min.on('click', function() {
				_barMinEvents(this, $selector, $salverBar, settings);
			});
		} 
	};
	
	/**
	 * 关闭事件
	 */
	let forcusEvent = function($selector) {
		$selector.off('click');
		$selector.on('click', function() {
			$selector.siblings().removeClass('popup-window-action');
			$selector.addClass('popup-window-action');
		});
	};
	
	/**
	 * 菜单事件
	 */
	let salverBarEvent = function($salverBar) {
		if (!!$salverBar && $salverBar.length > 0) {
			$salverBar.off('mouseover');
			$salverBar.on('mouseover', function() {
				$salverBar.children().removeClass('popup-salver-button-down').addClass('popup-salver-button-up');
			});
		
			$salverBar.off('mouseout');
			$salverBar.on('mouseout', function() {
				$salverBar.children().removeClass('popup-salver-button-up').addClass('popup-salver-button-down');
			});
		}
		
	};
	/**
	 * @param {Object} $selector
	 */
	let events = function($selector, settings) {
		let $salverBar = $(settings.salverBar);
		closeEvent($selector, $salverBar, settings.id);
		minEvent($selector, $salverBar, settings);
		forcusEvent($selector);
		salverBarEvent($salverBar);
		
	};
	/**
	 * 最小化/还原
	 */
	let _barMinEvents = function(_this, $selector, $salverBar, settings) {
		$salverBar.children().removeClass('salver-button-active');
		let $barpopup = _minEvent($selector, undefined, settings, function(_this) {
			$salverBar.children().removeClass('salver-button-active');
			$(_this).addClass('salver-button-active');
		});
		$barpopup.css('bottom', '-45px');
		$barpopup.addClass('popup-salver-button popup-salver-button-down salver-button-active');
		if(!!$salverBar) {
			$salverBar.append($barpopup);
		}
	};
	/**
	 * @param {Object} $barpopup
	 * @param {Object} settings
	 */
	let _minEvent = function($selector, $barpopup, settings, callback) {
		let id = settings.id;
		
		$selector.hide();
		$barpopup = $('[popup-id=' + id + ']');
		if ($barpopup.length == 0) {
			$barpopup = $(settings.barpopup);
			$barpopup.attr('popup-id', id);
			$barpopup.off('click');
			$barpopup.attr('title', settings.name);
		
			$barpopup.on('click', function() {
				callback && callback(this);
				let id = $barpopup.attr('popup-id');
				if ($selector.is(':hidden')) {
					$selector.show();
		
					$selector.siblings().removeClass('popup-window-action');
					$selector.addClass('popup-window-action');
					return !1;
				}
				$selector.hide();
				$selector.removeClass('popup-window-action');
			});
		}
		return $barpopup;
	};

	let measureContainment = function($ele, $eleParent) {
		let parent = $eleParent;
		var elemSize = getSize($ele);
		var containerSize = getSize(container);
		var elemRect = $ele.getBoundingClientRect();
		var containerRect = container.getBoundingClientRect();

		var borderSizeX = containerSize.borderLeftWidth + containerSize.borderRightWidth;
		var borderSizeY = containerSize.borderTopWidth + containerSize.borderBottomWidth;

		var position = this.relativeStartPosition = {
			x: elemRect.left - (containerRect.left + containerSize.borderLeftWidth),
			y: elemRect.top - (containerRect.top + containerSize.borderTopWidth)
		};

		this.containSize = {
			width: (containerSize.width - borderSizeX) - position.x - elemSize.width,
			height: (containerSize.height - borderSizeY) - position.y - elemSize.height
		};
	};
	/**
	 * @param {Object} elem
	 */
	let getSize = function(elem) {
		if (typeof elem == 'string') {
			elem = document.querySelector(elem);
		}

		if (!elem || typeof elem != 'object' || !elem.nodeType) {
			return;
		}

		var style = getComputedStyle(elem);

		if (style.display == 'none') {
			return getZeroSize();
		}

		var size = {};
		size.width = elem.offsetWidth;
		size.height = elem.offsetHeight;

		var isBorderBox = size.isBorderBox = style.boxSizing == 'border-box';

		for (var i = 0; i < measurementsLength; i++) {
			var measurement = measurements[i];
			var value = style[measurement];
			var num = parseFloat(value);
			size[measurement] = !isNaN(num) ? num : 0;
		}

		var paddingWidth = size.paddingLeft + size.paddingRight;
		var paddingHeight = size.paddingTop + size.paddingBottom;
		var marginWidth = size.marginLeft + size.marginRight;
		var marginHeight = size.marginTop + size.marginBottom;
		var borderWidth = size.borderLeftWidth + size.borderRightWidth;
		var borderHeight = size.borderTopWidth + size.borderBottomWidth;
		var isBoxSizeOuter = getStyleSize(style.width) == 200;
		var isBorderBoxSizeOuter = isBorderBox && isBoxSizeOuter;

		var styleWidth = getStyleSize(style.width);
		if (styleWidth !== false) {
			size.width = styleWidth +
				(isBorderBoxSizeOuter ? 0 : paddingWidth + borderWidth);
		}

		var styleHeight = getStyleSize(style.height);
		if (styleHeight !== false) {
			size.height = styleHeight +
				(isBorderBoxSizeOuter ? 0 : paddingHeight + borderHeight);
		}

		size.innerWidth = size.width - (paddingWidth + borderWidth);
		size.innerHeight = size.height - (paddingHeight + borderHeight);

		size.outerWidth = size.width + marginWidth;
		size.outerHeight = size.height + marginHeight;

		return size;
	};

	let getStyleSize = function(value) {
		var num = parseFloat(value);
		var isValid = value.indexOf('%') == -1 && !isNaN(num);
		return isValid && num;
	}
	/**
	 * 影藏数据
	 */
	let getZeroSize = function() {
		var size = {
			width: 0,
			height: 0,
			innerWidth: 0,
			innerHeight: 0,
			outerWidth: 0,
			outerHeight: 0
		};
		for (var i = 0; i < measurementsLength; i++) {
			var measurement = measurements[i];
			size[measurement] = 0;
		}
		return size;
	}
	/**
	 * 初始化页面托盘节点
	 * @param {Object} settings
	 */
	let initialPageSalverBar = function(settings) {
		let solder = settings.solder;
		if(solder !== 'bar') {
			return;
		}
		let salverBar = settings.salverBar;
		if (!salverBar) {
			return;
		}
		if ($(salverBar).length > 0) {
			return $(salverBar);
		}
		let $salverBar = $(
			'<div id="popup-salver-bar" class="popup-salver-bar popup-flex-center popup-fiex popup-fiex-row">');
		$('body').append($salverBar);
		return $salverBar;
	};
	/**
	 * 初始化配置
	 * @param {Object} settings
	 */
	let initialOption = function(settings) {
		if (Object.prototype.toString.call(settings).slice(8, -1) === 'String') {
			settings = {
				id: settings
			}
		}

		let setting = Object.assign(true, DEFAULT_OPTIONS, settings || {});

		let offset = setting.offset || [0, 0];
		let realOffset = realSize(offset[1], offset[0]);
		let $document = $(document);
		
		setting.id = setting.id || uuid();
		setting.initLeft = realOffset.left;
		setting.initTop = realOffset.top;
		setting.documentWidth = $document.width();
		setting.documentHeight = $document.height();
		setting.$body = $('body');
		
		return setting;
	};

	/**
	 * 获取真实位置
	 */
	let realSize = function(left, top) {
		let realDocumentWidth = $(document).width();
		let realDocumentHeight = $(document).height();

		let realLeft = left;
		if (left.endsWith('px')) {
			realLeft = ~~left.replace('px', '');
		} else if (left.endsWith('%')) {
			realLeft = ~~left.replace('%', '') * realDocumentWidth / 100;
		}

		let realTop = top;
		if (top.endsWith('px')) {
			realTop = ~~top.replace('px', '');
		} else if (left.endsWith('%')) {
			realTop = ~~top.replace('%', '') * realDocumentHeight / 100;
		}

		return {
			left: realLeft,
			top: realTop
		};
	};
	/**
	 * 真实长度
	 */
	let formatSize = function(value) {
		if (value.endsWith('px')) {
			return ~~value.replace('px', '');
		} else {
			return ~~value;
		}
	};

	/**
	 * uuid
	 */
	let uuid = function() {
		var chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');
		var uuid = [],
			i, len = 12;
		let radix = chars.length;

		if (len) {
			for (i = 0; i < len; i++) uuid[i] = chars[0 | Math.random() * radix];
		} else {
			var r;
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
}, $, window)
