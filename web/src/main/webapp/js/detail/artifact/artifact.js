
define([
    'flight/lib/component',
    'util/video/scrubber',
    './image/image',
    '../withTypeContent',
    '../withHighlighting',
    'detail/dropdowns/entityForm/entityForm',
    'tpl!./artifact'
], function(defineComponent, VideoScrubber, Image, withTypeContent, withHighlighting, EntityForm, template) {

    'use strict';

    return defineComponent(Artifact, withTypeContent, withHighlighting);

    function Artifact() {

        this.defaultAttrs({
            previewSelector: '.preview',
            imagePreviewSelector: '.image-preview',
            detectedObjectSelector: '.detected-object'
        });

        this.after('initialize', function() {
            var self = this;

            this.on('click', {
                detectedObjectSelector: this.onDetectedObjectClicked
            });
            this.$node.on('mouseenter mouseleave', '.detected-object', this.onDetectedObjectHover.bind(this));

            this.loadArtifact();
        });

        this.loadArtifact = function() {
            var self = this;

            this.handleCancelling(this.ucdService.getArtifactById(this.attr.data._rowKey || this.attr.data.rowKey, function(err, artifact) {
                if(err) {
                    console.error('Error', err);
                    return self.trigger(document, 'error', { message: err.toString() });
                }

                artifact.dataInfo = JSON.stringify({
                    _type: 'artifact',
                    _subType: artifact.type,
                    graphVertexId: artifact.Generic_Metadata['atc:graph_vertex_id'],
                    _rowKey: artifact.key.value
                });

                self.$node.html(template({ artifact: self.setupContentHtml(artifact), highlightButton:self.highlightButton() }));

                if (self[artifact.type + 'Setup']) {
                    self[artifact.type + 'Setup'](artifact);
                }

            }));
        };

        this.onDetectedObjectClicked = function(event) {
            var root = $('<div class="underneath">').insertAfter('.detected-object-labels');

            EntityForm.attachTo(root, {
                sign: '',
                artifactData: this.attr.data,
                coords: $(event.target).data('tag').coords,
                detectedObjectRowKey: $(event.target).data('tag')._rowKey
            });
        };

        this.onDetectedObjectHover = function(event) {
            if (event.type == 'mouseenter') {
                this.trigger(document, 'DetectedObjectEnter', $(event.target).data('tag'));
            } else {
                this.trigger(document, 'DetectedObjectLeave', $(event.target).data('tag'));
            }
        };

        this.setupContentHtml = function(artifact) {
            artifact.contentHtml = (artifact.Content.highlighted_text || artifact.Content.doc_extracted_text || "")
                    .replace(/[\n]+/g, "<br><br>\n");
            return artifact;
        };


        this.videoSetup = function(artifact) {
            VideoScrubber.attachTo(this.select('previewSelector'), {
                rawUrl: artifact.rawUrl,
                posterFrameUrl: artifact.posterFrameUrl,
                videoPreviewImageUrl: artifact.videoPreviewImageUrl,
                allowPlayback: true
            });
        };

        this.imageSetup = function(artifact) {
            Image.attachTo(this.select('imagePreviewSelector'), {
                src: artifact.rawUrl
            });
        };
    }
});
