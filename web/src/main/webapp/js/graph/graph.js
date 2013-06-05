

define([
    'flight/lib/component',
    'cytoscape',
    'service/workspace',
    'service/ucd',
    'tpl!./graph',
    'util/undoManager'
], function(defineComponent, cytoscape, WorkspaceService, UcdService, template, undoManager) {
    'use strict';

    return defineComponent(Graph);

    function Graph() {
        var WORKSPACE_SAVE_TIMEOUT = 1000;
        this.workspaceService = new WorkspaceService();
        this.ucdService = new UcdService();
        var cy = null;

        this.defaultAttrs({
            cytoscapeContainerSelector: '.cytoscape-container',
            emptyGraphSelector: '.empty-graph',
            graphToolsSelector: '.ui-cytoscape-panzoom'
        });

        this.addNode = function(title, info, position) {
            var node = {
                group:'nodes',
                renderedPosition: position,
            };

            node.data = info;
            node.data.id = info.rowKey;

            if (title.length > 10) {
                title = title.substring(0, 10) + "...";
            }
            node.data.title = title;

            var cyNode = cy.add(node);

            undoManager.performedAction( 'Add node: ' + title, {
                undo: function() {
                    cyNode.remove();
                    this.setWorkspaceDirty();
                },
                redo: function() {
                    cyNode.restore();
                    //node.renderedPosition = undefined;
                    //node.position = cyNode.position();
                    //cyNode = cy.add(node);
                    this.setWorkspaceDirty();
                    this.refreshRelationships();
                },
                bind: this
            });

            this.select('emptyGraphSelector').hide();
            this.setWorkspaceDirty();
            this.refreshRelationships();
        };

        this.removeSelectedNodes = function() {
            var nodes = cy.nodes().filter(':selected').remove();

            undoManager.performedAction( 'Delete ' + nodes.length + ' nodes', {
                undo: function() {
                    nodes.restore();
                    this.refreshRelationships();
                    this.setWorkspaceDirty();
                },
                redo: function() {
                    nodes.remove();
                    this.setWorkspaceDirty();
                },
                bind: this
            });
            this.setWorkspaceDirty();
        };

        this.onAddToGraph = function(event, data) {
            var el = $(event.target),
                p = el.offset(),
                c = this.$node.offset(),
                position = {
                    x: p.left - c.left + el.width() / 2.0, 
                    y: p.top - c.top + el.height() / 2.0
                };

            this.addNode(data.text, data.info, position); 
        };

        this.graphSelect = function(event) {
            // TODO: multiple selection is two different events
            this.trigger(document, 'searchResultSelected', event.cyTarget.data());
        };
        this.graphUnselect = function(event) {
            // TODO: send empty event? needs detail to support
        };

        this.onKeyHandler = function(event) {
            var down = event.type === 'keydown',
                up = !down,
                handled = true;

            switch (event.which) {

                case $.ui.keyCode.BACKSPACE:
                case $.ui.keyCode.BACKSPACE:
                    if ( down ) {
                        this.removeSelectedNodes();
                    }
                    break;

                default:
                    handled = false;
            }

            if (handled) {
                event.preventDefault();
                event.stopPropagation();
            }
        };

        this.graphDrag = function(event) { };

        this.graphGrab = function(event) {
            cy.nodes().filter(':selected,:grabbed').each(function() {
                var p = this.position();
                this.data('originalPosition', { x:p.x, y:p.y });
            });
            this.freecache = {};
        };

        this.graphFree = function(event) {

            var pos = event.cyTarget.position(),
                // Clone position so it doesn't change underneath us
                p = { x:pos.x, y:pos.y },
                target = event.cyTarget,
                key = event.timeStamp + target.data('id') + p.x + ',' + p.y;

            // CY is sending multiple "free" events, prevent that...
            if (this.freecache[key]) {
                return;
            }
            this.freecache[key] = true;

            var originalPosition = target.data('originalPosition'),
                dx = p.x - originalPosition.x,
                dy = p.y - originalPosition.y,
                distance = Math.sqrt(dx * dx + dy * dy);

            // If the user didn't drag more than a few pixels, select the
            // object, it could be an accidental mouse move
            if (distance < 5) {
                target.select();
            }

            undoManager.performedAction( 'Move node: ' + target.data('title'), {
                undo: function() {
                    //console.log(target.data('title'), '->', originalPosition);
                    target.position(originalPosition);
                    this.setWorkspaceDirty();
                },
                redo: function() {
                    //console.log(target.data('title'), '->', p);
                    target.position(p);
                    this.setWorkspaceDirty();
                },
                bind: this
            });

            this.setWorkspaceDirty();
        };

        this.setWorkspaceDirty = function() {
            if(this.saveWorkspaceTimeout) {
                clearTimeout(this.saveWorkspaceTimeout);
            }
            this.saveWorkspaceTimeout = setTimeout(this.saveWorkspace.bind(this), WORKSPACE_SAVE_TIMEOUT);
        };

        this.saveWorkspace = function() {
            var $this = this;
            var saveFn;
            var data = this.getGraphData();
            if($this.workspaceRowKey) {
                saveFn = $this.workspaceService.save.bind($this.workspaceService, $this.workspaceRowKey);
            } else {
                saveFn = $this.workspaceService.saveNew.bind($this.workspaceService);
            }
            $this.trigger(document, 'workspaceSaving', data);
            saveFn(data, function(err, data) {
                if(err) {
                    console.error('Error', err);
                    return $this.trigger(document, 'error', { message: err.toString() });
                }
                $this.trigger(document, 'workspaceSaved', data);
            });
        };

        this.getEntityIds = function() {
            return this.getGraphData().nodes
                .filter(function(node) {
                    return node.data.type == 'entities';
                })
                .map(function(node) {
                    return node.data.rowKey;
                });
        };

        this.getArtifactIds = function() {
            return this.getGraphData().nodes
                .filter(function(node) {
                    return node.data.type == 'artifacts';
                })
                .map(function(node) {
                    return node.data.rowKey;
                });
        };

        this.refreshRelationships = function() {
            var entityIds = this.getEntityIds();
            var artifactIds = this.getArtifactIds();
            this.ucdService.getRelationships(entityIds, artifactIds, function(err, relationships) {
                if(err) {
                    console.error('Error', err);
                    return $this.trigger(document, 'error', { message: err.toString() });
                }
                cy.edges().remove();
                relationships.forEach(function(relationship) {
                    cy.add({
                        group: "edges",
                        data: {
                            id: relationship.from + "->" + relationship.to,
                            source: relationship.from,
                            target: relationship.to,
                            type: 'relationship'
                        }
                    });
                });
            });
        };

        this.getGraphData = function() {
            return {
                nodes: cy.json().elements.nodes
            };
        };

        this.after('initialize', function() {
            var $this = this;
            this.$node.html(template({}));

            this.$node.droppable({
                drop: function( event, ui ) {
                    var draggable = ui.draggable,
                        droppableOffset = $(event.target).offset(),
                        text = draggable.text();

                    this.addNode(text, draggable.parents('li').data('info'), {
                        x: event.clientX - droppableOffset.left,
                        y: event.clientY - droppableOffset.top
                    });
                }.bind(this)
            });

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
                      'text-outline-width': 2,
                      'text-outline-color': 'white',
                      'text-valign': 'bottom',
                      'color': '#999'
                    })
                  .selector(':selected')
                    .css({
                      'background-color': '#0088cc',
                      'line-color': '#000',
                      'color': '#0088cc'
                    })
                  .selector('edge')
                    .css({
                      'width': 2,
                      'target-arrow-shape': 'triangle'
                    }),

                ready: function(){
                    cy = this;

                    var container = cy.container(),
                        options = cy.options();

                    $(container).cytoscapePanzoom({
                        minZoom: options.minZoom,
                        maxZoom: options.maxZoom
                    }).focus().on({
                        click: function() { this.focus(); },
                        keydown: $this.onKeyHandler.bind($this),
                        keyup: $this.onKeyHandler.bind($this)
                    });

                    var panZoom = $this.select('graphToolsSelector');
                    $this.on(document, 'detailPaneResize', function(e, data) {
                        panZoom.css({
                            right: data.width + 'px'
                        });
                    });
                    $this.on(document, 'addToGraph', $this.onAddToGraph);

                    cy.on({
                        select: $this.graphSelect.bind($this),
                        unselect: $this.graphUnselect.bind($this),
                        grab: $this.graphGrab.bind($this),
                        free: $this.graphFree.bind($this),
                        drag: $this.graphDrag.bind($this)
                    });

                    $this.workspaceService.getIds(function(err, ids) {
                        if(err) {
                            console.error('Error', err);
                            return $this.trigger(document, 'error', { message: err.toString() });
                        }
                        if(ids.length === 0) {
                            $this.workspaceRowKey = null;
                        } else {
                            $this.workspaceRowKey = ids[0]; // TODO handle more workspaces
                            $this.workspaceService.getByRowKey($this.workspaceRowKey, function(err, data) {
                                if(err) {
                                    console.error('Error', err);
                                    return $this.trigger(document, 'error', { message: err.toString() });
                                }
                                console.log('cy.load', data.data.nodes);
                                $this.select('emptyGraphSelector').hide();

                                // TODO for some reason cy.load doesn't work here.
                                data.data.nodes.forEach(function(node) {
                                    cy.add(node);
                                });
                                $this.refreshRelationships();
                            });
                        }
                    });
                }
            });
        });
    }

});

