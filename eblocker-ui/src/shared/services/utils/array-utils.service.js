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
export default function ArrayUtilsService() {
    'ngInject';

    // ** Sort by name
    function sortByPropertyName(unsorted) {
        return angular.copy(unsorted).sort(compareByName);
    }

    function compareByName(a,b) {
        if (a.name.toUpperCase() < b.name.toUpperCase()) {
            return -1;
        } else if (a.name.toUpperCase() > b.name.toUpperCase()) {
            return 1;
        }
        return 0;
    }

    // ** Sort by any property ignoring case
    let varProp;

    function sortByProperty(unsorted, property) {
        varProp = property;
        return angular.copy(unsorted).sort(compareByProperty);
    }

    function compareByProperty(a, b) {
        const aNorm = getNormalizedValue(a, varProp);
        const bNorm = getNormalizedValue(b, varProp);
        return compare(aNorm, bNorm);
    }


    // ** Sort by any property ignoring case
    let firstOrder, fallBackOrder;
    function sortByPropertyWithFallback(unsorted, property, fallback) {
        firstOrder = property;
        fallBackOrder = fallback || property;
        return angular.copy(unsorted).sort(compareByPropertyWithFallBack);
    }

    function compareByPropertyWithFallBack(a, b) {
        const aNorm = getNormalizedValue(a, firstOrder);
        const bNorm = getNormalizedValue(b, firstOrder);
        const aFallbackNorm = getNormalizedValue(a, fallBackOrder);
        const bFallbackNorm = getNormalizedValue(b, fallBackOrder);
        return compare(aNorm, bNorm) !== 0 || firstOrder ===  fallBackOrder ?
            compare(aNorm, bNorm) : compare(aFallbackNorm, bFallbackNorm);
    }

    function getNormalizedValue(object, property) {
        // to ignore case we convert to upper case (if value is string).
        return angular.isString(object[property]) ? object[property].toUpperCase() : object[property];
    }

    function compare(a, b) {
        if (a < b) {
            return -1;
        } else if (a > b) {
            return 1;
        }
        return 0;
    }


    // ** Sort by any property minding the case
    function sortByPropertyWithCase(unsorted, property) {
        varProp = property;
        return angular.copy(unsorted).sort(compareByProperty);
    }

    function compareByPropertyWithCase(a,b) {
        if (a[varProp] < b[varProp]) {
            return -1;
        } else if (a[varProp] > b[varProp]) {
            return 1;
        }
        return 0;
    }


    // ** Check for property with value
    function containsByProperty(list, property, value) {
        let contained = false;
        list.forEach(function(each) {
            if (each[property] === value) {
                contained = true;
            }
        });
        return contained;
    }

    function contains(list, value) {
        let contained = false;
        list.forEach(function(each) {
            if (each === value) {
                contained = true;
            }
        });
        return contained;
    }

    function getIndexOf(list, entry, property) {
        let index = -1;
        list.forEach((tmp, i) => {
            if (entry[property] === tmp[property]) {
                index = i;
            }
        });
        return index;
    }

    function getItemBy(list, property, value) {
        if (!angular.isArray(list)) {
            return false;
        }
        const obj = list.reduce((a, b) => {
            return (a[property] === value && a) || (b[property] === value && b);
        }, list[0]);
        return angular.isDefined(obj) ? obj : false;
    }

    function removeByProperty(list, entry, property) {
        const ret = [];
        list.forEach((each) => {
            if (each[property] !== entry[property]) {
                ret.push(each);
            }
        });
        return ret;
    }

    return {
        sortByPropertyName: sortByPropertyName,
        sortByProperty: sortByProperty,
        sortByPropertyWithFallback: sortByPropertyWithFallback,
        containsByProperty: containsByProperty,
        contains: contains,
        getIndexOf: getIndexOf,
        getItemBy: getItemBy,
        removeByProperty: removeByProperty
    };
}
