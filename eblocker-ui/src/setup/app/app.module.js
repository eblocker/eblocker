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
// ** Angular libs
import angular from 'angular';
import ngMaterial from 'angular-material';
import ngAnimate from 'angular-animate';
import uiRouter from '@uirouter/angularjs';

import translate from 'angular-translate';
import 'angular-translate-loader-static-files';

import 'angular-translate-interpolation-messageformat';

import ngIdle from '../../../npm_integrations/ng-idle';
import 'moment/min/locales.min';
import moment from 'moment';


// ** Controllers
import MainController from './main.controller.js';
import AppController from './app.controller.js';
import ExpiredController from './common/expired.controller';
import IdleDialogController from './common/idle.dialog.controller';


// ** Configs
import LocationConfig from './_bootstrap/_configs/locationConfig';
import TranslationConfig from './_bootstrap/_configs/translationConfig';
import RoutesConfig from './_bootstrap/_configs/routeConfig';
import IdleConfig from './_bootstrap/_configs/idleConfig';
import APP_CONTEXT from './app.constants.js';
import LANG_FILENAMES from '../../shared/locale/langFileNames';
import IDLE_TIMES from '../../shared/_constants/idleTimes';

import ThemingProvider from './app.theme.js';
import AppRouter from './app.router.js';


// ** Components
import NetworkStatusComponent from './networkStatus/network-status.component';
import LicenseStatusComponent from './licenseStatus/license-status.component';
import DeviceStatusComponent from './deviceStatus/device-status.component';

// ** Services
import NetworkService from './service/network/network.service';
import LicenseService from './service/license/license.service';
import LanguageService from '../../shared/services/language/language.service';
import SystemService from '../../shared/services/system/SystemService';
import RedirectService from '../../shared/services/redirect/RedirectService';

// ** Custom Modules
/*
 * Modules have to be loaded here. Afterwards they are
 * accessible via their name as defined in the module e.g.
 * 'eblocker.logger' (see dependency reference for module
 * 'eblocker.setup' below).
 * TODO: why can't these module be included by
 * "import logger from '...'" and then simply
 * be referenced by variable, e.g. 'logger'.
 * logger.module.js may then have to be changed to
 * 'export default function LoggerModule() {' notation.
 * But this results in the error 'unknown provider loggerProvider <- logger <- appController'
 */
// import Logger from './service/logger/logger.module';
// import Security from './service/security/security.module';
// import Settings from './service/settings/settings.module';
//
import './service/logger/logger.module';
import './service/security/security.module';
import './service/settings/settings.module';

angular.module('eblocker.setup', [
    ngMaterial,
    ngAnimate,
    uiRouter,
    translate,
    ngIdle,
    'eblocker.logger',
    'eblocker.settings',
    'eblocker.security',
    'template.setup.app'
])
    .config(LocationConfig)
    .config(TranslationConfig)
    .config(ThemingProvider)
    .config(RoutesConfig)
    .config(IdleConfig)
    .config(AppRouter)
    .constant('APP_CONTEXT', APP_CONTEXT)
    .constant('LANG_FILENAMES', LANG_FILENAMES)
    .constant('moment', moment)
    .constant('IDLE_TIMES', IDLE_TIMES)
    .controller('AppController', AppController)
    .controller('MainController', MainController)
    .controller('ExpiredController', ExpiredController)
    .controller('IdleDialogController', IdleDialogController)
    .component('deviceStatus', DeviceStatusComponent)
    .component('licenseStatus', LicenseStatusComponent)
    .component('networkStatus', NetworkStatusComponent)
    .factory('SystemService', SystemService)
    .factory('RedirectService', RedirectService)
    .factory('NetworkService', NetworkService)
    .factory('LicenseService', LicenseService)
    .factory('LanguageService', LanguageService);
