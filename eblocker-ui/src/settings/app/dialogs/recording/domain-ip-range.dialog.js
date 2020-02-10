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
export default function DomainIpRangeController($mdDialog, module) {
    'ngInject';

    const vm = this;

    function makeBits(count, array) {
        if (angular.isUndefined(array)) {
            array = [];
        }
        array.push(count + '');
        return count < 32 ? makeBits(count + 1, array) : array;
    }

    vm.bits = makeBits(8);

    vm.module = angular.copy(module);

    vm.originalModule = module;

    if (!angular.isDefined(vm.module.iprangemask)){
        vm.module.iprangemask=32;
    }
    //if (angular.isDefined(module.selected_domain)){
    //    vm.module.SelectedDomainIP = module.selected_domain;
    //}else{
    //	vm.module.SelectedDomainIP = module.url;
    //}
    if (angular.isDefined(module.flag)){
        if (module.flag === 'iprange'){
            // the user wants to make a rule based on the ip-address/-range,
            // therefore select the ip. Actual ip and range are already set via module.
            vm.module.selectedDomainIP = 'iprange';
        } else {
            // the user wants to make a rule based on the domain,
            // therefore select the proper domain.

            // has the user already made a decision about domain vs. parent domain?
            if (angular.isDefined(module.selectedDomain)){
                // if so, use the selected one
                vm.module.selectedDomainIP = module.selectedDomain;
            } else {
                // no selection made, assume recorded domain
                vm.module.selectedDomainIP = module.recordedDomain;
            }
        }
    } else{
        // if a domain is present, assume the user wants to use the domain and already select it?
        if (angular.isDefined(module.recordedDomain)){
            vm.module.selectedDomainIP = module.recordedDomain;
        } else {
            vm.module.selectedDomainIP = 'iprange';
        }
    }

//    vm.$watch('vm.module.iprangemask', function(newValue, oldValue){
//        vm.minIpRange(vm.module.recordedIp, newValue);
//        vm.maxIpRange(vm.module.recordedIp, newValue);
//    });

    vm.iprangemaskChanged = function(){
        vm.minIpRange(vm.module.recordedIp, vm.module.iprangemask);
        vm.maxIpRange(vm.module.recordedIp, vm.module.iprangemask);
    };


    vm.numericalIp = function(ipaddress) {
        let octetts = ipaddress.split('.'); // adress is a.b.c.d
        let numericalip = parseInt(octetts[0]);                 // contains a
        numericalip = numericalip * 256 + parseInt(octetts[1]); // contains a.b
        numericalip = numericalip * 256 + parseInt(octetts[2]); // contains a.b.c
        numericalip = numericalip * 256 + parseInt(octetts[3]); // contains a.b.c.d
        return numericalip;
    };

    vm.dottedIp = function(numip) {
        /*jshint bitwise: false*/
        let octett1 = numip & 255;
        let octett2 = ((numip >> 8) & 255);
        let octett3 = ((numip >> 16) & 255);
        let octett4 = ((numip >> 24) & 255);
        return octett4+'.'+octett3+'.'+octett2+'.'+octett1;
    };

    vm.minIpRange = function (ipaddress, netmask) {
        let numip = vm.numericalIp(ipaddress);
        netmask=parseInt(netmask);
        /*jshint bitwise: false*/
        // bitshift, removing any bits that are 'inside the net', leaving the nets address
        numip = numip >> (32-netmask);
        // filling with 0 from the right results in the lower bound
        numip = numip << (32-netmask);
        vm.minIPAddress = vm.dottedIp(numip);
    };

    vm.maxIpRange = function (ipaddress, netmask) {
        let numip = vm.numericalIp(ipaddress);
        netmask=parseInt(netmask);
        /*jshint bitwise: false*/
        // bitshift, removing any bits that are 'inside the net', leaving the address of the net
        numip = numip >> (32 - netmask);
        for (let i = 0; i < (32 - netmask); i++){
            numip = numip * 2 + 1; // *2 for left-shift, +1 to set rightmost-bit to 1
        }
        vm.maxIPAddress = vm.dottedIp(numip);
    };

    if (angular.isDefined(vm.module.selectedIp)){
        vm.maxIpRange(vm.module.selectedIp, vm.module.iprangemask);
        vm.minIpRange(vm.module.selectedIp, vm.module.iprangemask);
    } else {
        vm.maxIpRange(vm.module.recordedIp, vm.module.iprangemask);
        vm.minIpRange(vm.module.recordedIp, vm.module.iprangemask);
    }



    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.submit = function() {
        // transfer changes into vm.originalModule
        if (vm.module.selectedDomainIP === 'iprange'){
            // user wants to whitelist/etc. an ip-range
            // flag to store that the ip range is what matters
            vm.originalModule.flag = 'iprange';
            // store ip range
            vm.originalModule.selectedIp = vm.module.recordedIp; //vm.module.ip;
            vm.originalModule.iprangemask = vm.module.iprangemask; //vm.module.iprangemask
        } else{
            // user wants to whitelist/etc. a domain
            // flag to store that the domain is what matters
            vm.originalModule.flag = 'domain';
            // using 'selected_domain' since the user might want to edit again and select the original one
            vm.originalModule.selectedDomain = vm.module.selectedDomainIP;
        }

        $mdDialog.hide();
    };

}
