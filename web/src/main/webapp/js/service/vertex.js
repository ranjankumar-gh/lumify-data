define(
    [
        'service/serviceBase'
    ],
    function (ServiceBase) {
        function VertexService() {
            ServiceBase.call(this);
            return this;
        }

        VertexService.prototype = Object.create(ServiceBase.prototype);

        VertexService.prototype.setProperty = function (vertexId, propertyName, value, callback) {
            this._ajaxPost({
                url: 'vertex/' + vertexId + '/property/set',
                data: {
                    propertyName: propertyName,
                    value: value
                }
            }, function (err, response) {
                callback(err, response);
            });
        };

        return VertexService;
    });

