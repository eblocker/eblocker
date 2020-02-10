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

import 'babel-polyfill';
import 'moment/min/locales.min';
import moment from 'moment';


// ** Controllers
import MainController from './main.controller.js';
import ControlbarController from './controlbar.controller.js';
import ProvidePinDialogController from './dialogs/pin/provide-pin.dialog';
import ChangePinDialogController from './dialogs/pin/change-pin.dialog';
import TorActivationDialogController from '../../shared/dialogs/tor/tor-activation.dialog';
import VpnActivationDialogController from './dialogs/anon/vpn-activation.dialog';
import TorVerifyConnectionDialogController from './dialogs/anon/tor-verify-connection.dialog';
import CustomUserAgentDialogController from './dialogs/cloaking/custom-userAgent.dialog';
import PauseConfirmationDialogController from './dialogs/pause/pause-confirmation.dialog';
import MessageDialogController from './dialogs/messages/message.dialog';
import NotificationController from '../../shared/services/notification/NotificationController';


// ** Configs
import LocationConfig from './_bootstrap/_configs/locationConfig';
import TranslationConfig from './_bootstrap/_configs/translationConfig';
import RoutesConfig from './_bootstrap/_configs/routeConfig';
import IdleConfig from './_bootstrap/_configs/idleConfig';
import APP_CONTEXT from './controlbar.constants.js';
import LANG_FILENAMES from '../../shared/locale/langFileNames';
import IDLE_TIMES from '../../shared/_constants/idleTimes';

import FILTER_TYPE from '../../shared/_constants/filterTypes';
import ThemingProvider from '../../shared/theme/eblocker.theme';
import AppRouter from './controlbar.router.js';


// ** Components
import DashboardLinkComponent from './components/dashboardLink/dashboard-link.component';
import TrackersComponent from './components/trackers/trackers.component';
import ComComponent from './components/com/com.component';
import AnonComponent from './components/anon/ip-anon.component';
import UserComponent from './components/user/user.component';
import CloakingComponent from './components/cloaking/cloaking.component';
import PauseComponent from './components/pause/pause.component';
import MessagesComponent from './components/messages/messages.component';
import SettingsComponent from './components/settings/settings.component';
import HelpComponent from './components/help/help.component';
import ActivationComponent from './components/activation/activation.component';
import RenewComponent from './components/renew/renew.component';
import OnlineTimeComponent from './components/onlineTime/online-time.component';

// ** Services
import PageContextService from './service/pageContext/PageContextService';
import FilterService from './service/filters/FiltersService';
import WhitelistService from './service/filters/WhitelistService';
import DeviceService from './service/device/DeviceService';
import UserService from './service/user/UserService';
import ConsoleService from './service/console/ConsoleService';
import NotificationService from '../../shared/services/notification/NotificationService';
import ArrayUtilsService from '../../shared/services/utils/array-utils.service';
import RegistrationService from './service/registration/RegistrationService';
import VpnService from './service/anon/VpnService';
import TorService from './service/anon/TorService';
import ControlbarService from './service/controlbar/ControlbarService';
import MessageService from './service/messages/MessageService';
import UserAgentService from './service/userAgent/UserAgentService';
import TimeService from './service/time/TimeService';
import AccessUsageService from './service/parentalControl/AccessUsageService';
import DnsStatistics from './service/filters/DnsStatisticsService';
import SslService from './service/ssl/SslService';
import AccessContingentService from '../../shared/services/parentalControl/AccessContingentService';
import DataCachingService from '../../shared/services/caching/DataCachingService';
import LanguageService from '../../shared/services/language/language.service';

// ** Directives
import CompileDirective from '../../shared/directives/compile.directive';
import ButtonEntryDirective from './directives/button-entry.directive';
import DropdownFiltersDirective from './directives/dropdown-filters.directive';
import DropdownUserDirective from './directives/dropdown-user.directive';
import DropdownAnonDirective from './directives/dropdown-anon.directive';
import DropdownCloakingDirective from './directives/dropdown-cloaking.directive';
import DropdownOnlineTimeDirective from './directives/dropdown-online-time.directive';
import MyCloakDirective from './directives/my-cloak.directive';

// ** Custom Modules
/*
 * Modules have to be loaded here. Afterwards they are
 * accessible via their name as defined in the module e.g.
 * 'eblocker.logger' (see dependency reference for module
 * 'eblocker.controlbar' below).
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

angular.module('eblocker.controlbar', [
    ngMaterial,
    ngAnimate,
    ngMessage,
    uiRouter,
    translate,
    ngIdle,
    'eblocker.logger',
    'eblocker.settings',
    'eblocker.security',
    'template.controlbar.app'
])
    .config(LocationConfig)
    .config(TranslationConfig)
    .config(ThemingProvider)
    .config(RoutesConfig)
    .config(IdleConfig)
    .config(AppRouter)
    .constant('FILTER_TYPE', FILTER_TYPE)
    .constant('APP_CONTEXT', APP_CONTEXT)
    .constant('LANG_FILENAMES', LANG_FILENAMES)
    .constant('moment', moment)
    .constant('IDLE_TIMES', IDLE_TIMES)
    .controller('ControlbarController', ControlbarController)
    .controller('MainController', MainController)
    .controller('ProvidePinDialogController', ProvidePinDialogController)
    .controller('ChangePinDialogController', ChangePinDialogController)
    .controller('TorActivationDialogController', TorActivationDialogController)
    .controller('VpnActivationDialogController', VpnActivationDialogController)
    .controller('TorVerifyConnectionDialogController', TorVerifyConnectionDialogController)
    .controller('CustomUserAgentDialogController', CustomUserAgentDialogController)
    .controller('PauseConfirmationDialogController', PauseConfirmationDialogController)
    .controller('MessageDialogController', MessageDialogController)
    .controller('NotificationController', NotificationController)
    .component('dashboardLink', DashboardLinkComponent)
    .component('trackers', TrackersComponent)
    .component('ads', ComComponent)
    .component('ipAnon', AnonComponent)
    .component('user', UserComponent)
    .component('cloaking', CloakingComponent)
    .component('pause', PauseComponent)
    .component('messages', MessagesComponent)
    .component('settings', SettingsComponent)
    .component('help', HelpComponent)
    .component('activation', ActivationComponent)
    .component('renewLicense', RenewComponent)
    .component('onlineTime', OnlineTimeComponent)
    .directive('compile', CompileDirective)
    .directive('buttonEntry', ButtonEntryDirective)
    .directive('dropdownFilters', DropdownFiltersDirective)
    .directive('dropdownUsers', DropdownUserDirective)
    .directive('dropdownAnon', DropdownAnonDirective)
    .directive('dropdownCloaking', DropdownCloakingDirective)
    .directive('dropdownOnlineTime', DropdownOnlineTimeDirective)
    .directive('myCloak', MyCloakDirective)
    .factory('PageContextService', PageContextService)
    .factory('FilterService', FilterService)
    .factory('WhitelistService', WhitelistService)
    .factory('DeviceService', DeviceService)
    .factory('UserService', UserService)
    .factory('ConsoleService', ConsoleService)
    .factory('RegistrationService', RegistrationService)
    .factory('VpnService', VpnService)
    .factory('TorService', TorService)
    .factory('ControlbarService', ControlbarService)
    .factory('UserAgentService', UserAgentService)
    .factory('ArrayUtilsService', ArrayUtilsService)
    .factory('MessageService', MessageService)
    .factory('TimeService', TimeService)
    .factory('AccessUsageService', AccessUsageService)
    .factory('AccessContingentService', AccessContingentService)
    .factory('DnsStatistics', DnsStatistics)
    .factory('SslService', SslService)
    .factory('DataCachingService', DataCachingService)
    .factory('NotificationService', NotificationService)
    .factory('LanguageService', LanguageService);
