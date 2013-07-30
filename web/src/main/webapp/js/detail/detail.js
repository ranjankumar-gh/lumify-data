
define([
    'flight/lib/component',
    './dropdowns/termForm/termForm',
    './dropdowns/statementForm/statementForm',
    './artifact/image/image',
    'service/ucd',
    'util/video/scrubber',
    'underscore',
    'tpl!./artifactDetails',
    'tpl!./entityDetails',
    'tpl!./entityToEntityRelationshipDetails',
    'tpl!./entityToEntityRelationshipExcerpts',
    'tpl!./multipleSelection',
    'tpl!./entityDetailsMentions',
    'tpl!./style'
], function(
    defineComponent,
    TermForm,
    StatementForm,
    Image,
    UCD,
    VideoScrubber,
    _,
    artifactDetailsTemplate,
    entityDetailsTemplate,
    entityToEntityRelationshipDetailsTemplate,
    entityToEntityRelationshipExcerptsTemplate,
    multipleSelectionTemplate,
    entityDetailsMentionsTemplate,
    styleTemplate) {
    'use strict';


    var HIGHLIGHT_STYLES = [
            { name: 'None' },
            { name: 'Subtle Icons', cls:'icons' },
            { name: 'Underline', cls:'underline' },
            { name: 'Colors', cls:'colors' }
        ],
        DEFAULT = 2;

    return defineComponent(Detail);

    function Detail() {

        this.useDefaultStyle = true;

        this.defaultAttrs({
            mapCoordinatesSelector: '.map-coordinates',
            highlightTypeSelector: '.highlight-options a',
            entitiesSelector: '.entity',
            artifactsSelector: '.artifact',
            moreMentionsSelector: '.mention-request',
            mentionArtifactSelector: '.mention-artifact',
            previewSelector: '.preview',
            detectedObjectSelector: '.detected-object',
            imagePreviewSelector: '.image-preview',
            entityToEntityRelationshipSelector: '.entity-to-entity-relationship a.relationship-summary',
            entityDetailsMentionsSelector: '.entity-mentions'
        });

        this.after('initialize', function() {
            this.on('click', {
                mapCoordinatesSelector: this.onMapCoordinatesClicked,
                highlightTypeSelector: this.onHighlightTypeClicked,
                moreMentionsSelector: this.onRequestMoreMentions,
                mentionArtifactSelector: this.onMentionArtifactSelected,
                detectedObjectSelector: this.onDetectedObjectClicked,
                entitiesSelector: this.onEntityClicked,
                entityToEntityRelationshipSelector: this.onEntityToEntityRelationshipClicked
            });

            this.preventDropEventsFromPropagating();

            this.on('scrollstop', this.updateEntityAndArtifactDraggables);
            this.on(document, 'termCreated', this.updateEntityAndArtifactDraggables);
            this.on(document, 'searchResultSelected', this.onSearchResultSelected);
            this.on(document, 'loadRelatedSelected', this.onLoadRelatedSelected);

            this.$node.on('mouseenter mouseleave', '.detected-object', this.onDetectedObjectHover.bind(this));

            $(document).on('selectionchange', this.onSelectionChange.bind(this));
        });

        // Ignore drop events so they don't propagate to the graph/map
        this.preventDropEventsFromPropagating = function() {
            this.$node.droppable({ accept: '.entity,.term' });
        };

        this.onSelectionChange = function(e) {
            var selection = window.getSelection(),
                text = selection.type === 'Range' ? $.trim(selection.toString()) : '';

            // Ignore selection events within the dropdown
            if ( selection.type == 'None' || 
                 $(selection.anchorNode).is('.underneath') ||
                 $(selection.anchorNode).parents('.underneath').length ||
                 $(selection.focusNode).is('.underneath') ||
                 $(selection.focusNode).parents('.underneath').length) {
                return;
            }

            // Ignore if selection hasn't change
            if (text.length && text === this.previousSelection) {
                return;
            } else this.previousSelection = text;

            // Remove all dropdowns if empty selection
            if (selection.isCollapsed || text.length === 0) {
                TermForm.teardownAll();
                StatementForm.teardownAll();
            }

            this.handleSelectionChange();
        };

        this.onDetectedObjectClicked = function(event) {
            console.log('Clicked', event);
        };

        this.onDetectedObjectHover = function(event) {
            if (event.type == 'mouseenter') {
                this.trigger(document, 'DetectedObjectEnter', $(event.target).data('info'));
            } else {
                this.trigger(document, 'DetectedObjectLeave', $(event.target).data('info'));
            }
        };

        this.handleSelectionChange = _.debounce(function() {
            TermForm.teardownAll();

            var sel = window.getSelection(),
                text = sel && sel.type === 'Range' ? $.trim(sel.toString()) : '';

            if (text && text.length) {
                var anchor = $(sel.anchorNode),
                    focus = $(sel.focusNode),
                    is = '.detail-pane .text';
                
                // Ignore outside content text
                if (anchor.parents(is).length === 0 || focus.parents(is).length === 0) {
                    return;
                }

                // Ignore if too long of selection
                var wordLength = text.split(/\s+/).length;
                if (wordLength > 10) {
                    return;
                }

                // Find which way the selection was travelling (which is the
                // furthest element in document
                var end = focus, endOffset = sel.focusOffset;
                if (text.indexOf(anchor[0].textContent.substring(sel.anchorOffset, 1)) > 
                    text.indexOf(focus[0].textContent.substring(sel.focusOffset, 1))) {
                    end = anchor;
                    endOffset = sel.anchorOffset;
                }

                // Move to first space in end so as to not break up word when
                // splitting
                var i = Math.max(endOffset - 1, 0), character = '', whitespaceCheck = /^[^\s]$/;
                do {
                    character = end[0].textContent.substring(++i, i+1);
                } while (whitespaceCheck.test(character));

                end[0].splitText(i);
                this.dropdownEntity(end, sel, text);
            }
        }, 500);

        this.onEntityClicked = function(event) {
            var $target = $(event.target);
            if ($target.is('.underneath') || $target.parents('.underneath').length) {
                return;
            }
            _.defer(this.dropdownEntity.bind(this), $target);
        };

        this.dropdownEntity = function(insertAfterNode, sel, text) {
            TermForm.teardownAll();
            StatementForm.teardownAll();

            var form = $('<div class="underneath"/>');
            insertAfterNode.after(form);
            TermForm.attachTo(form, {
                sign: text,
                selection: sel && { anchor:sel.anchorNode, focus:sel.focusNode, anchorOffset: sel.anchorOffset, focusOffset: sel.focusOffset },
                mentionNode: insertAfterNode,
                artifactKey: this.currentRowKey
            });
        };

        this.onEntityToEntityRelationshipClicked = function(evt, data) {
            var self = this;
            var $target = $(evt.target).parents('li');
            var statementRowKey = $target.data('statement-row-key');

            if($target.hasClass('expanded')) {
                $target.removeClass('expanded');
                $target.addClass('collapsed');
                $('.artifact-excerpts', $target).hide();
            } else {
                $target.addClass('expanded');
                $target.removeClass('collapsed');

                new UCD().getStatementByRowKey(statementRowKey, function(err, statement) {
                    if(err) {
                        console.error('Error', err);
                        return self.trigger(document, 'error', { message: err.toString() });
                    }

                    var statementMentions = Object.keys(statement)
                        .filter(function(key) {
                            return key.indexOf('urn') == 0;
                        })
                        .map(function(key) {
                            return statement[key];
                        });
                        console.log(statement);
                        console.log(statementMentions);
                    var html = entityToEntityRelationshipExcerptsTemplate({
                        mentions: statementMentions
                    });
                    $('.artifact-excerpts', $target).html(html);
                    $('.artifact-excerpts', $target).show();
                    self.updateEntityAndArtifactDraggables();
                });
            }
        };

        this.onMapCoordinatesClicked = function(evt, data) {
            evt.preventDefault();
            var $target = $(evt.target);
            data = {
                latitude: $target.attr('latitude'),
                longitude: $target.attr('longitude')
            };
            this.trigger('mapCenter', data);
        };

        this.onHighlightTypeClicked = function(evt) {
            var target = $(evt.target),
                li = target.parents('li'),
                ul = li.parent('ul'),
                content = ul.parents('.content');

            ul.find('.checked').not(li).removeClass('checked');
            li.addClass('checked');

            this.removeHighlightClasses();
            
            var newClass = li.data('cls');
            if (newClass) {
                content.addClass('highlight-' + newClass);
            }

            this.useDefaultStyle = false;
        };


        this.getActiveStyle = function() {
            if (this.useDefaultStyle) {
                return DEFAULT;
            }

            var content = this.$node,
                index = 0;
            $.each( content.attr('class').split(/\s+/), function(_, item) {
                var match = item.match(/^highlight-(.+)$/);
                if (match) {
                    return HIGHLIGHT_STYLES.forEach(function(style, i) {
                        if (style.cls === match[1]) {
                            index = i;
                            return false;
                        }
                    });
                }
            });

            return index;
        };

        this.removeHighlightClasses = function() {
            var content = this.$node;
            $.each( content.attr('class').split(/\s+/), function(index, item) {
                if (item.match(/^highlight-(.+)$/)) {
                    content.removeClass(item);
                }
            });
        };

        this.onSearchResultSelected = function(evt, data) {
            if ($.isArray(data) && data.length === 1) {
                data = data[0];
            }

            self.currentArtifact = null;

            if ( !data || data.length === 0 ) {
                this.$node.empty();
                this.currentRowKey = null;
            } else if($.isArray(data)) {
                this.$node.html(multipleSelectionTemplate({nodes:data}));
                this.currentRowKey = null;
            } else if(data.type == 'artifact') {
                this.onArtifactSelected(evt, data);
            } else if(data.type == 'entity') {
                this.onEntitySelected(evt, data);
            } else if(data.type == 'relationship') {
                this.onRelationshipSelected(evt, data);
            } else {
                var message = 'Unhandled type: ' + data.type;
                console.error(message);
                return this.trigger(document, 'error', { message: message });
            }
        };

       this.onLoadRelatedSelected = function (evt, data){
            if ($.isArray (data) && data.length == 1){
                data = data [0];
            }
            
            if (!data || data.length == 0){
                this.$node.empty ();
                this.currentRowKey = null;
            } else if (data.type == 'entity') {
                this.onLoadRelatedEntitySelected (evt, data);
            } else if (data.type == 'artifact'){
                this.onLoadRelatedArtifactSelected (evt, data);
            } else {
                var message = 'Unhandled type: ' + data.type;
                console.error (message);
                return this.trigger (document, 'error', { message: message });
            }
       };

        this.onLoadRelatedEntitySelected = function (evt, data){
            var self = this;
            new UCD ().getEntityById (data.rowKey, function (err, entity){
                if (err){
                    console.error ('Error', err);
                    return self.trigger (document, 'error', { message: err.toString () });
                }

                self.loadRelatedEntities (data.rowKey, function (relatedEntities){
                    var entityData = {};
                    entityData.key = entity.key;
                    entityData.relatedEntities = relatedEntities;
                    self.onLoadRelatedItems (data, entityData.relatedEntities);
                });
            });
        };

        this.onLoadRelatedArtifactSelected = function (evt, data){
            var self = this;
            new UCD ().getArtifactById (data.rowKey, function (err, artifact){
                if (err){
                    console.error ('Error', err);
                    return self.trigger (document, 'error', { message: err.toString () });
                }

                self.loadRelatedTerms (data.rowKey, function (relatedTerms){
                    var termData = {};
                    termData.key = artifact.key;
                    termData.relatedTerms = relatedTerms;
                    self.onLoadRelatedItems (data, termData.relatedTerms);
                });
            });
        };

        this.onLoadRelatedItems = function (originalData, nodes){
            var xOffset = 100, yOffset = 100;
            var x = originalData.originalPosition.x;
            var y = originalData.originalPosition.y;
            this.trigger (document, 'addNodes', {
                nodes: nodes.map (function (relatedItem, index){
                    if (index % 10 === 0) {
                        y += yOffset;
                    }
                    return {
                        title: relatedItem.title,
                        rowKey: relatedItem.rowKey,
                        subType: relatedItem.subType,
                        type: relatedItem.type,
                        graphPosition: {
                            x: x + xOffset * (index % 10 + 1),
                            y: y
                        },
                        selected: true
                    };
                })
            });
        };

        this.loadRelatedEntities = function (key, callback){
            var self = this;
            new UCD().getRelatedEntitiesBySubject (key, function (err, relatedEntities){
                if (err){
                    console.error ('Error', err);
                    return self.trigger (document, 'error', { message: err.toString () });
                }
                console.log ('Related Entities', relatedEntities);
                callback (relatedEntities);
            });
        };

        this.loadRelatedTerms = function (key, callback){
            var self = this;
            new UCD().getRelatedTermsFromArtifact (key, function (err, relatedTerms){
                if (err){
                    console.error ('Error', err);
                    return self.trigger (document, 'error', { message: err.toString() });
                }
                console.log ('Related Terms', relatedTerms);
                callback (relatedTerms);
            });
        }

        this.onMentionArtifactSelected = function(evt, data) {
            var $target = $(evt.target).parents('a');

            this.trigger(document, 'searchResultSelected', {
                type: 'artifact',
                rowKey: $target.data("row-key")
            });
            evt.preventDefault();
        }

        this.onRelationshipSelected = function(evt, data) {
            this.openUnlessAlreadyOpen(data, function(finished) {
                var self = this;
                if(data.relationshipType == 'artifactToEntity') {
                    finished(true);
                    this.trigger(document, 'searchResultSelected', {
                        type: 'artifact',
                        rowKey: data.source,
                        entityOfInterest: data.target
                    });
                } else if(data.relationshipType == 'entityToEntity') {
                    new UCD().getEntityToEntityRelationshipDetails(data.source, data.target, function(err, relationshipData) {
                        finished(!err);

                        if(err) {
                            console.error('Error', err);
                            return self.trigger(document, 'error', { message: err.toString() });
                        }
                        console.log(relationshipData);
                        relationshipData.styleHtml = self.getStyleHtml();
                        self.$node.html(entityToEntityRelationshipDetailsTemplate(relationshipData));

                        self.applyHighlightStyle();
                        self.updateEntityAndArtifactDraggables();
                    });
                } else {
                    finished(false);
                    self.$node.html("Bad relationship type:" + data.relationshipType);
                }
            });
        };

        this.onArtifactSelected = function(evt, data) {
            this.openUnlessAlreadyOpen(data, function(finished) {
                var self = this;
                new UCD().getArtifactById(data.rowKey, function(err, artifact) {
                    finished(!err);

                    if(err) {
                        console.error('Error', err);
                        return self.trigger(document, 'error', { message: err.toString() });
                    }

                    self.currentArtifact = artifact;
                    console.log('Showing artifact:', artifact);
                    artifact.contentHtml = artifact.Content.highlighted_text || artifact.Content.doc_extracted_text || "";
                    artifact.contentHtml = artifact.contentHtml.replace(/[\n]+/g, "<br><br>\n");
                    var styleHtml = self.getStyleHtml();
                    self.$node.html(artifactDetailsTemplate({ artifact: artifact, styleHtml: styleHtml }));

                    if (artifact.type === 'video') {
                        self.setupVideo(artifact);
                    } else if (artifact.type === 'image') {
                        self.setupImage(artifact);
                    }

                    console.log('TODO: add some extra highlighting and scroll to this entity row key', data.entityOfInterest);

                    self.applyHighlightStyle();
                    self.updateEntityAndArtifactDraggables();
                });
            });
        };

        this.getStyleHtml = function() {
            return styleTemplate({ styles: HIGHLIGHT_STYLES, activeStyle: this.getActiveStyle() });
        };

        this.onEntitySelected = function(evt, data) {
            this.openUnlessAlreadyOpen(data, function(finished) {
                var self = this;

                console.log(data);

                new UCD().getEntityById(data.rowKey, function(err, entity) {
                    finished(!err);

                    if(err) {
                        console.error('Error', err);
                        return self.trigger(document, 'error', { message: err.toString() });
                    }

                    var offset = 0;
                    var limit = 2; // change later
                    var url = 'entity/' + encodeURIComponent(data.rowKey).replace(/\./g, '$2E$') + '/mentions?offset=' + offset + '&limit=' + limit;
                    var dataInfo = JSON.stringify({
                        'rowKey': entity.key.value,
                        'type': 'entity',
                        'subType': entity.key.conceptLabel
                    });

                    self.getMoreMentions(url, entity.key, dataInfo, function(mentionsHtml) {
                        var entityDetailsData = {};
                        entityDetailsData.key = entity.key;

                        self.getRelationships(data.rowKey, function(relationships) {
                            entityDetailsData.relationships = relationships.statements;
                            console.log('Showing entity:', entityDetailsData);
                            entityDetailsData.styleHtml = self.getStyleHtml();
                            self.$node.html(entityDetailsTemplate(entityDetailsData));
                            $('.entity-mentions', self.$node).html(mentionsHtml);
                            self.applyHighlightStyle();
                            self.updateEntityAndArtifactDraggables();
                        });
                    });

                });

            });
        };

        this.onRequestMoreMentions = function(evt, data) {
            var self = this;
            var $target = $(evt.target);
            data = {
                key: $target.data('key'),
                url: $target.attr("href")
            };

            var dataInfo = JSON.stringify({
                'rowKey': data.key.value,
                'type': 'entity',
                'subType': data.key.conceptLabel
            });

            this.getMoreMentions(data.url, data.key, dataInfo, function(mentionsHtml){
                $('.entity-mentions', self.$node).html(mentionsHtml);
                self.applyHighlightStyle();
                self.updateEntityAndArtifactDraggables();
            });
            evt.preventDefault();
        };

        this.getMoreMentions = function(url, key, dataInfo, callback) {
            new UCD().getEntityMentionsByRange(url, function(err, mentions){
                if(err) {
                    console.error('Error', err);
                    return self.trigger(document, 'error', { message: err.toString() });
                }

                mentions.mentions.forEach(function(termMention) {
                    var originalSentenceText = termMention['atc:sentenceText'];
                    var originalSentenceTextParts = {};

                    var artifactTermMention = JSON.parse(termMention.mention);

                    var termMentionStart = artifactTermMention.start - parseInt(termMention['atc:sentenceOffset'], 10);
                    var termMentionEnd = artifactTermMention.end - parseInt(termMention['atc:sentenceOffset'], 10);

                    termMention.sentenceTextParts = {
                        before: originalSentenceText.substring(0, termMentionStart),
                        term: originalSentenceText.substring(termMentionStart, termMentionEnd),
                        after: originalSentenceText.substring(termMentionEnd)
                    }
                });
                var html = entityDetailsMentionsTemplate({
                    mentions: mentions.mentions,
                    limit: mentions.limit,
                    offset: mentions.offset,
                    key: key,
                    dataInfo: dataInfo
                });
                console.log("Mentions: ", mentions);
                callback(html);
            });
        };

        this.getRelationships = function(rowKey, callback) {
            var self = this;
            new UCD().getEntityRelationshipsBySubject(encodeURIComponent(rowKey).replace(/\./g, '$2E$'), function(err, relationships) {
                if(err) {
                    console.error('Error', err);
                    return self.trigger(document, 'error', { message: err.toString() });
                }

                console.log("Relationships: ", relationships);
                callback(relationships);
            });
        };

        this.openUnlessAlreadyOpen = function(data, callback) {
            if (this.currentRowKey === data.rowKey) {
                return;
            }

            this.$node.html("Loading...");

            callback.call(this, function(success) {
                this.currentRowKey = success ? data.rowKey : null;
            }.bind(this));
        };


        this.updateEntityAndArtifactDraggables = function() {
            var self = this,
                entities = this.select('entitiesSelector'),
                terms = this.select('termsSelector'),
                artifacts = this.select('artifactsSelector');

            entities.add(artifacts).add(terms)
                // Filter list to those in visible scroll area
                .withinScrollable(this.$node)
                .draggable({
                    helper:'clone',
                    revert: 'invalid',
                    revertDuration: 250,
                    scroll: false,
                    zIndex: 100,
                    distance: 10,
                    start: function() {
                        $(this)
                            .parents('.sentence').addClass('focused')
                            .parents('.text').addClass('focus');
                    },
                    stop: function() {
                        $(this)
                            .parents('.sentence').removeClass('focused')
                            .parents('.text').removeClass('focus');
                    }
                })
                .droppable({
                    activeClass: 'drop-target',
                    hoverClass: 'drop-hover',
                    accept: function(el) {
                        var item = $(el),
                            isTerm = item.is('.term'),
                            sameSentence = isTerm && $(this).closest('.sentence').is(item.closest('.sentence'));
                        return isTerm && sameSentence;
                    },
                    drop: function(event, ui) {
                        var destTerm = $(this),
                            form = $('<div class="underneath"/>');

                        destTerm.after(form);
                        StatementForm.attachTo(form, {
                            sourceTerm: ui.helper,
                            destTerm: destTerm
                        });
                    }
                });
        };

        this.setupVideo = function(artifact) {
            VideoScrubber.attachTo(this.select('previewSelector'), {
                rawUrl: artifact.rawUrl,
                posterFrameUrl: artifact.posterFrameUrl,
                videoPreviewImageUrl: artifact.videoPreviewImageUrl,
                allowPlayback: true
            });
        };

        this.setupImage = function(artifact) {
            Image.attachTo(this.select('imagePreviewSelector'), {
                src: artifact.rawUrl
            });
        };

        this.applyHighlightStyle = function() {
            var newClass = HIGHLIGHT_STYLES[this.getActiveStyle()].cls;
            if (newClass) {
                this.removeHighlightClasses();
                this.$node.addClass('highlight-' + newClass);
            }
        };

    }
});
