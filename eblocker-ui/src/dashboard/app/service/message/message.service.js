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
export default function MessageService(logger, $q, $translate, $http, $interval, DataCachingService) {
    'ngInject';
    'use strict';

    const PATH = '/messages';
    let messagePromise, syncTimer;
    let numberOfAlerts = 0, numberOfInfo = 0;

    function getMessages(reload) {
        messagePromise = DataCachingService.loadCache(messagePromise, PATH, reload).then(function success(response) {
            const messages = response.data;

            for (let i = 0; i < messages.length; i++) {
                const message = messages[i];
                if (!angular.isDefined(message.titleKey)) {
                    message.title = message.titles[$translate.use()];
                }
                if (!angular.isDefined(message.contentKey)) {
                    message.content = message.contents[$translate.use()];
                }
            }
            updateMessageCount(messages);
            return $q.resolve({data: messages});
        }, function(response) {
            logger.error('Cannot load messages: ', response.status);
            return $q.reject(response.data);
        });
        return messagePromise;
    }

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncData() {
        getMessages(true);
    }

    function getNumberOfAlerts() {
        return numberOfAlerts;
    }

    function getNumberOfInfo() {
        return numberOfInfo;
    }

    function severitySortFunction(x, y) {
        if (x.messageSeverity === 'ALERT' && y.messageSeverity === 'INFO') {
            return 1;
        } else if (x.messageSeverity === 'INFO' && y.messageSeverity === 'ALERT') {
            return -1;
        }
        return 0;
    }

    function dateSortFunction(x, y) {
        if (x.date > y.date) {
            return 1;
        } else if (x.date < y.date) {
            return -1;
        }
        return 0;
    }

    function sortMessagesByDate(messages) {
        return messages.sort(dateSortFunction);
    }

    function sortMessagesBySeverity(messages) {
        return messages.sort(severitySortFunction);
    }

    function updateMessageCount(messages) {
        numberOfAlerts = 0;
        numberOfInfo = 0;
        messages.forEach(function(msg) {
            if (msg.messageSeverity === 'ALERT') {
                numberOfAlerts++;
            } else if (msg.messageSeverity === 'INFO') {
                numberOfInfo++;
            } else {
                logger.debug('Found undefined severity value: ', msg);
            }
        });
    }

    function updateDoNotShowAgain(id, doNotShowAgainStatus){
        const doNotShowAgain = angular.isDefined(doNotShowAgainStatus) ? !doNotShowAgainStatus : true;
        return $http.put('/messages/donotshowagain',{'messageId': id , 'doNotShowAgain': doNotShowAgain})
            .then(function success(response) {
                logger.info('Successfully called donotshowagain for message ' + id);
            }, function error(response) {
                logger.error('Failed to call donotshowagain for message ' + id);
        });
    }

    function hideMessage(id) {
        return $http.post('/messages/hide', {messageId: id}).then(function success() {
            getMessages(true);
        }, function error(response) {
            logger.error('Failed to call hide message for ' + id + '. Server responded: ', response);
        });
    }

    function executeAction(id) {
        return $http.post('/messages/action',{'messageId': id}).then(function success() {
            getMessages(true);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getMessages: getMessages,
        getNumberOfAlerts: getNumberOfAlerts,
        getNumberOfInfo: getNumberOfInfo,
        sortMessagesByDate: sortMessagesByDate,
        sortMessagesBySeverity: sortMessagesBySeverity,
        updateDoNotShowAgain: updateDoNotShowAgain,
        hideMessage: hideMessage,
        executeAction: executeAction
    };

}
