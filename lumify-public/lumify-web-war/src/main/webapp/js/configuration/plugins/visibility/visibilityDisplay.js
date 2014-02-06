
define([
    'flight/lib/component',
    'tpl!./display'
], function(
    defineComponent,
    displayTemplate) {
    'use strict';

    return defineComponent(VisibilityDisplay);

    function VisibilityDisplay() {
        this.after('initialize', function() {
            this.$node.html(displayTemplate({
                value: this.attr.value || ''
            }));
        });
    }
});
