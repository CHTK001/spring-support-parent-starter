modulejs.__config = {
	//配置基目录
	baseUrl: '',
	//清除缓存
	urlArgs: 'v=' + (new Date()).getTime(),
	//插件
	plugins: {
        "apexcharts": {
            path: 'apexcharts/apexcharts.min',
            name: '[apexcharts]图插件',
            deps: ['css!apexcharts/apexcharts.css']
        },
        "chart": {
            path: 'chart/chart.min',
            name: '[chart]图插件',
            deps: []
        },
        "visgraph": {
            path: 'visgraph/visgraph.min',
            name: '关系图插件',
            deps: []
        },
        "graph-vis": {
            path: 'graph-vis/visgraph.min',
            name: '关系图插件',
            deps: []
        },
        "leaflet": {
            path: 'leaflet/leaflet',
            name: '地图插件',
            deps: ['css!leaflet/leaflet.css']
        },
        "inotify": {
            path: 'inotify/notify.min',
            name: '通知插件',
            deps: ['notify.min']
        },
        "jquery": {
            path: 'jquery/jquery-3.3.1.min',
            name: 'Jquery插件',
            ver: {
                '3.3.1': 'jquery/jquery-3.3.1.min',
				'1.8.3': 'jquery/jquery-v1.8.3.min',
				'1.11.1': 'jquery/jquery-1.11.1.min'
			}
		},
		"jquery-crop": {
			path: 'jquery-crop/jcrop.min',
			name: 'Jquery裁剪插件',
			deps: ['jquery', 'jquery.color', 'css!crop/jcrop.css']
		},
		"jquery-customScrollba": {
			path: 'jquery-customScrollba/jquery.mCustomScrollbar.concat.min',
			name: 'Jquery滚动条插件',
			deps: ['jquery', 'css!jquery-customScrollba/jquery.mCustomScrollbar.css']
		},
		"jquery-mousewheel": {
			path: 'jquery-customScrollba/jquery.mousewheel-3.0.6.min',
			name: 'Jquery滚动插件',
			deps: ['jquery', 'css!jquery-customScrollba/jquery.mCustomScrollbar.css']
		},
		"jquery-ui": {
			path: 'jquery-ui/jquery-ui.min',
			name: 'JqueryUi插件',
			deps: ['jquery', 'css!jquery-ui/jquery-ui.min.css']
		},
		"jquery-hizoom": {
			path: 'jquery-hizoom/hizoom.min',
			name: 'Jquery缩放插件',
			deps: ['jquery', 'css!hizoom/hizoom.min.css']
		},
		"jquery-loading": {
			path: 'jquery-loading/jquery-loading',
			name: 'Jquery加载插件',
			deps: ['jquery'],
		},

		"jquery-tagsinput": {
			path: 'jquery-tagsinput/jquery.tagsinput',
			name: 'Jquery标签输入框插件',
			deps: ['jquery']
		},
		"jquery-tagsinput": {
			path: 'jquery-tagsinput/jquery.tagsinput',
			name: 'Jquery标签输入框插件',
			deps: ['jquery']
		},
		"jquery-timeline": {
			path: 'jquery.timeline/jquery.timeline.min',
			name: 'Jquery时间轴插件',
			deps: ['jquery', 'css!jquery.timeline/jquery.timeline.min.css']
		},
		"jquery-slimscroll": {
			path: 'jquery.slimscroll/jquery.slimscroll.min',
			name: 'Jquery滚动条插件',
			deps: ['jquery']
		},
		"jquery-contextmenu": {
			path: 'jquery-contextmenu/jquery.contextMenu.min',
			name: 'jquery-contextmenu滚动条插件',
			deps: ['jquery', 'css!jquery-contextmenu/jquery.contextMenu.css']
		},
		"jquery-scrollloading": {
			path: 'jquery-scrollloading/jquery-scrollloading.min',
			deps: ['jquery']
		},
		"jquery-sharrre": {
			path: 'jquery.sharrre/jquery.sharrre',
			deps: ['jquery']
		},
		"jquery-bootstrap": {
			path: 'jquery-select-bootstrap/jquery.select-bootstrap',
			name: 'select插件',
			deps: ['jquery']
		},
		'jquery-datetimepicker': {
			path: 'jquery-datetimepicker/my97DatePicker/WdatePicker.js',
			name: 'Jquery日期插件',
			deps: ['jquery', 'css!jquery-datetimepicker/my97DatePicker/skin/WdatePicker.css']
		},
		'jquery-inputlimiter': {
			path: 'jquery-inputlimiter/jquery.inputlimiter.1.3.1.min.js',
			name: 'Jquery输入限制插件',
			deps: ['jquery']
		},
		'jquery-hotkeys': {
			path: 'jquery-hotkeys/jquery.hotkeys.min',
			name: 'Jquery热键插件',
			deps: ['jquery']
		},
		'jquery-webuipopover': {
			path: 'jquery-webuipopover/jquery.webui-popover.min',
			name: 'jquery提示框',
			deps: ['jquery', 'css!jquery-webuipopover/jquery.webui-popover.min.css']
		},
		'infinite-scroll': {
			path: 'infinite-scroll/infinite-scroll.pkgd.min',
			name: '无限滚动插件'
		},
		'underscore': {
			path: 'underscore/underscore',
			name: '通用工具'
		},
		'fileinput': {
			path: 'fileinput/4.3.9/js/zh',
			deps: ['jquery', 'fileinput-core', 'css!fileinput/4.3.9/css/fileinput.min.css'],
			name: '上传插件',
		},
		'fileinput-core': {
			path: 'fileinput/4.3.9/js/fileinput.min',
			name: '上传插件',
		},
		'backbone': {
			path: 'backbone/backbone-min',
			deps: ['jquery', 'underscore'],
			name: 'MVC'
		},
		'videojs': {
			path: 'videojs/js/video.min',
			name: '视频插件',
			deps: ['css!videojs/css/video-js.min.css'],
			ver: {
				'ie8': 'videojs/js/video-ie8.min'
			}
		},
		'moment': 'moment/moment.min',
		'spin': {
			path: 'spin/spin',
			deps: ['css!spin/spin.css'],
			name: '等待条'
		},
		'store': {
			path: 'store/store.everything.min',
			name: '缓存插件'
		},
		'store-extension': {
			path: 'storeJs/store-extension.min',
			name: '缓存扩展插件',
			deps: ['store', 'pako']
		},
		'pako': {
			path: 'pako/pako.min',
			name: '数据压缩插件'
		},
		'wookmark': {
			path: 'wookmark/wookmark.min',
			name: '瀑布流插件'
		},
		'imageloaded': {
			path: 'imageloaded/imagesloaded.pkgd.min',
			name: '图片懒加载插件'
		},
		'stompjs': {
			path: 'socket/stomp.min',
			deps: ['sockjs']
		},
		'sockjs': {
			path: 'socket/sockjs.min'
		},
		'three': {
			path: 'three/three.min',
			name: '3D插件'
		},
		'less-env': {
			path: 'less/less.env',
			name: '用于支持less文件'
		},
		'icheck': {
			path: 'icheck/js/icheck.min',
			name: '多选框插件',
			deps: ['jquery', 'css!icheck/css/all.css']
		},
		'prism': {
			path: 'prism/prism',
			name: 'prism插件',
			deps: ['css!prism/prism-okaidia.css']
		},

		'html2canvas': {
			path: 'html2canvas/html2canvas',
			name: 'html转canvas'
		},

		"highlight": {
			name: 'highlight插件',
			path: 'highlight/highlight.pack',
			deps: ['css!highlight/atom-one-dark.css']
		},

		"codemirror": {
			name: 'codemirror插件',
			path: 'codemirror/codemirror',
			deps: ['css!codemirror/codemirror.css']
		},

		"beautifier": {
			name: 'beautifier插件',
			path: 'beautifier/beautifier.min'
		},

		"beautifier-html": {
			name: 'beautifier-html插件',
			path: 'beautifier/beautifier-html',
			deps: ['beautifier']
		},

		"beautifier-css": {
			name: 'beautifier-css插件',
			path: 'beautifier/beautifier-css',
			deps: ['beautifier']
		},

		'taffy': {
			path: 'taffy/taffy-min',
			name:  '集合查询',
		},
		'web-cache-factory': 'cache/web-cache-factory',
		'web-storage-cache': 'cache/web-storage-cache.min',
		'draggabilly': {
			path: 'draggabilly/draggabilly',
			name: '拖拽插件'
		},
		'loglevel': {
			path: 'loglevel/loglevel.min',
			name: '日志插件'
		},
		'masonry': {
			path: 'masonry/masonry.pkgd.min',
			name: '瀑布流插件'
		},

		'factory-loading': {
			path: 'factory/load-creator/loading-factory',
			name: '等待插件',
			deps: ['jquery', 'css!factory/load-creator/loading-factory.css']
		},
		
		'factory-drag': {
			path: 'factory/drag-creator/drag-factory',
			name: '拖拽插件',
			deps: ['bootstrap', 'draggabilly', 'css!factory/drag-creator/drag-factory.css']
		},
		
		'factory-data-creator': {
			path: 'factory/data-creator/data-creator-factory',
			name: '数据处理插件',
			deps: ['factory-loading', 'toastr', 'template', 'bootstrap-paginator', 'css!factory/data-creator/data-creator-factory.css']
		},
		'factory-paginate': {
			path: 'factory/paginate-factory',
			name: '分页插件',
			deps: ['bootstrap-paginator']
		},
		
		'go-js': {
			path: 'go-js/go'
		},
		'viewer': {
			path: 'viewer/viewer.min',
			name: '图片预览',
			deps: ['jquery', 'css!viewer/viewer.min.css']
		},
		'icheck-debug': {
			path: 'icheck/js/icheck',
			name: '多选框插件',
			deps: ['jquery', 'css!icheck/css/all.css']
		},
		'less': {
			path: 'less/less',
			name: '用于支持less文件'
		},
		'countUp': {
			path: 'countUp/countUp.min',
			name: '数字动画插件'
		},
		'toastr': {
			path: 'toastr/toastr.min',
			deps: ['jquery', 'css!toastr/toastr.min.css'],
			name: '通知插件'
		},
		'rivets': {
			path: 'rivets/rivets.bundled.min',
			name: '数据绑定'
		},
		'enjoyhint': {
			path: 'enjoyhint/jquery.enjoyhint',
			deps: ['jquery', 'css!enjoyhint/jquery.enjoyhint.css'],
			name: '引导插件'
		},
		'datatables': {
			path: 'datatables/datatables.min',
			deps: ['bootstrap', 'css!datatables/datatables.min.css'],
			name: '数据表'
		},
		'minimap': {
			path: 'minimap/minimap.min',
			deps: ['jquery', 'css!minimap/minimap.min.css'],
			name: '小地图'
		},
		'g6': {
			path: 'g6/g6.min',
			name: '关系图插件'
		},
		'graphin': {
			path: 'graphin/graphin.min',
			deps: ['g6'],
			name: '关系图插件'
		},
		'g2': {
			path: 'g2/g2.min',
			name: '图表插件'
		},
		'g2plot': {
			path: 'g2plot/g2plot',
			deps: ['g2'],
			name: '图表插件'
		},
		'dropzone': {
			path: 'dropzone/js/dropzone.min',
			deps: ['css!dropzone/css/basic.min.css', 'css!dropzone/css/dropzone.min.css'],
			name: '上传插件'
		},
		'json2': {
			path: 'json2/json2',
			name: 'json转化插件'
		},
		'waterfall': {
			path: 'waterfall/waterfall',
			deps: ['jquery', 'css!waterfall/waterfall.css']
		},
		//json格式化
		'jsonview': {
			path: 'json/jquery-jsonview.min',
			deps: ['css!json/jquery.jsonview.min.css'],
			name: 'json格式化'
		},
		//json格式化
		'sqlview': {
			path: 'sql/sql-formatter.min',
			name: 'sql格式化'
		},

		"bootstrap": {
			name: 'bootstrap插件',
			path: 'bootstrap/bootstrap-4.6.2/js/bootstrap.min',
			deps: ['jquery', 'css!bootstrap/bootstrap-4.6.2/css/bootstrap.min.css']
		},
		'bootstro': {
			name: '步骤插件',
			path: 'bootstro/bootstro.min',
			deps: ['css!bootstro/bootstro.min.css']
		},
		"bootstrap-select": {
			name: 'bootstrapSelect插件',
			path: 'bootstrap-select/bootstrap-select',
			deps: ['bootstrap', 'css!bootstrap-select/bootstrap-select.css']
		},

		"bootstrap-colorpicker": {
			name: 'bootstrapColorpicker插件',
			path: 'bootstrap-colorpicker/js/bootstrap-colorpicker.min',
			deps: ['bootstrap', 'css!bootstrap-colorpicker/css/bootstrap-colorpicker.min.css']
		},

		"bootstrap-multiselect": {
			name: 'bootstrapMultiselect插件',
			path: 'bootstrap-multiselect/bootstrap-multiselect',
			deps: ['bootstrap', 'css!bootstrap-multiselect/bootstrap-multiselect.css']
		},
		'bootstrap-waterfall': {
			name: 'bootstrap瀑布流插件',
			path: 'bootstrap-waterfall/bootstrap-waterfall',
			deps: ['bootstrap']
		},
		"bootstrap-datetimepicker": {
			name: 'bootstrapDatetimepicker插件',
			path: 'bootstrap-datetimepicker/bootstrap-datetimepicker',
			deps: ['bootstrap']
		},
		
		"bootstrap-slider": {
			name: 'bootstrap滑块插件',
			path: 'bootstrap-slider/js/bootstrap-slider.min',
			deps: ['bootstrap', 'css!bootstrap-slider/css/bootstrap-slider.min.css']
		},

		'bootstrap-treetable': {
			path: 'bootstrap-treetable/bootstrap-treetable',
			name: '树形表格',
			deps: ['bootstrap']
		},

		"bootstrap-paginator": {
			name: 'bootstrapPaginator插件',
			path: 'bootstrap-paginator/js/bootstrap-paginator.min',
			deps: ['bootstrap']
		},

		"bootstrap-contextmenu": {
			name: 'bootstrapContextmenu插件',
			path: 'bootstrap-contextmenu/bootstrap-contextmenu',
			deps: ['bootstrap']
		},
		
		"bootstrap-editable": {
			name: 'bootstrap可编辑插件',
			path: 'bootstrap-editable/js/bootstrap-editable.min',
			deps: ['bootstrap', 'css!bootstrap-editable/css/bootstrap-editable.css']
		},
		
		"bootstrap-maxlength": {
			name: 'bootstrap最大长度插件',
			path: 'bootstrap-maxlength/bootstrap-maxlength.min',
			deps: ['bootstrap']
		},
		
		"bootstrap-smartwizard": {
			name: 'bootstrap导航插件',
			path: 'bootstrap-smartwizard/js/jquery.smartWizard.min',
			deps: ['bootstrap', 'css!bootstrap-smartwizard/css/smart_wizard.min.css']
		},


		"bootstrap-smartwizard-arrows": {
			name: 'bootstrap导航插件',
			path: 'bootstrap-smartwizard/js/jquery.smartWizard.min',
			deps: ['bootstrap', 'css!bootstrap-smartwizard/css/smart_wizard_theme_arrows.min.css']
		},

		"bootstrap-smartwizard-circles": {
			name: 'bootstrap导航插件',
			path: 'bootstrap-smartwizard/js/jquery.smartWizard.min',
			deps: ['bootstrap', 'css!bootstrap-smartwizard/css/smart_wizard_theme_circles.min.css']
		},

		"bootstrap-smartwizard-dots": {
			name: 'bootstrap导航插件',
			path: 'bootstrap-smartwizard/js/jquery.smartWizard.min',
			deps: ['bootstrap', 'css!bootstrap-smartwizard/css/smart_wizard_theme_dots.min.css']
		},

		"bootstrap-notify": {
			name: 'bootstrapNotify插件',
			path: 'bootstrap-notify/bootstrap-notify',
			deps: ['bootstrap']
		},
		
		"bootstrap-switch": {
			name: '按钮切换插件',
			path: 'bootstrap-switch/js/bootstrap-switch.min',
			deps: ['bootstrap', 'css!bootstrap-switch/css/bootstrap3/bootstrap-switch.min.css']
		},
		
		
		"bootstrap-typeahead": {
			name: '自动补全',
			path: 'bootstrap-typeahead/bootstrap3-typeahead.min',
			deps: ['bootstrap']
		},

		"bootstrap-table": {
			name: 'bootstrapTable数据表格插件',
			path: 'bootstrap-table/bootstrap-table-locale-all.min',
			deps: ['bootstrap-table-locale-all', 'css!bootstrap-table/bootstrap-table.min.css']
		},

		"bootstrap-table-editor": {
			name: 'bootstrapTable数据表格编辑插件',
			path: 'bootstrap-table-editor/bootstrap-table-editor',
			deps: ['bootstrap-table']
		},

		"bootstrap-table-locale-all": {
			name: 'bootstrapTable数据表格插件',
			path: 'bootstrap-table/bootstrap-table.min',
			deps: ['bootstrap']
		},

		'imagehover': {
			path: 'imagehover/imagehover',
			deps: ['css!imagehover/imagehover.css', 'imagehover.charming', 'imagehover.imagesloaded.pkgd',
				'imagehover.tweenMax'
			],
			name: '图片浮动显示'
		},
		//弹出框
		'layx': {
			path: 'layx/layx.min',
			deps: ['css!layx/layx.css'],
			name: '弹出框'
		},

		'popper': {
			path: 'popper/popper.min',
			name: 'popper插件'
		},

		'select2': {
			path: 'select2/select2.min',
			deps: ['css!select2/select2.css'],
			name: 'select2'
		},
		'layui': {
			path: 'layui/layui.all',
			name: 'layui',
			deps: ['css!layui/css/layui.css']
		},
		'template': {
			path: 'template/template-web',
			name: '模板引擎'
		},
		'd3': {
			path: 'd3/d3.v3',
			ver: {
                '3': 'd3/d3.v3',
                '4': 'd3/d3.v4.min',
                '5': 'd3/d3.v5.min',
                '6': 'd3/d3.v6.min',
                '7': 'd3/d3.v7.min'
            }
		},
		'moment': 'moment/moment.min',
		'daterangepicker': {
			name: '时间插件',
			path: 'daterangepicker/daterangepicker',
			deps: ['bootstrap', 'moment', 'css!daterangepicker/daterangepicker.css']
		},
		'echarts': {
			path: 'echarts/2/echarts',
			ver: {
				'2': 'echarts/2/echarts',
				'3': 'echarts/3/echarts'
			}
		},
		'css': {
			path: 'core/css'
		},
		'log4j': {
			path: 'log4j/log4j'
		},
		'animate': {
			path: 'css!style/animate/animate.min.css',
			name: '动画插件'
		},
		'taffy': {
			path: 'taffy/taffy-min',
			name: '集合检索插件'
		},

		'utils-city': {
			path: 'utils/CityUtils',
			name: '身份证信息工具'
		},
		'utils': {
			path: 'utils/Utils',
			namae: '通用工具类'
		},
		'utils.define': {
			path: 'utils/Define',
			name: '将requirejs, seajs中的define转为同步加载的方法'
		}
	},
	/**
	 * 全局参数(无依赖)
	 */
	map: {
		'*': {
			'css': 'css',
			'store': 'store',
			'loglevel': 'loglevel'
		}
	}
}
modulejs.completeConfig();
