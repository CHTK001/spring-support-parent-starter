Define.preprocessor();
var _wHeight = $(window).height();
$('body').height(_wHeight);
$('#editor').height(_wHeight / 2);
$('#css').height(_wHeight / 2);
$('#html').height(_wHeight / 2);
$('#result').height(_wHeight / 2);

//-- 基本方法封装
var utils = {
	op	: function(){
		var op = Object.prototype,
			ap	= Array.prototype;
		return {
			ostring		: op.toString,
			hasOwn		: op.hasOwnProperty,
		}
	},
	eachProp : function(obj, func){
		var prop;
		for(prop in obj){
			if(this.hasProp(obj, prop)){
				if(func(obj[prop], prop)){
					break;
				}
			}
		}
	},
	hasProp : function(obj, prop){
		return this.op().hasOwn.call(obj, prop)
	},
	mixin : function(target, source, force, deepStringMixin) {
		if(source){
			this.eachProp(source, function(value, prop){
				if (force || !this.hasProp(target, prop)) {
					if (deepStringMixin && _.isObject(value) && value &&
						!_.isArray(value) && !_.isFunction(value) &&
						!(value instanceof RegExp)){
						if(!target[prop]){
							target[prop] = {};
						}
						mixin(target[prop], value, force, deepStringMixin);
					}else{
						target[prop] = value;
					}
				}
			})
		}
		return target;
	},
	id:function(id){
		return document.getElementById(id);
	},

	/**
	 * -- 缓存读写器
	 */
	store : {
		set:function(key,value){
			localStorage.setItem(key,value);
		},
		get:function(key) {
			return localStorage.getItem(key);
		}
	},

	//-- 读缓存里的代码
	isStore : function(key,editor){
		if(!!utils.store.get(key)){
			var result = utils.store.get(key);
		}else{
			var result = editor.getValue();
		}
		return result;
	},
};

var Api = {
	buffer: {
		jsEditor: undefined,
		cssEditor: undefined,
		htmlEditor: undefined,
		
		requireJs: undefined,
		
		jsSelect: [{
			url: 'module/plugins/jquery/jquery-1.11.1.min.js',
			name: 'jquery-1.11.1.min.js',
			selected: true,
			disabled: true
		},{
			url: 'module/plugins/vue/vue.min.js',
			name: 'vue.min.js'
		},{
			url: 'module/plugins/utils/_.js',
			name: '_.js',
			selected: true
		},{
			url: 'module/plugins/utils/Array.js',
			name: 'Array.js'
		},{
			url: 'module/plugins/utils/Date.js',
			name: 'Date.js'
		},{
			url: 'module/plugins/utils/String.js',
			name: 'String.js'
		},{
			url: 'module/plugins/utils/Logger.js',
			name: 'Logger.js',
			selected: true,
		},{
			url: 'module/plugins/utils/Type.js',
			name: 'Type.js'
		},{
			url: 'module/plugins/factory/PaginatFactory.js',
			name: 'PaginatFactory.js'
		}],
	},
	inital : function() {
		this.requirJs();		


		ace.require("ace/ext/language_tools");
		this.buffer.jsEditor = ace.edit("editor");
		this.buffer.jsEditor.session.setMode("ace/mode/javascript");
		this.buffer.jsEditor.setTheme("ace/theme/monokai");
		this.buffer.jsEditor.setFontSize(16);
		this.buffer.jsEditor.resize(true);
		this.buffer.jsEditor.setOptions({
			enableLiveAutocompletion: !0
		});
		
		this.buffer.cssEditor = ace.edit("css");
		this.buffer.cssEditor.session.setMode("ace/mode/css");
		this.buffer.cssEditor.setTheme("ace/theme/tomorrow");
		this.buffer.cssEditor.setFontSize(16);
		this.buffer.cssEditor.resize(true);
		this.buffer.cssEditor.setOptions({
			enableLiveAutocompletion: !0,
			enableEmmet: !0
		});
		this.buffer.htmlEditor = ace.edit("html");
		this.buffer.htmlEditor.session.setMode("ace/mode/html");
		this.buffer.htmlEditor.setTheme("ace/theme/tomorrow_night_eighties");
		this.buffer.htmlEditor.setFontSize(16);
		this.buffer.htmlEditor.resize(true);
		this.buffer.htmlEditor.setOptions({
			enableLiveAutocompletion: !0
		});
		this.buffer.htmlEditor.setValue(utils.isStore('html', this.buffer.htmlEditor));
		this.buffer.jsEditor.setValue(utils.isStore('js', this.buffer.jsEditor));
		this.buffer.cssEditor.setValue(utils.isStore('css', this.buffer.cssEditor));
	},
	requirJs: function() {
		this.buffer.requireJs = $("#requireJs");
		this.buffer.requireJs.selectpicker('destroy');
		this.buffer.requireJs.find('option').remove().empty();
		for(var index = 0, oneData = undefined; index < this.buffer.jsSelect, oneData = this.buffer.jsSelect[index++];) {
			var _attr = "";
			if(oneData['selected']) {
				_attr += " selected "; 
			}
			if(oneData['disabled']) {
				_attr += " disabled "; 
			}
			var newId = oneData.name.replace(/\./g, '');
			this.buffer.requireJs.append('<option  id="'+ newId +'" value="'+oneData.url+'" '+_attr+'>'+oneData.name+'</option>');
		}
		
		this.buffer.requireJs.selectpicker('render');
		this.buffer.requireJs.selectpicker('refresh');
		
		this.buffer.requireJs.parent().find('li').filter(":not(.disabled )").on('click', function() {
			var isSelected = $(this).hasClass('selected');
			var id = $(this).find('.text').text();
			var newId = id.replace(/\./g, '');
			if(isSelected) {
				$('script[id='+ newId +']').remove();
			} else {
				var url = $('option[id='+newId+']').attr('value');
				if($('script[id='+ newId +']').length == 0) {
					var last = $('script').last();
					last.after('<script type="text/javascript" src="' + url + '" id="'+ newId +'"></script>');
				}
			}
		});
		
	},
	//-- 保存代码记录
	save: function(){
		utils.store.set('html', this.buffer.htmlEditor.getValue())
		utils.store.set('css', this.buffer.cssEditor.getValue())
		utils.store.set('js', this.buffer.jsEditor.getValue())
	},
	//--
	get: function(key) {
		return utils.store.get(key);
	},
	//-- 运行并跳转到result面板
	run: function(){
		var html = Api.buffer.htmlEditor.getValue();
		var css = '<style>'+ Api.buffer.cssEditor.getValue() +'</style>';
		var js = Api.buffer.jsEditor.getValue();
		
		var requireJs = "";
		
		this.buffer.requireJs.parent().find('li').filter(".selected").each(function(index, selector) {
			var id = $(selector).find('.text').text();
			var newId = id.replace(/\./g, '');
			var url = $('option[id='+newId+']').attr('value');
			requireJs += '<script type="text/javascript" src="' + url + '"></script>';
		});

		var iframe = $('iframe').contents();
		//iframe.document.getElementsByTagName('body')[0].innerHTML=(html + css + requireJs + "<script id='ext'>"+js+"</script>")
		//iframe.empty();
		
		window = document.getElementsByTagName('iframe')[0].contentWindow;
		

		iframe.find('body').empty();
		iframe.find('head').empty();

		iframe.find('head')[0].innerHTML = (css);
		document.getElementsByTagName('iframe')[0].contentWindow.document.write(
		`
		<html>
			<head>
				<script type="text/javascript" src="module/core.js" data-core></script>
				<script type="text/javascript" src="module/main.js"></script>
				<script type="text/javascript" src="module/plugins/utils/Define.js"></script>
				${requireJs}
				<script type='text/javascript'>${js}</script>
				${css}
			</head>
			<body>${html}</body>
		</html>
		`);
		
		//document.getElementsByTagName('iframe')[0].contentWindow.document.write("<body>"+ html +"</body>");
		//iframe.find('body').append("<script type='text/javascript'>"+js+"</script>");
		//window.frames['iframe'].eval(js);
		//iframe[0].eval(js);
	}
}
Api.inital();
/*var highlight = ace.require("ace/ext/static_highlight")
var dom = ace.require("ace/lib/dom")
function qsa(sel) {
    return Array.apply(null, document.querySelectorAll(sel));
}

qsa(".code").forEach(function (codeEl) {
    highlight(codeEl, {
        mode: codeEl.getAttribute("ace-mode"),
        theme: codeEl.getAttribute("ace-theme"),
        startLineNumber: 1,
        showGutter: codeEl.getAttribute("ace-gutter"),
        trim: true
    }, function (highlighted) {
        
    });
});*/
