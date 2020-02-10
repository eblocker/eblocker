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
var webpack = require('webpack'),
    path = require('path'),
    UglifyJSPlugin = require('uglifyjs-webpack-plugin');
// var CommonsChunkPlugin = require('webpack-vendor-chunk-plugin');


module.exports = function(module, isDev) {

    var srcPath  = path.join(__dirname, '/src/' + module + '/app/'),
        distPath = path.join(__dirname, '/build/js');

    var plugins = [];

    // plugins.push(new webpack.optimize.CommonsChunkPlugin({
    //     name: 'vendor',
    //     minChunks: function (module) {
    //         // this assumes your vendor imports exist in the node_modules directory
    //         return module.context && module.context.includes('node_modules');
    //     }
    // }));

    if (!isDev) {
        plugins.push(new UglifyJSPlugin({
            sourceMap: false,
            compress: true
        }));
    }

    // plugins.push('transform-es2015-arrow-functions');

    var config = {
        watch: false,
        cache: true,
        devtool: '#eval', // #cheap-module-eval-source-map
        // context: srcPath,
        // FIXME: edit here to rename files to module name
        entry: {
            app: module === 'controlbar' || module === 'settings' ?
                ['babel-polyfill', srcPath + module + '.module.js'] : ['babel-polyfill', srcPath + 'app.module.js'],
        },
        output: {
            // path: distPath,
            filename: '[name].js',
        },

        // babel appears to be not need .. maybe its default?
        module: {
            loaders: [
                {
                    loader: 'babel-loader',

                    // Skip any files outside of your project's `src` directory
                    include: [
                        path.resolve(__dirname, 'src'),
                    ],

                    // Only run `.js` and `.jsx` files through Babel
                    test: /\.jsx?$/,

                    // Options to configure babel with
                    query: {
                        presets: ['env'],
                        plugins: [
                            'transform-es2015-block-scoping',
                            'transform-es2015-arrow-functions',
                            'angularjs-annotate'
                        ]
                    }
                },
            ],
            rules: [
                {
                    test: /\.jsx?$/,
                    include: [
                        path.resolve(__dirname, '/src/' + module + '/app/')
                    ],
                    // these are matching conditions, each accepting a regular expression or string
                    // test and include have the same behavior, both must be matched
                    // exclude must not be matched (takes preferrence over test and include)
                    // Best practices:
                    // - Use RegExp only in test and for filename matching
                    // - Use arrays of absolute paths in include and exclude
                    // - Try to avoid exclude and prefer include

                    // issuer: { test, include },
                    // conditions for the issuer (the origin of the import)

                    enforce: 'pre',
                    enforce: 'post',// jshint ignore:line
                    // flags to apply these rules, even if they are overridden (advanced option)
                    use: {
                        loader: 'babel-loader',
                        // the loader which should be applied, it'll be resolved relative to the context
                        // -loader suffix is no longer optional in webpack2 for clarity reasons
                        // see webpack 1 upgrade guide
                        options: {
                            // presets: ['es2015']
                            // presets: [
                            //     ['env', {
                            //         targets: {
                            //             browsers: ['last 2 versions', 'safari >= 5']
                            //         }
                            //     }]
                            // ]
                            sourceMap: false,
                            presets: ['env'],
                            plugins: [
                                'transform-es2015-block-scoping',
                                'transform-es2015-arrow-functions',
                                'angularjs-annotate'
                            ]
                        }
                    }
                }
            ]
        },

        resolve: {
            modules: ['node_modules'],
        },
        plugins: plugins
    };
    return config;
};
