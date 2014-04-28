define([], function() {
    'use strict';

    return withFileDrop;

    function withFileDrop() {

        this.after('initialize', function() {
            var self = this;

            if (!this.handleFilesDropped) {
                return console.warn('Implement handleFilesDropped');
            }

            this.node.ondragover = function() {
                $(this).addClass('file-hover'); return false;
            };
            this.node.ondragenter = function() {
                $(this).addClass('file-hover'); return false;
            };
            this.node.ondragleave = function() {
                $(this).removeClass('file-hover'); return false;
            };
            this.node.ondrop = function(e) {
                if (e.dataTransfer && e.dataTransfer.files) {
                    e.preventDefault();
                    e.stopPropagation();

                    if (self.$node.hasClass('uploading')) return;

                    self.handleFilesDropped(e.dataTransfer.files, event);
                }
            };
        });
    }
});
