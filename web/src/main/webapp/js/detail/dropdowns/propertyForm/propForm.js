
define([
    'flight/lib/component',
    '../withDropdown',
    'tpl!./propForm',
    'tpl!./options'
], function(defineComponent, withDropdown, template, options) {
    'use strict';

    return defineComponent(PropertyForm, withDropdown);

    function PropertyForm() {

        this.defaultAttrs({
            propertySelector: 'select',
            propertyValueSelector: '.property-value',
            addPropertySelector: '.add-property'
        });

        this.after('initialize', function() {
            var self = this,
                vertex = this.attr.data;

            this.on('click', {
                addPropertySelector: this.onAddPropertyClicked
            });

            this.on('keyup', {
                propertyValueSelector: this.onInputKeyUp
            });

            this.on('addPropertyError', this.onAddPropertyError);

            this.$node.html(template({}));

            if (vertex.properties._subType){
                self.attr.service.propertiesByConceptId(vertex.properties._subType)
                    .done(function(properties) {
                        var propertiesList = [];

                        properties.properties.forEach (function (property){
                            if (property.title.charAt(0) !== '_') {
                                var data = {
                                    title: property.title,
                                    displayName: property.displayName
                                };
                                propertiesList.push (data);
                            }
                        });

                        self.select('propertySelector').html(options({
                            properties: propertiesList || ''
                        }));
                    });
            } else {
                self.attr.service.propertiesByRelationshipLabel(vertex.properties.relationshipLabel)
                    .done(function(properties){
                        var propertiesList = [];

                        properties.list.forEach (function (property){
                            if (property.title.charAt(0) != '_'){
                                var data = {
                                    title: property.title,
                                    displayName: property.displayName
                                };
                                propertiesList.push (data);
                            }
                        });

                        self.select('propertySelector').html(options({
                            properties: propertiesList || ''
                        }));
                    });
            }

        });

        this.onInputKeyUp = function (event) {
            switch (event.which) {
                case $.ui.keyCode.ENTER:
                    this.onAddPropertyClicked(event);
            }
        };

        this.onAddPropertyError = function(event) {
            this.select('propertyValueSelector').addClass('validation-error');
        };

        this.onAddPropertyClicked = function (evt){
            var vertexId = this.attr.data.id,
                propertyName = this.select('propertySelector').val(),
                value = $.trim(this.select('propertyValueSelector').val());

            this.select('propertyValueSelector').removeClass('validation-error');
            if (propertyName.length && value.length) {
                this.trigger('addProperty', { 
                    property: {
                        name: propertyName,
                        value: value
                    }
                });
            }
        };
    }
});
