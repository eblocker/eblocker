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
    templateUrl: 'app/cards/filterStatistics/filterStatisticsDiagram.component.html',
    controller: FilterStatisticsDiagramController,
    bindings: {
        binSizeMinutes: '<',
        numberOfBins: '<',
    }
};

function FilterStatisticsDiagramController($scope, $interval, $q, $translate, filterStatistics, LanguageService,
                                           DeviceSelectorService, EVENTS) {
    'ngInject';
    'use strict';

    const ctrl = this;

    ctrl.retrievedStatistics = {};

    ctrl.$onInit = loadData;

    $scope.$on(EVENTS.DEVICE_SELECTED, loadData);

    function loadData() {
        let device = DeviceSelectorService.getSelectedDevice();
        ctrl.labelFormat = getLabelFormat(ctrl.numberOfBins, ctrl.binSizeMinutes);
        filterStatistics
            .getStatistics(device.id, ctrl.numberOfBins, ctrl.binSizeMinutes)
            .then(function(response) {
                ctrl.retrievedStatistics = response;
                ctrl.updateChart(response);
                ctrl.updatePieChart(response.summary);
                ctrl.updateDisplayBin(response.summary);
            });
    }

    var reasons = [ 'TRACKERS', 'ADS', 'CUSTOM' ];
    var colorsByReason = {
        ADS: '#803690',
        CUSTOM: '#46BFBD',
        TRACKERS: '#00ADF9',
    };

    ctrl.lineColors = {};
    for(let i = 0; i < reasons.length; ++i) {
        ctrl.lineColors[reasons[i]] = {
            'background-color': colorsByReason[reasons[i]]
        };
    }

    ctrl.chart = {
        series: [
        ],
        labels: [],
        data: [
        ],
        options: {
            scales: {
                yAxes: [{
                    ticks: {
                        beginAtZero: true
                    },
                    stacked: true
                }]
            }
        },
        responsive: true,
        colors: []
    };

    ctrl.pie = {
        data: [1,2,3],
        labels: [],
        options: {},
        colors: ['#00ADF9', '#803690', '#46BFBD']
    };

    ctrl.displayBin = {};

    ctrl.chartClick = function(points) {
        let bin;
        if (points.length === 0) {
            bin = ctrl.retrievedStatistics.summary;
        } else {
            bin = ctrl.retrievedStatistics.bins[points[0]._index];
        }
        ctrl.updateDisplayBin(bin);
        ctrl.updatePieChart(bin);
    };

    ctrl.updateDisplayBin = function(bin) {
        let displayBin = {
            blockedAds: bin.blockedQueriesByReason['ADS'] || 0,
            blockedTrackers: bin.blockedQueriesByReason['TRACKERS'] || 0,
            blockedCustom: bin.blockedQueriesByReason['CUSTOM'] || 0
        };
        displayBin.blockedQueries = displayBin.blockedAds + displayBin.blockedTrackers + displayBin.blockedCustom;
        ctrl.displayBin = displayBin;
    };

    ctrl.updatePieChart = function(bin) {
        let data = [];
        let labels = [];

        let sum = 0;
        for(let i = 0; i < reasons.length; ++i) {
            let blockedQueries = bin.blockedQueriesByReason[reasons[i]] || 0;
            sum += blockedQueries;
            data.push(blockedQueries);
            labels.push($translate.instant('DNS_STATISTICS.CARD.CHART.SERIES.' + reasons[i]));
        }
        for(let i = 0; i < reasons.length; ++i) {
            data[i] = (data[i] / sum * 100).toFixed(2);
        }

        ctrl.pie.data = data;
        ctrl.pie.labels = labels;
    };

    ctrl.updateChart = function(response) {
        ctrl.chart.series = [];
        ctrl.chart.data = [];
        ctrl.chart.colors = [];
        for (let i = 0; i < reasons.length; ++i) {
            ctrl.chart.series.push($translate.instant('DNS_STATISTICS.CARD.CHART.SERIES.' + reasons[i]));
            ctrl.chart.data.push([]);
            ctrl.chart.colors.push(colorsByReason[reasons[i]]);
        }

        ctrl.chart.labels = [];
        for(let i = 0; i < response.bins.length; ++i) {
            ctrl.chart.labels.push(LanguageService.getDate(response.bins[i].end * 1000, ctrl.labelFormat));
            for(let j = 0, v; j < reasons.length; ++j) {
                v = response.bins[i].blockedQueriesByReason[reasons[j]] || 0;
                ctrl.chart.data[j].push(v);
            }
        }
        return response.summary;
    };

    function getLabelFormat(numberOfBins, binSizeMinutes) {
        let diagramWidth = numberOfBins * binSizeMinutes;
        if (diagramWidth >= 60 * 24 * 15) {
            // 15 days or more
            return $translate.instant('DNS_STATISTICS.CARD.CHART.L18N.LONG');
        } else if (diagramWidth >= 60 * 36) {
            // 36 hours or more
            return $translate.instant('DNS_STATISTICS.CARD.CHART.L18N.MIDDLE');
        } else {
            // less than 36 hours
            return $translate.instant('DNS_STATISTICS.CARD.CHART.L18N.SHORT');
        }
    }
}
