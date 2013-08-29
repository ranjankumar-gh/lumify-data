
define(['service/ucd'], function(UCD) {

    return withTypeContent;

    function withTypeContent() {

        this.ucdService = new UCD();
        this._xhrs = [];

        this.after('teardown', function() {
            this.cancel();
            this.$node.empty();
        });

        this.before('initialize', function() {
            this.$node.html('Loading...');
        });

        this.classesForVertex = function(vertex) {
            var cls = [],
                props = vertex.properties || vertex;

            if (props._type == 'artifact') {
                cls.push('artifact');
                cls.push(props._subType);
            } else {
                cls.push('entity');
                cls.push('subType-' + props._subType);
            }
            cls.push('gId-' + (vertex.id || props.graphNodeId));

            return cls.join(' ');
        };

        this.cancel = function() {
            this._xhrs.forEach(function(xhr) {
                if (xhr.state() !== 'complete') {
                    xhr.abort();
                }
            });
            this._xhrs.length = 0;
        };

        // Pass a started XHR request to automatically cancel if detail pane
        // changes
        this.handleCancelling = function(xhr) {
            this._xhrs.push(xhr);
        };
    }
});
