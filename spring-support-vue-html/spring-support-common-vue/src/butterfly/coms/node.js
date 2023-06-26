import {Node} from 'butterfly-dag';
import $ from 'jquery';
import '@/assets/node.less';
import 'jquery-contextmenu'
import 'jquery-contextmenu/dist/jquery.contextMenu.min.css'

class BaseNode extends Node {
    constructor(opts) {
        super(opts);
        this.options = opts;
    }

    draw = (opts) => {
        let container = $(`<div class="policy-base-node policy-base-node-${this.options.userData ? this.options.userData.value || this.options.value : this.options.value}"></div>`)
            .attr('id', opts.id)
            .css('top', opts.top + 'px')
            .css('left', opts.left + 'px');

        this._createTypeIcon(container);
        this._createText(container);
        if(this.options.menus) {
            $.contextMenu({
                selector: `.policy-base-node-${this.options.userData ? this.options.userData.value || this.options.value : this.options.value}`,
                items: this.options.menus
            });
        }
        return container[0];
    }

    _createTypeIcon(dom = this.dom) {
        const iconContainer = $(`<span class="icon-box ${this.options.className}"></span>`)[0];
        const icon = $(`<i class="iconfont ${this.options.iconType}"></i>`)[0];

        iconContainer.append(icon);
        $(dom).append(iconContainer);
    }

    _createText(dom = this.dom) {
        $('<span class="text-box"></span>').text(this.options.label).appendTo(dom);
    }
}

export default BaseNode;