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
/*
 * Set this variable to true, to disable tests and uglification in production build (e.g. for testing on real eBlocker).
 * ** SHOULD BE FALSE BY DEFAULT ** We need uglification in production.
 */
let quickRun = false;

const args = require('yargs').alias('r', 'redirectRestApi').argv;
const browserSync = require('browser-sync');
const config = require('./gulp.config')();
const del = require('del');
const glob = require('glob');
const gulp = require('gulp');
const path = require('path');
const $ = require('gulp-load-plugins')({ lazy: true });
const ansiColors = require('ansi-colors');
const _ = require('lodash');
const debug = require('gulp-debug-streams');

const gulpInject = require('gulp-inject');
const gulpConcat = require('gulp-concat');
const gulpRef = require('gulp-rev');
const gulpIf = require('gulp-if');
const gulpImageMin = require('gulp-imagemin');
const jsonminify = require('gulp-jsonminify');

const gulpTap = require('gulp-tap');
const file = require('gulp-file');

const sass = require('gulp-sass')(require('sass'));
const cleanCSS = require('gulp-clean-css');

const port = process.env.PORT || config.defaultPort;

const ngHtml2Js = require('gulp-ng-html2js');
const htmlmin = require('gulp-htmlmin');

const browserify = require('browserify');
const jshint = require('jshint');
const babel = require('babelify');
const vinylSource = require('vinyl-source-stream');
const vinylBuffer = require('vinyl-buffer');
const sourcemaps = require('gulp-sourcemaps');
const gulpUglify = require('gulp-uglify');

const fs = require('fs');
const csv2json = require('csv2json');
const Json2csvParser = require('json2csv').Parser;
const nlf = require('nlf');


/**
 * yargs variables can be passed in to alter the behavior, when present.
 * Example: gulp serve-dev
 *
 * --verbose  : Various tasks will produce more output to the console.
 * --nosync   : Don't launch the browser with browser-sync when serving code.
 * --inspect    : Launch debugger with node-inspector.
 * --inspect-brk: Launch debugger and break on 1st line with node-inspector.
 * --startServers: Will start servers for midway tests on the test task.
 */

/**
 * Avoid error: (node:85927) MaxListenersExceededWarning: Possible EventEmitter memory leak detected.
 * 11 task_stop listeners added. Use emitter.setMaxListeners() to increase limit
 */
gulp.setMaxListeners(15);

/**
 * Function to swallow all errors. Print them on console
 * for ease of use. Otherwise gulp watchers will exit
 * on error.
 * @param err
 */
function handleError(err) {
    if (err !== undefined) {
        log(err.toString());
    } else {
        log('handleError called w/o message.');
    }

    this.emit('end');
}

function exitBuild(err) {
    if (err !== undefined) {
        log(err.toString());
    } else {
        log('exitBuild called w/o message.');
    }
    process.exit(1);
}

function createTemplateCache(module) {
    return gulp.src([config.src + '/' + module + '/' + '**/*.html', config.srcShared + '**/*.html'])
        .pipe(htmlmin({
            collapseWhitespace: true,
            removeComments: true,
            minifyJS: true,
            minifyCSS: true,
            minifyURLs: true
        }))
        .pipe(ngHtml2Js({
            stripPrefix: module + '/',
            moduleName: 'template.' + module + '.app'
        }))
        // Name workaround for inject-order: starts with 't' and is therefore injected after 'app-rev.js'
        // This is important, so that angular is imported before the templates are initialized. Or the angular.module
        // call will fail.
        .pipe(gulpConcat('templates.' + module + '.js'))
        .pipe(gulpIf(!quickRun, gulpUglify()))
        .pipe(gulp.dest(config.temp))
        .pipe(gulpRef())
        .pipe(gulp.dest(config.build + module));
}


function templateCache(done) {
    let counter = 0;
    for (let i = 0; i < config.modules.length; i++) {
        createTemplateCache(config.modules[i])
            .on('end', function() {
                counter++;
                log('template-cache task ' + counter + ' is done: ' + config.modules[counter - 1]);
                if (counter === config.modules.length) {
                    done();
                }
            }).on('error', function() {
                counter++;
                log('template-cache task ' + counter + ' ended with error.');
                if (counter === config.modules.length) {
                    done();
                }
            }
        );
    }
}

/**
 * Only called in dev, so that overview page exists with all modules
 */
function copyRootIndex() {
    // not required, if 'template'-task also copies the index.html's
    // ** this task is required for the sample and dashboard root page, opened by serve-dev
    return gulp.src(config.src + 'index.html')
        .pipe(gulp.dest(config.build));
}

function doInject(module) {
    let target = gulp.src(config.index(module));
    let sources = gulp.src([config.build + module + '/*.js', config.build + '**/*.css'], {read: false});

    return target
        .pipe(gulpInject(sources, {ignorePath: 'build'}))
        .pipe(htmlmin({
            collapseWhitespace: true,
            removeComments: true,
            minifyJS: true,
            minifyCSS: true,
            minifyURLs: true
        }))
        .pipe(gulp.dest(config.build + module));
}

/**
 * Injects each JS and CSS bundle into each index.html (per module)
 */
function injectCompiled(done) {
    let counter = 0;
    for (let i = 0; i < config.modules.length; i++) {
        doInject(config.modules[i])
            .on('end', function() {
                counter++;
                log('inject-compiled task ' + counter + ' is done: ' + config.modules[counter - 1]);
                if (counter === config.modules.length) {
                    done();
                }
            }).on('error', function() {
                counter++;
                log('inject-compiled task ' + counter + ' ended with error.');
                if (counter === config.modules.length) {
                    done();
                }
            }
        );
    }
}

function browserifyBundle(module, isDev) {

    let src = config.src + module + '/app/';
    let sourceName = module === 'setup' || module === 'sample' ?
        src + 'app.module.js' : src + module + '.module.js';

    const browserifyConfig = {
        entries: sourceName,
        outputName: 'app.js',
        outputNameMin: 'app.min.js',
        debug: isDev
    };

    // .transform(jshint, {continuous: true}) // enable continuous to prevent JS linting errors from breaking the build
    const browserifyInstance = browserify(browserifyConfig)
        .transform(babel, {
            presets: ['env'],
            plugins: [
                'angularjs-annotate'
            ]
        });

    log('Starting to bundle module ' + module + ' for ' + (isDev ? 'development' : 'production') + ' ...');

    let browserifyResult = browserifyInstance
        .bundle()
        .on('error', handleError)
        // vinyl-source-stream makes the stream compatible with gulp.
        .pipe(gulpIf(
            (!isDev),
            vinylSource(browserifyConfig.outputNameMin),
            vinylSource(browserifyConfig.outputName)
        ))
        .pipe(vinylBuffer())
        .pipe(gulpRef());

    if (quickRun) {
        log('Neither uglification nor sourcemaps requested for module ' + module + ' ... ');
    } else if (isDev) {
        log('Creating sourcemaps for module ' + module + ' ... ');
    } else {
        log('Uglifying module ' + module + ' ... ');
    }

    browserifyResult = browserifyResult
        .pipe(gulpIf(isDev && !quickRun, sourcemaps.init({loadMaps: true})))
        .pipe(gulpIf(!isDev && !quickRun, gulpUglify()))
        .pipe(gulpIf(isDev && !quickRun, sourcemaps.write('./')));

    browserifyResult = browserifyResult.pipe(gulp.dest(config.build + module));

    return browserifyResult;
}

function browserifyDevelop(done) {
    doBrowserifyBundle(true, done);
}

function browserifyProduction(done) {
    doBrowserifyBundle(false, done);
}

function doBrowserifyBundle(isDev, done) {
    let counter = 0;
    // ** This is somewhat a hack. We need the return stream from browserifyBundle(..) to determine
    // when the bundle() task is done. Otherwise the next task (in the sequence) will be executed
    // before the file is written to the FS. Browser would reload, but no change would be visible.
    // So here we use the on-end event and then manually notify that the task is done. The counter
    // makes sure that all modules have been build.
    for (let i = 0; i < config.modules.length; i++) {
        browserifyBundle(config.modules[i], isDev)
            .on('end', function() {
                counter++;
                log('Browserify task ' + counter + ' is done.');
                if (counter === config.modules.length) {
                    done();
                }
            })
            .on('error', function() {
                counter++;
                log('Browserify task ' + counter + ' ended with error.');
                if (counter === config.modules.length) {
                    done();
                }
            });
    }
}


// ***** WATCHERS

/**
 * Browserify takes quite some time to bundle (app + node_modules). As a
 * quick workaround, we split the JS watchers into modules here.
 * So when JS changes only the changed module is re-bundled.
 */
function watch() {

    for (let i = 0; i < config.modules.length; i++) {
        addModuleWatchers(config.modules[i]);
    }

    gulp.watch(config.src + '**/*.scss', gulp.series(
        cleanCss,
        sassTask,
        cleanIndex,
        injectCompiled,
        browserSyncReload));

    gulp.watch(config.src + 'locale/*.json', watchLocale);
}

function addModuleWatchers(module) {
    // create browserify bundler for each module.
    // Called by each watch-js-* task: see sequence of
    // watch-js-*
    const browserifyMod = function() {
        return browserifyBundle(module, true);
    };

    const templateCacheMod = function() {
        return createTemplateCache(module);
    };

    const cleanJsMod = function(done) {
        return getCleanJSFnForModule(done, module + '/');
    };

    const cleanTemplateMod = function(done) {
        return getCleanTemplateFnForModule(done, module);
    };

    // Set display names for better log messages:
    browserifyMod.displayName = 'browserify:' + module;
    templateCacheMod.displayName = 'template-cache:' + module;
    cleanJsMod.displayName = 'clean-js:' + module;
    cleanTemplateMod.displayName = 'clean-template:' + module;

    const watchJSSeriesMod = gulp.series(
        cleanJsMod,
        browserifyMod,
        cleanIndex,
        injectCompiled,
        browserSyncReload
    );

    const watchTemplateSeriesMod = gulp.series(
        cleanTemplateMod,
        templateCacheMod,
        cleanIndex,
        injectCompiled,
        browserSyncReload
    );

    // register the actual JS + HTML watcher
    gulp.watch([config.src + module + '/**/*.js', config.src + 'shared/**/*.js'], watchJSSeriesMod);
    gulp.watch([config.src + module + '/**/*.html', config.src + 'shared/**/*.html'], watchTemplateSeriesMod);
}

function browserSyncReload(done) {
    browserSync.reload();
    done();
}

// ***** CLEAN TASKS

/**
 * Cleans all
 */
function cleanAll(done) {
    let delconfig = [].concat(config.build, config.temp, config.report);
    log('Cleaning: ' + ansiColors.blue(delconfig));
    clean(delconfig, done);
}
exports.clean = cleanAll;

function cleanReport(done) {
    let delconfig = [].concat(config.report);
    log('Cleaning: ' + ansiColors.blue(delconfig));
    clean(delconfig, done);
}
exports['clean-report'] = cleanReport;

exports['clean-styles'] = function(done) {
    let files = [].concat(
        config.temp + '**/*.css',
        config.build + 'css/**/*.css'
    );
    clean(files, done);
};

/**
 * Fixes issue:
 * - after gulp watcher tasks was complete, the injection did not work anymore.
 *  e.g. When HTML file was altered, the other module's injection was empty.
 *  So here we clean out the index, copy it again, and after that we can
 *  call the injection task.
 */
function cleanIndex(done) {
    let counter = 0;
    for (let i = 0; i < config.modules.length; i++) {
        let module = config.modules[i];
        let delconfig = [config.build + module + '/index.html'];
        log('Cleaning: ' + ansiColors.blue(delconfig));
        del(delconfig, {force: true}).then(function(paths) {
            log('Deleted : ' + paths.join(', '));
            counter++;
            if (counter === config.modules.length) {
                done();
            }
        });
    }
}

/**
 * We split the clean JS tasks into
 * module as well, so that we can clean
 * specific modules only. All
 */
function getCleanJSFnForModule(done, module) {
    // We need to be specific about the cleaned js file: we do not want to delete the templates.
    let delconfig = [config.build + module + 'app-*.js'];
    log('Cleaning: ' + ansiColors.blue(delconfig));
    clean(delconfig, done);
}

function getCleanTemplateFnForModule(done, module) {
    // We need to be specific about the cleaned template file: we do not want to delete the JS bundle.
    let delconfig = [config.build + module + '/templates-*.' + module + '.js'];
    log('Cleaning: ' + ansiColors.blue(delconfig));
    clean(delconfig, done);
}

function cleanCss(done) {
    let delconfig = [config.build + 'css'];
    log('Cleaning: ' + ansiColors.blue(delconfig));
    clean(delconfig, done);
}

function cleanImages(done) {
    clean(config.build + 'img/**/*.*', done);
}

function cleanLocale(done) {
    clean(config.build + 'locale/**/*.*', done);
}

function sassTask() {
    let sassConf = config.sassConfig();
    return gulp.src([sassConf.entryPoint])
        .pipe(sass())
        .on('error', handleError)
        .pipe(gulpConcat('main.css'))
        .pipe(cleanCSS())
        .pipe(gulpRef())
        .pipe(gulp.dest(sassConf.dest));
}

/**
 * To add static css files. Planing to use this to add a style to the content of an iframe (fragFinn dashboard card).
 * The idea is to get the element with Javascript and add a link with href to the static css file. We cannot know
 * the ref number, so here we simply copy static css files to the build folder.
 */

function specialCss(done) {
    let counter = 0;
    let runTimes = 1; // dashboard only
    config.modules.forEach(module => {
        if (module === 'dashboard') {
            addCss(module)
                .on('end', function () {
                    counter++;
                    log('special-css task ' + counter + ' is done: ' + module);
                    if (counter === runTimes) {
                        done();
                    }
                }).on('error', function () {
                    counter++;
                    log('special-css task ' + counter + ' ended with error.');
                    if (counter === runTimes) {
                        done();
                    }
                }
            );
        }
    });
}

function addCss(module) {
    return gulp.src(config.src + '/' + module + '/' + '**/*.css').
        pipe(gulp.dest(config.build + module));
}

/**
 *  LOCALE. This is a little more complex due to angular-translate:
 *  we don't want the browser to cache the language json files. So we need a ref number. However, with ref-number, we
 *  cannot specify the prefix and suffix as required by angular-translate for asynch loading of the json files.
 *  As a workaround, we here copy the json files into the build folder and give them a random number as ref-number.
 *  We then copy this information (which random number for which files / module) into a file that is then bundled into
 *  the js-app. This file contains an export that is used in translationConfig.js to configure angular-translate.
 *
 *  Note:
 *  When we watch the lang files, we do not need to change the ref-number. At least this makes it a lot quicker to
 *  update the files. If we were to change the ref number on each watch, we'd had to also bundle the js, since the input
 *  of the information file changes as well. Since we develop w/o cache anyway, I think this is sufficient.
 */

// needs to be global for now, so that we can use it in 'write-down' task
const langObj = {};

/**
 * Get file name for module when given suffix (ref-number) has already been created. This is done, so that we do not
 * need to rebundle JS after a lang file has been changed. The ref-number is required for angular-translate's
 * configuration, and once the ref number changes, we also need to create the langFileNames.js file that contains that
 * number. So we'd also have to rebundle the JS app. To avoid this, we reuse the ref number, when the watcher fires.
 * @param module name, e.g. 'settings' or 'setup' or 'controlbar'
 * @param suffix dash + ref number + extension, e.g. '-2524597802.json'
 * @param originalFileName as defined in sources, e.g. 'lang-settings-de.json'
 * @returns {string}
 */
function getFileName(module, suffix, originalFileName) {
    const splitPrefix = originalFileName.split('-');
    const splitSuffix = splitPrefix[2].split('.');
    // splitPrefix[0] lang
    // splitPrefix[1] sample
    // splitPrefix[2] de.json
    // splitSuffix[0] de
    // splitSuffix[1] json
    return 'lang-' + module + '-' + splitSuffix[0] + suffix;
}

/**
 * As mentioned above: avoid rebundle of js files (for ref-number information) when watcher fires.
 * @param module
 * @param obj contains the ref numbers for all modules (contained in suffix)
 * @returns {*}
 */
function copyExistingLangFiles(module, obj) {
    let newFileName = '';
    let pathOnly = '';
    return gulp
    // .src(config.build + 'locale/lang-' + module +  '*.json')
        .src(config.src + 'locale/lang-' + module +  '*.json')
        .pipe(jsonminify())
        .pipe(gulpTap(function (file, t) {
            pathOnly = file.path.split(path.basename(file.path))[0];
            newFileName = getFileName(module, obj[module].suffix, path.basename(file.path));
            // ** rename file name and path
            file.path = pathOnly + newFileName;
        }))
        .pipe(gulp.dest(config.build + 'locale/'));
}

let localeWatchSemaphore = false;
function watchLocale(done) {
    if (!localeWatchSemaphore) {
        localeWatchSemaphore = true;
        gulp.series(cleanLocale, doLocale)();
    } else {
        log('Language watch in progress. Cannot executing watch task.');
    }
    done();
}

function doLocale(done) {
    let counter = 0;
    const modules = [];
    // copy modules so that we can add 'shared' string w/o adding it for other places that use modules
    config.modules.forEach((item) => {
        modules.push(item);
    });
    modules.push('shared'); // also copy shared lang files
    for (let i = 0; i < modules.length; i++) {
        let module = modules[i];
        copyExistingLangFiles(module, langObj).on('end', function() {
            counter++;
            if (counter === modules.length) {
                localeWatchSemaphore = false;
                done();
            }
        }).on('error', function(e) {
            counter++;
            log('do-locale task ' + counter + ' ended with error: ' + e);
            if (counter === modules.length) {
                localeWatchSemaphore = false;
                done();
            }
        });
    }
}

/**
 * Provides a random / ref number for the lang files. We use the same number for both 'en' and 'de' file. This is
 * necessary because in angular-translate we can only define prefix and suffix for both 'en' and 'de'. If these files
 * were to have a different ref number, we would not be able to match both within the translationConfig.js (angular-
 * translate definition).
 */
function getRand() {
    return Math.floor(Math.random() * 10000000000);
}

function getCleanFileaName(name, ref) {
    const splitPrefix = name.split('-');
    const splitSuffix = splitPrefix[2].split('.');
    // splitPrefix[0] lang
    // splitPrefix[1] sample
    // splitPrefix[2] de.json
    // splitSuffix[0] de
    // splitSuffix[1] json
    return 'lang-' + splitPrefix[1] + '-' + splitSuffix[0] + '-' + ref + '.json';
}

function copyAndRenameLangFiles(module, obj) {
    let newFileName = '';
    let pathOnly = '';
    const random = getRand();
    return gulp
    // .src(config.build + 'locale/lang-' + module +  '*.json')
        .src(config.src + 'locale/lang-' + module +  '*.json')
        .pipe(jsonminify())
        .pipe(gulpTap(function (file, t) {

            // set the lang-objects prefix and suffix for this module and file. This will be copied to the src-shared
            // folder, so that we can inject this to use for angular-translate definition.
            obj[module] = {
                prefix: '/locale/lang-' + module + '-',
                suffix: '-' + random + '.json'
            };
            pathOnly = file.path.split(path.basename(file.path))[0];
            newFileName = getCleanFileaName(path.basename(file.path), random);
            // ** rename file name and path
            file.path = pathOnly + newFileName;
            log('Copied lang file as ' + newFileName);
        }))
        .pipe(gulp.dest(config.build + 'locale/'));
}

function setLangFilenames(done) {
    let counter = 0;
    const modules = [];
    // copy modules so that we can add 'shared' string w/o adding it for other places that use modules
    config.modules.forEach((item)=>{
        modules.push(item);
    });
    modules.push('shared'); // also copy shared lang files
    for (let i = 0; i < modules.length; i++) {
        let module = modules[i];
        copyAndRenameLangFiles(module, langObj).on('end', function() {
            counter++;
            if (counter === modules.length) {
                done();
            }
        }).on('error', function() {
            counter++;
            log('set-lang-filenames task ' + counter + ' ended with error.');
            if (counter === modules.length) {
                done();
            }
        });
    }
}

/**
 * Writes a javascript file which exports the language file information (ref number), so that we can give
 * angular-translate the prefix and suffix information based on the dynamically created file name (lang files need
 * a ref number, to force browser to reload the file).
 * This export can be imported in the corresponding translationConfig.js file.
 */
function writeDown() {
    const string = '/* This file has been generated automatically. Please do not edit. ' +
        'Please do not push into repo. */\n\nexport default ' +
        JSON.stringify(langObj) +
        '; // jshint ignore: line';
    log('Writing JS exports file for language reference numbers to ' + config.src + 'shared/locale/langFileNames.js');
    return file('langFileNames.js', Buffer.from(string), { src: true })
        .pipe(gulp.dest(config.src + 'shared/locale'));
}

/**
 * Copy and compress images
 */
function compressAndCopyImages() {
    log('Compressing and copying images');

    return gulp
        .src(config.images)
        .pipe(gulpImageMin([
                gulpImageMin.optipng({optimizationLevel: 5}),
                gulpImageMin.svgo({plugins: [
                        {removeViewBox: false}, {cleanupIDs: false}
                    ]})
            ])
        )
        .on('error', exitBuild)
        .pipe(gulp.dest(config.build + 'img'));
}
const images = gulp.series(cleanImages, compressAndCopyImages);

/**
 * vet the code and create coverage report
 */
function vet() {
    log('Analyzing source with JSHint and JSCS: ' + config.allJs);

    return gulp
        .src(config.allJs)
        .pipe(debug('DEBUG vet - '))
        // .pipe($.if(args.verbose, $.print()))
        .pipe($.jshint())
        .pipe($.jshint.reporter('jshint-stylish', { verbose: true }))
        .pipe($.jshint.reporter('fail'))
        .pipe($.jscs());
}

/**
 * Run specs once and exit
 * To start servers and run midway specs as well:
 *    gulp test --startServers
 * @return {Stream}
 */
const test = gulp.series(cleanReport, function(done) {
    if (!quickRun) {
        log('Starting test cases...');
        startTests(true /*singleRun*/, done);
    } else {
        log('Skipping test cases.');
        done();
    }
});

function addLicense(item, license) {
    let summary = item.summary;
    if (license !== undefined && license !== '' && license !== summary) {
        if (summary !== '') {
            summary = summary + ';';
        }
        if (summary.indexOf(license) === -1) {
            summary = summary + license;
        }
    }
    return summary;
}

function normalizeRepository(repository) {
    // Already (none) or a URL?
    if (repository === '(none)' || repository.startsWith('http://') || repository.startsWith('https://')) {
        return repository;
    }

    // normalize:
    // * git@github.com:xyz
    // * github.com:xyz
    // * ssh://git@github.com/xyz
    if (repository.includes('github.com')) {
        return repository.replace(/.*github\.com[\/:]/, 'https://github.com/');
    }

    // default: assume github.com
    return 'https://github.com/' + repository;
}

function generateJavaScriptLicenseCSV(done) {
    nlf.find({
        directory: './',
        summaryMode: 'off',
        depth: 100,
        production: true
    }, function (err, data) {
        /* Remove duplicates (due to different versions) */
        const duplicates = [];
        data.forEach((item, iOut) => {
            for (let iIn = iOut + 1; iIn < data.length; iIn++) {
                const inner = data[iIn];
                if (item.name === inner.name && iOut !== iIn && duplicates.indexOf(iOut) === -1) {
                    duplicates.push(iOut);
                }
            }
        });
        duplicates.sort(function(a, b) {
            const numA = Number(a);
            const numB = Number(b);
            return numB - numA;
        }).forEach((index) => {
            data.splice(index, 1);
        });

        if (duplicates.length > 0) {
            log('Removed ' + duplicates.length +
                ' duplicate Javascript dependencies (with different version) from list');
        }

        /* Add license summary (e.g. MIT or ISC), remove unused info, remove eblocker-ui from list */
        let eblockerUiIndex;
        // add license summary to each entry and remove unused information
        data.forEach((item, index) => {
            if (item.name === 'eblocker-ui') {
                eblockerUiIndex = index;
            }
            item.summary = '';
            item.licenseSources.package.sources.forEach((l) => {
                l.text = '';
                item.summary = addLicense(item, l.license);
            });
            item.licenseSources.license.sources.forEach((l) => {
                l.text = '';
                item.summary = addLicense(item, l.license);
            });
            item.licenseSources.readme.sources.forEach((l) => {
                l.text = '';
                item.summary = addLicense(item, l.license);
            });
            delete item.licenseSources;
            delete item.directory;
            delete item.id;
            delete item.type;

            /* Add some hard coded values that are otherwise missing */

            if (item.summary === '' && item.name === 'color-convert') {
                // color-convert has a MIT license, but nlf does not read this correctly
                item.summary = 'MIT';
                log('Manually setting license of module \'' + item.name + '\' to \'MIT\'');
            }

            if (item.name === 'debug') {
                item.repository = 'https://github.com/visionmedia/debug';
            } else if (item.name === 'glob') {
                item.repository = 'https://github.com/isaacs/node-glob';
            } else if (item.name === 'json-schema-traverse') {
                item.repository = 'https://github.com/epoberezkin/json-schema-traverse';
            } else if (item.name === 'regenerator-runtime') {
                item.repository = 'https://github.com/facebook/regenerator/tree/main/packages/runtime';
            }
            item.repository = normalizeRepository(item.repository);
        });

        /* Remove eblocker-ui from list */
        if (eblockerUiIndex) {
            log('Removing module \'' + data[eblockerUiIndex].name + '\' from list');
            data.splice(eblockerUiIndex, 1);
        }

        /* Parse and save as CSV */
        const parser = new Json2csvParser();
        const csv = parser.parse(data);
        fs.writeFileSync(config.src + 'settings/app/components/openSourceLicenses/raw/javascript-dependencies.csv',
            csv, {encoding:'utf8'});
        done();
    });
}

function convertLicenseCSVtoJSON() {
    return fs.createReadStream(config.src +
        'settings/app/components/openSourceLicenses/raw/javascript-dependencies.csv')
        .pipe(csv2json({
            // Defaults to comma.
            separator: ','
        }))
        .pipe(fs.createWriteStream(config.src +
            'settings/app/components/openSourceLicenses/raw/javascript-dependencies.json'));
}

function generateAndWriteJsLicenses(done) {
    if (quickRun) {
        log('Skipping generation of Javascript dependency list');
        done();
        return;
    }
    const dir = config.src + 'settings/app/components/openSourceLicenses/raw/';
    if (!fs.existsSync(dir)){
        fs.mkdirSync(dir);
    }
    gulp.series(generateJavaScriptLicenseCSV, convertLicenseCSVtoJSON, createJavaScriptLicenseHTML)();
    done();
}

function createJavaScriptLicenseHTML(done) {
    const html = fs.readFileSync(config.src +
        'settings/app/components/openSourceLicenses/libs-javascript.component.html', {encoding:'utf8'});
    const jsDepJson = require(config.src +
        'settings/app/components/openSourceLicenses/raw/javascript-dependencies.json');

    const parsed = jsDepJson.map(function(item) {
        /* jshint ignore:start */
        return '            <tr>' +
                '<td>' + item.name + '</td>' +
                '<td>' + item.version + '</td>' +
                '<td>' + 'npm' + '</td>' +
                '<td>' +
                    '<span ng-show="\'' + item.repository + '\' === \'(none)\'">' + item.summary + '</span>' +
                    '<a ng-hide="\'' + item.repository + '\' === \'(none)\'" ' + 'target="_blank" href=\"' + item.repository + '\">' + item.summary + '</a>' +
                '</td>' +
            '</tr>';
        /* jshint ignore:end */
    });

    // add table content
    let htmlDest = html.replace('<!-- CONTENT -->', parsed.join('\n'));
    // remove old warning and add new warning
    htmlDest = htmlDest.replace(/## CAUTION[\*\w\W\s]*##/gim,
        '*** THIS FILE HAS BEEN AUTOMATICALLY GENERATED. DO NOT EDIT ***');
    // write to destination file which is actually used as template by angular component
    fs.writeFileSync(config.src + 'settings/app/components/openSourceLicenses/raw/libs-javascript.generated.html',
        htmlDest, {encoding:'utf8'});
    done();
}

/**
 * Run specs and wait.
 * Watch for file changes and re-run tests on each change
 * To start servers and run midway specs as well:
 *    gulp autotest --startServers
 */
exports.autotest = function(done) {
    startTests(false /*singleRun*/, done);
};

function setQuickRun(done) {
    quickRun = (args.quickrun !== undefined && (args.quickrun === true || args.quickrun === 'true'));
    log('Running tests and minification: ' + (!quickRun));
    done();
}

const startProd = gulp.series(
    setQuickRun,
    vet, cleanAll, setLangFilenames, writeDown, generateAndWriteJsLicenses,
    gulp.parallel(browserifyProduction, templateCache, sassTask, images, specialCss),
    injectCompiled, test
);
exports['start-prod'] = startProd;

const startDev = gulp.series(
    cleanAll, setLangFilenames, writeDown,
    gulp.parallel(browserifyDevelop, templateCache, sassTask, images, specialCss),
    copyRootIndex, injectCompiled
);
exports['start-dev'] = startDev;

const build = gulp.series(
    startProd,
    function(done) {
        let msg = {
            title: 'gulp build',
            subtitle: 'Deployed to the build folder',
            message: 'Running `gulp build`'
        };
        log(msg);
        done();
    }
);
exports.build = build;

const buildDev = gulp.series(startDev, watch);
exports['build-dev'] = buildDev;

/**
 * serve the dev environment
 * --inspect-brk or --inspect
 * --nosync
 */
exports['serve-dev'] = gulp.series(buildDev, function() {
    serve(true /*isDev*/);
});

/**
 * serve the build environment
 * --inspect-brk or --inspect
 * --nosync
 */
exports['serve-build'] = gulp.series(build, function() {
    serve(false /*isDev*/);
});

exports.generateJavaScriptLicenseCSV = generateJavaScriptLicenseCSV;

// -----------------
// private functions
// -----------------

/**
 * Delete all files in a given path
 * @param  {Array}   path - array of paths to delete
 * @param  {Function} done - callback when complete
 */
function clean(path, done) {
    log('Cleaning: ' + ansiColors.blue(path));
    del(path, {force: true}).then(function() {
        done();
    });
}

/**
 * Inject files in a sorted sequence at a specified inject label
 */
// Modified according to https://github.com/johnpapa/gulp-patterns/issues/122
function inject(src, label, order, injectOptions) {
    if (typeof injectOptions === 'undefined') {
        injectOptions = {};
    }
    if (label) {
        injectOptions.name = 'inject:' + label;
    }
    return $.inject(orderSrc(src, order), injectOptions);
}

/**
 * Order a stream
 */
// Modified according to https://github.com/johnpapa/gulp-patterns/issues/122
function orderSrc(src, order) {
    log('src: ' + src);
    return gulp
        .src(src, {read: false})
        .pipe($.if(order, $.order(order)))
        ;
}

/**
 * Log a message or series of messages using chalk's blue color.
 * Can pass in a string, object or array.
 */
function log(msg) {
    if (typeof (msg) === 'object') {
        for (let item in msg) {
            if (msg.hasOwnProperty(item)) {
                $.util.log(ansiColors.blue(msg[item]));
            }
        }
    } else {
        $.util.log(ansiColors.blue(msg));
    }
}

/**
 * Start the tests using karma.
 * @param  {boolean} singleRun - True means run once and end (CI), or keep running (dev)
 * @param  {Function} done - Callback to fire when karma is done
 * @return {undefined}
 */
function startTests(singleRun, done) {
    let child;
    let fork = require('child_process').fork;
    let Server = require('karma').Server;

    if (args.startServers) {
        log('Starting servers');
        let savedEnv = process.env;
        savedEnv.NODE_ENV = 'dev';
        savedEnv.PORT = 8888;
        child = fork(config.nodeServer);
    }

    let karmaConfig = {
        configFile: __dirname + '/karma.conf.js',
        singleRun: singleRun
    };

    const karma = new Server(karmaConfig, karmaCompleted);
    karma.start();

    ////////////////

    function karmaCompleted(karmaResult) {
        log('Karma completed');
        if (child) {
            log('shutting down the child process');
            child.kill();
        }
        if (karmaResult === 1) {
            done('karma: tests failed with code ' + karmaResult);
        } else {
            done();
        }
    }
}

/**
 * serve the code
 * --inspect-brk or --inspect
 * --nosync
 * @param  {Boolean} isDev - dev or build mode
 */
function serve(isDev) {

    let debugMode = '--inspect';
    let nodeOptions = getNodeOptions(isDev);

    nodeOptions.nodeArgs = [debugMode + '=5858'];

    if (args.verbose) {
        log(nodeOptions);
    }

    return $.nodemon(nodeOptions)
    // .on('restart', ['vet'], function(ev) {
        .on('restart', function(ev) {
            log('*** nodemon restarted');
            log('files changed:\n' + ev);
            // browserSync.notify('reloading now ...');
            // browserSync.reload({ stream: false });
            setTimeout(function() {
                browserSync.notify('reloading now ...');
                browserSync.reload({ stream: false });
            }, config.browserReloadDelay);
        })
        .on('start', function() {
            log('*** nodemon started');
            startBrowserSync(isDev);
        })
        .on('crash', function() {
            log('*** nodemon crashed: script crashed for some reason');
        })
        .on('exit', function() {
            log('*** nodemon exited cleanly');
        });
}

function getNodeOptions(isDev) {
    // let buildPath = config.build + 'dashboard/app/*.js';
    // log('Nodemon is watching ' + buildPath);

    return {
        script: config.nodeServer,
        delayTime: 50,
        env: {
            'PORT': port,
            'NODE_ENV': isDev ? 'dev' : 'build',
            'REDIRECT_REST_API': typeof(args.redirectRestApi) !== 'undefined',
            'REST_API': config.redirectedRestApi
        },
        watch: [config.srcServer]
    };
}

/**
 * Start BrowserSync
 * --nosync will avoid browserSync
 */
function startBrowserSync(isDev) {
    if (args.nosync || browserSync.active) {
        return;
    }

    log('Starting BrowserSync on port ' + port);

    let options = {
        proxy: 'localhost:' + port,
        port: 3000,
        files: [],//isDev ? config.allWatches : [], // XXX hpe: use gulp watch instead
        ghostMode: { // these are the defaults t,t,t
            clicks: true,
            forms: true,
            scroll: true
        },
        injectChanges: true,
        logFileChanges: true,
        logLevel: 'info',
        logPrefix: config.name,
        notify: true,
        reloadDelay: 0, //1000,
        startPath: config.startPath
    };

    browserSync(options);
}

/**
 * When files change, log it
 * @param  {Object} event - event that fired
 */
function changeEvent(event) {
    let srcPattern = new RegExp('/.*(?=/' + config.src + ')/');
    log('File ' + event.path.replace(srcPattern, '') + ' ' + event.type);
}

/**
 * Formatter for bytediff to display the size changes after processing
 * @param  {Object} data - byte data
 * @return {String}      Difference in bytes, formatted
 */
function bytediffFormatter(data) {
    let difference = (data.savings > 0) ? ' smaller.' : ' larger.';
    return data.fileName + ' went from ' +
        (data.startSize / 1000).toFixed(2) + ' kB to ' +
        (data.endSize / 1000).toFixed(2) + ' kB and is ' +
        formatPercent(1 - data.percent, 2) + '%' + difference;
}

/**
 * Format a number as a percentage
 * @param  {Number} num       Number to format as a percent
 * @param  {Number} precision Precision of the decimal
 * @return {String}           Formatted perentage
 */
function formatPercent(num, precision) {
    return (num * 100).toFixed(precision);
}

/**
 * Show OS level notification using node-notifier
 */
function notify(options) {
    let notifier = require('node-notifier');
    let notifyOptions = {
        sound: 'Bottle',
        contentImage: path.join(__dirname, 'gulp.png'),
        icon: path.join(__dirname, 'gulp.png')
    };
    _.assign(notifyOptions, options);
    notifier.notify(notifyOptions);
}
