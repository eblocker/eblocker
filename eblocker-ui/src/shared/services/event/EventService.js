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
export default function EventService(logger, $window) {
    'ngInject';

    function fireEvent(name) {
        try {
            // ** Constructor not supported by IE11 ...
            const event = new Event(name);
            $window.dispatchEvent(event);
        } catch(error) {
            // ** .. so we catch the error if necessary and try it another way.
            logger.debug('Error firing ' + name + ' event. You may be using IE.');
            const event = $window.document.createEvent('UIEvents');
            if (angular.isFunction(event.initUIEvent)) {
                event.initUIEvent(name, true, false, $window, 0);
                $window.dispatchEvent(event);
            } else {
                logger.error('Unable to initialize ' + name + ' event. Function initUIEvent is missing.');
                return;
            }
        }
    }

    return {
        fireEvent: fireEvent
    };
}
