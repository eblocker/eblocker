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
let istanbul = require('browserify-istanbul');
require('babel-core');

module.exports = function(config) {
    let gulpConfig = require('./gulp.config')();

    let src = gulpConfig.src;
    let temp = gulpConfig.temp;
    let report = gulpConfig.report;

    let karmaConf = {
        // base path that will be used to resolve all patterns (eg. files, exclude)
        basePath: gulpConfig.karmaWorkingDir,

        // frameworks to use
        // some available frameworks: https://npmjs.org/browse/keyword/karma-adapter
        frameworks: ['jasmine', 'browserify'],

        // list of files / patterns to load in the browser
        files: [
            src + '**/*.module.js',
            src + '**/*.spec.js',
            src + '**/*.html',
            temp + '*.js' // template caches
        ],

        // list of files to exclude
        exclude: [
            src + 'server/' + '**/*.js',
            src + 'index.html'
        ],

        proxies: {
            '/': 'http://localhost:8888/'
        },

        // preprocess matching files before serving them to the browser
        // available preprocessors: https://npmjs.org/browse/keyword/karma-preprocessor
        preprocessors: {},

        // transform from ES6 to ES5
        browserify: {
            debug: true,
            extensions: ['.js'],
            transform: [
                ['babelify', {
                        presets: ['env'],
                        plugins: ['angularjs-annotate']
                    }
                ]
                // ,
                // // Issue: https://github.com/karma-runner/karma-coverage/issues/157
                // istanbul({
                //     instrumenterConfig: {embedSource: true},
                //     // ignore: ['**/*.spec.js', 'npm_integrations/**/*.js', '.tmp/*.*'],
                //     ignore: ['**/*.spec.js', '**/npm_integrations/**', '**/.tmp/**',
                //         '**/build/**', '**/dep/**', '**/node/**', '**/report/**',
                //         '**/target/**'],
                //     defaultIgnore: true // node_modules will be ignored by default
                // })
                // [
                //     'browserify-istanbul', {
                //         instrumenterConfig: {embedSource: true},
                //         ignore: ['**/*.spec.js', '**/npm_integrations/**/*.js'],
                //         defaultIgnore: true // node_modules will be ignored by default
                //     }
                // ]
            ]
        },

        // test results reporter to use
        // possible values: 'dots', 'progress', 'coverage'
        // available reporters: https://npmjs.org/browse/keyword/karma-reporter
        reporters: ['progress', 'coverage', 'sonarqubeUnit'],

        sonarQubeUnitReporter: {
            sonarQubeVersion: 'LATEST',
            outputFile: report + 'unit/ut_report.xml',
            useBrowserName: false
        },

        coverageReporter: {
            dir: report + 'coverage',
            includeAllSources: true,
            istanbul: { noCompact: true },
            reporters: [
                // reporters not supporting the `file` property
                { type: 'html', subdir: 'reportbr-html', includeAllSources: true},
                { type: 'lcov', subdir: 'report-lcov', includeAllSources: true },
                { type: 'text-summary' } //, subdir: '.', file: 'text-summary.txt'}
            ]
        },

        // web server port
        port: 9876,

        // enable / disable colors in the output (reporters and logs)
        colors: true,

        // level of logging
        // possible values: config.LOG_DISABLE || config.LOG_ERROR ||
        // config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
        logLevel: config.LOG_INFO, // setting to LOG_DISABLE fixes issue "unknown property 'text' of undefined"

        // enable / disable watching file and executing tests whenever any file changes
        autoWatch: false,

        // If browser does not capture in given timeout [ms], kill it
        captureTimeout: 60000,

        // start these browsers
        // available browser launchers: https://npmjs.org/browse/keyword/karma-launcher
        //        browsers: ['Chrome', 'ChromeCanary', 'FirefoxAurora', 'Safari', 'PhantomJS'],
        browsers: ['PhantomJS'],

        // Continuous Integration mode
        // if true, Karma captures browsers, runs the tests and exits
        singleRun: true,

        failOnEmptyTestSuite: false
    };

    // karmaConf.preprocessors[src + '**/*.js'] = 'babel';
    karmaConf.preprocessors[src + '**/!(*.spec)+(.js)'] = 'coverage';
    karmaConf.preprocessors[src + '**/*.js'] = ['browserify'];


    config.set(karmaConf);
};
