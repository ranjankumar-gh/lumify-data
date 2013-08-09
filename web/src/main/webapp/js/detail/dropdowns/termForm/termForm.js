

define([
    'flight/lib/component',
    '../withDropdown',
    'tpl!./termForm',
    'tpl!./concept-options',
    'service/ucd',
    'service/entity',
    'underscore'
], function(defineComponent, withDropdown, dropdownTemplate, conceptsTemplate, Ucd, EntityService, _) {
    'use strict';

    return defineComponent(TermForm, withDropdown);


    function TermForm() {
        this.entityService = new EntityService();
        this.ucd = new Ucd();

        this.defaultAttrs({
            entityConceptMenuSelector: '.underneath .dropdown-menu a',
            createTermButtonSelector: '.create-term',
            signSelector: '.sign',
            objectSignSelector: '.object-sign',
            conceptSelector: 'select'
        });

        this.after('teardown', function() {
            if (this.promoted && this.promoted.length) {
                this.demoteSpanToTextNode(this.promoted);
            }
            
            var info = $(this.attr.mentionNode).removeClass('focused').data('info');
            if (info) {
                this.updateConceptLabel(info.subType);
            }
            
            // Remove extra textNodes
            this.node.parentNode.normalize();
        });

        this.after('initialize', function() {
            this.setupContent();
            this.registerEvents();
        });

        this.onCreateTermClicked = function(event) {
            var self = this,
                $mentionNode = $(this.attr.mentionNode),
                sentence = this.$node.parents('.sentence'),
                sentenceInfo = sentence.data('info'),
                sign = this.select('signSelector').text(),
                newObjectSign = $.trim(this.select('objectSignSelector').val()),
                mentionStart = sentenceInfo.start + sentence.text().indexOf(sign),
                parameters = {
                    sign: sign,
                    conceptLabel: this.select('conceptSelector').val(),
                    artifactKey: this.attr.artifactKey,
                    mentionStart: mentionStart,
                    mentionEnd: mentionStart + sign.length
                };

            if ( !parameters.conceptLabel || parameters.conceptLabel.length === 0) {
                this.select('conceptSelector').focus();
                return;
            }

            if (this.graphNodeId) {
                parameters.graphNodeId = this.graphNodeId;
            }

            if (newObjectSign.length) {
                parameters.objectSign = newObjectSign;
                $mentionNode.attr('title', newObjectSign);
            }

            $mentionNode.addClass('resolved');
console.log(parameters);
            this.entityService.createTerm(parameters, function(err, data) {
                if (err) {
                    self.trigger(document, 'error', err);
                } else {
                    self.highlightTerm(data);
                    console.log(data);
                    self.trigger(document, 'termCreated', data);
                    _.defer(self.teardown.bind(self));
                }
            });
        };

        this.onConceptChanged = function(event) {
            var select = $(event.target);
            
            this.updateConceptLabel(select.val());
        };

        this.updateConceptLabel = function(conceptLabel) {
            if (this.allConcepts && this.allConcepts.length) {

                var node = $(this.promoted || this.attr.mentionNode),
                    labels = this.allConcepts.map(function(c) { 
                        return c.conceptLabel; 
                    });

                node.removeClass(labels.join(' '))
                    .addClass(conceptLabel);
            }
        };

        this.setupContent = function() {
            var self = this,
                node = this.$node,
                mentionNode = $(this.attr.mentionNode),
                sign = this.attr.sign || mentionNode.text(),
                data = mentionNode.data('info'),
                title = $.trim(data && data.title || ''),
                existingEntity = mentionNode.addClass('focused').hasClass('entity'),
                objectSign = '';

            this.graphNodeId = data && data.graphNodeId;

            if (this.attr.selection && !existingEntity) {
                this.promoted = this.promoteSelectionToSpan();
            }

            var titleMatchesSign = new RegExp("^" + sign + "$", "i").test(title);
            if (!titleMatchesSign && title.length) {
                objectSign = title;
            }

            node.html(dropdownTemplate({
                sign: sign,
                objectSign: objectSign || '',
                buttonText: existingEntity ? 'Update' : 'Create'
            }));
        };

        this.registerEvents = function() {

            this.on('opened', function() {
                this.setupObjectTypeAhead();
                this.loadConcepts();
            });

            this.on('change', {
                conceptSelector: this.onConceptChanged
            });

            this.on('click', {
                entityConceptMenuSelector: this.onEntityConceptSelected,
                createTermButtonSelector: this.onCreateTermClicked
            });
        };

        this.loadConcepts = function() {
            var self = this;
            self.allConcepts = [];
            self.entityService.concepts(function(err, concepts) {
                var mentionNode = $(self.attr.mentionNode),
                    mentionNodeInfo = mentionNode.data('info');

                self.allConcepts = concepts;
                self.select('conceptSelector').html(conceptsTemplate({
                    concepts:concepts,
                    selectedConceptLabel:mentionNodeInfo && mentionNodeInfo.subType || ''
                }));
            });
        };

        this.setupObjectTypeAhead = function() {
            var self = this;

            self.select('objectSignSelector').typeahead({
                source: function(query, callback) {
                    self.ucd.entitySearch(query.toLowerCase(), function(err, entities) {
                        if(err) {
                            console.error('Error', err);
                            callback([]);
                            return self.trigger(document, 'error', { message: err.toString() });
                        }

                        // Convert dictionary map with type keys into flat
                        // array
                        var types = Object.keys(entities);
                        var entityArrays = types.map(function(type) { return entities[type]; });
                        var all = Array.prototype.concat.apply([], entityArrays);

                        callback(all.map(function(e) {
                            return e.sign;
                        }));
                    });
                    return;
                }
            });
        };

        this.highlightTerm = function(data) {
            var mentionNode = $(this.attr.mentionNode),
                updatingEntity = mentionNode.is('.entity');


            if (updatingEntity) {

                mentionNode.data('info', data.info);
                // TODO: remove classes and reapply

            } else if (this.promoted) {

                this.promoted.data('info', data.info)
                             .addClass(data.cssClasses.join(' '))
                             .removeClass('focused');
                this.promoted = null;
            }
        };

        this.promoteSelectionToSpan = function() {
            var textNode = this.node,
                range = this.attr.selection.range;

            range.startContainer.splitText(range.startOffset);
            if (range.endOffset < range.endContainer.textContent.length) {
                range.endContainer.splitText(range.endOffset);
            }

            // TODO: handle case where selection includes existing entity
            while (textNode && textNode.textContent !== this.attr.sign) {
                textNode = textNode.previousSibling;
            }
            if (!textNode) return;

            var span = $('<span>').text(textNode.textContent)
                              .addClass('entity focused')
                              .insertBefore(textNode);

            textNode.parentNode.removeChild(textNode);
            return span;
        };

        this.demoteSpanToTextNode = function(node) {
            var textNode = document.createTextNode(node.text());

            node.parent().get(0).insertBefore(textNode, node[0]);
            node.remove();
        };
    }
});
