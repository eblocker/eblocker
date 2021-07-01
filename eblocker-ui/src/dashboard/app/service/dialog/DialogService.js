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
export default function DialogService($mdDialog, $q) {
    'ngInject';

    function confirmationDialog(event, msgKeyTitle, msgKeyText, msgKeyOkButton, msgKeyCancelButton,
                                subject, okCallback, cancelCallback) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                msgKeys: {
                    title: msgKeyTitle,
                    text: msgKeyText,
                    okButton: msgKeyOkButton,
                    cancelButton: msgKeyCancelButton
                },
                subject: subject,
                okAction: okCallback,
                cancelAction: cancelCallback || function() {}
            }
        }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function mobileRevokeCertificate(event, subject, okCallback, cancelCallback) {
        return confirmationDialog(
            event,
            'MOBILE.CARD.REVOKE_CONFIRM_DIALOG.TITLE',
            'MOBILE.CARD.REVOKE_CONFIRM_DIALOG.TEXT',
            'MOBILE.CARD.ACTION.CONFIRM_REVOKE',
            'MOBILE.CARD.ACTION.CANCEL_REVOKE',
            subject,
            okCallback,
            cancelCallback || function() {
        }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function userChangePin(operatingUser) {
        return $mdDialog.show({
            controller: 'ChangePinDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/pin/change-pin.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                module: operatingUser
            }
        }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function userProvidePin(user, lockEnabled, mode, onOk, onLock, users) {
        return $mdDialog.show({
            controller: 'ProvidePinDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/pin/provide-pin.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            locals: {
                user: user,
                mode: mode,
                lockEnabled: lockEnabled,
                users: users || [],
                onOk: onOk,
                onLock: onLock,
                onCancel: function() {
                    //
                }
            }
        }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function adminLogin(onOk, onCancel) {
        return $mdDialog.show({
            controller: 'AdminLoginDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/admin/admin-login.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            locals: {
                onOk: onOk,
                onCancel: onCancel
            }
        }).then(function success(response) {
            return response;
        }, function error(reason) {
            return reason;
        });
    }

    function dnsFilterChangeInfo(event, okAction, device) {
        return $mdDialog.show({
            controller: 'InformationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/information/information.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: 'SHARED.DIALOG.DNS_FILTER_CHANGE.TITLE',
                    text: 'SHARED.DIALOG.DNS_FILTER_CHANGE.TEXT',
                    checkbox: 'SHARED.DIALOG.DNS_FILTER_CHANGE.ACTION.DO_NOT_SHOW_AGAIN',
                    okButton: 'SHARED.DIALOG.DNS_FILTER_CHANGE.ACTION.OK'
                },
                subject: device,
                checkbox: true,
                okAction: okAction
            }
        });
    }

    function showTorActivationDialog(showWarnings, event) {
        return $mdDialog.show({
            controller: 'TorActivationDialogController',
            controllerAs: 'ctrl',
            templateUrl: 'dialogs/tor/tor-activation.dialog.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                showWarnings: showWarnings
            }
        });
    }

    function welcome(os, browser) {
        return $mdDialog.show({
            controller: 'WelcomeDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/welcome/welcome.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                os: os,
                browser: browser
            }
        });
    }

    function closeMobileWizard(event, ok) {
        return confirmationDialog(
            event,
            'WIZARD.MOBILE.DIALOG.TITLE',
            'WIZARD.MOBILE.DIALOG.TEXT',
            'WIZARD.MOBILE.DIALOG.CONFIRM',
            'WIZARD.MOBILE.DIALOG.CANCEL',
            undefined,
            ok,
            function() {
            }).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function confirmPauseAndContinue(event, okCallback) {
        return confirmationDialog(event,
            'SQUID_BLOCKER.BLOCKED_ADS_TRACKERS.PAUSE_AND_CONTINUE.TITLE',
            'SQUID_BLOCKER.BLOCKED_ADS_TRACKERS.PAUSE_AND_CONTINUE.TEXT',
            'SQUID_BLOCKER.BLOCKED_ADS_TRACKERS.PAUSE_AND_CONTINUE.BUTTON_OK',
            'SQUID_BLOCKER.BLOCKED_ADS_TRACKERS.PAUSE_AND_CONTINUE.BUTTON_CANCEL',
            {},
            okCallback,
            function() {}
        );
    }

    return {
        confirmationDialog: confirmationDialog,
        mobileRevokeCertificate: mobileRevokeCertificate,
        userChangePin: userChangePin,
        userProvidePin: userProvidePin,
        adminLogin: adminLogin,
        dnsFilterChangeInfo: dnsFilterChangeInfo,
        showTorActivationDialog: showTorActivationDialog,
        welcome: welcome,
        closeMobileWizard: closeMobileWizard,
        confirmPauseAndContinue: confirmPauseAndContinue
    };
}
