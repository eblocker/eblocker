/* Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
 * Licensed under the EUPL, Version 1.2 or later.
 */
export default function PickerSaveController($scope, $window, $http, $q) {
    'ngInject';
    const vm = this;
    const browserLanguage = ($window.navigator.languages && $window.navigator.languages[0]) ||
        $window.navigator.language || 'en';
    const german = /^de(?:-|$)/i.test(browserLanguage);
    vm.t = german ? {
        title: 'In eBlocker übernehmen', selectedRule: 'Ausgewählte Regel', matches: 'Treffer auf dieser Seite:',
        password: 'Admin-Passwort', confirmWide: 'Große Ausblendung bestätigen',
        check: 'Prüfen', apply: 'Übernehmen', cancel: 'Abbrechen',
        choose: 'Element auswählen', authUnavailable: 'eBlocker-Anmeldung nicht erreichbar',
        wrongSite: 'Regel passt nicht zur Webseite', statusCheck: 'Regel prüfen',
        duplicate: 'Regel ist bereits vorhanden',
        rechoose: function(count) { return count + ' Treffer: Bitte Element erneut auswählen'; },
        wide: 'Regel gültig, blendet aber eine große Fläche aus',
        valid: 'Regel gültig: 1 Treffer auf dieser Seite',
        authFailed: 'Anmeldung fehlgeschlagen', invalid: 'Ungültige Domain oder CSS-Regel',
        checkFailed: 'Regelprüfung fehlgeschlagen', saved: 'Regel übernommen',
        disabled: 'eigene Regeln sind deaktiviert', authExpired: 'Anmeldung abgelaufen',
        saveFailed: 'Regel konnte nicht gespeichert werden'
    } : {
        title: 'Save in eBlocker', selectedRule: 'Selected rule', matches: 'Matches on this page:',
        password: 'Admin password', confirmWide: 'Confirm hiding a large area',
        check: 'Check', apply: 'Apply', cancel: 'Cancel',
        choose: 'Select an element', authUnavailable: 'eBlocker login is unavailable',
        wrongSite: 'The rule does not match this website', statusCheck: 'Check the rule',
        duplicate: 'This rule already exists',
        rechoose: function(count) { return count + ' matches: Please select the element again'; },
        wide: 'Valid rule, but it hides a large area',
        valid: 'Valid rule: 1 match on this page',
        authFailed: 'Login failed', invalid: 'Invalid domain or CSS rule',
        checkFailed: 'Rule check failed', saved: 'Rule applied',
        disabled: 'custom rules are disabled', authExpired: 'Login expired',
        saveFailed: 'Could not save the rule'
    };
    vm.rule = '';
    vm.password = '';
    vm.passwordRequired = false;
    vm.authenticated = false;
    vm.authReady = false;
    vm.checking = false;
    vm.saving = false;
    vm.checked = false;
    vm.duplicate = false;
    vm.saved = false;
    vm.confirmWide = false;
    vm.matches = -1;
    vm.coverage = 0;
    vm.status = vm.t.choose;
    vm.error = false;
    let token = null;

    function loadToken() {
        return $http.get('/api/adminconsole/authentication/token/ADMINCONSOLE').then(function(response) {
            token = response.data.token;
            vm.passwordRequired = response.data.passwordRequired === true;
            vm.authenticated = !vm.passwordRequired;
            vm.authReady = true;
        }, function() {
            vm.error = true;
            vm.status = vm.t.authUnavailable;
        });
    }

    function loginIfNeeded() {
        if (vm.authenticated) {
            return $q.when(token);
        }
        return $http.post('/api/adminconsole/authentication/login/ADMINCONSOLE',
            {currentPassword: vm.password}).then(function(response) {
            token = response.data.token;
            vm.password = '';
            vm.authenticated = true;
            return token;
        });
    }

    function onMessage(event) {
        if (event.source !== $window.parent || !event.data ||
                event.data.type !== 'eblocker-picker-rule') {
            return;
        }
        let hostname;
        try {
            hostname = new $window.URL(event.origin).hostname.toLowerCase();
        } catch (error) {
            return;
        }
        const candidate = event.data.rule;
        if (typeof candidate !== 'string' || !candidate.startsWith(hostname + '##') ||
                candidate.indexOf('\n') !== -1 || candidate.indexOf('\r') !== -1) {
            $scope.$applyAsync(function() {
                vm.rule = '';
                vm.error = true;
                vm.status = vm.t.wrongSite;
            });
            return;
        }
        $scope.$applyAsync(function() {
            vm.rule = candidate;
            vm.matches = typeof event.data.matches === 'number' ? event.data.matches : -1;
            vm.coverage = typeof event.data.coverage === 'number' ? event.data.coverage : 0;
            vm.checked = false;
            vm.duplicate = false;
            vm.saved = false;
            vm.confirmWide = false;
            vm.error = false;
            vm.status = vm.t.statusCheck;
        });
    }

    vm.check = function() {
        if (!vm.rule || !vm.authReady || vm.checking) {
            return;
        }
        vm.checking = true;
        vm.error = false;
        loginIfNeeded().then(function() {
            return $http.post('/api/blockers/element-hiding/validate', {rule: vm.rule},
                {headers: {Authorization: 'Bearer ' + token}});
        }).then(function(response) {
            vm.rule = response.data.rule;
            vm.duplicate = response.data.duplicate === true;
            vm.checked = !vm.duplicate && vm.matches === 1;
            if (vm.duplicate) {
                vm.status = vm.t.duplicate;
            } else if (vm.matches !== 1) {
                vm.status = vm.t.rechoose(vm.matches);
                vm.error = true;
            } else if (vm.coverage > 50) {
                vm.status = vm.t.wide;
            } else {
                vm.status = vm.t.valid;
            }
        }).catch(function(response) {
            vm.checked = false;
            vm.error = true;
            if (response && response.status === 401) {
                vm.status = vm.t.authFailed;
            } else if (response && response.status === 400) {
                vm.status = vm.t.invalid;
            } else {
                vm.status = vm.t.checkFailed;
            }
        }).finally(function() {
            vm.checking = false;
        });
    };

    vm.canApply = function() {
        return vm.checked && !vm.duplicate && !vm.saved && !vm.saving &&
            (vm.coverage <= 50 || vm.confirmWide);
    };

    vm.apply = function() {
        if (!vm.canApply()) {
            return;
        }
        vm.saving = true;
        vm.error = false;
        const savingRule = vm.rule;
        $http.post('/api/blockers/element-hiding/append', {rule: savingRule},
            {headers: {Authorization: 'Bearer ' + token}}).then(function(response) {
            if (vm.rule !== savingRule) {
                return;
            }
            vm.saved = true;
            vm.status = response.data.added ? vm.t.saved : vm.t.duplicate;
            if (!response.data.enabled) {
                vm.status += ' – ' + vm.t.disabled;
            }
            $window.parent.postMessage({type: 'eblocker-picker-save-result',
                added: response.data.added, enabled: response.data.enabled}, '*');
        }).catch(function(response) {
            vm.error = true;
            if (response && response.status === 401) {
                vm.status = vm.t.authExpired;
            } else {
                vm.status = vm.t.saveFailed;
            }
        }).finally(function() {
            vm.saving = false;
        });
    };

    vm.cancel = function() {
        $window.parent.postMessage({type: 'eblocker-picker-save-cancel'}, '*');
    };

    $window.addEventListener('message', onMessage);
    $scope.$on('$destroy', function() { $window.removeEventListener('message', onMessage); });
    loadToken();
    $window.parent.postMessage({type: 'eblocker-picker-save-ready'}, '*');
}
