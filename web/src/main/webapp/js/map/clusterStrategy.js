
define(['openlayers'], function(OpenLayers) {

    return OpenLayers.Class(OpenLayers.Strategy.Cluster, {
        activate: function() {
            var activated = OpenLayers.Strategy.prototype.activate.call(this);
            if(activated) {
                this.selectedFeatures = {};
                this.layer.events.on({
                    "beforefeaturesadded": this.cacheFeatures,
                    "featuresremoved": this.removeFeatures,
                    "featureselected": this.onFeaturesSelected,
                    "featureunselected": this.onFeaturesUnselected,
                    "moveend": this.cluster,
                    scope: this
                });
            }
            return activated;
        },

        onFeaturesSelected: function(event) {
            var feature = event.feature,
                sf = this.selectedFeatures = {};

            (feature.cluster || [feature]).forEach(function(f) {
                sf[f.id] = true;
            });
        },

        onFeaturesUnselected: function(event) {
            if (!this.selectedFeatures) return;

            var sf = this.selectedFeatures;

            if (event.feature) {
                delete sf[event.feature.id];
                if (event.feature.cluster) {
                    event.feature.cluster.forEach(function(f) {
                        delete sf[f.id];
                    });
                }
            } else {
                this.selectedFeatures = {};
            }
        },

        addToCluster: function(cluster, feature) {
            OpenLayers.Strategy.Cluster.prototype.addToCluster.apply(this, arguments);
            if (this.selectedFeatures[feature.id]) {
                cluster.renderIntent = 'select';
            }
        },

        cluster: function(event) {
            OpenLayers.Strategy.Cluster.prototype.cluster.apply(this, arguments);

            var needsRedraw = false;
            if (this.clusters) {
                var selectedIds = _.keys(this.selectedFeatures);
                this.clusters.forEach(function(feature) {
                    if (feature.cluster) {
                        var some = false, all = true;
                        feature.cluster.forEach(function(f) {
                            var selected = ~selectedIds.indexOf(f.id);
                            some = some || selected;
                            all = all && selected;
                        });

                        if (all) {
                            if (feature.renderIntent !== 'select') {
                                feature.renderIntent = 'select';
                                needsRedraw = true;
                            }
                        } else if (some) {
                            if (feature.renderIntent !== 'temporary') {
                                feature.renderIntent = 'temporary';
                                needsRedraw = true;
                            }
                        } else {
                            if (feature.renderIntent && feature.renderIntent !== 'default') {
                                feature.renderIntent = 'default';
                                needsRedraw = true;
                            }
                        }
                    }
                });
            }

            if (event && event.object) {
                var zoom = event.object.map.zoom;
                if (!this._lastZoom || zoom !== this._lastZoom) {
                    needsRedraw = true;
                    this._lastZoom = zoom;
                }
            }

            if (!this._throttledRedraw) {
                this._throttledRedraw = _.debounce(function() {
                    this.layer.redraw();
                }.bind(this), 250);
            }

            if (needsRedraw) {
                this._throttledRedraw();
            }
        },

        createCluster: function(feature) {
            var cluster = OpenLayers.Strategy.Cluster.prototype.createCluster.apply(this, arguments);
            if (this.selectedFeatures[feature.id]) {
                cluster.renderIntent = 'select';
            } else cluster.renderIntent = 'default';
            return cluster;
        },

        cacheFeatures: function(event) {
            var propagate = true;
            if(!this.clustering) {
                this.features = this.features || [];
                var currentIds = [];
                this.features.forEach(function gatherId(feature) {
                    if (feature.cluster) {
                        feature.cluster.forEach(gatherId);
                        return;
                    }
                    currentIds.push(feature.id);
                });
                event.features.forEach(function(feature) {
                    if (! ~currentIds.indexOf(feature.id)) {
                        this.features.push(feature);
                    }
                }.bind(this));
                this.cluster();
                propagate = false;
            }
            return propagate;
        },

        removeFeatures: function(event) {
            if(!this.clustering) {
                var existingIds = _.pluck(event.features, 'id');
                this.features = _.filter(this.features, function(feature) {
                    return ! ~existingIds.indexOf(feature.id);
                });
            }
        }
    });
});
