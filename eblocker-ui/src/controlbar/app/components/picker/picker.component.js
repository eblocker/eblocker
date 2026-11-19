/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
 * Licensed under the EUPL, Version 1.2 or later.
 */
export default {
    templateUrl: 'app/components/picker/picker.component.html',
    controller: PickerController,
    controllerAs: 'ctrl'
};

function PickerController($window) {
    'ngInject';
    this.handleClick = function() {
        $window.parent.postMessage({'type': 'start-eblocker-element-picker'}, '*');
    };
}
