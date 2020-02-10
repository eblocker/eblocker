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
module.exports = function() {
    var modules = {
        'dashboard': {
            'appName': 'eblocker.app',
            'excludeShared': [
                //'security/'
            ],
            'excludeBower': [
            ]
        },
        'setup': {
            'appName': 'eblocker.setup',
            'excludeShared': [
                //'security/'
            ],
            'excludeBower': [
            ]
        },
        'controlbar': {
            'appName': 'eblocker.controlbar',
                'excludeShared': [
                //'security/'
            ],
                'excludeBower': [
            ]
        },
        'settings': {
            'appName': 'eblocker.console',
                'excludeShared': [
                //'security/'
            ],
                'excludeBower': [
            ]
         },
        'advice': {
            'appName': 'eblocker.advice',
                'excludeShared': [
                //'security/'
            ],
                'excludeBower': [
            ]
         },
        'sample': {
            'appName': 'eblocker.sample',
            'excludeShared': [
                //'settings/'
            ],
            'excludeBower': [
            ]
        }
    };
    var redirectedRestApi = 'http://localhost:3000';

    var base = './';
    var src = base + 'src/';
    var temp = base + '.tmp/';
    var build = base + 'build/';
    var report = base + 'report/';

    var srcShared = src + 'shared/';
    var srcServer = src + 'server/';

    var config = {
        name: 'eBlocker Community UI',
        redirectedRestApi : redirectedRestApi,

        /**
         * all modules (i.e. independent SPAs) of this project
         */
        modules: Object.keys(modules),

        getAppNameForModule: function(name) {
            return modules[name].appName;
        },

        /**
         * Base path to all our sources
         */
        src: src,

        /**
         * All JS files that should be vetted
         */

        allJs: [].concat(
            src + '**/*.js',
            base + '[a-z]*.js'
        ),

        /**
         * LESS files that should be compiled and included
         */
        less: [].concat(
            src + '*/styles/styles.less',
            src + 'styles/styles.less'
        ),

        /**
         * i18n resource files
         */
        locale: [].concat(
            src + 'locale/lang-' + '*.json'
        ),

        /**
         * image files
         */
        images: [].concat(
            src + 'img/**/*.*'
        ),

        /**
         * HTML files
         */
        index: index,
        htmlTemplates: htmlTemplates,
        srcModule: srcModule,
        srcShared: srcShared,

        /**
         * All HTML files that should be watched
         */
        allHtml: src + '**/*.html',

        htmlPattern: '**/*.html',

        /**
         * All files that should be watched, when serving the app for testing
         */
        allWatches: [].concat(
            src + '**/*.*',
            '!' + src + '**/' + '*.less',
            temp + '**/', 'styles.css'
        ),

        /**
         * Project's package.json
         */
        packageJson: base + 'package.json',

        /**
         * Module's js, without specs (unit tests)
         */
        appJs: appJs,
        sharedJs: sharedJs,

        /**
         * Order of JS files:
         * Main module first, then all other modules, finally all other JS files
         */
        jsOrder: [
            '**/*.module.js',
            '**/*.js'
        ],

        /**
         * Module's CSS files
         */
        appCss: appCss,

        /**
         * optimized files
         */
        optimized: {
            app: 'app.js',
            lib: 'lib.js'
        },

        sassConfig: sassConfig,

        /**
         *
         */
        karmaWorkingDir: base,

        /**
         * Mock server
         */
        startPath: '',
        srcServer: srcServer,
        nodeServer: srcServer + 'app.js',
        defaultPort: '8001',

        /**
         * browser sync
         */
        browserReloadDelay: 1000,

        /**
         * plato config
         */
        plato: { js: src + '**/*.js' },

        /**
         * Temporary directory for precompiled/aggregated CSS and HTML files
         */
        temp: temp,

        /**
         * Target directory for production build files
         */
        build: build,

        /**
         * output dir for reports
         */
        report: report,

        _end_: '_end_'
    };

    return config;

    // ----

    function index(module) {
        return src + module + '/index.html';
    }

    function srcModule(module) {
        return src + module + '/';
    }

    function htmlTemplates(module) {
        var postfix = '**/*.html';
        var pathList = [];

        // Add modules own HTML templates
        pathList.push(src + module + '/app/' + postfix);

        // By default, add all shared HTML templates
        pathList.push(srcShared + postfix);

        // Now exclude all shared dependencies, that should not be included
        var excludeShared = modules[module].excludeShared;
         for (var i = 0; i < excludeShared.length; i++) {
            pathList.push('!' + srcShared + excludeShared[i] + postfix);
        }
        return pathList;
    }

    function appJs(module) {
        return [
            src + module + '/' + '**/*.module.js',
            src + module + '/' + '**/*.js',
            '!' + src + module + '/' + '**/*.spec.js'
        ];
    }

    function sharedJs(module) {
        var pathList = [];

        // By default, add all shared JS files w/o unit tests
        pathList.push(srcShared + '**/*.module.js');
        pathList.push(srcShared + '**/*.js');
        pathList.push('!' + srcShared + '**/*.spec.js');

        // Now exclude all shared dependencies, that should not be included
        var excludeShared = modules[module].excludeShared;
        for (var i = 0; i < excludeShared.length; i++) {
            pathList.push('!' + srcShared + excludeShared[i] + '**/*.js');
        }
        return pathList;
    }

    function appCss(module) {
        return [
            temp + 'styles.css',
            temp + module + '/styles/styles.css'
        ];
    }

    function sassConfig() {
        return {
            autoprefixer: { browsers: ['last 2 version'] },
            lintingSrc: src + '**/*.s+(a|c)ss',
            entryPoint: src + 'styles/main.scss',
            dest: config.build + 'css'
        };
    }
};
