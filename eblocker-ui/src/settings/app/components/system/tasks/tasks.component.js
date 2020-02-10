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
    templateUrl: 'app/components/system/tasks/tasks.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $timeout, TableService, TasksService, DialogService, LanguageService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.config = {
        columns: {}
    };

    const showHeader = function(header) {
        return vm.config.columns[header._tasksViewKey];
    };

    vm.table = {
        id: TableService.getUniqueTableId('system-tasks-table'),
        header: [
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.NAME',
                isSortable: true,
                sortingKey: 'name',
                _tasksViewKey: 'NAME',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.EXECUTOR',
                isSortable: true,
                sortingKey: 'executor',
                _tasksViewKey: 'EXECUTOR',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.STATUS',
                isSortable: true,
                sortingKey: 'status',
                _tasksViewKey: 'STATUS',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.TYPE',
                isSortable: true,
                sortingKey: 'type',
                _tasksViewKey: 'TYPE',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.EXECUTIONS',
                isSortable: true,
                sortingKey: 'executions',
                _tasksViewKey: 'EXECUTIONS',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.STARTED',
                isSortable: true,
                sortingKey: 'started',
                _tasksViewKey: 'STARTED',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.FINISHED',
                isSortable: true,
                sortingKey: 'finished',
                _tasksViewKey: 'FINISHED',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.RUNTIME.TOTAL',
                isSortable: true,
                sortingKey: 'totalRuntime',
                _tasksViewKey: 'TOTAL',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.RUNTIME.AVG',
                isSortable: true,
                sortingKey: 'avgRuntime',
                _tasksViewKey: 'AVG',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.RUNTIME.MIN',
                isSortable: true,
                sortingKey: 'minRuntime',
                _tasksViewKey: 'MIN',
                showHeader: showHeader
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.TASKS.HEADERS.RUNTIME.MAX',
                isSortable: true,
                sortingKey: 'maxRuntime',
                _tasksViewKey: 'MAX',
                showHeader: showHeader
            }
        ],
        data: [],
        callback: {}
    };

    vm.schedulerTable = {
        id: TableService.getUniqueTableId('system-tasks-scheduler-table'),
        header: [
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.NAME',
                isSortable: true,
                sortingKey: 'name',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.ACTIVE_COUNT',
                isSortable: true,
                sortingKey: 'activeCount',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.COMPLETED_TASK_COUNT',
                isSortable: true,
                sortingKey: 'completedTaskCount',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.CORE_POOL_SIZE',
                isSortable: true,
                sortingKey: 'corePoolSize',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.KEEP_ALIVE_TIME',
                isSortable: true,
                sortingKey: 'keepAliveTime',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.LARGEST_POOL_SIZE',
                isSortable: true,
                sortingKey: 'largestPoolSize',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.POOL_SIZE',
                isSortable: true,
                sortingKey: 'poolSize',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.QUEUE_LENGTH',
                isSortable: true,
                sortingKey: 'queueLength',
            },
            {
                label: 'ADMINCONSOLE.TASKS.TABLES.SCHEDULERS.HEADERS.TASK_COUNT',
                isSortable: true,
                sortingKey: 'taskCount',
            }
        ],
        data: [],
    };

    function loadLog() {
        TasksService.getLog().then(function (response) {
            function mapStatus(task) {
                if (task.running > 0) {
                    return 'RUNNING';
                }
                if (task.exception) {
                    return 'ERROR';
                }
                return 'OK';
            }

            function mapDateTime(dt, format) {
                if (!dt) {
                    return null;
                }
                return LanguageService.getDate(dt, format);
            }

            function mapType(schedule) {
                if (!schedule) {
                    return 'ONCE';
                }

                return schedule.type.toUpperCase();
            }

            vm.table.data = [];
            Object.keys(response).forEach(function (executor) {
                response[executor].forEach(function (task) {
                    let executed = task.executions - task.running;
                    vm.table.data.push({
                        name: task.name,
                        executor: executor,
                        status: mapStatus(task),
                        type: mapType(task.schedule),
                        exception: task.exception,
                        running: { 'n': task.running },
                        executions: task.executions,
                        lastStart: mapDateTime(task.lastStart, 'ADMINCONSOLE.TASKS.TIME_FORMAT'),
                        lastStop: mapDateTime(task.lastStop, 'ADMINCONSOLE.TASKS.TIME_FORMAT'),
                        lastStartTooltip: mapDateTime(task.lastStart, 'ADMINCONSOLE.TASKS.TIME_FORMAT_TOOLTIP'),
                        lastStopTooltip: mapDateTime(task.lastStop, 'ADMINCONSOLE.TASKS.TIME_FORMAT_TOOLTIP'),
                        totalRuntime: executed ? task.totalRuntime : null,
                        avgRuntime: executed ?  task.totalRuntime / executed : null,
                        minRuntime: executed ? task.minRuntime : null,
                        maxRuntime: executed ? task.maxRuntime : null,
                        schedule: task.schedule,
                        columns: vm.config.columns
                    });
                });
            });
            reloadTable();
        }, function error(response) {
            logger.error('Could not load tasks: ', response);
        });
    }

    function loadStats() {
        TasksService.getStats().then(function(stats) {
            vm.schedulerTable.data = Object.keys(stats).map(function(e) {
                stats[e].name = e;
                return stats[e];
            });
        });
    }

    vm.$onInit = function() {
        vm.loadConfig().then(function() {
            // wait until config is loaded, so that columns is set
            loadLog();
        });
        loadStats();
    };

    function updateConfig(config) {
        vm.config = {
            enabled: config.enabled,
            columns: {}
        };

        vm.table.header.forEach(function(header) {
            vm.config.columns[header._tasksViewKey] = true;
        });

        vm.table.data.forEach((e) => {
            // set column-config for each entry, so that table can use updated values in tasks-table.template
            e.columns = vm.config.columns;
        });

        config.hiddenColumns.forEach(function(column) {
            if (vm.config.columns[column]) {
                vm.config.columns[column] = false;
            }
        });
    }

    function reloadTable() {
        if (angular.isFunction(vm.table.callback.reload)) {
            $timeout(vm.table.callback.reload, 0);
        }
    }

    vm.loadConfig = function() {
        return TasksService.getConfig().then(updateConfig, function error(response) {
            logger.error('Could not load config', response);
        });
    };

    function saveConfig(newConfig) {
        var config = {
            enabled: newConfig.enabled,
            hiddenColumns: []
        };
        Object.entries(newConfig.columns).forEach(function(kv) {
            if (!kv[1]) {
                config.hiddenColumns.push(kv[0]);
            }
        });
        TasksService.setConfig(config).then(updateConfig, function error(response) {
            logger.error('Could not save config', response);
        });
    }

    vm.editConfig = function(event) {
        DialogService.editTasksViewConfig(event, angular.copy(vm.config), vm.table.header).then(function(config) {
            saveConfig(config);
        });
    };

    vm.refresh = function() {
        loadLog();
        loadStats();
    };
}
