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
// ** JQuery
import '../../../npm_integrations/jQuery';

// ** Angular libs
import angular from 'angular';
import ngMaterial from 'angular-material';
import ngAnimate from 'angular-animate';
import ngMessage from 'angular-messages';
import uiRouter from '@uirouter/angularjs';

import translate from 'angular-translate';
import 'angular-translate-loader-static-files';

import 'angular-translate-interpolation-messageformat';

import ngIdle from '../../../npm_integrations/ng-idle';

import 'moment/min/locales.min';
import moment from 'moment';


// ** Controllers
import NotificationController from '../../shared/services/notification/NotificationController';
import ConfirmationDialogController from '../../shared/dialogs/confirmation/confirmation.dialog';


// ** Configs
import LocationConfig from './_bootstrap/_configs/locationConfig';
import TranslationConfig from './_bootstrap/_configs/translationConfig';
import RoutesConfig from './_bootstrap/_configs/routeConfig';
import IdleConfig from './_bootstrap/_configs/idleConfig';
import APP_CONTEXT from './advice.constants.js';
import LANG_FILENAMES from '../../shared/locale/langFileNames';
import IDLE_TIMES from '../../shared/_constants/idleTimes';

import ThemingProvider from '../../shared/theme/eblocker.theme';


// ** Components
import AdviceComponent from './advice.component';
import WelcomeComponent from './welcome/welcome.component';
import ReminderComponent from './reminder/reminder.component';

// ** Services
import RegistrationService from './service/registration/RegistrationService';
import ConsoleService from './service/console/ConsoleService';
import NotificationService from '../../shared/services/notification/NotificationService';
import ArrayUtilsService from '../../shared/services/utils/array-utils.service';
import AccessContingentService from '../../shared/services/parentalControl/AccessContingentService';
import DeviceService from './service/device/DeviceService';
import DataCachingService from '../../shared/services/caching/DataCachingService';
import LoadingService from './service/system/LoadingService';
import DialogService from './service/dialog/DialogService';
import LanguageService from '../../shared/services/language/language.service';
import AutoCloseService from './service/closeWindow/AutoCloseService';

// ** Directives

// ** Custom Modules
/*
 * Modules have to be loaded here. Afterwards they are
 * accessible via their name as defined in the module e.g.
 * 'eblocker.logger' (see dependency reference for module
 * 'eblocker.advice' below).
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

angular.module('eblocker.advice', [
    ngMaterial,
    ngAnimate,
    ngMessage,
    uiRouter,
    translate,
    ngIdle,
    'eblocker.logger',
    'eblocker.settings',
    'eblocker.security',
    'template.advice.app'
])
    .config(LocationConfig)
    .config(TranslationConfig)
    .config(ThemingProvider)
    .config(RoutesConfig)
    .config(IdleConfig)
    .constant('APP_CONTEXT', APP_CONTEXT)
    .constant('LANG_FILENAMES', LANG_FILENAMES)
    .constant('IDLE_TIMES', IDLE_TIMES)
    .constant('moment', moment)
    .controller('NotificationController', NotificationController)
    .controller('ConfirmationDialogController', ConfirmationDialogController)
    .component('adviceComponent', AdviceComponent)
    .component('welcomeComponent', WelcomeComponent)
    .component('reminderComponent', ReminderComponent)
    .factory('DataCachingService', DataCachingService)
    .factory('DeviceService', DeviceService)
    .factory('ConsoleService', ConsoleService)
    .factory('RegistrationService', RegistrationService)
    .factory('ArrayUtilsService', ArrayUtilsService)
    .factory('AccessContingentService', AccessContingentService)
    .factory('LoadingService', LoadingService)
    .factory('DialogService', DialogService)
    .factory('LanguageService', LanguageService)
    .factory('AutoCloseService', AutoCloseService)
    .factory('NotificationService', NotificationService);
