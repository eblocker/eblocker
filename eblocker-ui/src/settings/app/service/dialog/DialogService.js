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
export default function DialogService($mdDialog, $q, $translate) {// jshint ignore: line
    'ngInject';

    function openDirtyConfirmDialog() {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.DIRTY_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.DIRTY_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.DIRTY_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.DIRTY_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: function() {
                    let def = $q.defer();
                    return $q.resolve(true);
                },
                cancelAction: function() {
                    return $q.reject(false);
                }
            }
        });
    }

    function updateStartConfirmDialog(event) {
        return $mdDialog.show({
            controller: 'UpdateDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/update/update.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false
        });
    }

    function updateSetTimeDialog(event, config) {
        return $mdDialog.show({
            controller: 'SetUpdateTimeDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/update/set-update-time.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals:{
                config: config
            }
        });
    }

    function shutdownOrReboot(event, rebooting, okAction, cancelAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: rebooting ? 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.REBOOT_TITLE'
                        : 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.SHUTDOWN_TITLE',
                    text: rebooting ? 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.REBOOT_TEXT'
                        : 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.SHUTDOWN_TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.SHUTDOWN_REBOOT_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function licenseUpdate(event ) {
        return $mdDialog.show({
            controller: 'UpdateLicenseDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/license/update-license.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false
        });
    }

    function userAdd(event, user) {
        return $mdDialog.show({
            controller: 'AddUserDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/user-add.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                user: user
            }
        });
    }

    function userRoleEdit(event, user) {
        return $mdDialog.show({
            controller: 'EditUserRoleController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/user-role-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                user: user
            }
        });
    }

    function userAddDevice(event, user, devices) {
        return $mdDialog.show({
            controller: 'AddDeviceToUserDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/user-add-device.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                user: user,
                devices: devices
            }
        });
    }

    function confirmReassignUser(devString, username, oldUsername) {

        let title;
        let text;
        const config = {
            deviceString: devString,
            oldUsername: $translate.instant(oldUsername),
            username: $translate.instant(username)
        };
        if (angular.isDefined(oldUsername)) {
            title = $translate.instant('ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.TITLE_SINGULAR');
            text = $translate.instant('ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.TEXT_SINGULAR', config);
        } else {
            title = $translate.instant('ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.TITLE');
            text = $translate.instant('ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.TEXT', config);
        }

        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                msgKeys: {
                    title: title,
                    text: text,
                    okButton: 'ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.REASSIGN_DEVICES_CONFIRM.ACTION.CANCEL'
                },
                subject: {

                },
                okAction: function() {
                    // dummy to return from confirmation okAction (we do not have an ok action here)
                    const deferred = $q.defer();
                    deferred.resolve('default resolve');
                    return deferred.promise;
                },
                cancelAction: function() {}
            }
        });
    }

    function userUpdatePIN(event, user) {
        return $mdDialog.show({
            controller: 'SetPinDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/set-pin.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                user: user
            }
        }).then(function success(response) {
            return response;
        }, function cancel(response) {
            if (response === 'RESET_PIN' && !user.system) {
                return userResetPIN(event, user);
            }
            return $q.reject(response);
        });
    }

    function userResetPIN(event, user) {
        return $mdDialog.show({
            controller: 'PinResetConfirmDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/pin-reset-confirm.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                user: user
            }
        });
    }

    function profileAddEdit(profile, openDetails, event) {
        return $mdDialog.show({
            controller: 'AddUserProfileDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/user-profile-add.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                module: profile,
                openDetails: openDetails
            }
        });
    }

    function filterNewEdit(module, event, filterType) {
        return $mdDialog.show({
            controller: 'FilterAddDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/filter-add.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals:{
                module: module,
                filterType: filterType
            }
        });
    }

    function deleteEntries(title, text, textUndeletable, undeletableNum, okCallback, cancelCallback, event) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                msgKeys: {
                    title: title,
                    text: text,
                    undeletable: textUndeletable,
                    undeletableNum: undeletableNum,
                    okButton: 'ADMINCONSOLE.DIALOG.DELETE_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.DELETE_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: okCallback,
                cancelAction: cancelCallback || function() {}
            }
        });
    }

    function vpnConnectionEdit(dialog, isProfileNew, profile, parsedOptions) {
        dialog.isProfileNew = isProfileNew;
        dialog.step = 0;
        dialog.profile = profile;
        dialog.parsedOptions = parsedOptions;
        dialog.configurationComplete = false;
        dialog.showActiveOptions = false;
        dialog.showIgnoredOptions = false;
        dialog.showBlacklistedOptions = false;
        dialog.configurationComplete = false;

        return $mdDialog.show({
            controller: 'VpnConnectionEditController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/vpn/new-vpn-connect.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            locals: {
                dialog: dialog
            }
        });
    }

    function vpnTestConnection(profile) {
        return $mdDialog.show({
            locals: {
                profile: profile
            },
            controller: 'VpnConnectionTestController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/vpn/vpn-connect-test.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false
        });
    }

    function dnsAddEditServer(event, entry, update) {
        return $mdDialog.show({
            controller: 'DnsAddEditServerController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/dns/dns-edit-add-server.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                entry: entry,
                update: update
            }
        });
    }

    function dnsAddEditRecord(record, isNotUnique, saveFn, ip6FeatureEnabled, event) {
        return $mdDialog.show({
            controller: 'DnsRecordAddEditController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/dns/dns-record-new-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                record: angular.copy(record),
                isNotUnique: isNotUnique,
                save: saveFn,
                ip6FeatureEnabled: ip6FeatureEnabled
            }
        });
    }

    function sslStatusWizard(event, ssl, caOptions) {
        return $mdDialog.show({
            controller: 'SslStatusWizardController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/ssl/ssl-status-wizard.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            targetEvent: event,
            locals: {
                ssl: ssl,
                caOptions: caOptions
            }
        });
    }

    function trustedAppAdd(event, module) {
        return $mdDialog.show({
            controller: 'TrustedAppAddController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/trustedApps/trusted-app-add.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                module: module
            }
        });
    }

    function trustedDomainAddEdit(event, module) {
        return $mdDialog.show({
            controller: 'TrustedDomainAddEditController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/trustedDomains/trusted-domain-add-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                module: module
            }
        });
    }

    function recordingDomainIpRangeEdit(event, module) {
        return $mdDialog.show({
            controller: 'DomainIpRangeController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/recording/domain-ip-range.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                module: module
            }
        });
    }

    // used in ssl errors
    function addDomainToApp(event, appModules, suggestions, selectedDomains) {
        return $mdDialog.show({
            controller: 'AddDomainToAppController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/trustedApps/add-domain-to-app.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                appModules: appModules,
                suggestions: suggestions,
                selectedDomains: selectedDomains
            }
        });
    }

    function homeVpnReset(event, okCallback, cancelCallback) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.RESET_OPEN_VPN_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.RESET_OPEN_VPN_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.DELETE_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.DELETE_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: okCallback,
                cancelAction: cancelCallback || function() {}
            }
        });
    }

    function homeVpnStart(event, status) {
        return $mdDialog.show({
            controller: 'VpnHomeStartController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/vpn/vpn-home-start.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                status: status
            }
        });
    }

    function networkEditMode(event, config) {
        return $mdDialog.show({
            controller: 'NetworkEditModeController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/network/network-edit-mode.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                config: config
            }
        });
    }

    function networkEditContent(event, configuration, dnsEnabled) {
        return $mdDialog.show({
            controller: 'NetworkEditContentController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/network/network-edit-content.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                configuration: configuration,
                dnsEnabled: dnsEnabled
            }
        });
    }

    function networkExpertConfirm(event, eblockerIsDhcp) {
        return $mdDialog.show({
            controller: 'ExpertConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/network/expert-confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                eblockerIsDhcp: eblockerIsDhcp
            }
        });
    }

    function analysisToolDetails(event, recordedTransaction, header) {
        return $mdDialog.show({
            controller: 'AnalysisToolDetailsController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/recording/analysis-tool-details.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                details: recordedTransaction,
                content: header
            }
        });
    }

    function mobileWizardSaveConfirm(event, close, save, subject, validateStatus) {
        return $mdDialog.show({
            controller: 'MobileConfirmCloseController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/mobile/mobile-confirm-close.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.TEXT',
                    error: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.ERROR_TEXT',
                    yesButton: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.ACTION.YES',
                    noButton: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.ACTION.NO',
                    cancelButton: 'ADMINCONSOLE.DIALOG.MOBILE_SAVE_CONFIRM.ACTION.CANCEL'
                },
                close: close,
                save: save,
                subject: subject,
                validateSubject: validateStatus
            }
        });
    }

    function mobileWizardCloseConfirm(event, okAction, cancelAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: 'ADMINCONSOLE.DIALOG.MOBILE_CLOSE_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.MOBILE_CLOSE_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.MOBILE_CLOSE_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.MOBILE_CLOSE_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function setupWizardCloseConfirm(event, okAction, cancelAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: 'ADMINCONSOLE.DIALOG.SETUP_CANCEL_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.SETUP_CANCEL_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.SETUP_CANCEL_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.SETUP_CANCEL_CONFIRM.ACTION.CANCEL'
                },
                subject: {},
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function editTorCountryList(event, countryList, selectedList) {
        return $mdDialog.show({
            controller: 'EditTorCountryListController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/tor/edit-tor-countries.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            targetEvent: event,
            locals: {
                countryList: countryList,
                selectedList: selectedList
            }
        });
    }

    function openEditDialog(event, subject, msgKeys, okAction, isUnique, maxLength, minLength, isPassword, isDate) {
        return $mdDialog.show({
            controller: 'EditDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/edit/edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                subject: subject,
                msgKeys: msgKeys,
                isUnique: isUnique,
                okAction: okAction,
                maxLength: angular.isDefined(maxLength) ? maxLength : undefined,
                minLength: angular.isDefined(minLength) ? minLength : undefined,
                isPassword: angular.isDefined(isPassword) ? isPassword : undefined,
                isDate: angular.isDefined(isDate) ? isDate : undefined
            }
        });
    }

    function openEditDomainsDialog(event, subject, msgKeys, okAction, maxLength) {
        return $mdDialog.show({
            controller: 'EditDomainsDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/edit/edit-domains.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                subject: subject,
                msgKeys: msgKeys,
                okAction: okAction,
                maxLength: angular.isDefined(maxLength) ? maxLength : undefined,
            }
        });
    }

    function openEditFormatDialog(event, subject, okAction) {
        return $mdDialog.show({
            controller: 'EditBlockerFormatDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/edit/blocker-format-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                subject: subject,
                okAction: okAction
            }
        });
    }

    function helpInfoDialog(event, template) {
        return $mdDialog.show({
            controller: 'NotificationDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/notification/notification.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                template: template,
                msgKeys: undefined
            }
        });
    }

    function deleteEventsConfirm(event, deleteEventsMode, okAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.DELETE_EVENTS_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.DELETE_EVENTS_CONFIRM.TEXT.' + deleteEventsMode,
                    okButton: 'ADMINCONSOLE.DIALOG.DELETE_EVENTS_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.DELETE_EVENTS_CONFIRM.ACTION.CANCEL'
                },
                subject: {
                    name: deleteEventsMode
                },
                okAction: okAction,
                cancelAction: function() {}
            }
        });
    }

    function factoryResetConfirm(event, okAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                msgKeys: {
                    'title': 'ADMINCONSOLE.DIALOG.FACTORY_RESET_CONFIRM.TITLE',
                    'text': 'ADMINCONSOLE.DIALOG.FACTORY_RESET_CONFIRM.TEXT',
                    'okButton': 'ADMINCONSOLE.DIALOG.FACTORY_RESET_CONFIRM.ACTION.OK',
                    'cancelButton': 'ADMINCONSOLE.DIALOG.FACTORY_RESET_CONFIRM.ACTION.CANCEL'
                },
                subject: {
                    name: ''
                },
                okAction: okAction,
                cancelAction: function() {}
            }
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

    function sslDisableWarning(event, okAction, sslState, msgKeys) {
        return $mdDialog.show({
            controller: 'InformationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/information/information.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : msgKeys,
                subject: sslState,
                checkbox: undefined,
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


    function revokeCertificateInfo(event, okAction, cancelAction, subject) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.REVOKE_CERTIFICATE.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.REVOKE_CERTIFICATE.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.REVOKE_CERTIFICATE.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.REVOKE_CERTIFICATE.ACTION.CANCEL'
                },
                subject: subject,
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function resetDeviceConfirm(event, okAction, cancelAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.RESET_DEVICE_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.RESET_DEVICE_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.RESET_DEVICE_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.RESET_DEVICE_CONFIRM.ACTION.CANCEL'
                },
                subject: undefined,
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function addCertificateInfo(event, okAction, device) {
        return $mdDialog.show({
            controller: 'InformationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/information/information.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                msgKeys : {
                    title: 'ADMINCONSOLE.DIALOG.ADD_CERTIFICATE.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.ADD_CERTIFICATE.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.ADD_CERTIFICATE.ACTION.OK',
                },
                subject: device,
                checkbox: undefined,
                okAction: okAction
            }
        });
    }

    function editTasksViewConfig(event, config, headers) {
        return $mdDialog.show({
            controller: 'EditTasksViewConfigController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/system/tasks-view-config-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose:false,
            locals: {
                config: config,
                headers: headers
            }
        });
    }

    function deleteUserThatIsAssignedOrOperatingDevice(event, okAction, cancelAction) {
        return $mdDialog.show({
            controller: 'ConfirmationDialogController',
            controllerAs: 'vm',
            templateUrl: 'dialogs/confirmation/confirmation.dialog.tmpl.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                msgKeys: {
                    title: 'ADMINCONSOLE.DIALOG.DELETE_ASSIGNED_USER_CONFIRM.TITLE',
                    text: 'ADMINCONSOLE.DIALOG.DELETE_ASSIGNED_USER_CONFIRM.TEXT',
                    okButton: 'ADMINCONSOLE.DIALOG.DELETE_ASSIGNED_USER_CONFIRM.ACTION.OK',
                    cancelButton: 'ADMINCONSOLE.DIALOG.DELETE_ASSIGNED_USER_CONFIRM.ACTION.CANCEL'
                },
                subject: undefined,
                okAction: okAction,
                cancelAction: cancelAction
            }
        });
    }

    function updateCustomBlockerList(blockerName, formatList, edit, blockerList, isProcessing, okAction) {
        return $mdDialog.show({
            controller: 'addCustomListController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/blocker/update-custom-list.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                blockerName: blockerName, // name of the blocker
                edit: edit || false, // new or edit
                formatList: formatList,
                okAction: okAction,
                blockerList: blockerList, // custom user defined blocker list
                isProcessing: isProcessing // not editable while processing
            }
        });
    }

    return {
        confirmDirtyDialog: openDirtyConfirmDialog,
        updateSetTimeDialog: updateSetTimeDialog,
        updateStartConfirmDialog: updateStartConfirmDialog,
        shutdownOrReboot: shutdownOrReboot,
        licenseUpdate: licenseUpdate,
        userAdd: userAdd,
        userAddDevice: userAddDevice,
        userRoleEdit: userRoleEdit,
        confirmReassignUser: confirmReassignUser,
        userUpdatePIN: userUpdatePIN,
        userResetPIN: userResetPIN,
        profileAddEdit: profileAddEdit,
        filterNewEdit: filterNewEdit,
        deleteEntries: deleteEntries,
        vpnConnectionEdit: vpnConnectionEdit,
        vpnTestConnection: vpnTestConnection,
        dnsAddEditServer: dnsAddEditServer,
        dnsAddEditRecord: dnsAddEditRecord,
        sslStatusWizard: sslStatusWizard,
        trustedAppAdd: trustedAppAdd,
        trustedDomainAddEdit: trustedDomainAddEdit,
        recordingDomainIpRangeEdit: recordingDomainIpRangeEdit,
        addDomainToApp: addDomainToApp,
        homeVpnReset: homeVpnReset,
        homeVpnStart: homeVpnStart,
        networkEditMode: networkEditMode,
        networkEditContent: networkEditContent,
        networkExpertConfirm: networkExpertConfirm,
        analysisToolDetails: analysisToolDetails,
        mobileWizardSaveConfirm: mobileWizardSaveConfirm,
        mobileWizardCloseConfirm: mobileWizardCloseConfirm,
        setupWizardCloseConfirm: setupWizardCloseConfirm,
        editTorCountryList: editTorCountryList,
        openEditDialog: openEditDialog,
        openEditDomainsDialog: openEditDomainsDialog,
        openEditFormatDialog: openEditFormatDialog,
        helpInfoDialog: helpInfoDialog,
        deleteEventsConfirm: deleteEventsConfirm,
        factoryResetConfirm: factoryResetConfirm,
        dnsFilterChangeInfo: dnsFilterChangeInfo,
        sslDisableWarning: sslDisableWarning,
        showTorActivationDialog: showTorActivationDialog,
        revokeCertificateInfo: revokeCertificateInfo,
        addCertificateInfo: addCertificateInfo,
        editTasksViewConfig: editTasksViewConfig,
        resetDeviceConfirm: resetDeviceConfirm,
        deleteUserThatIsAssignedOrOperatingDevice: deleteUserThatIsAssignedOrOperatingDevice,
        updateCustomBlockerList: updateCustomBlockerList
    };
}
