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

    const IP4_ADDRESS_REGEX = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/; // jshint ignore: line

    const IP6_FIELD_REGEX = /^[0-9a-fA-F]{1,4}$/;

    const IP_ADDRESS_OR_RANGE_REGEX = /^([0-9\.]+|[0-9a-fA-F:]+)(\/[0-9]+)?$/;

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

    function isIpv4Address(address) {
        if (!angular.isString(address)) {
            return false;
        }
        return IP4_ADDRESS_REGEX.test(address);
    }

    function isIpv6Address(address) {
        if (!angular.isString(address)) {
            return false;
        }
        // at most one placeholder is allowed
        const parts = address.split('::');
        if (parts.length > 2) {
            return false;
        }
        const placeholder = parts.length === 2;

        // max 8 fields are allowed
        const split = function(value) {
            if (value.length === 0) {
                return [];
            }
            return value.split(':');
        };
        const fieldsA = split(parts[0]);
        const fieldsB = placeholder ? split(parts[1]) : [];
        if ((!placeholder && fieldsA.length + fieldsB.length !== 8) || (placeholder && fieldsA.length + fieldsB.length > 7)) {
            return false;
        }

        const validateHexFields = function(fields) {
            for(let i = 0; i < fields.length; ++i) {
                if (!IP6_FIELD_REGEX.test(fields[i])) {
                    return false;
                }
            }
            return true;
        };
        return validateHexFields(fieldsA) && validateHexFields(fieldsB);
    }

    function isIpAddress(address) {
        return isIpv4Address(address) || isIpv6Address(address);
    }

    function isIpv4Range(ipRange) {
        return isIpRange(ipRange, isIpv4Address, 32);
    }

    function isIpv6Range(ipRange) {
        return isIpRange(ipRange, isIpv6Address, 128);
    }

    function isIpRange(ipRange, isIpAddress, maxLength) {
        if (!angular.isString(ipRange)) {
            return false;
        }
        const parts = ipRange.split('/');
        if (parts.length !== 2) {
            return false;
        }
        if (!isIpAddress(parts[0])) {
            return false;
        }
        const len = parseInt(parts[1]);
        if (isNaN(len)) {
            return false;
        }
        if (len > maxLength) {
            return false;
        }
        return true;
    }

    function isIpAddressOrRange(address) {
        if (!IP_ADDRESS_OR_RANGE_REGEX.test(address)) {
            return false;
        }
        return isIpAddress(address) || isIpv4Range(address) || isIpv6Range(address);
    }

    return {
        sortByVersion: sortByVersion,
        sortingKey: sortingKey,
        isIpAddress: isIpAddress,
        isIpv4Address: isIpv4Address,
        isIpv6Address: isIpv6Address,
        isIpv4Range: isIpv4Range,
        isIpv6Range: isIpv6Range,
        isIpAddressOrRange: isIpAddressOrRange
    };
}
