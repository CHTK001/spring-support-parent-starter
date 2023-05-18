/**
 * 分页插件
 */
;
(function(fn, $, window) {
	fn($, window);
})(function($, w) {

	const cache = {};
	const cacheInterval = {};

	var DEFAULT_SETTING = {
		tl: 'reload',
		type: {
			audio: '<img class="audio" src="./svg-loaders/audio.svg" width="60" alt="">',
			rings: '<img class="rings" src="./svg-loaders/rings.svg" width="60" alt="">',
			grid: '<img class="grid" src="./svg-loaders/grid.svg" width="60" alt="">',
			hearts: '<img class="hearts" src="./svg-loaders/hearts.svg" width="60" alt="">',
			oval: '<img class="oval" src="./svg-loaders/oval.svg" width="60" alt="">',
			'three-dots': '<img class="three-dots" src="./svg-loaders/three-dots.svg" width="60" alt="">',
			'spinning-circles': '<img class="spinning-circles" src="./svg-loaders/spinning-circles.svg" width="60" alt="">',
			puff: '<img class="puff" src="./svg-loaders/puff.svg" width="60" alt="">',
			circle: '<img class="circles" src="./svg-loaders/circles.svg" width="60" alt="">',
			'circle.1': '<img class="circles" src="./svg-loaders/circles.1.svg" width="60" alt="">',
			'tail-spin': '<img class="tail-spin" src="./svg-loaders/tail-spin.svg" width="60" alt="">',
			bars: '<img class="bars" src="./svg-loaders/bars.svg" width="60" alt="">',
			'ball-triangle': '<img class="ball-triangle" src="./svg-loaders/ball-triangle.svg" width="60" alt="">',
			'ball': '<img class="ball" src="./svg-loaders/ball.svg" width="60" alt="">',
			'dual-ball': '<img class="dual-ball" src="./svg-loaders/dual-ball.svg" width="60" alt="">',
			'bean-eater':'<img class="bean-eater" src="./svg-loaders/bean-eater.svg" width="60" alt="">',
			'blocks':'<img class="blocks" src="./svg-loaders/blocks.svg" width="60" alt="">',
			'cube':'<img class="cube" src="svg-loaders/cube.svg" width="60" alt="">',
			'spin':'<img class="spin" src="./svg-loaders/spin.svg" width="60" alt="">',
			'wedges':'<img class="wedges" src="./svg-loaders/wedges.svg" width="60" alt="">',
			'spinner':'<img class="spinner" src="./svg-loaders/spinner.svg" width="60" alt="">',
			'rolling':'<img class="rolling" src="./svg-loaders/rolling.svg" width="60" alt="">',
			'ripple':'<img class="ripple" src="./svg-loaders/ripple.svg" width="60" alt="">',
			'reload':'<img class="reload" src="./svg-loaders/reload.svg" width="60" alt="">',
			'pulse':'<img class="pulse" src="./svg-loaders/pulse.svg" width="60" alt="">',
			'magnify':'<img class="magnify" src="./svg-loaders/magnify.svg" width="60" alt="">',
			'infinity':'<img class="infinity" src="./svg-loaders/infinity.svg" width="60" alt="">',
			'ellipsis':'<img class="ellipsis" src="./svg-loaders/ellipsis.svg" width="60" alt="">',
			'dual-ring':'<img class="dual-ring" src="./svg-loaders/dual-ring.svg" width="60" alt="">',
			'double-ring':'<img class="double-ring" src="./svg-loaders/double-ring.svg" width="60" alt="">',
			'circle.2': `
				<div class="circle-border">
					<div class="circle-core"></div>
				</div>
			`,
			'circle.3': `
				<div class="circle-loader">
				</div>
			`,
			configure: `
				<div class="configure-border-1">
					<div class="configure-core"></div>
				</div>
				<div class="configure-border-2">
					<div class="configure-core"></div>
				</div>
			`,
			pulse: `
				<div class="pulse-container">
					<div class="pulse-bubble pulse-bubble-1"></div>
					<div class="pulse-bubble pulse-bubble-2"></div>
					<div class="pulse-bubble pulse-bubble-3"></div>
				</div>
			`,
			solar: `
				<div class="solar-system">
					<div class="earth-orbit orbit">
						<div class="planet earth"></div>
						<div class="venus-orbit orbit">
							<div class="planet venus"></div>
							<div class="mercury-orbit orbit">
								<div class="planet mercury"></div>
								<div class="sun"></div>
							</div>
						</div>
					</div>
				</div>
			`
		},
		layout: 'ud', //[ud, lr]
		timeout: 0,
		message: !1,
		size: [20, 20],
		height: 20,
		classes: '',
		time: !1,
		className: 'factory-loading',
		top: "50%",
		left: "50%",
		script: (function() {
			let script = document.currentScript;
			let scriptHtml = script.outerHTML;
			let index = scriptHtml.indexOf('src=');
			scriptHtml = scriptHtml.substring(index + 5);
			index = scriptHtml.indexOf("\"");
			if (index > -1) {
				scriptHtml = scriptHtml.substring(0, index);
			}
			index = scriptHtml.lastIndexOf("/");
			if (index > -1) {
				scriptHtml = scriptHtml.substring(0, index);
			}
			return scriptHtml + "/";
		})()
	}
	/**
	 * loading设置
	 * @param settings
	 * @returns {(boolean & {classes: string, className: string, type: {solar: string, "bean-eater": string, magnify: string, "spinning-circles": string, ball: string, rolling: string, reload: string, hearts: string, audio: string, cube: string, spinner: string, "tail-spin": string, "circle.1": string, "dual-ball": string, "circle.2": string, "circle.3": string, oval: string, blocks: string, "ball-triangle": string, "three-dots": string, configure: string, bars: string, ellipsis: string, puff: string, wedges: string, rings: string, ripple: string, grid: string, spin: string, pulse: string, infinity: string, circle: string, "dual-ring": string, "double-ring": string}, message: boolean, timeout: number, script: string, layout: string, size: number[], top: string, left: string, tl: string, time: boolean, height: number} & {tl: *}) | (boolean & {classes: string, className: string, type: {solar: string, "bean-eater": string, magnify: string, "spinning-circles": string, ball: string, rolling: string, reload: string, hearts: string, audio: string, cube: string, spinner: string, "tail-spin": string, "circle.1": string, "dual-ball": string, "circle.2": string, "circle.3": string, oval: string, blocks: string, "ball-triangle": string, "three-dots": string, configure: string, bars: string, ellipsis: string, puff: string, wedges: string, rings: string, ripple: string, grid: string, spin: string, pulse: string, infinity: string, circle: string, "dual-ring": string, "double-ring": string}, message: boolean, timeout: number, script: string, layout: string, size: number[], top: string, left: string, tl: string, time: boolean, height: number})}
	 */
	$.loadSetting = function(settings) {
		if (Object.prototype.toString.call(settings).slice(8, -1) === 'String') {
			settings = {
				tl: settings
			}
		}
		let ds = Object.assign(true, DEFAULT_SETTING, settings || {});
		return ds;
	}
	/**
	 * 等待加载
	 * @param {Object} ele
	 */
	$.fn.loading = function(settings) {
		let $ele = $(this);
		
		let setting = renderSpan($ele, settings);
		
		noSpinLoad(setting, $ele);
		position($ele.find('.' + setting.className), setting);
		timeOrMessage(setting, $ele);
	}
	/**
	 * 全屏加载
	 */
	$.loading = function(settings) {
		let $ele = $('body');
		if(Object.prototype.toString.call(settings).slice(8, -1) === 'String') {
			settings = {
				tl: settings
			}
		}
		let setting = renderSpan($ele, settings);
		//禁止滚动
		$ele.addClass("scroll-off");
		
		setting.message = '加载中<a href="javascript:$loadClose()">取消</a>...';
		setting.time = !0;
		
		noSpinLoad(setting, $ele);
		timeOrMessage(setting, $ele);
		
		let $className =  $ele.children('.' + setting.className);
		let $children = $className.children();
		$className.addClass('full-screen');
		
	}
	/**
	 * 加载完毕
	 */
	$.loaded = function() {
		$('.' + $.loadSetting().className).remove();
	}
	/**
	 * 可被等待的ajax
	 */
	$.fn.loadAjax = function(setting, loadSetting) {
		let async = setting.async;
		if(async == 'false') {
			setting.async = !0;
		};
		let success = setting.success;
		let error = setting.error;
		
		let $this = $(this);
		$this.loading(loadSetting || {
			tl: 'spinner',
			size: [40, 40]
		});
		$.when($.ajax(setting)).then(function(resolve, reject) {
			$this.loaded();
			success && success(resolve);
			error && error(reject);
		});
	};
	/**
	 * 加载完毕
	 */
	$.fn.loaded = function() {
		let setting = Object.assign(true, DEFAULT_SETTING, arguments[0] || {});
		let className = setting.className;
		let selector = $(this).children('.' + className).attr('loadid');
		let $chilrend = this.children('.'+ className +'.' + selector + '');
		
		let uid = $chilrend.attr('uid');
		if(!!uid) {
			let interval = cacheInterval[uid];
			if(!!interval) {
				clearInterval(interval);
				delete cacheInterval[uid];
			}
		}
		$chilrend.remove();
	}
	
	/**
	 * 信息或者时间
	 * @param {Object} settings
	 * @param {Object} $ele
	 */
	let timeOrMessage = function(setting, $ele) {
		let message = setting.message;
		let time = setting.time;
		let layout = setting.layout;
		let $className =  $ele.children('.' + setting.className);
		let $children = $className.children();
		
		if(time) {
			let _uuid = uuid();
			
			$className.attr('uid', _uuid);
			$children.append('<div class="load-time"><div>');
			
			let $loadTime = $children.find('.load-time');
			let cnt = 0;
			let si = setInterval(function() {
				var showMsg = "00:00";
				var temp = ++ cnt;
				var more = Math.floor(temp / 60);
				var less = temp % 60;
				if(more < 10) {
					showMsg = "0" + more + ":";
				} else {
					showMsg = more + ":";
				}
				if(less < 10) {
					showMsg += "0" + less ;
				} else {
					showMsg += less;
				}
				
				$loadTime.html(showMsg);
			}, 1000);
			
			cacheInterval[_uuid] = si;
		}
		let width = setting.size[0];
		let height = setting.size[1];
		if(message) {
			if(layout == 'lr') {
				$children.after(
					'<div class="load-message" style="padding-left: 10px; float: left; width: '+(width * 2)+'px; line-height: '+ height +'px;">'+ setting.message +'<div>');
			} else {
				$children.append('<div class="load-message">'+ setting.message +'<div>');
			}
		} else {
			let $loading = $ele.children('.factory-loading');
			$loading.height(height);
			$loading.width(width);
			lrPosition(setting, $loading, width, height);
		}
		
		if(time || message) {
			$children.addClass('loading-retain');
		}
		
		
	};
	
	/**
	 * 渲染标签
	 */
	let renderSpan = function($ele, settings) {
		$ele.removeClass('factory-loading-container');
		$ele.addClass('factory-loading-container');
		
		let setting = Object.assign(true, DEFAULT_SETTING, settings);
		let className = setting.className;
		$ele.children('.' + className).remove();
		
		return setting;
	}
	/**
	 * spin
	 */
	let SpinLoad = function(setting, $ele) {
		try {
			var spinner = new Spinner(setting).spin();
			$ele.append(spinner.el);
		} catch (e) {
		}
	}
	/**
	 * 关闭
	 */
	w.$loadClose = function() {
		$('body').loaded();
	};
	/**
	 * uuid
	 */
	let uuid = function() {
		var chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');
		var uuid = [],
			i, len = 12;
		let radix =  chars.length;
	
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
	/**
	 * 非spin
	 */
	let noSpinLoad = function(setting, $ele) {
		let selector = uuid();
		let className = setting.className;
		let type = setting.tl;
		let layout = setting.layout;
		let position = '';
		
		let $loading = $('<div loadid='+ selector +' class="'+ className +' '+ selector +' '+ setting.classes +'"><div class="loading-content">' + setting.type[type] + '</div></div>');
		if(layout === 'lr') {
			$loading.children('.loading-content').css({
				float: 'left'
			});
		}
		dealImg($loading, setting, $ele);
		
		$ele.append($loading);
		
		timeout($ele, selector, setting, className);
	}
	/**
	 * 定位
	 */
	let position = function($loading, setting) {
		let width = setting.size[0];
		let height = setting.size[1];
		let newWidth = width;
		let newHeight = height;
		let layout = setting.layout;
		
		if(layout === 'lr') {
			newWidth = width * 3;
			$loading.height(newHeight);
			$loading.width(newWidth);
		} else {
			$loading.height(height);
			$loading.width(width);
		}
	
		$loading.children().height(height);
		$loading.children().width(width);
		$loading.children().children().height(height);
		$loading.children().children().width(width);
	
		lrPosition(setting, $loading, newWidth, newHeight);
		
	};
	/**
	 * @param {Object} setting
	 * @param {Object} $loading
	 */
	let lrPosition = function(setting, $loading, newWidth, newHeight) {
		if(!setting.center) {
			$loading.css({
				top: setting.top,
				left: setting.left
			});
		} else {
			$loading.css({
				top: 'calc(50% - '+ newHeight / 2 +'px)',
				left: 'calc(50% - '+ newWidth / 2 +'px)',
			});
		}
	};
	/**
	 * 处理 <div><img /></dic>
	 * @param {Object} $loading
	 */
	let dealImg = function ($loading, setting, $ele) {
		let $img = $loading.find('img');
		if($img.length > 0) {
			let _bgColor = $ele.css('background-color');
			
			let scriptHtml = $img.get(0).outerHTML;
			let index = scriptHtml.indexOf('src=');
			scriptHtml = scriptHtml.substring(index + 5);
			index = scriptHtml.indexOf("\"");
			if (index > -1) {
				scriptHtml = scriptHtml.substring(0, index);
			}
			$img.attr('src', setting.script + scriptHtml);
			$img.css('background-color', _bgColor);
		}
	};
	/**
	 * 处理超时
	 */
	let timeout = function($ele, selector, setting, className) {
		clearTimeout(cache[selector]);
		if (setting.timeout && ~~setting.timeout > 0) {
			var s = setTimeout(function() {
				$ele.children("."+ className +"." + selector + "").remove();
			}, ~~setting.timeout);
			cache[selector] = s;
		}
	};

}, $, window);
