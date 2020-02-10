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
export default function StateService($state, logger, $window, ArrayUtilsService, STATES) {
    'ngInject';
    'use strict';

    let states, initState, initParam, hasUnsavedChanges;

    function getStates() {
        return states;
    }

    function setStates(newStates) {
        states = newStates;
    }

    /**
     * This function is used to save the state, if the user initially
     * enters the console via URL. The user will be redirected
     * to the authentication state, thus loosing the actual
     * target state information. This objects saves this info.
     * It can later be used by authentication component to continue
     * the workflow. But it should be deleted to avoid loops and unexpected
     * return to the saved / workflow state.
     * @param state name of the state
     * @param param optional param to pass to the workflow state
     */
    function setWorkflowState(state, param) {
        if (workflowStateValid(state)) {
            logger.debug('Setting workflow state to ', state);
            initState = state;
            initParam = param;
        } else {
            logger.warn('Invalid workflow state: ', state);
        }
    }
    let workflowStatePersistent;
    function isWorkflowStatePersistent(bool) {
        if (angular.isDefined(bool)) {
            workflowStatePersistent = bool;
        }
        return workflowStatePersistent;
    }

    function workflowStateValid(state) {
        return state !== STATES.LOGOUT && state !== STATES.AUTH && state !== STATES.LOGIN;
    }

    function getWorkflowState() {
        logger.debug('Getting workflow state: ', initState);
        return initState;
    }

    function getWorkflowParam() {
        logger.debug('Getting workflow param: ', initParam);
        return initParam;
    }

    function clearWorkflow() {
        initState = undefined;
        initParam = undefined;
        logger.debug('Cleared workflow state and parameter.');
    }

    function isStateValid(url) {
        return angular.isDefined(getStateByName(url));
    }

    function disableState(name, bool) {
        if (angular.isArray(states)) {
            states.forEach((state) => {
                if (state.name === name) {
                    state.disabled = bool;
                }
            });
        }
    }

    function getStateByName(name) {
        let ret;
        if (angular.isArray(states)) {
            states.forEach((state) => {
                if (state.name === name) {
                    ret = state;
                }
            });
        }
        return ret;
    }

    /**
     * This function returns a list
     * with all sub states of a parent state.
     * @param stateName
     * @returns {Array}
     */
    function getSubStates(stateName, license) {
        const ret = [];
        if (angular.isArray(states)) {
            states.forEach((state) => {
                if (state.parent === stateName &&
                    !state.ignoreTab &&
                    (!angular.isString(license) ||
                        (angular.isString(license) && state.requiredLicense() === license))) {
                    ret.push(state);
                }
            });
        }
        return ArrayUtilsService.sortByProperty(ret, 'tabOrder');
    }

    function goToState(state, params, reload) {
        // param other than undefined also causes parent state to be re-initialized if parent state has property
        // 'params' set
        const tmp = angular.isObject(params) ? { param: params } : undefined;
        return $state.transitionTo(state, tmp, {
            location: true,
            inherit: true,
            reload: reload === true,
            relative: $state.$current,
            notify: reload === true
        }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function reloadBrowserWindow() {
        $window.location.reload();
    }

    function setHighlightedNavbarItem(name, bool) {
        let counter = 0;
        let found = findAndHighlightState(name, bool);
        let parentName = name;
        while(!found && parentName !== STATES.PARENT && parentName !== STATES.MAIN && counter < 4) {
            counter++;
            const parent = getStateByName(parentName);
            parentName = angular.isObject(parent) ? parent.parent : '';
            found = findAndHighlightState(parentName, bool);
        }
    }

    function findAndHighlightState(name, bool) {
        let found = false;
        if (angular.isArray(states)) {
            states.forEach((state) => {
                // either state is in navBar or it is explicitly allow to be active
                // ('outOfApp' states need to set toolbar heading as well)
                if (state.name === name && (state.showInNavbar || state.allowActive)) {
                    state.isActive = bool;
                    found = true;
                }
            });
        }
        return found;
    }

    function getActiveState() {
        let activeState;
        if (angular.isArray(states)) {
            states.forEach((state) => {
                if (state.isActive) {
                    activeState = state;
                }
            });
        }
        return activeState;
    }

    function resetActiveStates() {
        if (angular.isArray(states)) {
            states.forEach((state) => {
                state.isActive = false;
            });
        }
    }

    function getCurrentState() {
        return $state.$current;
    }

    function isFormDirty(bool) {
        if (angular.isDefined(bool)) {
            hasUnsavedChanges = bool;
        }
        return hasUnsavedChanges;
    }

    /**
     * Allows to route directly into a details state: we need the ID from the $stateParam service. UiRouter requires
     * us to specify or initialize the parameter name beforehand (see routesConfig.js -> params: {param: null}).
     * Since we want to pass many different paremeters like 'entry', 'getAllEntries()' etc. we wrap these parameters
     * into a object-property called 'param', which is initlized as null. So we can fill this object with all our
     * actual parameters.
     *
     * In this function here, we need to check this param-object for the ID from the URL. When we route within
     * the app, before we get into the details state, our ID parameter will be wrapped into the params-object as
     * workflow state. The wrapping is done in the StateService#goToState function.
     *
     * When we route from within the settings app, the ID from the URL will be set into the params-object directly.
     * So we need to check for stateParam.id as well.
     *
     * @param stateParam
     * @returns {number}
     */
    function getIdFromParam(stateParam) {
        let idAsString;
        if (angular.isString(stateParam.id)) {
            idAsString = stateParam.id;
        } else if (angular.isObject(stateParam.param) && angular.isString(stateParam.param.id)) {
            idAsString = stateParam.param.id;
        }
        return onlyDigits(idAsString) ? parseInt(idAsString, 10) : idAsString;
    }

    function onlyDigits(value) {
        return /^-{0,1}\d+$/.test(value);
    }

    // function confirmDirty() {
    //     return DialogService.openDirtyConfirmDialog();
    // }

    return {
        getStates: getStates,
        setStates: setStates,
        setWorkflowState: setWorkflowState,
        getWorkflowState: getWorkflowState,
        getWorkflowParam: getWorkflowParam,
        isWorkflowStatePersistent: isWorkflowStatePersistent,
        clearWorkflow: clearWorkflow,
        isStateValid: isStateValid,
        getSubStates: getSubStates,
        goToState: goToState,
        reloadBrowserWindow: reloadBrowserWindow,
        setHighlightedNavbarItem: setHighlightedNavbarItem,
        getActiveState: getActiveState,
        resetActiveStates: resetActiveStates,
        getCurrentState: getCurrentState,
        getStateByName: getStateByName,
        isFormDirty: isFormDirty,
        disableState: disableState,
        getIdFromParam: getIdFromParam
    };
}
