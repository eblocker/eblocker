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
import ngMessages from 'angular-messages';
import uiRouter from '@uirouter/angularjs';

import translate from 'angular-translate';
import 'angular-translate-loader-static-files';
import 'moment/min/locales.min';
import moment from 'moment';

import 'angular-translate-interpolation-messageformat';

import '../../../node_modules/chart.js/dist/Chart.min.js';
import chartJs from 'angular-chart.js';

import ngIdle from '../../../npm_integrations/ng-idle';

import '../../../node_modules/jquery-ui/ui/data';
import '../../../node_modules/jquery-ui/ui/widget';
import '../../../node_modules/jquery-ui/ui/scroll-parent';
import '../../../node_modules/jquery-ui/ui/widgets/mouse';
import '../../../node_modules/jquery-ui/ui/widgets/sortable';

import uiSortable from '../../../npm_integrations/ui-sortable';

import '../../../node_modules/re-tree/index'; // required for ng-device-detector
import '../../../node_modules/ua-device-detector/index';  // required for ng-device-detector
import ngDeviceDetector from '../../../node_modules/ng-device-detector/index';

import '../../../node_modules/jquery-ui-touch-punch/jquery.ui.touch-punch.min.js';

// ** Controllers
import ConsoleRedirectController from './consoleRedirect.controller';
import ActionController from './action.controller';
import NotificationController from '../../shared/services/notification/NotificationController';
import ConfirmationDialogController from '../../shared/dialogs/confirmation/confirmation.dialog';
import InformationDialogController from '../../shared/dialogs/information/information.dialog';
import TorActivationDialogController from '../../shared/dialogs/tor/tor-activation.dialog';
import ChangePinDialogController from './dialogs/pin/change-pin.dialog';
import ProvidePinDialogController from './dialogs/pin/provide-pin.dialog';
import WelcomeDialogController from './dialogs/welcome/welcome.dialog';


// ** Configs
import LocationConfig from './_bootstrap/_configs/locationConfig';
import TranslationConfig from './_bootstrap/_configs/translationConfig';
import IdleConfig from './_bootstrap/_configs/idleConfig';
import APP_CONTEXT from './dashboard.constants.js';
import FILTER_TYPE from '../../shared/_constants/filterTypes';
import IDLE_TIMES from '../../shared/_constants/idleTimes';
import LANG_FILENAMES from '../../shared/locale/langFileNames';

import DurationFilter from './filter/duration.filter';
import ThemingProvider from '../../shared/theme/eblocker.theme';
import AppRouter from './_bootstrap/_configs/routeConfig.js';

import CARD_HTML from './_bootstrap/_constants/cardHtml';

// ** Components
import dashboardCardComponent from './components/card.component';
import dashboardComponent from './dashboard.component';
import MainComponent from './main.component';
import StatsComponent from './stats.component.js';
import PauseComponent from './cards/pause/pause.component';
import OnlineTimeComponent from './cards/onlineTime/onlineTime.component';
import DashboardConsoleComponent from './cards/console/console.component';
import IconComponent from './cards/icon/icon.component';
import SslComponent from './cards/ssl/ssl.component';
import MessageComponent from './cards/message/message.component';
import WhitelistComponent from './cards/whitelist/whitelist.component';
import FilterStatisticsComponent from './cards/filterStatistics/filterStatistics.component';
import FilterStatisticsDiagramComponent from './cards/filterStatistics/filterStatisticsDiagram.component';
import FilterStatisticsTotalComponent from './cards/filterStatisticsTotal/filterStatisticsTotal.component';
import FilterComponent from './cards/filterCard/filter.component';
import WhitelistDns from './cards/whitelistDns/whitelist-dns.component';
import WhitelistDnsTable from './cards/whitelistDns/whitelist-dns-table.component';
import EblockerMobile from './cards/mobile/eblocker-mobile.component';
import UserComponent from './cards/user/user.component';
import AnonComponent from './cards/anon/anon.component';
import ParentalControlComponent from './cards/parentalControl/parental-control.component';
import FragFinnComponent from './cards/fragFinn/frag-finn.component';
import MobileSetupWizard from './wizard/mobile/mobile-setup-wizard.component';
import HttpsWizardComponent from './wizard/https/https-wizard.component';
import ConnectionTestComponent from './cards/connectionTest/connectiontest.component';
import ConnectionTestDetail from './cards/connectionTest/connectionTestDetail.component';

// squid-error / blocker components
import BlockerComponent from './blocker/blocker.component';
import AccessDeniedComponent from './blocker/access-denied.component';
import BlockedAdsTrackersComponent from './blocker/blocked-ads-trackers.component';
import BlockedMalwareComponent from './blocker/blocked-malware.component';
import BlockerWhitelisted from './blocker/blocked-whitelisted.component';
import SquidErrorComponent from './blocker/squid-error.component';
import SslWhitelistedComponent from './blocker/ssl-whitelist-options.component';

// redirect components
import RedirectComponent from './redirect/redirect.component';
import RedirectOptionsComponent from './redirect/redirect-options.component';
import BlockOptionsComponent from './redirect/block-options.component';

// ** Components for reuse
import ebFilterComponent from '../../shared/components/filter/filter.component';
import WizardComponent from '../../shared/components/wizard/eb-wizard.component';
import DropdownComponent from '../../shared/components/dropdown/drop-down.component';

// ** filter
import ebFilter from '../../shared/filter/eb-filter';

// ** Directives
import compileCard from '../../shared/directives/compile.directive';

// ** Services
import DataService from './service/data/DataService';
import MessageService from './service/message/message.service';
import WhitelistService from './service/whitelist/whitelist.service';
import ArrayUtilsService from '../../shared/services/utils/array-utils.service';
import CardService from './service/card/card.service';
import CardAvailabilityService from './service/card/card-availability.service';
import UserProfile from './service/devices/userProfile.service';
import FilterService from './service/filter/FilterService';
import UserService from './service/users/UserService';
import VpnHomeService from './service/vpn/VpnHomeService';
import NotificationService from '../../shared/services/notification/NotificationService';
import DialogService from './service/dialog/DialogService';
import DeviceService from './service/devices/device.service';
import PauseService from './service/devices/pause.service';
import UserProfileService from './service/devices/userProfile.service';
import DnsService from './service/dns/DnsService';
import LicenseService from './service/license/license.service';
import NetworkService from './service/network/network.service';
import TorService from './service/tor/TorService';
import VpnService from './service/vpn/VpnService';
import CloakingService from './service/cloaking/CloakingService';
import LanguageService from '../../shared/services/language/language.service';
import EventService from '../../shared/services/event/EventService';
import ResolutionService from './service/window/ResolutionService';

// ** Shared Services
import DataCachingService from '../../shared/services/caching/DataCachingService';
import NumberUtilsService from '../../shared/services/utils/number-utils.service';
import DomainUtilsService from '../../shared/services/utils/domain-utils.service';
import SystemService from '../../shared/services/system/SystemService';
import RedirectService from '../../shared/services/redirect/RedirectService';


// ** Custom Modules
/*
 * Modules have to be loaded here. Afterwards they are
 * accessible via their name as defined in the module e.g.
 * 'eblocker.logger' (see dependency reference for module
 * 'eblocker.dashboard' below).
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
import './service/service.module';

angular.module('eblocker.dashboard', [
    ngMaterial,
    ngAnimate,
    ngMessages,
    uiRouter,
    translate,
    chartJs,
    ngIdle,
    uiSortable,
    ngDeviceDetector,
    // Logger,
    // Security,
    // Settings
    'eblocker.logger',
    'eblocker.settings',
    'eblocker.security',
    'eblocker.services',
    'template.dashboard.app'
])
    .config(LocationConfig)
    .config(TranslationConfig)
    .config(ThemingProvider)
    .config(IdleConfig)
    .config(AppRouter)
    .config(DurationFilter)
    .constant('APP_CONTEXT', APP_CONTEXT)
    .constant('FILTER_TYPE', FILTER_TYPE)
    .constant('moment', moment)
    .constant('IDLE_TIMES', IDLE_TIMES)
    .constant('LANG_FILENAMES', LANG_FILENAMES)
    .constant('CARD_HTML', CARD_HTML)
    .controller('ConsoleRedirectController', ConsoleRedirectController)
    .controller('ActionController', ActionController)
    .controller('NotificationController', NotificationController)
    .controller('ConfirmationDialogController', ConfirmationDialogController)
    .controller('InformationDialogController', InformationDialogController)
    .controller('TorActivationDialogController', TorActivationDialogController)
    .controller('ChangePinDialogController', ChangePinDialogController)
    .controller('WelcomeDialogController', WelcomeDialogController)
    .controller('ProvidePinDialogController', ProvidePinDialogController)
    .component('dashboardComponent', dashboardComponent)
    .component('mainComponent', MainComponent)
    .component('blockerComponent', BlockerComponent)
    .component('accessDeniedComponent', AccessDeniedComponent)
    .component('blockedAdsTrackersComponent', BlockedAdsTrackersComponent)
    .component('blockedMalwareComponent', BlockedMalwareComponent)
    .component('blockerWhitelisted', BlockerWhitelisted)
    .component('squidErrorComponent', SquidErrorComponent)
    .component('sslWhitelistedComponent', SslWhitelistedComponent)
    .component('redirectComponent', RedirectComponent)
    .component('redirectOptionsComponent', RedirectOptionsComponent)
    .component('blockOptionsComponent', BlockOptionsComponent)
    .component('ebWizardComponent', WizardComponent)
    .component('mobileSetupWizardComponent', MobileSetupWizard)
    .component('httpsWizardComponent', HttpsWizardComponent)
    .component('ebDropdown', DropdownComponent)
    .component('dashboardIcon', IconComponent)
    .component('dashboardSsl', SslComponent)
    .component('dashboardStats', StatsComponent)
    .component('dashboardPause', PauseComponent)
    .component('dashboardOnlineTime', OnlineTimeComponent)
    .component('dashboardConsole', DashboardConsoleComponent)
    .component('dashboardMessage', MessageComponent)
    .component('dashboardWhitelist', WhitelistComponent)
    .component('dashboardFilterStatistics', FilterStatisticsComponent)
    .component('dashboardFilterStatisticsDiagram', FilterStatisticsDiagramComponent)
    .component('dashboardFilterStatisticsTotal', FilterStatisticsTotalComponent)
    .component('dashboardFilter', FilterComponent)
    .component('dashboardWhitelistDns', WhitelistDns)
    .component('dashboardWhitelistDnsTable', WhitelistDnsTable)
    .component('dashboardMobile', EblockerMobile)
    .component('dashboardUser', UserComponent)
    .component('dashboardAnonymization', AnonComponent)
    .component('dashboardParentalControl', ParentalControlComponent)
    .component('dashboardFragFinn', FragFinnComponent)
    .component('dashboardConnectionTest', ConnectionTestComponent)
    .component('connectionTestDetail', ConnectionTestDetail)
    .component('ebFilterTable', ebFilterComponent)
    .component('ebCard', dashboardCardComponent)
    .directive('compile', compileCard)
    .factory('DataService', DataService)
    .factory('MessageService', MessageService)
    .factory('WhitelistService', WhitelistService)
    .factory('ArrayUtilsService', ArrayUtilsService)
    .factory('NumberUtilsService', NumberUtilsService)
    .factory('DomainUtilsService', DomainUtilsService)
    .factory('RedirectService', RedirectService)
    .factory('CardService', CardService)
    .factory('CardAvailabilityService', CardAvailabilityService)
    .factory('FilterService', FilterService)
    .factory('UserService', UserService)
    .factory('userProfile', UserProfile)
    .factory('VpnHomeService', VpnHomeService)
    .factory('NotificationService', NotificationService)
    .factory('DialogService', DialogService)
    .factory('DeviceService', DeviceService)
    .factory('PauseService', PauseService)
    .factory('UserProfileService', UserProfileService)
    .factory('DnsService', DnsService)
    .factory('LicenseService', LicenseService)
    .factory('NetworkService', NetworkService)
    .factory('DataCachingService', DataCachingService)
    .factory('TorService', TorService)
    .factory('VpnService', VpnService)
    .factory('CloakingService', CloakingService)
    .factory('LanguageService', LanguageService)
    .factory('EventService', EventService)
    .factory('SystemService', SystemService)
    .factory('ResolutionService', ResolutionService)
    .filter('ebfilter', ebFilter);
