(function() {
	/**
	 * 监控对象
	 */
	var monitor = function(obj, objName, property, callback) {
		if(!obj || !property) {
			return ;
		}
		var pName = objName || obj.constructor.name;
		console.groupCollapsed(pName + "::[" + property + "]数据变化监听", obj)
		try{
			Object.defineProperty(obj, property, {
				enumerable: true,
				configurable: true,
				set: function(value) {
					if(!!callback && !!callback['set']) {
						value = callback['set'](value);
					}
					console.log('检测到['+ pName +'::'+ property +']数据变化' + value);
					name = value;
					console.groupEnd();
				},
				get: function() {
					return name;
				}
			});
		}catch(e){
			console.groupEnd();
		}
		
	}
	
	window.monitorObject = {
		monitor: monitor
	}
})();