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
import ngAria from 'angular-aria'; // required for md-time-picker
import ngMessage from 'angular-messages';
import uiRouter from '@uirouter/angularjs';
import ngTimePicker from 'angular-material-time-picker';

import translate from 'angular-translate';
import 'angular-translate-loader-static-files';

import 'angular-translate-interpolation-messageformat';

import '../../../node_modules/chart.js/dist/Chart.min.js';
import chartJs from 'angular-chart.js';

import ngIdle from '../../../npm_integrations/ng-idle';
import ngStorage from '../../../npm_integrations/ng-storage';

import ngFileUpload from '../../../npm_integrations/ng-fileupload';
import 'moment/min/locales.min';
import moment from 'moment';

import '../../../node_modules/re-tree/index'; // required for ng-device-detector
import '../../../node_modules/ua-device-detector/index';  // required for ng-device-detector
import ngDeviceDetector from '../../../node_modules/ng-device-detector/index';

// import ngFileUpload from '../../../node_modules/ng-file-upload/dist/ng-file-upload-all.min.js';


// ** Controllers
import IdleDialogController from './common/idle.dialog.controller';
import UpdateLicenseDialogController from './dialogs/license/update-license.dialog';
import PasswordDialogController from './dialogs/adminPassword/password.dialog';
import UpdateDialogController from './dialogs/update/update.dialog';
import SetUpdateTimeDialogController from './dialogs/update/set-update-time.dialog';
import NotificationController from '../../shared/services/notification/NotificationController';
import PinResetConfirmDialogController from './dialogs/parentalControl/pin-reset-confirm.dialog';
import SetPinDialogController from './dialogs/parentalControl/set-pin.dialog';
import ConfirmationDialogController from '../../shared/dialogs/confirmation/confirmation.dialog';
import InformationDialogController from '../../shared/dialogs/information/information.dialog';
import RegistrationResetDialogController from './dialogs/reset/reset-registration.dialog';
import AddUserDialogController from './dialogs/parentalControl/user-add.dialog';
import EditUserRoleController from './dialogs/parentalControl/user-role-edit.dialog';
import AddDeviceToUserDialogController from './dialogs/parentalControl/user-add-device.dialog';
import AddUserProfileDialogController from './dialogs/parentalControl/user-profile-add.dialog';
import EditRestrictionsDialogController from './dialogs/parentalControl/access-restrictions-edit.dialog';
import EditContingentDialogController from './dialogs/parentalControl/access-contingent-edit.dialog';
import EditUsageDialogController from './dialogs/parentalControl/access-usage-edit.dialog';
import FilterAddDialogController from './dialogs/parentalControl/filter-add.dialog';
import VpnConnectionEditController from './dialogs/vpn/new-vpn-connect.dialog';
import VpnConnectionTestController from './dialogs/vpn/vpn-connect-test.dialog';
import DnsRecordAddEditController from './dialogs/dns/dns-record-new-edit.dialog';
import DnsAddEditServerController from './dialogs/dns/dns-edit-add-server.dialog';
import SslStatusWizardController from './dialogs/ssl/ssl-status-wizard.dialog';
import TrustedAppAddController from './dialogs/trustedApps/trusted-app-add.dialog';
import TrustedDomainAddEditController from './dialogs/trustedDomains/trusted-domain-add-edit.dialog';
import DomainIpRangeController from './dialogs/recording/domain-ip-range.dialog';
import AddDomainToAppController from './dialogs/trustedApps/add-domain-to-app.dialog';
import VpnHomeStartController from './dialogs/vpn/vpn-home-start.dialog';
import NetworkEditModeController from './dialogs/network/network-edit-mode-add-server.dialog';
import NetworkEditContentController from './dialogs/network/network-edit-content.dialog';
import ExpertConfirmationDialogController from './dialogs/network/expert-confirmation.dialog';
import AnalysisToolDetailsController from './dialogs/recording/analysis-tool-details.dialog';
import EditTorCountryListController from './dialogs/tor/edit-tor-countries.dialog';
import EditDialogController from './dialogs/edit/edit.dialog';
import EditDomainsDialogController from './dialogs/edit/edit-domains.dialog';
import EditBlockerFormatDialogController from './dialogs/edit/blocker-format-edit.dialog';
import MobileConfirmCloseController from './dialogs/mobile/mobile-confirm-close.dialog';
import NotificationDialogController from './dialogs/notification/notification.dialog';
import TorActivationDialogController from '../../shared/dialogs/tor/tor-activation.dialog';
import EditTasksViewConfigController from './dialogs/system/tasks-view-config-edit.dialog';
import AddCustomListController from './dialogs/blocker/update-custom-list.dialog';


// ** Configs
import LocationConfig from './_bootstrap/_configs/locationConfig';
import TranslationConfig from './_bootstrap/_configs/translationConfig';
import RoutesConfig from './_bootstrap/_configs/routeConfig';
import IdleConfig from './_bootstrap/_configs/idleConfig';
import HttpInterceptor from './_bootstrap/_configs/httpInterceptor';
import APP_CONTEXT from './settings.constants.js';
import STATES from './_bootstrap/_constants/statesEnum';
import BE_ERRORS from '../../shared/_constants/backendErrors';
import FILTER_TYPE from '../../shared/_constants/filterTypes';
import IDLE_TIMES from '../../shared/_constants/idleTimes';
import USER_ROLES from '../../shared/_constants/userRoles';
import BLOCKER_TYPE from './_bootstrap/_constants/blockerType';
import BLOCKER_CATEGORY from './_bootstrap/_constants/blockerCategory';
import SUPPORTED_LOCALE from '../../shared/locale/supportedLocale';
import LANG_FILENAMES from '../../shared/locale/langFileNames';
import TimestampFilter from './_bootstrap/_configs/timestamp';

import ThemingProvider from '../../shared/theme/eblocker.theme';

// ** Runs
import StateTransitionHooks from './_bootstrap/_runs/stateTransitionHooks';

// ** Components
import SettingsComponent from './settings.component';
import MainComponent from './main.component';
import SplashScreenComponent from './components/splash/splash-screen.component';
import ExpiredComponent from './components/authentication/expired/expired.component';
import ActivationComponent from './components/activation/activation.component';
import ActivationFinishComponent from './components/activation/finish/activation-finish.component';
import LoginComponent from './components/authentication/login/login.component';
import LogoutComponent from './components/authentication/logout/logout.component';
import ResetPasswordComponent from './components/authentication/resetPassword/reset-password.component';
import AuthComponent from './components/authentication/auth.component';
import HomeComponent from './components/home/home.component';
import LicenseComponent from './components/home/license/license.component';
import UpdateComponent from './components/home/update/update.component';
import AdminPasswordComponent from './components/system/adminPassword/admin-password.component';
import AboutComponent from './components/home/about/about.component';
import LegalComponent from './components/home/legal/legal.component';
import ParentalControlComponent from './components/parentalControl/parental-control.component';
import UsersComponent from './components/parentalControl/users/users.component';
import UsersDetailsComponent from './components/parentalControl/users/users-details.component';
import UserProfilesComponent from './components/parentalControl/userProfiles/userProfiles.component';
import UserProfileDetailsComponent from './components/parentalControl/userProfiles/user-profile-details.component';
import WhitelistsComponent from './components/parentalControl/whitelists/whitelists.component';
import WhitelistDetailsComponent from './components/parentalControl/whitelists/whitelist-details.component';
import BlackistsComponent from './components/parentalControl/blacklists/blacklists.component';
import BlacklistDetailsComponent from './components/parentalControl/blacklists/blacklist-details.component';
import DevicesComponent from './components/devices/devices.component';
import DevicesListComponent from './components/devices/list/devices-list.component';
import DevicesDiscoveryComponent from './components/devices/discovery/devices-discovery.component';
import DevicesDetailsComponent from './components/devices/list/devices-details.component';
import SslComponent from './components/ssl/ssl.component';
import IpAnonComponent from './components/ipAnon/ip-anon.component';
import SystemComponent from './components/system/system.component';
import NetworkComponent from './components/network/network.component';
import NetworkWizardComponent from './components/network/wizard/network-wizard.component';
import AdvancedComponent from './components/advanced/advanced.component';
import TorComponent from './components/ipAnon/tor/tor.component';
import VpnConnectComponent from './components/ipAnon/vpn/vpn-connect.component';

import DnsComponent from './components/dns/dns.component';
import DnsStatusComponent from './components/dns/status/dns-status.component';
import DnsServerComponent from './components/dns/server/dns-server.component';
import DnsLocalComponent from './components/dns/local/dns-local.component';

import DiagnosticsComponent from './components/system/diagnostics/diagnostics.component';
import ReportComponent from './components/system/diagnostics/report.component';
import EventsComponent from './components/system/events/events.component';
import FactoryResetComponent from './components/system/factoryReset/factory-reset.component';
import ResetComponent from './components/system/reset/reset.component';
import ResetActivationComponent from './components/system/resetActivation/reset-activation.component';
import StatusComponent from './components/system/status/status.component';
import TasksComponent from './components/system/tasks/tasks.component';
import TimeLangComponent from './components/system/timeLang/time-language.component';
import ConfigBackupComponent from './components/system/configBackup/config-backup.component';
import LedSettingsComponent from './components/system/ledSettings/led-settings.component';

import NetworkSettingsComponent from './components/network/settings/network-settings.component';
import VpnHomeComponent from './components/vpnHome/vpn-home.component';
import VpnHomeStatusComponent from './components/vpnHome/status/vpn-home-status.component';
import VpnHomeDevicesComponent from './components/vpnHome/devices/vpn-home-devices.component';
import VpnHomeWizardComponent from './components/vpnHome/wizard/vpn-home-wizard.component';

import CaptivePortalComponent from './components/advanced/captivePortal/captive-portal.component';
import CompressionComponent from './components/advanced/compression/compression.component';
import DoNotTrackComponent from './components/advanced/doNotTrack/do-not-track.component';
import ReferrerComponent from './components/advanced/referrer/referrer.component';
import WebRtcComponent from './components/advanced/webRtc/web-rtc.component';

import SslStatusComponent from './components/ssl/status/ssl-status.component';
import SslCertificateComponent from './components/ssl/certificate/ssl-certificate.component';
import SslFailsComponent from './components/ssl/fails/ssl-fails.component';
import TrustedAppsComponent from './components/ssl/trustedApps/trustedApps.component';
import TrustedDomainsComponent from './components/ssl/trustedDomains/trustedDomains.component';
import ManualRecordingComponent from './components/ssl/manualRecording/manual-recording.component';

import NotLicensedComponent from './components/notLicensed/not-licensed.component';
import VpnConnectDetailsComponent from './components/ipAnon/vpn/vpn-connect-details.component';

import TrustedAppsDetailsComponent from './components/ssl/trustedApps/trusted-apps-details.component';

import SystemPendingComponent from './components/systemPending/system-pending.component';
import FactoryResetScreenComponent from './components/systemPending/factoryReset/factory-reset.component';
import StandByComponent from './components/systemPending/standBy/stand-by.component';
import BootingComponent from './components/systemPending/booting/booting.component';
import ShutdownComponent from './components/systemPending/shutdown/shutdown.component';
import UpdatingComponent from './components/systemPending/updating/updating.component';
import PrintComponent from './components/print/print.component';

import FilterComponent from './components/filters/filter.component';
import FilterOverviewComponent from './components/filters/overview/filter-overview.component';
import FilterDetailsComponent from './components/filters/overview/filter-details.component';
import AdvancedSettingsComponent from './components/filters/advanced/advanced-settings.component';
import AnalysisComponent from './components/filters/analysis/analysis.component';
import AnalysisDetailsComponent from './components/filters/analysis/analysis-details.component';

import OpenSourceLicensesComponent from './components/openSourceLicenses/open-source-licenses.component';
import LibsJavaComponent from './components/openSourceLicenses/libs-java.component';
import LibsCCppComponent from './components/openSourceLicenses/libs-c-cpp.component';
import LibsJavascriptComponent from './components/openSourceLicenses/libs-javascript.component';
import LibsRubyComponent from './components/openSourceLicenses/libs-ruby.component';
import LibsDebianComponent from './components/openSourceLicenses/libs-debian.component';
import DoctorDiagnosisComponent from './components/doctor/diagnosis/doctor-diagnosis.component';
import DoctorDiagnosisDetailsComponent from './components/doctor/diagnosis/diagnosis-details.component';

// ** Components for code reuse (instead of directives)
import RemoveTableEntriesComponent from './components/table/remove-entries.component';
import PaginatorTableComponent from './components/table/paginator.component';
import PaginatorDetailsComponent from './components/table/details-paginator.component';
import FilterTableComponent from '../../shared/components/filter/filter.component';
import TableComponent from './components/table/table.component';
import EditTableComponent from './components/table/edit-table.component';
import ScrollPaginatorComponent from './components/table/scroll-paginator.component';
import CheckEntryComponent from './components/table/check-entry.component';
import DetailsGoBackComponent from './components/table/details-go-back.component';
import WizardComponent from '../../shared/components/wizard/eb-wizard.component';
import HelpInlineComponent from './components/help/help-inline.component';
import HelpIconComponent from './components/help/help-icon.component';
import DropdownComponent from '../../shared/components/dropdown/drop-down.component';
import DateComponent from '../../shared/components/date/date.component';


// ** filter
import ebFilter from '../../shared/filter/eb-filter';


// ** Services
import SplashService from './service/console/SplashService';
import ConsoleService from './service/console/ConsoleService';
import StateService from './service/routing/StateService';
import PageContextService from './service/pageContext/PageContextService';
import RegistrationService from './service/registration/RegistrationService';
import SetupService from './service/registration/SetupService';
import UpdateService from './service/update/UpdateService';
import PasswordService from './service/security/PasswordService';
import TasksService from './service/system/TasksService';
import UserService from './service/parentalControl/UserService';
import UserProfileService from './service/parentalControl/UserProfileService';
import DnsService from './service/dns/DnsService';
import SslService from './service/ssl/SslService';
import DeviceService from './service/devices/DeviceService';
import ReferrerService from './service/anonymous/ReferrerService';
import CaptivePortalService from './service/anonymous/CaptivePortalService';
import WebRtcService from './service/anonymous/WebRtcService';
import CompressionService from './service/anonymous/CompressionService';
import DoNotTrackService from './service/anonymous/DoNotTrackService';
import TimezoneService from './service/system/TimezoneService';
import EventService from './service/system/EventService';
import DiagnosticsService from './service/system/DiagnosticsService';
import FactoryResetService from './service/system/FactoryResetService';
import ConfigBackupService from './service/system/ConfigBackupService';
import LedSettingsService from './service/system/LedSettingsService';
import FilterService from './service/parentalControl/FilterService';
import BlockerService from './service/blocker/BlockerService';
import DialogService from './service/dialog/DialogService';
import CloakingService from './service/cloaking/CloakingService';
import NetworkService from './service/network/NetworkService';
import VpnService from './service/vpn/VpnService';
import DoctorService from './service/doctor/DoctorService';
import TorService from './service/tor/TorService';
import TrustedAppsService from './service/ssl/TrustedAppsService';
import SslSuggestionsService from './service/ssl/SslSuggestionsService';
import PaginationService from './service/table/PaginationService';
import TrustedDomainsService from './service/ssl/TrustedDomainsService';
import ManualRecordingService from './service/recording/ManualRecordingService';
import UpsellService from './service/upsell/UpsellService';
import VpnHomeService from './service/vpn/VpnHomeService';
import CustomerInfoService from './service/customerInfo/CustomerInfoService';
import UrlService from './service/routing/UrlService';
import TableService from './service/table/TableService';
import AnalysisToolService from './service/recording/AnalysisToolService';
import DnsStatistics from './service/dns/DnsStatisticsService';
import TosService from './service/registration/TosService';
import PauseService from './service/pause/PauseService';
import UpnpService from './service/upnp/UpnpService';

// ** Shared Services
import NotificationService from '../../shared/services/notification/NotificationService';
import ArrayUtilsService from '../../shared/services/utils/array-utils.service';
import AccessContingentService from '../../shared/services/parentalControl/AccessContingentService';
import DataCachingService from '../../shared/services/caching/DataCachingService';
import LanguageService from '../../shared/services/language/language.service.js';
import WindowEventService from '../../shared/services/event/EventService';
import SystemService from '../../shared/services/system/SystemService';
import RedirectService from '../../shared/services/redirect/RedirectService';
import FeatureToggleService from '../../shared/services/system/FeatureToggleService';

// ** Directives
import EbLabelContainer from '../../shared/directives/eb-label-container.directive';
import EbTextContainer from '../../shared/directives/eb-textarea-container.directive';
import PasswordQualityDirective from './directives/password-quality.directive';
import UniqueNameDirective from './directives/unique-name.directive';
import IpRangeDirective from './directives/ip-range.directive';
import IpAddressDirective from './directives/ip-address.directive';
import Ip6AddressDirective from './directives/ip6-address.directive';
import IsUnique from './directives/is-unique.directive';

// ** Custom Modules
/*
 * Modules have to be loaded here. Afterwards they are
 * accessible via their name as defined in the module e.g.
 * 'eblocker.logger' (see dependency reference for module
 * 'eblocker.console' below).
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

angular.module('eblocker.adminconsole', [
    ngAria,
    ngMaterial,
    ngAnimate,
    ngTimePicker,
    ngMessage,
    uiRouter,
    ngStorage,
    ngFileUpload,
    ngDeviceDetector,
    translate,
    chartJs,
    ngIdle,
    'eblocker.logger',
    'eblocker.settings',
    'eblocker.security',
    'template.settings.app'
])
    .config(LocationConfig)
    .config(TranslationConfig)
    .config(ThemingProvider)
    .config(RoutesConfig)
    .config(HttpInterceptor)
    .config(IdleConfig)
    .config(TimestampFilter)
    .run(StateTransitionHooks)
    .constant('APP_CONTEXT', APP_CONTEXT)
    .constant('STATES', STATES)
    .constant('BE_ERRORS', BE_ERRORS)
    .constant('FILTER_TYPE', FILTER_TYPE)
    .constant('IDLE_TIMES', IDLE_TIMES)
    .constant('USER_ROLES', USER_ROLES)
    .constant('BLOCKER_TYPE', BLOCKER_TYPE)
    .constant('BLOCKER_CATEGORY', BLOCKER_CATEGORY)
    .constant('SUPPORTED_LOCALE', SUPPORTED_LOCALE)
    .constant('LANG_FILENAMES', LANG_FILENAMES)
    .constant('moment', moment)
    .controller('IdleDialogController', IdleDialogController)
    .controller('NotificationController', NotificationController)
    .controller('UpdateLicenseDialogController', UpdateLicenseDialogController)
    .controller('PasswordDialogController', PasswordDialogController)
    .controller('UpdateDialogController', UpdateDialogController)
    .controller('SetUpdateTimeDialogController', SetUpdateTimeDialogController)
    .controller('PinResetConfirmDialogController', PinResetConfirmDialogController)
    .controller('SetPinDialogController', SetPinDialogController)
    .controller('ConfirmationDialogController', ConfirmationDialogController)
    .controller('InformationDialogController', InformationDialogController)
    .controller('NotificationDialogController', NotificationDialogController)
    .controller('RegistrationResetDialogController', RegistrationResetDialogController)
    .controller('AddUserDialogController', AddUserDialogController)
    .controller('EditUserRoleController', EditUserRoleController)
    .controller('AddDeviceToUserDialogController', AddDeviceToUserDialogController)
    .controller('AddUserProfileDialogController', AddUserProfileDialogController)
    .controller('EditRestrictionsDialogController', EditRestrictionsDialogController)
    .controller('EditContingentDialogController', EditContingentDialogController)
    .controller('EditUsageDialogController', EditUsageDialogController)
    .controller('FilterAddDialogController', FilterAddDialogController)
    .controller('VpnConnectionEditController', VpnConnectionEditController)
    .controller('VpnConnectionTestController', VpnConnectionTestController)
    .controller('DnsRecordAddEditController', DnsRecordAddEditController)
    .controller('DnsAddEditServerController', DnsAddEditServerController)
    .controller('SslStatusWizardController', SslStatusWizardController)
    .controller('TrustedAppAddController', TrustedAppAddController)
    .controller('TrustedDomainAddEditController', TrustedDomainAddEditController)
    .controller('DomainIpRangeController', DomainIpRangeController)
    .controller('AnalysisToolDetailsController', AnalysisToolDetailsController)
    .controller('AddDomainToAppController', AddDomainToAppController)
    .controller('VpnHomeStartController', VpnHomeStartController)
    .controller('NetworkEditModeController', NetworkEditModeController)
    .controller('NetworkEditContentController', NetworkEditContentController)
    .controller('ExpertConfirmationDialogController', ExpertConfirmationDialogController)
    .controller('EditTorCountryListController', EditTorCountryListController)
    .controller('EditDialogController', EditDialogController)
    .controller('EditDomainsDialogController', EditDomainsDialogController)
    .controller('EditBlockerFormatDialogController', EditBlockerFormatDialogController)
    .controller('MobileConfirmCloseController', MobileConfirmCloseController)
    .controller('TorActivationDialogController', TorActivationDialogController)
    .controller('EditTasksViewConfigController', EditTasksViewConfigController)
    .controller('addCustomListController', AddCustomListController)
    .component('settingsComponent', SettingsComponent)
    .component('mainComponent', MainComponent)
    .component('splashScreenComponent', SplashScreenComponent)
    .component('expiredComponent', ExpiredComponent)
    .component('loginComponent', LoginComponent)
    .component('logoutComponent', LogoutComponent)
    .component('resetPasswordComponent', ResetPasswordComponent)
    .component('authComponent', AuthComponent)
    .component('notLicensedComponent', NotLicensedComponent)
    .component('homeComponent', HomeComponent)
    .component('licenseComponent', LicenseComponent)
    .component('updateComponent', UpdateComponent)
    .component('adminPasswordComponent', AdminPasswordComponent)
    .component('aboutComponent', AboutComponent)
    .component('legalComponent', LegalComponent)
    .component('parentalControlComponent', ParentalControlComponent)
    .component('usersComponent', UsersComponent)
    .component('usersDetailsComponent', UsersDetailsComponent)
    .component('userProfilesComponent', UserProfilesComponent)
    .component('userProfileDetailsComponent', UserProfileDetailsComponent)
    .component('whitelistsComponent', WhitelistsComponent)
    .component('blacklistsComponent', BlackistsComponent)
    .component('devicesComponent', DevicesComponent)
    .component('devicesListComponent', DevicesListComponent)
    .component('devicesDiscoveryComponent', DevicesDiscoveryComponent)
    .component('sslComponent', SslComponent)
    .component('ipAnonComponent', IpAnonComponent)
    .component('systemComponent', SystemComponent)
    .component('networkComponent', NetworkComponent)
    .component('networkWizardComponent', NetworkWizardComponent)
    .component('advancedComponent', AdvancedComponent)
    .component('dnsComponent', DnsComponent)
    .component('dnsStatusComponent', DnsStatusComponent)
    .component('dnsServerComponent', DnsServerComponent)
    .component('dnsLocalComponent', DnsLocalComponent)
    .component('torComponent', TorComponent)
    .component('vpnConnectComponent', VpnConnectComponent)
    .component('diagnosticsComponent', DiagnosticsComponent)
    .component('reportComponent', ReportComponent)
    .component('eventsComponent', EventsComponent)
    .component('resetComponent', ResetComponent)
    .component('factoryResetComponent', FactoryResetComponent)
    .component('resetActivationComponent', ResetActivationComponent)
    .component('statusComponent', StatusComponent)
    .component('tasksComponent', TasksComponent)
    .component('timeLanguageComponent', TimeLangComponent)
    .component('configBackupComponent', ConfigBackupComponent)
    .component('ledSettingsComponent', LedSettingsComponent)
    .component('networkSettingsComponent', NetworkSettingsComponent)
    .component('vpnHomeComponent', VpnHomeComponent)
    .component('vpnHomeStatusComponent', VpnHomeStatusComponent)
    .component('vpnHomeDevicesComponent', VpnHomeDevicesComponent)
    .component('vpnHomeWizardComponent', VpnHomeWizardComponent)
    .component('doctorDiagnosisComponent', DoctorDiagnosisComponent)
    .component('doctorDiagnosisDetailsComponent', DoctorDiagnosisDetailsComponent)
    .component('captivePortalComponent', CaptivePortalComponent)
    .component('compressionComponent', CompressionComponent)
    .component('doNotTrackComponent', DoNotTrackComponent)
    .component('webRtcComponent', WebRtcComponent)
    .component('referrerComponent', ReferrerComponent)
    .component('sslStatusComponent', SslStatusComponent)
    .component('sslCertificateComponent', SslCertificateComponent)
    .component('sslFailsComponent', SslFailsComponent)
    .component('trustedAppsComponent', TrustedAppsComponent)
    .component('trustedDomainsComponent', TrustedDomainsComponent)
    .component('manualRecordingComponent', ManualRecordingComponent)
    .component('blacklistDetailsComponent', BlacklistDetailsComponent)
    .component('whitelistDetailsComponent', WhitelistDetailsComponent)
    .component('devicesDetailsComponent', DevicesDetailsComponent)
    .component('vpnConnectDetailsComponent', VpnConnectDetailsComponent)
    // .component('dnsLocalDetailsComponent', DnsLocalDetailsComponent)
    // .component('dnsServerDetailsComponent', DnsServerDetailsComponent)
    .component('trustedAppsDetailsComponent', TrustedAppsDetailsComponent)
    .component('systemPendingComponent', SystemPendingComponent)
    .component('factoryResetScreenComponent', FactoryResetScreenComponent)
    .component('standByComponent', StandByComponent)
    .component('bootingComponent', BootingComponent)
    .component('shutdownComponent', ShutdownComponent)
    .component('updatingComponent', UpdatingComponent)
    .component('activationComponent', ActivationComponent)
    .component('activationFinishComponent', ActivationFinishComponent)
    .component('printComponent', PrintComponent)
    .component('filterComponent', FilterComponent)
    .component('filterOverviewComponent', FilterOverviewComponent)
    .component('filterDetailsComponent', FilterDetailsComponent)
    .component('advancedSettingsComponent', AdvancedSettingsComponent)
    .component('analysisComponent', AnalysisComponent)
    .component('analysisDetailsComponent', AnalysisDetailsComponent)
    .component('openSourceLicensesComponent', OpenSourceLicensesComponent)
    .component('libsJavaComponent', LibsJavaComponent)
    .component('libsCCppComponent', LibsCCppComponent)
    .component('libsJavascriptComponent', LibsJavascriptComponent)
    .component('libsRubyComponent', LibsRubyComponent)
    .component('libsDebianComponent', LibsDebianComponent)
    .component('tableRemoveEntries', RemoveTableEntriesComponent)
    .component('ebTablePaginator', PaginatorTableComponent)
    .component('ebDetailsPaginator', PaginatorDetailsComponent)
    .component('ebFilterTable', FilterTableComponent)
    .component('ebTable', TableComponent)
    .component('ebCheckEntry', CheckEntryComponent)
    .component('ebEditTable', EditTableComponent)
    .component('ebScrollPaginator', ScrollPaginatorComponent)
    .component('ebBackToTable', DetailsGoBackComponent)
    .component('ebWizardComponent', WizardComponent)
    .component('ebHelpInline', HelpInlineComponent)
    .component('ebHelpIcon', HelpIconComponent)
    .component('ebDropdown', DropdownComponent)
    .component('ebDatePicker', DateComponent)
    .directive('ebLabelContainer', EbLabelContainer)
    .directive('ebTextContainer', EbTextContainer)
    .directive('passwordQuality', PasswordQualityDirective)
    .directive('ebUrlDomains', UniqueNameDirective)
    .directive('ebIpRange', IpRangeDirective)
    .directive('ebIpAddress', IpAddressDirective)
    .directive('ebIp6Address', Ip6AddressDirective)
    .directive('ebUnique', IsUnique)
    .factory('ConsoleService', ConsoleService)
    .factory('StateService', StateService)
    .factory('PageContextService', PageContextService)
    .factory('RegistrationService', RegistrationService)
    .factory('SetupService', SetupService)
    .factory('UpdateService', UpdateService)
    .factory('PasswordService', PasswordService)
    .factory('ArrayUtilsService', ArrayUtilsService)
    .factory('FeatureToggleService', FeatureToggleService)
    .factory('SystemService', SystemService)
    .factory('RedirectService', RedirectService)
    .factory('TasksService', TasksService)
    .factory('UserService', UserService)
    .factory('UserProfileService', UserProfileService)
    .factory('SslService', SslService)
    .factory('DnsService', DnsService)
    .factory('DeviceService', DeviceService)
    .factory('DoNotTrackService', DoNotTrackService)
    .factory('CaptivePortalService', CaptivePortalService)
    .factory('CompressionService', CompressionService)
    .factory('ReferrerService', ReferrerService)
    .factory('WebRtcService', WebRtcService)
    .factory('TimezoneService', TimezoneService)
    .factory('LanguageService', LanguageService)
    .factory('EventService', EventService)
    .factory('DiagnosticsService', DiagnosticsService)
    .factory('FactoryResetService', FactoryResetService)
    .factory('ConfigBackupService', ConfigBackupService)
    .factory('LedSettingsService', LedSettingsService)
    .factory('FilterService', FilterService)
    .factory('BlockerService', BlockerService)
    .factory('AccessContingentService', AccessContingentService)
    .factory('NotificationService', NotificationService)
    .factory('DialogService', DialogService)
    .factory('CloakingService', CloakingService)
    .factory('NetworkService', NetworkService)
    .factory('VpnService', VpnService)
    .factory('TorService', TorService)
    .factory('TrustedAppsService', TrustedAppsService)
    .factory('SslSuggestionsService', SslSuggestionsService)
    .factory('PaginationService', PaginationService)
    .factory('TrustedDomainsService', TrustedDomainsService)
    .factory('ManualRecordingService', ManualRecordingService)
    .factory('AnalysisToolService', AnalysisToolService)
    .factory('DataCachingService', DataCachingService)
    .factory('UpsellService', UpsellService)
    .factory('VpnHomeService', VpnHomeService)
    .factory('CustomerInfoService', CustomerInfoService)
    .factory('UrlService', UrlService)
    .factory('TableService', TableService)
    .factory('UpnpService', UpnpService)
    .factory('DnsStatistics', DnsStatistics)
    .factory('TosService', TosService)
    .factory('PauseService', PauseService)
    .factory('SplashService', SplashService)
    .factory('WindowEventService', WindowEventService)
    .factory('DoctorService', DoctorService)
    .filter('ebfilter', ebFilter)
    .filter('displayHours', function() {
        return function(minutes) {
            return Math.floor(minutes / 60);
        };
    }).filter('displayMinutes', function() {
        return function (minutes) {
            return minutes % 60;
        };
    }).filter('htmlSafe', function($sce){
        'ngInject';
        return function(htmlCode){
            return $sce.trustAsHtml(htmlCode);
        };
    });


