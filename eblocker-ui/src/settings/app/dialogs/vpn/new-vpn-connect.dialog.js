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
export default function UpdateDialogController(logger, $mdDialog, dialog, VpnService) {
    'ngInject';

    const vm = this;

    vm.dialog = VpnService.updateCompletionStatus(dialog);

    vm.hide = function() {
        $mdDialog.hide();
    };

    vm.cancel = function() {
        $mdDialog.cancel(vm.dialog.profile);
    };

    vm.save = function() {
        const profile = vm.dialog.profile;
        profile.temporary = false;
        profile.keepAliveMode = vm.dialog.profile.keepAlivePingEnabled ? 'OPENVPN_REMOTE' : 'DISABLED';
        VpnService.updateProfile(profile).then(function(response) {
            $mdDialog.hide(response.data);
        });
    };

    function reloadProfileAndContinue() {
        VpnService.getProfile(vm.dialog.profile).then(function(response) {
            vm.dialog.profile = response.data;
            vm.next();
        });
    }

    vm.uploadConfig = function (file, invalidFiles) {
        vm.invalidFile = invalidFiles && invalidFiles[0];
        if (file || vm.invalidFile) {
            vm.dialog.parsedOptions = null;
        }
        if (file) {
            vm.uploading = true;
            VpnService.uploadProfileConfig(vm.dialog.profile, file).then(function (resp) {
                vm.uploading = false;
                vm.dialog.parsedOptions = resp.data;
                vm.dialog = VpnService.updateCompletionStatus(vm.dialog);
                if (vm.dialog.configurationComplete) {
                    reloadProfileAndContinue();
                }
            }, function (resp) {
                vm.uploading = false;
                logger.error('Error status: ', resp);
            }, function (evt) {
                vm.uploadProgress = parseInt(100.0 * evt.loaded / evt.total);
                logger.info('progress: ' + vm.uploadProgress + '%');
            });
        }
    };

    vm.uploadInlineContent = function (option, file, invalidFiles) {
        vm.invalidFile = invalidFiles && invalidFiles[0];
        if (file) {
            vm.uploading = true;

            VpnService.uploadProfileConfigOption(vm.dialog.profile, option, file).then(function (resp) {
                vm.uploading = false;
                vm.dialog.requiredFileError[option] = false;
                vm.dialog.parsedOptions = resp.data;
                vm.dialog = VpnService.updateCompletionStatus(vm.dialog);
                if (vm.dialog.configurationComplete) {
                    reloadProfileAndContinue();
                }
            }, function (resp) {
                vm.uploading = false;
                if (resp.status === 400) {
                    vm.dialog.requiredFileError[option] = true;
                    vm.dialog = VpnService.updateCompletionStatus(vm.dialog);
                }
            }, function (evt) {
                vm.uploadProgress = parseInt(100.0 * evt.loaded / evt.total);
                logger.info('progress: ' + vm.uploadProgress + '%');
            });
        }
    };

    vm.next = function() {
        ++vm.dialog.step;
    };

    vm.isOverriddenOption = function(option) {
        return option.overriddenLine && option.line !== option.overriddenLine;
    };

    vm.isEblockerOption = function(option) {
        return option.source === 'eblocker' && !option.overriddenLine;
    };

    /**
     * Prevent browsers (esp. firefox) from autofilling the password field; autofill causes VPN profile w/o password to
     * actually use the admin password.
     * We could check the ngChange callback for unwanted changes, e.g. a change w/o user interaction.
     * Using $touched / $untouched is not sufficient because these properties are only about focus
     * Using $dirty / $pristine are not sufficient because they are also set when the Browser autofills the input.
     * Both parts in the component's lifecycle ($onInit, $postLink) cannot be used either, because the autofill
     * has not happened at that point, yet. Perhaps $doCheck, but that involves multiple calls that have
     * to be distinguished from user interaction again.
     * This leaves us with either a timeout (changing the password within the first couple of seconds) or this
     * solution (changing text to password dynamically).
     *
     * ngChange is executed immediately, so it should not reveal any letters of the password.
     *
     */
    let pwChanged = false;

    if (angular.isDefined(vm.dialog.profile.loginCredentials.password) &&
        vm.dialog.profile.loginCredentials.password !== '') {
        pwChanged = true;
    }

    vm.getPasswordFieldType = function() {
        if (pwChanged) {
            return 'password';
        }
        return 'text';
    };

    vm.passwordChanged = function() {
        pwChanged = true;
    };
}
