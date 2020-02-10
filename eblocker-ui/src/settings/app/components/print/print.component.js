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
export default {
    templateUrl: 'app/components/print/print.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        updatesStatus: '<'
    }
};

/*
    Use like so:

    const param = {
        myParam: vm.myParam,                // some param that can be accessed within the template by 'vm.param.myParam'
        someOtherParam: vm.someOtherParam,  // can be accessed within the template by 'vm.param.someOtherParam'
        heading: 'ADMINCONSOLE.XXXX.PRINTVIEW_TITLE',     // Updates the toolbar title in the print-view
        templateUrl: 'app/components/network/wizard/print-settings.template.html'     // the template: REQUIRED
    };
        StateService.goToState(STATES.PRINT, param);
 */

function Controller(logger, StateService, $scope, $window, $timeout, $localStorage, $translate, $q) {
    'ngInject';

    const vm = this;

    vm.$onInit = function () {
        if (!angular.isObject($localStorage.eblockerPrintParam)) {
            logger.debug('No print-view parameter has been set yet ', $localStorage);
        }
        setOneTimeWatcher();
    };

    function setOneTimeWatcher() {
        // ** Supposed issue: $localStorage synch (between tabs) is sometimes too slow, so that eblockerPrintParam
        // is not yet defined when loading this app/state.
        // Hence, we need need a one-time-watcher to wait until it is defined. If it is already defined, the OTW
        // will fire anyway and set the value.
        vm.otwParam = $scope.$watch(function() {
            return $localStorage.eblockerPrintParam;
            // return angular.toJson($localStorage.eblockerPrintParam);
        }, function() {
            if (angular.isObject($localStorage.eblockerPrintParam)) {
                setParam($localStorage.eblockerPrintParam).then(function success() {
                    // ** Give time to finish rendering the page before printing, otherwise the page may be empty
                    // or print view will show the raw HTML with angular expressions and untranslated texts.
                    $timeout(doPrint, 2000);
                    vm.otwParam(); // remove OTW
                });
            }
        });
    }

    vm.$onDestroy = function() {
        if (angular.isDefined(vm.otwParam)) {
            vm.otwParam();
        }
    };

    function setParam(value) {
        vm.param = value;
        vm.templateUrl = vm.param.templateUrl;
        if (angular.isString(vm.param.heading)) {
            StateService.getActiveState().translationKey = vm.param.heading;

            return $translate(vm.param.heading).then(function(title) {
                vm.title = title;
            });
        }
        const deferred = $q.defer();
        deferred.resolve({});
        return deferred.promise;
    }

    function doPrint() {
        const printContents = document.getElementById('print-canvas').innerHTML;
        $window.document.write('' +
            '<html>' +
                '<head>' +
                    '<title>' + vm.title + '</title>' +
                '</head>' +
                '<body>' +
                    '' + printContents + '' +
                '</body>' +
            '</html>'
        );
        $window.document.close();
    }
}
