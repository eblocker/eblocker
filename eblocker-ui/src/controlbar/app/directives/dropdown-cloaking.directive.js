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
export default function DropdownCloakingDirective($document, ControlbarService) {
    'ngInject';

    return {
        restrict: 'E',
        scope: {
            label: '=',                 // all
            imageLabel: '=',            // all
            imageUrl: '=',              // all
            userAgentList: '=',         // user agent specific
            selectedUserAgent: '=',     // user agent specific
            showSslWarning: '&',        // user agent specific
            setUserAgent: '&',          // user agent specific
            setCustomUserAgent: '&',    // user agent specific
            closeDropdown: '=',         // some -- used to close dropdown when dialog opens
            tooltip: '='                // all
        },
        templateUrl: 'app/directives/dropdown-cloaking.directive.html',
        replace: true,
        link: function(scope, element) {
            // otherwise click event will re-open the dropdown
            // when closeDropdown should actually close it.
            scope.dialogOpen = false;
            scope.closeDropdown = {
                now: function() {
                    scope.dialogOpen = true;
                    setIsOpen(false);
                }
            };

            setIsOpen(false);

            // See fix below:
            // two click-events when toggling prevent correct
            // ControlbarService update. So we explicitly set
            // ControlbarService.isDropdownOpen to true, when
            // the click was on *this button*. This however prevents
            // us from closing the dropdown again (toggle).
            // So we use a bool value to leave out
            // ControlbarService.isDropdownOpen when toggle
            // is used to close the dropdown.
            scope.toggled = false;

            scope.toggleSelect = function(){
                scope.toggled = scope.isOpen;
                setIsOpen(!scope.isOpen);
            };

            function setIsOpen(bool) {
                scope.isOpen = bool;
                ControlbarService.isDropdownOpen(scope.isOpen, scope.label);
            }

            $document.bind('click', function(event) {
                let isClickedElementChildOfPopup = element
                    .find(event.target)
                    .length > 0;

                if (isClickedElementChildOfPopup) {
                    // fixes issue where click on background closes the entire
                    // controlbar and not just the dropdown (click on button
                    // toggles dropdown and fires (two) click-event(s), which results
                    // in setting flag to false (see below) and then second event
                    // ends up here. So we need to (re) open the dropdown by setting
                    // the flag again (only required for background-click closing
                    // dropdown NOT controlbar)
                    if (!scope.toggled && !scope.dialogOpen) {
                        scope.toggled = false;
                        scope.dialogOpen = false;
                        setIsOpen(true);
                    }
                    return;
                }
                setIsOpen(false);
                scope.$apply();
            });

            scope.$on('$destroy', function() {
                $document.unbind('click');
            });
        }
    };
}
