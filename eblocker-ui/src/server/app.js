/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
/*jshint node:true*/
'use strict';

var express = require('express');
var app = express();
var bodyParser = require('body-parser');
var favicon = require('serve-favicon');
var logger = require('morgan');
var port = process.env.PORT || 8001;
var four0four = require('./utils/404')();
var routes = require('./routes');
var environment = process.env.NODE_ENV;
var redirectRestApi = (process.env.REDIRECT_REST_API === 'true');
var restApi = process.env.REST_API;

app.use(favicon(__dirname + '/favicon.ico'));
app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());
app.use(logger('dev'));

app.use('/api', getRestApi('./routes'));

function getRestApi(mockServer) {
    if (redirectRestApi) {
        return function(request, response) {
            response.redirect(301, restApi + request.originalUrl);
        };
    } else {
        return require(mockServer);
    }
}
console.log('About to crank up node');
console.log('PORT = ' + port);
console.log('NODE_ENV = ' + environment);
console.log('REST_API = ' + (redirectRestApi ? process.env.REST_API : 'Using internal mock server'));

switch (environment) {
    case 'build':
        console.log('** BUILD **');

        app.use(express.static('./build'));

        // Any invalid calls for templateUrls are under app/* and should return 404
        app.use('/app/*', function(req, res, next) {
            console.log('Return 404 --> ');
            four0four.send404(req, res);
        });
        // Any deep link calls should return index.html
        app.use('/*', express.static('../build/index.html'));
        break;

    default:
        console.log('** DEV **');

        // Serves the main SPA modules and the shared modules
        app.use(express.static('./build/'));

        // Serves to bower components
        app.use(express.static('./'));


        //app.use(express.static('./src/sample/'));
        //app.use(express.static('./'));
        //app.use(express.static('./tmp'));
        // Any invalid calls for templateUrls are under app/* and should return 404
        app.use('/app/*', function(req, res, next) {
            four0four.send404(req, res);
        });
        // Any deep link calls should return index.html
        app.use('/*', function(req, res, next) {
            four0four.send404(req, res);
        });
        //app.use('/*', express.static('./src/sample/index.html'));
        break;
}

app.listen(port, function() {
    console.log('Express server listening on port ' + port);
    console.log('env = ' + app.get('env') +
        '\n__dirname = ' + __dirname +
        '\nprocess.cwd = ' + process.cwd());
});
