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
export default function MessageService($http, $q, $translate, logger) {
    'ngInject';
    'use strict';

    let PATH = '/api/messages';

    let messagePromise;

    let hasAlert = false;
    let rejected = false;

    function getMessages(reload) {
        if (angular.isUndefined(messagePromise) || reload || rejected) {
            rejected = false;
            messagePromise = $http.get(PATH).then(function success(response) {
                return getMessagesForThisSession(response.data);
            }, function error(response) {
                logger.error('Cannot load messages: ', response.status);
                rejected = true;
                $q.reject(response);
            });
        }
        return messagePromise;
    }

    function getMessagesForThisSession(messages) {
        const tmp = [];
        for (let i = 0; i < messages.length; i++) {
            let message = messages[i];
            if (!angular.isDefined(message.titleKey)) {
                message.title = message.titles[$translate.use()];
            }
            if (!angular.isDefined(message.contentKey)) {
                message.content = message.contents[$translate.use()];
            }
            if (messages[i].messageSeverity === 'ALERT') {
                hasAlert = true;
            }
            tmp.push(message);
        }
        return tmp;
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

    function removeMessage(messageId) {
        getMessages().then(function(messages) {
            let toBeRemoved = -1;
            for (let i = 0; i < messages.length; i++) {
                if (messages[i].id === messageId) {
                    toBeRemoved = i;
                }
            }
            if (toBeRemoved >= 0) {
                messages.splice(toBeRemoved, 1);
            }
        });
    }

    function updateDoNotShowAgain(id, doNotShowAgainStatus){
        let doNotShowAgain = angular.isDefined(doNotShowAgainStatus) ? !doNotShowAgainStatus : true;
        return $http.put(PATH + '/donotshowagain',{'messageId': id , 'doNotShowAgain': doNotShowAgain})
            .then(function success(response) {
                logger.info('Successfully called donotshowagain for message ' + id);
                return response;
            }, function error(response) {
                logger.error('Failed to call donotshowagain for message ' + id);
                $q.reject(response);
            });
    }

    function hideMessage(id) {
        return $http.post(PATH + '/hide', {messageId: id}).then(function success() {
            removeMessage(id);
            updateAlertStatus();
        }, function error(response) {
            logger.error('Failed to call hide message for ' + id + '. Server responded: ', response);
            $q.reject(response);
        });
    }

    function executeAction(id) {
        return $http.post(PATH + '/action',{'messageId': id}).then(function success() {
            removeMessage(id);
            updateAlertStatus();
        }, function error(response) {
            logger.error('Failed to execute action with id ', id, ' ', response);
            $q.reject(response);
        });
    }

    function updateAlertStatus() {
        getMessages().then(function(messages) {
            var alert = false;
            messages.forEach(function(msg) {
                if (msg.messageSeverity === 'ALERT') {
                    alert = true;
                }
            });
            hasAlert = alert;
        });
    }

    function hasAlertMessage() {
        return hasAlert;
    }

    return {
        getMessages: getMessages,
        sortMessagesByDate: sortMessagesByDate,
        sortMessagesBySeverity: sortMessagesBySeverity,
        updateDoNotShowAgain: updateDoNotShowAgain,
        hideMessage: hideMessage,
        hasAlertMessage: hasAlertMessage,
        executeAction: executeAction
    };
}
