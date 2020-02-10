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
/**
 * https://ui-router.github.io/ng1/docs/latest/classes/transition.transitionservice.html
 * https://ui-router.github.io/ng1/docs/latest/index.html
 */
export default function StateTransitionHooks(logger, $transitions, RegistrationService, StateService, STATES,
                                             security, ConsoleService) {
    'ngInject';

    /**
     * Success is needed to disable the spinner
     */
    $transitions.onSuccess({}, function (trans) {
        const from = trans.from();
        const to = trans.to();

        logger.debug('** Successfully transitions from \'' + from.name + '\' to state \'' + to.name + '\' **');

        if (to.name !== STATES.AUTH && to.name !== STATES.PARENT) {
            // auth state is not a final state. The actual final state should disable the spinner.
            ConsoleService.isPageSpinner(false);
            ConsoleService.isGlobalSpinner(false);
        }
    });

    $transitions.onBefore({}, function (trans) {
        const from = trans.from();
        const to = trans.to();
        logger.debug('** About to transition from state \'' + from.name + '\' to state \'' + to.name + '\' ** ' +
            'with params: ', trans.params());

        // as soon as we change the state we want the page spinner ON
        const goToDifferentState = to.name !== from.name;
        ConsoleService.isPageSpinner(goToDifferentState);

        // coming from undefined state or from login state we want the global spinner ON
        // will be turned OFF once app settles in final state (see onSuccess / onFinish).
        // There may be an issue where multiple HTTP requests cause multiple transitions to logout and back to login;
        // Once successfully in login state the state will not transition again and thus not disable the spinner;
        // So we also check that to.name does not equal from.name before enabling the spinner.
        const isParentState = (to.name !== from.name && (from.name === '' || from.name === STATES.LOGIN)) ||
            ConsoleService.isGlobalSpinner();
        ConsoleService.isGlobalSpinner(isParentState); // have init spinner before navbar is visible


        if (from.name === '' && to.name !== STATES.PARENT && to.name !== STATES.AUTH && to.name !== STATES.LOGOUT) {
            // ** coming from undefined state (e.g. initial call to 'http://domain:3000/settings/#!/some-path')
            // we want to save the target state (to.name), so that after authentication (in AUTH state) we can
            // redirect the user to the actual state. Otherwise this info is lost and the user can only be
            // redirected to some default state.
            // Once we redirect to STATES.PARENT the next transition will have from.name === 'app', so we will not
            // set the workflow state / param twice.
            StateService.setWorkflowState(to.name, trans.params());

            // if we have an undefined state (''), we go to PARENT ('app'), which may redirect to main app or out-of-app
            // state.
            return redirect(trans, STATES.PARENT);
        } else {
            // hidden states (e.g. tasks tab in 'System' page) must not be entered (not even via URL)
            return !to.hide;
        }
    });

    /*
     * Only to decide whether to default to 'license' or 'devices' state.
     * Authentication etc. will be checked by other onStart when actually transitioning to
     * devices or license state.
     */
    $transitions.onStart({to: STATES.DEFAULT}, function (trans) {
        const from = trans.from();
        const to = trans.to();
        const params = trans.params(); // to open navbar on small devices

        logger.debug('** (default) Started to transition from state \'' + from.name +
            '\' to state \'' + to.name + '\' **');
        let transTo;
        if (RegistrationService.getRegistrationInfo().licenseType === 'NONE' ||
            RegistrationService.getRegistrationInfo().licenseAboutToExpire) {
            transTo = STATES.HOME;
        } else {
            // Just to open navbar on small devices
            if (angular.isObject(params) && angular.isObject(params.param) &&
                angular.isDefined(params.param.openNavbar) && params.param.openNavbar) {
                ConsoleService.initiallyShowNavBar(true);
            }
            transTo = STATES.DEVICES;
        }
        logger.debug('** Redirect to \'' + transTo + '\' **');
        return redirect(trans, transTo);
    });

    /*
     * makes sure that going to url '.../#!/login' will not open login mask, unless not authenticated
     */
    $transitions.onStart({to: STATES.LOGIN}, function (trans) {
        const from = trans.from();
        const to = trans.to();

        logger.debug('** Login-Hook: Started to transition from state \'' + from.name + '\'. **');

        if (security.isAuthenticated()) {
            // ** If authenticated, we redirect to the auth state, which eventually will redirect to default state.
            // This is useful, so that the user can type in the url '[DOMAIN]:3000/login' and will actually
            // land on a page and not the login screen.
            return redirect(trans, STATES.AUTH);
        }
        // TODO maybe we could use loginNeeded & loginNotNeeded states to determine whether to proceed or not
        // ** If not authenticated, we need to be able to enter state
        // LOGIN, so that the user can provide a password.
        return allowTransition();
    });

    /*
     * Handles all other cases: authentication, license state, etc.
     */
    $transitions.onStart({}, function (trans) {
        const from = trans.from();
        const to = trans.to();

        // ** Enter state w/o further checks
        if (to.ignoreHook) {
            logger.debug('** Ignoring hook from state \'' + from.name + '\' to state \'' + to.name + '\' **');
            // ** e.g. always enter state AUTH, no exceptions, no redirects
            // AUTH will make sure that user is authenticated and that
            // the correct target state will be opened.
            // return allowTransition();
            return updateActiveAndAllow(from, to);
        }

        logger.debug('** Started to transition from state \'' + from.name + '\' to state \'' + to.name + '\' **');

        // ** Check if user is authenticated
        if (!security.isAuthenticated() && to.name !== STATES.LOGIN) {
            // TODO: to.name cannot be login, because login state has ignoreHook property, so it will redirect
            // before reaching this code.
            logger.debug(' ** Not authenticated - transition hook redirects to state \'' + STATES.AUTH + '\' **');
            // ** If not authentication, we enter the AUTH state, which
            // will take care of Login for us, if required.
            return redirect(trans, STATES.AUTH);
        }

        // ** just to make sure that we have product info. If not, go to default state. The transition to default
        // will take care of authentication etc..
        if (!angular.isObject(RegistrationService.getProductInfo())) {
            return redirect(trans, STATES.DEFAULT);
        }

        // ** Don't update active state for NOT_LICENSED
        if (to.name === STATES.NOT_LICENSED) {
            // ** when we enter NOT_LICENSED state, we do not want
            // to update the active state (keep state active, that
            // the user originally wanted to transition to)
            return allowTransition();
        }

        // ** If a state is disabled, we simply redirect to DEFAULT state.
        // This should only happen, if the user uses an URL to navigate to
        // a actually disabled state like for instance any SSL state,
        // when SSL is not enabled.
        if (to.disabled) {
            logger.debug(' ** State is disabled - transition hook redirects to state \'' + STATES.DEFAULT + '\' **');
            return redirect(trans, STATES.DEFAULT);
        }

        // ** Update active state (for highlighting of navbar and dispaly of toolbar heading
        updateActiveState(from, to);

        // ** Check if license has sufficient privileges
        let hasAccess = false;
        if (angular.isFunction(to.requiredLicense)) {
            hasAccess = RegistrationService.hasProductKey(to.requiredLicense());
        }

        if (!hasAccess) {
            logger.debug(' ** Feature \'' + to.name + '\' not licensed. Transition hook redirects to state \'' +
                STATES.NOT_LICENSED + '\' **');

            // ** If feature is unlicensed, we show the upsell/error page
            return redirect(trans, STATES.NOT_LICENSED);
        }

        return allowTransition();
    });

    function allowTransition() {
        return true;
    }

    function updateActiveState(from, to) {
        // ** make sure that initially no state is highlighted in navbar
        StateService.resetActiveStates();
        StateService.setHighlightedNavbarItem(to.name, true);
    }

    function redirect(transition, target) {
        return transition.router.stateService.target(target);
    }

    /**
     * It makes sure that the state is activated, so that the toolbar heading is visible; it may set a different
     * title if required.
     */
    $transitions.onStart({ to: STATES.ACTIVATION_FINISH }, function (trans) { // jshint ignore: line
        const from = trans.from();
        const to = trans.to();
        const params = trans.params();
        logger.debug('** Transition hook ACTIVATION_FINISH: from state \'' +
            from.name + '\' to state \'' + to.name + '\' with params: ', params, ' **');
        updateActiveState(from, to);
        if (from.name === STATES.ACTIVATION) {
            StateService.getActiveState().translationKey = 'ADMINCONSOLE.ACTIVATION_FINISH.TOOLBAR.TITLE';
        } else {
            StateService.getActiveState().translationKey = 'ADMINCONSOLE.ACTIVATION_FINISH.TOOLBAR.TITLE_REMIND';
        }
        return true;
    });


    /**
     * Just set the active state to correctly display toolbar header
     */
    $transitions.onStart({ to: STATES.PRINT }, function (trans) {
        const from = trans.from();
        const to = trans.to();
        StateService.resetActiveStates();
        // since print has no 'showInNavbar' or 'outOfApp' property StateService.setHighlightedNavbarItem
        // will not set print state to active. So we need to do this here.
        StateService.getStateByName(to.name).isActive = true;
        return true;
    });

    function updateActiveAndAllow(from, to) {
        logger.debug('** Transition hook (updateActiveAndAllow) from state \'' +
            from.name + '\' to state \'' + to.name + '\' **');
        updateActiveState(from, to);
        return true;
    }

    $transitions.onError({}, function (trans) {
        // logger.error(' ### Error caught during transition: ', trans);
        // ConsoleService.isPageSpinner(false);
        // ConsoleService.isGlobalSpinner(false);
        // StateService.goToState(STATES.AUTH);
        // return redirect(trans, STATES.AUTH);
    });
}
