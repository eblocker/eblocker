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
export default {
    templateUrl: 'app/cards/message/message.component.html',
    controller: MessageController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function MessageController($interval, LanguageService, MessageService, CardService, $timeout, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'MESSAGE'; // 'card-4';

    let updateInterval;

    vm.cardStatus = '';
    vm.messages = [];

    vm.getNumberOfAlerts = getNumberOfAlerts;
    vm.getNumberOfInfo = getNumberOfInfo;
    vm.hasMessages = hasMessages;

    vm.getCardStatus = getCardStatus;

    vm.clickedActionButton = clickedActionButton;
    vm.clickedHideButton = clickedHideButton;
    vm.updateDoNotShowAgain = updateDoNotShowAgain;

    vm.isMessageLoading = isMessageLoading;
    vm.refreshMessages = refreshMessages;
    vm.setShowDetails = setShowDetails;

    vm.$onInit = function() {
        getMessages();
        startInterval();
        DataService.registerComponentAsServiceListener(CARD_NAME, 'MessageService');
    };

    vm.$onDestroy = function() {
        stopInterval();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'MessageService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function startInterval() {
        if (angular.isUndefined(updateInterval)) {
            updateInterval = $interval(intervalExpired, 2000);
        }
    }

    function stopInterval() {
        if (angular.isDefined(updateInterval)) {
            $interval.cancel(updateInterval);
            updateInterval = undefined;
        }
    }

    function intervalExpired() {
        getMessages(false);
    }

    function clickedActionButton(message) {
        MessageService.executeAction(message.id).then(function success() {
            getMessages();
        });
    }

    function clickedHideButton(message) {
        MessageService.hideMessage(message.id).then(function success() {
            getMessages();
        });
    }

    function updateDoNotShowAgain(message) {
        MessageService.updateDoNotShowAgain(message.id, message.doNotShowAgain);
    }

    function hasMessages() {
        return getNumberOfAlerts() > 0 || getNumberOfInfo() > 0;
    }

    function getCardStatus() {
        if (getNumberOfAlerts() > 0) {
            return 'ERROR';
        }
            return '';
    }

    function getNumberOfAlerts() {
        return MessageService.getNumberOfAlerts();
    }

    function getNumberOfInfo() {
        return MessageService.getNumberOfInfo();
    }

    function getMessagesSuccess(response) {
        vm.messages = MessageService.sortMessagesByDate(angular.copy(response.data));
        vm.messages = MessageService.sortMessagesBySeverity(vm.messages);
        vm.messages.reverse();
        vm.messages.forEach((e)=>{
            e.displayDate = LanguageService.getDate(e.date, 'MESSAGE.CARD.FORMAT_DATE');
        });
        vm.isLoading = false;
    }

    function isMessageLoading() {
        return vm.isLoading;
    }

    function refreshMessages() {
        getMessages(true);
    }

    function getMessages(reload) {
        vm.isLoading = true;
        vm.messages.length = 0;
        MessageService.getMessages(reload).then(getMessagesSuccess).finally(function(){
            vm.isLoading = false;
        });
    }

    function setShowDetails(show) {
        vm.showDetails = show;
        if (show) {
            stopInterval();
        } else {
            startInterval();
        }
    }
}
