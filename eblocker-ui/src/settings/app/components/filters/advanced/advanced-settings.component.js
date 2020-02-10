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
    templateUrl: 'app/components/filters/advanced/advanced-settings.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        captivePortal: '<',
        compressionMode: '<',
        doNotTrack: '<',
        referrer: '<',
        webRtc: '<',
        sslEnabled: '<'
    }
};

function Controller(logger, TableService, CaptivePortalService, CompressionService, DoNotTrackService,
                    ReferrerService, WebRtcService, $translate) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.resetDefault = resetDefault;

    vm.$onInit = function() {
        vm.templateCallback = {
            sslEnabled: vm.sslEnabled
        };
        vm.tableData = generateTableData();
    };

    // ** START: TABLE#
    vm.tableId = TableService.getUniqueTableId('filters-advanced-settings-table');

    // ** TODO: if sorting is required, the help-texts need to be fixed.
    // The overlay is has property 'position: absolute', which causes it to stay at the same place on re-order
    vm.tableHeaderConfig = [
        {
            label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.TABLE.COLUMN.STATUS',
            isSortable: false,
            showOnSmallTable: false,
            flex: 15,
            sortingKey: ''
        },
        {
            label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.TABLE.COLUMN.NAME',
            isSortable: false,
            flexGtXs: 25,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.TABLE.COLUMN.VALUE',
            isSortable: false,
            sortingKey: ''
        },
        {
            label: '',
            isSortable: false,
            showOnLargeTable: false
        }
    ];

    // ### TABLE END

    function resetDefault() {
        logger.debug('Dummy function: reset default.');
    }

    function generateTableData() {
        const tableData = [];
        tableData.push({
            name: $translate.instant('ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.LABEL'),
            setValue: setCapitivePortalState,
            selectedValue: vm.captivePortal,
            isActive: function () {
                return true;
            },
            tooltipSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.TOOLTIP_SSL_FILTER',
            tooltipNoSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.TOOLTIP_NO_SSL_FILTER',
            tooltipSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.TOOLTIP_SSL_NO_FILTER',
            tooltipNoSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.TOOLTIP_NO_SSL_NO_FILTER', // jshint ignore: line
            options: [
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.ENABLED', value: true},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.CAPTIVE_PORTAL.DISABLED', value: false}
            ],
            template: 'app/components/filters/advanced/help-filters-captive-portal.template.html'
        });
        tableData.push({
            name: $translate.instant('ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.LABEL'),
            selectedValue: vm.compressionMode,
            setValue: setCompressionMode,
            isActive: function (value) {
                return value !== 'OFF';
            },
            tooltipSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.TOOLTIP_SSL_FILTER',
            tooltipNoSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.TOOLTIP_NO_SSL_FILTER',
            tooltipSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.TOOLTIP_SSL_NO_FILTER',
            tooltipNoSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.TOOLTIP_NO_SSL_NO_FILTER', // jshint ignore: line
            options: [
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.MODE.OFF', value: 'OFF'},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.MODE.VPN_CLIENTS_ONLY',
                    value: 'VPN_CLIENTS_ONLY'},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_COMPRESSION.MODE.ALWAYS', value: 'ALWAYS'}
            ],
            template: 'app/components/filters/advanced/help-filters-web-compression.template.html'
        });
        tableData.push({
            name: $translate.instant('ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.LABEL'),
            selectedValue: vm.webRtc,
            setValue: setWebRtcState,
            isActive: function (value) {
                return value;
            },
            tooltipSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.TOOLTIP_SSL_FILTER',
            tooltipNoSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.TOOLTIP_NO_SSL_FILTER',
            tooltipSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.TOOLTIP_SSL_NO_FILTER',
            tooltipNoSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.TOOLTIP_NO_SSL_NO_FILTER', // jshint ignore: line
            options: [
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.ENABLED', value: true},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.WEB_RTC.DISABLED', value: false}
            ],
            template: 'app/components/filters/advanced/help-filters-web-rtc.template.html'
        });
        tableData.push({
            name: $translate.instant('ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.LABEL'),
            selectedValue: vm.referrer,
            setValue: setReferrerState,
            isActive: function (value) {
                return value;
            },
            tooltipSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.TOOLTIP_SSL_FILTER',
            tooltipNoSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.TOOLTIP_NO_SSL_FILTER',
            tooltipSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.TOOLTIP_SSL_NO_FILTER',
            tooltipNoSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.TOOLTIP_NO_SSL_NO_FILTER', // jshint ignore: line
            options: [
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.ENABLED', value: true},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.REFERRER.DISABLED', value: false}
            ],
            template: 'app/components/filters/advanced/help-filters-referrer.html'
        });
        tableData.push({
            name: $translate.instant('ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.LABEL'),
            selectedValue: vm.doNotTrack,
            setValue: setDoNotTrackState,
            isActive: function (value) {
                return value;
            },
            tooltipSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.TOOLTIP_SSL_FILTER',
            tooltipNoSslFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.TOOLTIP_NO_SSL_FILTER',
            tooltipSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.TOOLTIP_SSL_NO_FILTER',
            tooltipNoSslNoFilter: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.TOOLTIP_NO_SSL_NO_FILTER', // jshint ignore: line
            options: [
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.ENABLED', value: true},
                {label: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.SETTING.DO_NOT_TRACK.DISABLED', value: false}
            ],
            template: 'app/components/filters/advanced/help-filters-do-not-track.html'
        });
        return tableData;
    }


    //Enable/Disable Captive Portal responding (globally)
    function setCapitivePortalState(value) {
        const config = {
            captivePortalResponderEnabled: value
        };
        CaptivePortalService.set(config).then(function success() {
            logger.debug('Set state of Google Captive Portal responder: ' + value);
        });
    }

    function setCompressionMode(value) {
        CompressionService.set(value).then(function success(response) {
            logger.debug('Set compression mode to: ', response.data);
        });
    }

    function setDoNotTrackState(value) {
        const config = {
            dntHeaderEnabled: value
        };
        DoNotTrackService.set(config).then(function success() {
            logger.debug('Set state of DNT Header: ' + value);
        });
    }

    function setReferrerState(value) {
        const config = {
            httpRemovingEnabled: value
        };
        ReferrerService.set(config).then(function success() {
            logger.debug('Set state of Removing HTTP Referer Header: ' + value);
        });
    }

    function setWebRtcState(value) {
        const config = {
            webRTCBlockEnabled: value
        };
        WebRtcService.set(config).then(function success() {
            logger.debug('Set WebRTC blocking state: ' + value);
        });
    }
}
