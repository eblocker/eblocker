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
* Shows splash screen and moves on to 'app.main'
*
* The UI Router takes care that the application is bootstrapped,
* before 'app.main' is actually rendered.
*/
export default  function AppController($scope, $state, $stateParams, $timeout, $translate, Idle, logger,
                                       security, APP_CONTEXT, SystemService) {
    'ngInject';
    'use strict';

    const vm = this;
    let iconPosRight = true;

    vm.scope = $scope;

    vm.showControlBar = false;

    vm.toggleControlBar = toggleControlBar;
    vm.showRightIcon = showRightIcon;
    vm.toggleLangSettings = toggleLangSettings;
    vm.getLang = getLang;

    vm.$onInit = function() {
        SystemService.start(3000);
        SystemService.startTokenWatcher(security.requestToken, APP_CONTEXT.name);
    };

    vm.$onDestroy = function() {
        SystemService.stop();
        SystemService.stopTokenWatcher();
    };

    if (angular.isDefined($stateParams.lang)) {
        setFrontendLanguage({id: $stateParams.lang});
    }

    function getLang() {
        return $translate.use();
    }

    function toggleLangSettings() {
        if ($translate.use() === 'en') {
            setFrontendLanguage({id: 'de'});
        } else {
            setFrontendLanguage({id: 'en'});
        }
    }

    function showRightIcon() {
        return iconPosRight;
    }

    function toggleControlBar(iconPos) {
        iconPosRight = iconPos === 'right';
        vm.showControlBar = !vm.showControlBar;
        if (vm.showControlBar === true && angular.isUndefined(vm.contentWindow)) {
            // since we manually inject the controlbar here, the controlbar's close function
            // has no effect on the parent scope (the scope in this controller).
            // Workaround: wait until the iframe is rendered, then override the postMessage function
            // of the controlbar's window. When the controlbar notices a click it will use the postMessage
            // to close, but will now call the function registered here instead.
            vm.tries = 0;
            setContentWindow();
        }
    }

    function setContentWindow() {
        vm.tries++;
        // https://stackoverflow.com/questions/18437594/angularjs-call-other-scope-which-in-iframe
        let wrap = angular.element(document.getElementsByClassName('iframe-for-controlbar'));

        for (let i = 0; i < wrap['0'].childNodes.length; i++) {
            if (angular.isDefined(wrap['0'].childNodes[i].id) &&
                angular.isFunction(wrap['0'].childNodes[i].id.indexOf) &&
                wrap['0'].childNodes[i].id.indexOf('iframe-for-controlbar') > -1 ){
                vm.contentWindow = wrap['0'].childNodes[i].contentWindow;
            }
        }
        if (angular.isDefined(vm.contentWindow)) {
            // workaround to close the controlbar from the scope of the controlbar
            // --> we need to reach the parent's scope vm.showControlBar.
            vm.contentWindow.parent.postMessage = function () {
                vm.showControlBar = false;
                vm.scope.$apply();
            };
        } else {
            if (vm.tries < 5) {
                $timeout(setContentWindow, 1000);
            }
        }
    }

    $scope.$on('IdleStart', function() {
        logger.debug('IdleStart');
    });


    function setFrontendLanguage (language) {
        $translate.use(language.id);
    }

    $scope.$on('IdleTimeout', function() {
        logger.debug('IdleTimeout');
        $state.go('app.expired', {});
    });

    $scope.$on('IdleEnd', function() {
        logger.debug('IdleEnd');
    });

    $scope.$on('Keepalive', function() {
        logger.debug('Keepalive');
        security.requestToken(APP_CONTEXT.name);
    });

    $state.go('app.main', {}).catch(function(e) {
        logger.error('Could not transition to app.main: ' + e);
    });
}
