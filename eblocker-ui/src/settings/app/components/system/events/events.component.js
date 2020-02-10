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
    templateUrl: 'app/components/system/events/events.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, EventService, NotificationService, TableService, DialogService, LanguageService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.dateFormat = 'ADMINCONSOLE.EVENTS.DATE_FORMAT';
    vm.timeFormat = 'ADMINCONSOLE.EVENTS.TIME_FORMAT';

    vm.$onInit = function() {
        reloadEvents();
    };

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('system-events-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            isSortable: false,
            showOnSmallTable: false,
            isXsColumn: true
        },
        {
            label: 'ADMINCONSOLE.EVENTS.TABLE.COLUMN.DATE',
            isSortable: true,
            isReversed: true,
            sortingKey: 'timestamp'
        },
        {
            label: 'ADMINCONSOLE.EVENTS.TABLE.COLUMN.TIME',
            flexGtXs: 15,
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.EVENTS.TABLE.COLUMN.EVENT',
            flexGtXs: 55,
            isSortable: false
        }
    ];
    // ## END: TABLE

    vm.filteredTableData = [];
    vm.reloadEvents = reloadEvents;
    vm.deleteEvents = deleteEvents;
    vm.deleteEventsMode = 'WEEK';

    function getEventDate(timestamp){
        return LanguageService.getDate(timestamp*1000, vm.dateFormat);
    }

    function getEventTime(timestamp){
        return LanguageService.getDate(timestamp*1000, vm.timeFormat);
    }

    function reloadEvents() {
        EventService.getEvents().then(function(response) {
            // Translate dates, times in response.data
            for (var i = 0; i < response.data.length; i++) {
                response.data[i].eventDate = getEventDate(response.data[i].timestamp);
                response.data[i].eventTime = getEventTime(response.data[i].timestamp);
            }
            vm.filteredTableData = response.data;
        }, function error(response) {
            logger.error('Could not load events: ', response);
        });
    }

    function deleteEvents(event) {
        DialogService.deleteEventsConfirm(event, vm.deleteEventsMode, deleteOk)
            .then(confirmationDialogDeleted, function() {});
    }

    function deleteOk() {
        return deleteEventsMode().then(function success() {
            reloadEvents();
        }, function(response) {
            return $q.reject(response);
        });
    }

    function confirmationDialogDeleted(subject) {
        NotificationService.info('ADMINCONSOLE.EVENTS.NOTIFICATION.' + subject.name);
    }

    function deleteEventsMode() {
        return EventService.deleteEvents(vm.deleteEventsMode).then(function success(response) {
            return response;
        }, function(response) {
            NotificationService.error('ADMINCONSOLE.EVENTS.NOTIFICATION.ERROR_DELETE', response);
            return $q.reject(response);
        });
    }
}
