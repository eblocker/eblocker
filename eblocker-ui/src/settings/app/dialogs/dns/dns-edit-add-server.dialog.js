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
export default function DnsAddEditServerController(logger, $mdDialog, entry, update, transport) {
    'ngInject';

    const vm = this;

    vm.entry = angular.copy(entry);
    vm.update = update;
    vm.transport = transport || 'custom';
    vm.maxLength = transport === 'doh' ? 230 : transport === 'dot' ? 270 :
        transport === 'plain' ? 45 : 16;
    vm.example = transport === 'plain' ? '9.9.9.9 or 2620:fe::fe' :
        transport === 'dot' ? '9.9.9.9|dns.quad9.net' :
        'https://9.9.9.9/dns-query|dns.quad9.net';
    vm.validServer = function(value) {
        if (vm.transport === 'custom') { return true; }
        if (!angular.isString(value)) { return false; }
        if (vm.transport === 'plain') { return validIp(value); }
        const parts = value.split('|');
        if (parts.length !== 2 || !/^[a-z0-9][a-z0-9.-]*[a-z0-9]$/i.test(parts[1])) { return false; }
        if (vm.transport === 'dot') {
            return validIp(parts[0]);
        }
        const match = /^https:\/\/([^/]+)\/dns-query$/i.exec(parts[0]);
        return !!match && (validIpv4(match[1]) ||
            /^\[[0-9a-f:.]+\]$/i.test(match[1]) && validIpv6(match[1].slice(1, -1)));
    };

    function validIp(value) {
        return validIpv4(value) || validIpv6(value);
    }

    function validIpv6(value) {
        if (!/^[0-9a-f:]+$/i.test(value) || !value.includes(':')) { return false; }
        const halves = value.split('::');
        if (halves.length > 2) { return false; }
        const groups = value.split(':').filter((group) => group.length > 0);
        if (!groups.every((group) => /^[0-9a-f]{1,4}$/i.test(group))) { return false; }
        return halves.length === 2 ? groups.length < 8 : groups.length === 8;
    }

    function validIpv4(value) {
        const octets = value.split('.');
        return octets.length === 4 && octets.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255);
    }

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.submit = function() {
        if (!vm.dnsServerForm.$valid || !vm.validServer(vm.entry.server)) {
            return;
        }
        $mdDialog.hide(vm.entry);
    };
}
