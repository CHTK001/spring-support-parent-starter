module: 
	bootstrap-select

html:
	<select class="selectpicker" id="requireJs" title="不限"  data-size="10" data-live-search="true" multiple>
		<option value="jquery">jquery/jquery-1.11.1.min.js</option>
	</select>

js:
	this.buffer.requireJs = $("#requireJs");
	this.buffer.requireJs.selectpicker('render');//, { 'showSubtext': true }
	this.buffer.requireJs.selectpicker('refresh');