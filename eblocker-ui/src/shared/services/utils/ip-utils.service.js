/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
export default function IpUtilsService() {
    'ngInject';

    /*
      Return a string that can be used as a sorting key for an IP address.
      The sort order is first by IP version, then by IP value.
    */
    function sortingKey(address) {
        if (address.includes('.')) { // IPv4
            return 'ipv4_' + normalizeIpv4(address);
        } else {
            return 'ipv6_' + normalizeIpv6(address);
        }
    }

    /*
      Sort IP addresses by version (first IPv4, then IPv6), then by value
    */
    function sortByVersion(addresses) {
        return angular.copy(addresses).sort(compareByVersion);
    }

    function compareByVersion(a, b) {
        const aIsIpv4 = a.includes('.');
        const bIsIpv4 = b.includes('.');

        if (aIsIpv4 && !bIsIpv4) {
            return -1;
        }
        if (!aIsIpv4 && bIsIpv4) {
            return 1;
        }
        // same protocol, sort by address:
        if (aIsIpv4) {
            return compare(normalizeIpv4(a), normalizeIpv4(b));
        } else {
            return compare(normalizeIpv6(a), normalizeIpv6(b));
        }
    }

    function normalizeIpv4(address) {
        return address.split('.')
            .map(function (part) {
                if (part.length === 1) { return '00' + part; }
                if (part.length === 2) { return '0' + part; }
                return part;
            })
            .join('.');
    }

    function normalizeIpv6(address) {
        const tpl = '0000:0000:0000:0000:0000:0000:0000:0000';
        const parts = address.split('::');

        // left part
        const left = normalizeIpv6Sequence(parts[0]);
        var norm = left + tpl.substr(left.length);

        // optional right part
        if (parts.length > 1) {
            const right = normalizeIpv6Sequence(parts[1]);
            norm = norm.substr(0, tpl.length - right.length) + right;
        }

        return norm;
    }

    function normalizeIpv6Sequence(sequence) {
        return sequence.split(':')
            .map(function (part) {
                if (part.length === 1) { return '000' + part; }
                if (part.length === 2) { return '00' + part; }
                if (part.length === 3) { return '0' + part; }
                return part;
            })
            .join(':');
    }

    function compare(a, b) {
        if (a < b) {
            return -1;
        } else if (a > b) {
            return 1;
        }
        return 0;
    }

    return {
        sortByVersion: sortByVersion,
        sortingKey: sortingKey
    };
}
