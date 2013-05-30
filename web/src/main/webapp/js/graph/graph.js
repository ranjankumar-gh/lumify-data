

define([
    'flight/lib/component',
    'cytoscape',
    'tpl!./graph'
], function(defineComponent, cytoscape, template) {
    'use strict';

    return defineComponent(Graph);

    function Graph() {
        var cy = null;

        this.defaultAttrs({
            cytoscapeContainerSelector: '.cytoscape-container',
            emptyGraphSelector: '.empty-graph'
        });

        this.addNode = function(title, info, position) {
            var node = {
                group:'nodes',
                position: position,
            };

            node.data = info;
            node.data.id = info.rowKey;
            node.data.title = title;

            cy.add(node);

            this.select('emptyGraphSelector').hide();
        };

        this.graphSelect = function(event) {
            // TODO: multiple selection is two different events
            this.trigger(document, 'searchResultSelected', event.cyTarget.data());
        };
        this.graphUnselect = function(event) {
            // TODO: send empty event? needs detail to support
        };

        this.after('initialize', function() {
            this.$node.html(template({}));

            this.$node.droppable({
                drop: function( event, ui ) {
                    var draggable = ui.draggable,
                        droppableOffset = $(event.target).offset(),
                        text = draggable.text();

                    if (text.length > 10) {
                        text = text.substring(0, 10) + "...";
                    }

                    this.addNode(text, draggable.parents('li').data('info'), {
                        x: event.clientX - droppableOffset.left,
                        y: event.clientY - droppableOffset.top
                    });
                }.bind(this)
            });

            var that = this;
            cytoscape({
                showOverlay: false,
                minZoom: 0.5,
                maxZoom: 2,
                container: this.select('cytoscapeContainerSelector').css({height:'100%'})[0],
                style: cytoscape.stylesheet()
                  .selector('node')
                    .css({
                      'content': 'data(title)',
                      'font-family': 'helvetica',
                      'font-size': 14,
                      'text-outline-width': 3,
                      'text-outline-color': '#888',
                      'text-valign': 'center',
                      'color': '#fff',
                      'width': 'mapData(weight, 30, 80, 20, 50)',
                      'height': 'mapData(height, 0, 200, 10, 45)',
                      'border-color': '#fff'
                    })
                  .selector(':selected')
                    .css({
                      'background-color': '#000',
                      'line-color': '#000',
                      'target-arrow-color': '#000',
                      'text-outline-color': '#000'
                    })
                  .selector('edge')
                    .css({
                      'width': 2,
                      'target-arrow-shape': 'triangle'
                    }),

                ready: function(){
                    cy = this;

                    cy.on({
                        select: that.graphSelect.bind(that),
                        unselect: that.graphUnselect.bind(that)
                    });
                }
            });
        });
    }

});

