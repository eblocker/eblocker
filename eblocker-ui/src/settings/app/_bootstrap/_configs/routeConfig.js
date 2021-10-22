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
/**
 * Definition of all states.
 *
 * Property showInNavbar: state will be visible in nav bar
 *
 * Property allowActive: allows a state to become active, so that the toolbar heading is changed to the
 * translationKey of that state (used in StateService)
 *
 * Property ignoreHook: true, state will be ignored during the onStart transition hook and will
 * be entered. E.g. AUTH state should always be entered and not check for license or authentication.
 *
 * NOTE: also checkout the transition hooks and httpInterceptor to understand state changes:
 * _bootstrap/_runs/stateTransitionHooks.js
 * _bootstrap/_config/httpInterceptor.js
 *
 * Links:
 *
 * https://docs.angularjs.org/api/ng/service/$http#interceptors
 * https://ui-router.github.io/guide/transitionhooks
 * Component and Bindings:
 *  https://ui-router.github.io/guide/ng1/route-to-component
 *
 *
 * Some properties:
 *
 * - ** Moving a state from one tab to another **
 *      - move the files to correct folder
 *      - [OPT] rename files if necessary
 *      - change template path in component's JS file (e.g. 'dns.component.js')
 *      - change component's path in settings.module.js
 *      - [OPT] rename component in settings.module.js if necessary (line: ".component('dnsComponent', DnsComponent)")
 *          -> !! rename the component in the state below !!
 * - ** Renaming a state (name property) **
 *      - State names should only be hard coded in this file here, in '_bootstrap/_constants/statesEnum.js', and in the
 *      .spec (test) of the component, so make sure to rename the state name there as well.
 *
 *
 * Resolve Policy and spinner:
 *
 * https://ui-router.github.io/ng1/docs/1.0.0-beta.1/interfaces/resolve.resolvepolicy.html
 * https://ui-router.github.io/ng1/docs/1.0.0-beta.1/classes/transition.transitionservice.html#onstart
 * https://ui-router.github.io/ng1/docs/1.0.0-beta.1/classes/transition.transitionservice.html#onenter
 *
 * 'async' property: Determines the unwrapping behavior of asynchronous resolve values.
 * In all resolves we want the async property to be 'WAIT'. Otherwise the ui.router does not wait for the promise and
 * continues with the transitions. Without waiting we have no time-period during which we can display the spinner.
 *
 * 'when' property: Defines when a Resolvable is resolved (fetched) during a transition
 * This does not seem to make any noticeable difference for us.
 *      LAZY: (default) resolved as the resolve's state is being entered (hook: onEnter)
 *      EAGER: resolved as the transition is starting (hook: onStart)
 *
 */
export default function RoutesConfig($urlRouterProvider, $stateProvider, STATES) { // jshint ignore: line
    'ngInject';
    'use strict';

    // Sets the default State when root URL is called: 'https://domain:3000/settings' / 'https://domain:3000'
    $urlRouterProvider.otherwise('/' + STATES.AUTH);

    const slashOptionUrl = '';
    const slashOptionSubState = '/';

    /*
     * Default state used in transition hook to decide where to redirect to;
     * when license is about to expire go to 'license', else go to 'devices'
     */
    const defaultState = {
        name: STATES.DEFAULT,
        parent: STATES.MAIN,
        requiredLicense: function() {
            return 'WOL';
        }
    };

    // ** MAIN STATE: HOME
    const home = {
        name: STATES.HOME,
        parent: STATES.MAIN,
        redirectTo: 'license', // auto activate substate
        url: slashOptionUrl + 'home',
        showInNavbar: true,
        iconUrl: '/img/icons/eblocker.svg',
        navbarOrder: 1,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.HOME.LABEL',
        component: 'homeComponent'
    };

    const homeLicense = {
        name: 'license',
        url: slashOptionSubState + 'license',
        parent: STATES.HOME,
        tabOrder: 1,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.LICENSE.LABEL',
        component: 'licenseComponent'
    };

    const homeUpdate = {
        name: 'update',
        url: slashOptionSubState + 'update',
        parent: STATES.HOME,
        tabOrder: 2,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.UPDATE.LABEL',
        component: 'updateComponent'
    };

    const homeAbout = {
        name: 'about',
        url: slashOptionSubState + 'about',
        parent: STATES.HOME,
        tabOrder: 3,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.ABOUT.LABEL',
        component: 'aboutComponent'
    };

    const homeLegal = {
        name: 'legal',
        url: slashOptionSubState + 'legal',
        parent: STATES.HOME,
        tabOrder: 4,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.LEGAL.LABEL',
        component: 'legalComponent'
    };

    // ** PARENT STATE for PARENTAL CONTROL
    const parentalControl = {
        name: 'parentalcontrol',
        parent: STATES.MAIN,
        redirectTo: 'parentalcontrolstate', // auto activate substate
        url: slashOptionUrl + 'parentalcontrol',
        showInNavbar: true,
        iconUrl: '/img/icons/icons8-teddy-bear.svg',
        navbarOrder: 3,
        requiredLicense: function() {
            return 'FAM';
        },
        // ** This should allow to use different names in resolve and component-bindings:
        // See: https://ui-router.github.io/guide/ng1/route-to-component
        // bindings: { profiles: 'userProfiles'}
        // asynch = wait: we need to wait before the promise is resolved or onSuccess in transition hook is called
        // right away disabling the spinner.
        // when = lazy: we want to wait until the tansition has started, or else onBefore is not called until the data
        // is loaded (thus not enabling the spinner).
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            users: ['UserService', function(UserService) {
                return UserService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            profiles: ['UserProfileService', function(UserProfileService) {
                return UserProfileService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            devices: ['DeviceService', function(DeviceService) {
                return DeviceService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            dnsEnabled: ['DnsService', function(DnsService) {
                return DnsService.loadDnsStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            sslEnabled: ['SslService', function(SslService) {
               return SslService.getStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                   return null;
               });
            }]
        },
        translationKey: 'ADMINCONSOLE.PARENTAL_CONTROL.LABEL'
    };

    // ** Actual parentalcontrol state, that creates tabs and provides
    // ui-view for each tab-view.
    const parentalControlState = {
        name: 'parentalcontrolstate',
        parent: parentalControl.name,
        redirectTo: 'users', // auto activate substate
        requiredLicense: parentalControl.requiredLicense,
        component: 'parentalControlComponent'
    };

    const users = {
        name: 'users',
        url: slashOptionSubState + 'users/:id', //:id defines that the URL may contain a param 'id'
        parent: parentalControlState.name,
        tabOrder: 1,
        requiredLicense: parentalControl.requiredLicense,
        translationKey: 'ADMINCONSOLE.USERS.LABEL',
        component: 'usersComponent'
    };

    const usersDetails = {
        name: STATES.USER_DETAILS,
        url: slashOptionSubState + 'users/details',
        parent: parentalControl.name,
        ignoreTab: true,
        requiredLicense: parentalControl.requiredLicense,
        component: 'usersDetailsComponent'
    };

    // TODO remove when new parental control is done, until then it may be useful for debugging
    // const userProfiles = {
    //     name: 'userprofiles',
    //     url: slashOptionSubState + 'userprofiles',
    //     parent: parentalControlState.name,
    //     tabOrder: 2,
    //     requiredLicense: parentalControl.requiredLicense,
    //     translationKey: 'ADMINCONSOLE.USER_PROFILES.LABEL',
    //     component: 'userProfilesComponent'
    // };

    const usersProfileDetails = {
        name: 'userprofiledetails',
        url: slashOptionSubState + 'userprofiles/details',
        parent: parentalControl.name,
        ignoreTab: true,
        requiredLicense: parentalControl.requiredLicense,
        component: 'userProfileDetailsComponent'
    };

    const blacklists = {
        name: 'blacklists',
        url: slashOptionSubState + 'blacklists/:id',
        parent: parentalControlState.name,
        tabOrder: 3,
        requiredLicense: parentalControl.requiredLicense,
        translationKey: 'ADMINCONSOLE.BLACKLISTS.LABEL',
        component: 'blacklistsComponent'
    };

    const whitelists = {
        name: 'whitelists',
        url: slashOptionSubState + 'whitelists/:id',
        parent: parentalControlState.name,
        tabOrder: 4,
        requiredLicense: parentalControl.requiredLicense,
        translationKey: 'ADMINCONSOLE.WHITELISTS.LABEL',
        component: 'whitelistsComponent'
    };

    const blacklistDetails = {
        name: 'blacklistdetails',
        url: slashOptionSubState + 'blacklists/details',
        parent: parentalControl.name,
        ignoreTab: true,
        requiredLicense: parentalControl.requiredLicense,
        component: 'blacklistDetailsComponent'
    };

    const whitelistDetails = {
        name: 'whitelistdetails',
        url: slashOptionSubState + 'whitelists/details',
        parent: parentalControl.name,
        ignoreTab: true,
        requiredLicense: parentalControl.requiredLicense,
        component: 'whitelistDetailsComponent'
    };


    // ** Parent STATE: DEVICES
    const devices = {
        name: 'devices',
        parent: STATES.MAIN,
        url: slashOptionUrl + 'devices',
        redirectTo: 'devicesstate',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_devices_black.svg',
        navbarOrder: 2,
        separator: true,
        requiredLicense: function() {
            return 'WOL';
        },
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            users: ['UserService', function(UserService) {
                return UserService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            profiles: ['UserProfileService', function(UserProfileService) {
                return UserProfileService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            dnsEnabled: ['DnsService', function(DnsService) {
                return DnsService.loadDnsStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            sslEnabled: ['SslService', function(SslService) {
                return SslService.getStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            vpnHomeStatus: ['VpnHomeService', function(VpnHomeService) {
                return VpnHomeService.loadStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            vpnHomeCertificates: ['VpnHomeService', 'vpnHomeStatus', function(VpnHomeService, vpnHomeStatus) {
                if (vpnHomeStatus.isRunning) {
                    return VpnHomeService.loadCertificates().then(function success(response) {
                        return response.data;
                    }, function error() {
                        return null;
                    });
                } else {
                    return [];
                }
            }]
        },
        translationKey: 'ADMINCONSOLE.DEVICES.LABEL'
        // template: '<div ui-view></div>'
        // component: 'devicesComponent'
    };

    // Actual devices page
    const devicesState = {
        name: 'devicesstate',
        parent: devices.name,
        redirectTo: 'deviceslist', // auto activate substate
        requiredLicense: devices.requiredLicense,
        component: 'devicesComponent'
    };

    const devicesList = {
        name: 'deviceslist',
        url: slashOptionSubState + 'list/:id',
        parent: devicesState.name,
        tabOrder: 1,
        requiredLicense: devices.requiredLicense,
        translationKey: 'ADMINCONSOLE.DEVICES_LIST.LABEL',
        component: 'devicesListComponent'
    };

    const devicesDiscovery = {
        name: 'devicesdiscovery',
        url: slashOptionSubState + 'discovery',
        parent: devicesState.name,
        tabOrder: 2,
        requiredLicense: devices.requiredLicense,
        translationKey: 'ADMINCONSOLE.DEVICES_DISCOVERY.LABEL',
        component: 'devicesDiscoveryComponent'
    };

    // details for devices: uses ui-view of main-state, but is
    // actually ancestor of devices (for navbar selection)
    const devicesDetails = {
        name: 'devicedetails',
        url: slashOptionSubState + 'details',
        parent: devices.name,
        requiredLicense: devices.requiredLicense,
        component: 'devicesDetailsComponent'
    };

    // ** MAIN STATE: SSL
    const ssl = {
        name: STATES.HTTPS,
        parent: STATES.MAIN,
        redirectTo: 'sslstate', // auto activate substate
        url: slashOptionUrl + 'https',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_lock_outline_black.svg',
        navbarOrder: 8,
        requiredLicense: function() {
            return 'PRO';
        },
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            sslEnabled: ['SslService', function(SslService) {
                return SslService.getStatus(true).then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            sslCertStatus: ['SslService', function(SslService) {
                return SslService.getSslCertStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            sslSettings: ['SslService', function(SslService) {
                return SslService.getUpdatedSettingsRenewalStatus().then(function success(settings) {
                    return settings;
                }, function error() {
                    return null;
                });
            }],
            caOptions: ['SslService', function(SslService) {
                return SslService.getRootCaOptions().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            sslRecordingEnabled: ['SslService', function(SslService) {
                return SslService.getSslErrorRecordingEnabled().then(function success(response) {
                    return response.data.enabled;
                }, function error() {
                    return null;
                });
            }],
            devices: ['DeviceService', function(DeviceService) {
                return DeviceService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.SSL.LABEL',
    };

    const sslstate = {
        name: 'sslstate',
        parent: ssl.name,
        redirectTo: 'sslstatus', // auto activate substate
        requiredLicense: ssl.requiredLicense,
        component: 'sslComponent'
    };

    const sslStatus = {
        name: 'sslstatus',
        url: slashOptionSubState + 'status',
        parent: sslstate.name,
        tabOrder: 1,
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.SSL_STATUS.LABEL',
        component: 'sslStatusComponent'
    };

    const sslCertificate = {
        name: 'sslcertificate',
        url: slashOptionSubState + 'certificate',
        parent: sslstate.name,
        tabOrder: 2,
        disableWhenNoSsl: true, // to disable tab, when ssl disabled
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.SSL_CERTIFICATE.LABEL',
        component: 'sslCertificateComponent'
    };

    const sslFails = {
        name: 'sslfails',
        url: slashOptionSubState + 'fails',
        parent: sslstate.name,
        tabOrder: 3,
        disableWhenNoSsl: true,
        showWarningWhenSuggestions: true,
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.SSL_FAILS.LABEL',
        component: 'sslFailsComponent'
    };

    const trustedApps = {
        name: 'trustedapps',
        url: slashOptionSubState + 'trustedapps/:id',
        parent: sslstate.name,
        tabOrder: 4,
        disableWhenNoSsl: true,
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.TRUSTED_APPS.LABEL',
        component: 'trustedAppsComponent'
    };

    const trustedAppsDetails = {
        name: 'trustedappsdetails',
        url: slashOptionSubState + 'details',
        parent: ssl.name,
        requiredLicense: ssl.requiredLicense,
        component: 'trustedAppsDetailsComponent'
    };

    const trustedDomains = {
        name: 'trusteddomains',
        url: slashOptionSubState + 'trusteddomains',
        parent: sslstate.name,
        tabOrder: 5,
        disableWhenNoSsl: true,
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.TRUSTED_DOMAINS.LABEL',
        component: 'trustedDomainsComponent'
    };

    const manualRecording = {
        name: 'manualrecording',
        url: slashOptionSubState + 'manualrecording',
        parent: sslstate.name,
        tabOrder: 6,
        disableWhenNoSsl: true,
        requiredLicense: ssl.requiredLicense,
        translationKey: 'ADMINCONSOLE.MANUAL_RECORDING.LABEL',
        component: 'manualRecordingComponent'
    };

    // ** MAIN STATE: ANONYMIZATION
    const ipAnon = {
        name: 'anonymization',
        parent: STATES.MAIN,
        redirectTo: 'anonymizationstate', // auto activate substate
        url: slashOptionUrl + 'anonymization',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_security.svg',
        navbarOrder: 4,
        requiredLicense: function() {
            return 'BAS';
        },
        translationKey: 'ADMINCONSOLE.IP_ANON.LABEL'
    };

    const ipAnonState = {
        name: 'anonymizationstate',
        parent: ipAnon.name,
        redirectTo: 'tor', // auto activate substate
        requiredLicense: ipAnon.requiredLicense,
        component: 'ipAnonComponent'
    };

    const tor = {
        name: 'tor',
        url: slashOptionSubState + 'tor',
        parent: ipAnonState.name,
        tabOrder: 1,
        requiredLicense: ipAnonState.requiredLicense,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            torCountries: ['TorService', function(TorService) {
                return TorService.getAllTorCountries().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            selectedTorCountries: ['TorService', function(TorService) {
                return TorService.getSelectedTorExitNodes().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.TOR.LABEL',
        component: 'torComponent'
    };

    const vpnconnect = {
        name: 'vpnconnect',
        url: slashOptionSubState + 'vpn/:id',
        parent: ipAnonState.name,
        tabOrder: 2,
        requiredLicense: ipAnonState.requiredLicense,
        translationKey: 'ADMINCONSOLE.VPN_CONNECT.LABEL',
        component: 'vpnConnectComponent'
    };

    const vpnconnectDetails = {
        name: 'vpnconnectdetails',
        url: slashOptionSubState + 'vpn/details',
        parent: ipAnon.name,
        requiredLicense: ipAnon.requiredLicense,
        component: 'vpnConnectDetailsComponent'
    };


    // ** MAIN STATE: DNS
    const dns = {
        name: 'dns',
        parent: STATES.MAIN,
        redirectTo: 'dnsstate', // auto activate substate
        url: slashOptionUrl + 'dns',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_dns_black.svg',
        navbarOrder: 7,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            dnsEnabled: ['DnsService', function(DnsService) {
                return DnsService.loadDnsStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            configuration: ['DnsService', function(DnsService) {
                return DnsService.loadDnsConfiguration().then(function success(configuration) {
                    return configuration;
                }, function error() {
                    return null;
                });
            }]
        },
        requiredLicense: function() {
            return 'BAS';
        },
        translationKey: 'ADMINCONSOLE.DNS.LABEL'
    };

    const dnsState = {
        name: 'dnsstate',
        parent: dns.name,
        redirectTo: 'dnsstatus', // auto activate substate
        requiredLicense: dns.requiredLicense,
        component: 'dnsComponent'
    };

    const dnsStatus = {
        name: 'dnsstatus',
        url: slashOptionSubState + 'status',
        parent: dnsState.name,
        tabOrder: 1,
        requiredLicense: dns.requiredLicense,
        translationKey: 'ADMINCONSOLE.DNS_STATUS.LABEL',
        component: 'dnsStatusComponent'
    };

    const dnsServer = {
        name: 'dnsserver',
        url: slashOptionSubState + 'server',
        parent: dnsState.name,
        disabledWhenNoDns: true,
        disabledWhenNotCustom: true,
        tabOrder: 2,
        requiredLicense: dns.requiredLicense,
        translationKey: 'ADMINCONSOLE.DNS_SERVER.LABEL',
        component: 'dnsServerComponent'
    };

    const dnsLocal = {
        name: 'dnslocal',
        url: slashOptionSubState + 'local',
        parent: dnsState.name,
        disabledWhenNoDns: true,
        tabOrder: 3,
        requiredLicense: dns.requiredLicense,
        translationKey: 'ADMINCONSOLE.DNS_LOCAL.LABEL',
        component: 'dnsLocalComponent'
    };

    // const dnsServerDetails = {
    //     name: 'dnsserverdetails',
    //     url: slashOptionSubState + 'dnsserverdetails',
    //     parent: dns.name,
    //     requiredLicense: dns.requiredLicense,
    //     component: 'dnsServerDetailsComponent'
    // };
    //
    // const dnsLocalDetails = {
    //     name: 'dnslocaldetails',
    //     url: slashOptionSubState + 'dnslocaldetails',
    //     parent: dns.name,
    //     requiredLicense: dns.requiredLicense,
    //     component: 'dnsLocalDetailsComponent'
    // };

    // ** MAIN STATE: FILTER
    const filter = {
        name: 'filter',
        parent: STATES.MAIN,
        redirectTo: 'filterstate', // auto activate substate, show tab view
        url: slashOptionUrl + 'filter',
        showInNavbar: true,
        iconUrl: '/img/icons/eblocker-blocked-24px-2.svg',
        navbarOrder: 6,
        requiredLicense: function() {
            return 'PRO';
        },
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            sslEnabled: ['SslService', function(SslService) {
                return SslService.getStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.FILTER.LABEL'
    };

    // contains the tabs
    const filterState = {
        name: 'filterstate',
        parent: filter.name,
        redirectTo: 'filteroverview', // auto activate substate, actual tab
        requiredLicense: filter.requiredLicense,
        component: 'filterComponent'
    };

    const filterOverview = {
        name: 'filteroverview',
        url: slashOptionSubState + 'overview/:id',
        parent: filterState.name,
        tabOrder: 1,
        requiredLicense: filter.requiredLicense,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            updateStatus: ['UpdateService', function(UpdateService) {
                return UpdateService.getStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            devices: ['DeviceService', function(DeviceService) {
                return DeviceService.getAll().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
            dnsEnabled: ['DnsService', function(DnsService) {
                return DnsService.loadDnsStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.FILTER_OVERVIEW.LABEL',
        component: 'filterOverviewComponent'
    };

    const filterDetails = {
        name: STATES.FILTER_DETAILS,
        url: slashOptionSubState + 'details',
        parent: filter.name,
        ignoreTab: true,
        requiredLicense: filter.requiredLicense,
        component: 'filterDetailsComponent'
    };

    const advancedFilterSettings = {
        name: 'advancedsettings',
        url: slashOptionSubState + 'advanced',
        parent: filterState.name,
        tabOrder: 2,
        requiredLicense: filter.requiredLicense,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            captivePortal: ['CaptivePortalService', function(CaptivePortalService) {
                return CaptivePortalService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            compressionMode: ['CompressionService', function(CompressionService) {
                return CompressionService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            doNotTrack: ['DoNotTrackService', function(DoNotTrackService) {
                return DoNotTrackService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            referrer: ['ReferrerService', function(ReferrerService) {
                return ReferrerService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            webRtc: ['WebRtcService', function(WebRtcService) {
                return WebRtcService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.ADVANCED_FILTER_SETTINGS.LABEL',
        component: 'advancedSettingsComponent'
    };

    const filterAnalysis = {
        name: 'filteranalysis',
        url: slashOptionSubState + 'analysis',
        parent: filterState.name,
        tabOrder: 3,
        requiredLicense: filter.requiredLicense,
        translationKey: 'ADMINCONSOLE.FILTER_ANALYSIS.LABEL',
        component: 'analysisComponent'
    };

    const analysisDetails = {
        name: 'analysisdetails',
        url: slashOptionSubState + 'analysis/details',
        parent: filter.name,
        ignoreTab: true,
        requiredLicense: filterAnalysis.requiredLicense,
        component: 'analysisDetailsComponent'
    };

    // ** MAIN STATE: SYSTEM
    const system = {
        name: 'system',
        parent: STATES.MAIN,
        redirectTo: 'timeandlanguage', // auto activate substate
        url: slashOptionUrl + 'system',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_settings.svg',
        navbarOrder: 9,
        requiredLicense: function() {
            return 'WOL';
        },
        translationKey: 'ADMINCONSOLE.SYSTEM.LABEL',
        component: 'systemComponent'
    };

    const adminPassword = {
        name: 'adminpassword',
        url: slashOptionSubState + 'adminpassword',
        parent: system.name,
        tabOrder: 2,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.ADMIN_PASSWORD.LABEL',
        component: 'adminPasswordComponent'
    };

    const diagnostics = {
        name: 'diagnostics',
        url: slashOptionSubState + 'diagnostics',
        parent: system.name,
        tabOrder: 6,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.DIAGNOSTICS.LABEL',
        component: 'diagnosticsComponent'
    };

    const events = {
        name: 'events',
        url: slashOptionSubState + 'events',
        parent: system.name,
        tabOrder: 4,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.EVENTS.LABEL',
        component: 'eventsComponent'
    };

    const reset = {
        name: 'reset',
        url: slashOptionSubState + 'reset',
        parent: system.name,
        tabOrder: 7,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.RESET.LABEL',
        component: 'resetComponent'
    };

    const status = {
        name: 'status',
        url: slashOptionSubState + 'status',
        parent: system.name,
        tabOrder: 2,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.STATUS.LABEL',
        component: 'statusComponent'
    };

    const tasks = {
        name: 'tasks',
        url: slashOptionSubState + 'tasks',
        parent: system.name,
        tabOrder: 5,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.TASKS.LABEL',
        component: 'tasksComponent',
        hide: true
    };

    const timeAndLanguage = {
        name: 'timeandlanguage',
        url: slashOptionSubState + 'locale',
        parent: system.name,
        tabOrder: 1,
        requiredLicense: system.requiredLicense,
        translationKey: 'ADMINCONSOLE.TIME_LANGUAGE.LABEL',
        component: 'timeLanguageComponent'
    };

    // ** MAIN STATE: NETWORK
    const network = {
        name: 'network',
        parent: STATES.MAIN,
        // redirectTo: 'networksettings', // auto activate substate
        url: slashOptionUrl + 'network',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_settings_ethernet_black.svg',
        navbarOrder: 10,
        requiredLicense: function() {
            return 'WOL';
        },
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            configuration: ['NetworkService', function(NetworkService) {
                return NetworkService.getNetworkConfig().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            dnsEnabled: ['DnsService', function(DnsService) {
                return DnsService.loadDnsStatus(true).then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.NETWORK_SETTINGS.LABEL',
        component: 'networkSettingsComponent'
        // component: 'networkComponent'
    };

    // const networkSettings = {
    //     name: 'networksettings',
    //     url: slashOptionSubState + 'networksettings',
    //     parent: network.name,
    //     requiredLicense: network.requiredLicense,
    //     translationKey: 'ADMINCONSOLE.NETWORK_SETTINGS.LABEL',
    //     component: 'networkSettingsComponent'
    // };

    const networkWizard = {
        name: STATES.NETWORK_WIZARD,
        parent: STATES.PARENT,
        url: 'network/assistent', // must be equal to state name for now
        requiredLicense: network.requiredLicense,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.NETWORK_WIZARD.TOOLBAR.TITLE',
        component: 'networkWizardComponent'
    };

    const doctor = {
        name: STATES.DOCTOR,
        parent: STATES.MAIN,
        url: slashOptionUrl + 'doctor',
        showInNavbar: true,
        iconUrl: '/img/icons/ic_doctor.svg',
        navbarOrder: 11,
        requiredLicense: function() {
            return 'BAS';
        },
        translationKey: 'ADMINCONSOLE.DOCTOR.LABEL',
        component: 'doctorDiagnosisComponent'
    };

    const doctorDiagnosisDetails = {
        name: STATES.DOCTOR_DIAGNOSIS_DETAILS,
        url: slashOptionSubState + 'doctordiagnosis/details',
        parent: parentalControl.name,
        ignoreTab: true,
        requiredLicense: function() {
            return 'BAS';
        },
        component: 'doctorDiagnosisDetailsComponent'
    };

    // ** MAIN STATE: VPN HOME (eBlocker Mobile)
    const vpnHome = {
        name: 'mobile',
        url: slashOptionUrl + 'mobile',
        parent: STATES.MAIN,
        showInNavbar: true,
        iconUrl: '/img/icons/ic_smartphone_black.svg',
        navbarOrder: 5,
        requiredLicense: function() {
            return 'BAS';
        },
        translationKey: 'ADMINCONSOLE.VPN_HOME.LABEL',
        component: 'vpnHomeStatusComponent'
    };

    // const vpnHomeState = {
    //     name: 'mobilestate',
    //     parent: vpnHome.name,
    //     redirectTo: 'mobilestatus', // auto activate substate, actual tab
    //     requiredLicense: vpnHome.requiredLicense,
    //     component: 'vpnHomeComponent'
    // };
    //
    // const vpnHomeStatus = {
    //     name: 'mobilestatus',
    //     url: slashOptionSubState + 'mobilestatus',
    //     parent: vpnHomeState.name,
    //     tabOrder: 1,
    //     requiredLicense: vpnHomeState.requiredLicense,
    //     translationKey: 'ADMINCONSOLE.VPN_HOME_STATUS.LABEL',
    //     component: 'vpnHomeStatusComponent'
    // };
    //
    // const vpnHomeDevices = {
    //     name: 'mobiledevices',
    //     url: slashOptionSubState + 'mobiledevices',
    //     parent: vpnHomeState.name,
    //     tabOrder: 2,
    //     requiredLicense: vpnHomeState.requiredLicense,
    //     translationKey: 'ADMINCONSOLE.VPN_HOME_DEVICES.LABEL',
    //     component: 'vpnHomeDevicesComponent'
    // };

    const vpnHomeWizard = {
        name: STATES.VPN_HOME_WIZARD,
        parent: STATES.PARENT,
        url: 'mobile/assistent', // must be equal to state name for now
        requiredLicense: vpnHome.requiredLicense,
        allowActive: true,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            vpnHomeStatus: ['VpnHomeService', function (VpnHomeService) {
                return VpnHomeService.loadStatus().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            security: 'security',
            token: ['security', function(security) {
                // ** security service should already be initialized
                return security.getToken();
            }],
            registrationInfo: ['token', 'RegistrationService', function(token, RegistrationService) {
                // 'token' needed only indirectly for the REST call, so we wait until
                // the token has been loaded (from storage or server). Afterwards the security
                // service will set the default bearer header, allowing to make this call.
                // We need to load the registration data, because wizard is out-of-app. If user reloads
                // the browser, the app will try to re-enter wizard, w/o loading registration info, because that is
                // done in main state. But wizard, as said, is out-of-app and not child of main state.
                return RegistrationService.loadRegistrationInfo().then(function success(response) {
                    return response;
                }, function error() {
                    return null;
                });
            }]
        },
        translationKey: 'ADMINCONSOLE.VPN_HOME_WIZARD.TOOLBAR.TITLE',
        component: 'vpnHomeWizardComponent'
    };


    // FIXME: don't use const for each state. e.g. push into allStates right away
    const allStates = [home, homeLicense, homeUpdate, adminPassword, homeAbout,
        homeLegal, parentalControl, parentalControlState, devices, ssl, sslStatus,
        sslCertificate, sslFails, trustedApps, trustedDomains, ipAnon, ipAnonState,
        system, network, networkWizard, vpnHome, manualRecording, users,
        blacklists, whitelists, tor, vpnconnect, dns, status, timeAndLanguage,
        events, reset, diagnostics, usersDetails, usersProfileDetails,
        blacklistDetails, whitelistDetails, devicesState, devicesDetails, vpnconnectDetails, tasks,
        trustedAppsDetails, sslstate, filter, filterState, advancedFilterSettings,
        vpnHomeWizard, devicesList, devicesDiscovery, dnsStatus, dnsLocal,
        dnsServer, dnsState, filterOverview, filterAnalysis, analysisDetails, defaultState, filterDetails,
        doctor, doctorDiagnosisDetails];

    // ** MAIN STATE ERROR: NOT LICENSED
    const notLicensed = {
        name: 'nolicense',
        parent: STATES.MAIN,
        requiredLicense: function() {
            return 'WOL';
        },
        component: 'notLicensedComponent'
    };

    /**
     * Unified layout for out-of-app states (booting/updating/shutdown)
     */
    const systemPending = {
        name: 'systempending',
        parent: STATES.PARENT,
        // abstract: true,
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.STAND_BY.TOOLBAR.TITLE',
        component: 'systemPendingComponent'
    };

    const print = {
        name: 'print',
        parent: STATES.PARENT,
        url: 'print',
        ignoreHook: true, // should always be possible
        translationKey: 'ADMINCONSOLE.PRINT.TOOLBAR.TITLE',
        component: 'printComponent'
    };

    /**
     * Required, so that we can move from booting/updating/shutdown back
     * to the managing state. Making state siblings allows the onInit / onDestroy
     * to be called. We could remove this state and let booting/etc. inherit from
     * systemPending directly. Then we need to change the data management.
     */
    const standBy = {
        name: 'standby',
        parent: systemPending.name,
        url: 'standby?origin',
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.STAND_BY.TOOLBAR.TITLE',
        component: 'standByComponent'
    };

    const factoryResetScreen = {
        name: 'factoryResetScreen',
        parent: systemPending.name,
        url: 'factoryreset',
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.FACTORY_RESET_SCREEN.TOOLBAR.TITLE',
        component: 'factoryResetScreenComponent'
    };

    const booting = {
        name: 'booting',
        parent: systemPending.name,
        url: 'booting',
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.BOOTING.TOOLBAR.TITLE',
        component: 'bootingComponent'
    };

    const updating = {
        name: 'updating',
        parent: systemPending.name,
        url: 'updating',
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.UPDATING.TOOLBAR.TITLE',
        component: 'updatingComponent'
    };

    const shutdown = {
        name: 'shutdown',
        parent: systemPending.name,
        url: 'shutdown',
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.SHUTDOWN.TOOLBAR.TITLE',
        component: 'shutdownComponent'
    };

    /*
     * Splash screen
     */
    const appState = {
        name: 'app',
        url: '/',
        component: 'settingsComponent',
        ignoreHook: true, // parent state: should always be possible
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            /*
             * to avoid flash of untranslated content: returns a call to $translate.onReady, effectively blocking the
             * rendering of the app until the first translation file is loaded:
             */
            translateReady: ['$translate', function($translate) {
                return $translate.onReady();
            }],
            states: ['StateService', function(StateService) {
                StateService.setStates(allStates);
                return allStates;
            }],
            settings: 'settings',
            locale: ['settings', function(settings) {
                return settings.load().then(function s(r) {
                    return r;
                }, function e() {
                    return settings.getDefaultLocale();
                });
            }],
            consoleUrl: ['ConsoleService', function(ConsoleService) {
                return ConsoleService.init().then(function success(response) {
                    return response;
                }, function error() {
                    return null;
                });
            }],
            systemStatus: ['SystemService', function(SystemService) {
                return SystemService.loadSystemStatus();
            }]
        }
    };

    const activation = {
        name: 'activation',
        parent: STATES.PARENT,
        resolvePolicy: { async: 'WAIT', when: 'LAZY' },
        resolve: {
            setupWizardInfo: ['SetupService', function(SetupService) {
                return SetupService.getInfo().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            regions: ['TimezoneService', function(TimezoneService) {
                return TimezoneService.getRegions().then(function success(response) {
                    return response.data;
                }, function error() {
                    return [];
                });
            }],
        },
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.ACTIVATION.TOOLBAR.TITLE',
        component: 'activationComponent'
    };

    const activationFinish = {
        name: 'activationfinish',
        parent: STATES.PARENT,
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.ACTIVATION_FINISH.TOOLBAR.TITLE',
        component: 'activationFinishComponent'
    };

    const login = {
        name: 'login',
        parent: STATES.PARENT,
        url: 'login',
        allowActive: true,
        ignoreHook: true,
        translationKey: 'ADMINCONSOLE.LOGIN.TOOLBAR.TITLE',
        component: 'loginComponent'
    };

    const logout = {
        name: 'logout',
        parent: STATES.PARENT,
        ignoreHook: true, // logout should always be possible
        component: 'logoutComponent'
    };

    const resetPassword = {
        name: 'resetpassword',
        parent: STATES.PARENT,
        url: 'resetpassword',
        allowActive: true,
        ignoreHook: true, // reset PW must always be available
        component: 'resetPasswordComponent'
    };

    const authentication = {
        name: 'auth',
        parent: STATES.PARENT,
        ignoreHook: true,
        url: 'auth',
        component: 'authComponent'
    };

    const splashScreen = {
        name: STATES.SPLASH,
        parent: STATES.PARENT,
        ignoreHook: true, // should always be possible
        allowActive: true,
        translationKey: 'ADMINCONSOLE.SPLASH_SCREEN.TOOLBAR.TITLE',
        component: 'splashScreenComponent'
    };

    /*
     * Main screen with resolved bootstrap dependencies
     */

    const mainState = {
        name: STATES.MAIN,
        parent: STATES.PARENT,
        abstract: true,
        component: 'mainComponent',
        // MUST be eager, so that e.g. registrationInfo is present in transition hook
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            featureToggleIp6: ['FeatureToggleService', function(FeatureToggleService) {
                return FeatureToggleService.isFeatureEnabled('ip6').then(function success(response) {
                    return response.data;
                }, function error() {
                    return false;
                });
            }],
            security: 'security',
            token: ['security', function(security) {
                // ** security service should already be initialized
                return security.getToken();
            }],
            registrationInfo: ['token', 'RegistrationService', function(token, RegistrationService) {
                // 'token' needed only indirectly for the REST call, so we wait until
                // the token has been loaded (from storage or server). Afterwards the security
                // service will set the default bearer header, allowing to make this call.
                return RegistrationService.loadRegistrationInfo().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            postRegistrationInformation: ['CustomerInfoService', function(CustomerInfoService) {
                return CustomerInfoService.getCustomerInfo().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            setupInfo: ['SetupService', function(SetupService) {
                return SetupService.getInfo().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            configSections: ['StateService', function(StateService) {
                return StateService.getStates().filter(function(state) {
                    return state.showInNavbar;
                }, function error() {
                    return null;
                });
            }],
            showSplashScreen: ['SplashService', function(SplashService) {
                return SplashService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return null;
                });
            }],
            viewConfig: ['TasksService', function(TasksService) {
                // decide whether to show task tab or not. Must be done here, so that we can go to task-state by URL.
                // Otherwise hide property (default is false) will prevent traversing to task state until system state
                // is loaded and updates the property.
                return TasksService.getConfig().then(function success(config) {
                    tasks.hide = !config.enabled;
                    return config;
                }, function error() {
                    return {};
                });
            }]
        }
    };

    const expiredState = {
        name: 'expired',
        parent: STATES.PARENT,
        translationKey: 'ADMINCONSOLE.EXPIRED.TOOLBAR.TITLE',
        ignoreHook: true,
        component: 'expiredComponent'
    };

    const openSourceLicenses = {
        name: STATES.OPEN_SOURCE_LICENSES,
        parent: STATES.PARENT,
        url: 'licenses', // must be equal to state name for now
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'openSourceLicensesComponent'
    };

    const openSourceLicensesJava = {
        name: STATES.OPEN_SOURCE_LICENSES_JAVA,
        parent: STATES.PARENT,
        url: 'licenses/java',
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'libsJavaComponent'
    };

    const openSourceLicensesCCpp = {
        name: STATES.OPEN_SOURCE_LICENSES_CCPP,
        parent: STATES.PARENT,
        url: 'licenses/ccpp',
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'libsCCppComponent'
    };

    const openSourceLicensesJavascript = {
        name: STATES.OPEN_SOURCE_LICENSES_JAVASCRIPT,
        parent: STATES.PARENT,
        url: 'licenses/javascript',
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'libsJavascriptComponent'
    };

    const openSourceLicensesRuby = {
        name: STATES.OPEN_SOURCE_LICENSES_RUBY,
        parent: STATES.PARENT,
        url: 'licenses/ruby',
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'libsRubyComponent'
    };

    const openSourceLicensesDebian = {
        name: STATES.OPEN_SOURCE_LICENSES_DEBIAN,
        parent: STATES.PARENT,
        url: 'licenses/debian',
        requiredLicense: function() {
            return 'BAS';
        },
        ignoreHook: true,
        allowActive: true,
        translationKey: 'ADMINCONSOLE.OPEN_SOURCE_LICENSES.TOOLBAR.TITLE',
        component: 'libsDebianComponent'
    };

    allStates.push(splashScreen);

    allStates.push(activationFinish);
    allStates.push(activation);
    allStates.push(updating);
    allStates.push(booting);
    allStates.push(standBy);
    allStates.push(factoryResetScreen);
    allStates.push(print);
    allStates.push(systemPending);

    allStates.push(shutdown);
    allStates.push(login);
    allStates.push(logout);
    allStates.push(resetPassword);
    allStates.push(notLicensed);
    allStates.push(authentication);
    allStates.push(expiredState);
    allStates.push(mainState);
    allStates.push(appState);
    allStates.push(openSourceLicenses);
    allStates.push(openSourceLicensesJava);
    allStates.push(openSourceLicensesCCpp);
    allStates.push(openSourceLicensesJavascript);
    allStates.push(openSourceLicensesRuby);
    allStates.push(openSourceLicensesDebian);

    /*
     * Reason that states 'main' and 'app' are excluded from setting state.params
     *
     * Issue:
     * We are in 'deviceslist' state. We click on a device which will call goToState('devciesdetails'). We start
     * to transition to 'devicesdetails'. The parent state, 'app' state, should not be called again,
     * since we are already in one of its child states. However, when we set state-property 'params' of the 'app' state
     * the 'app' state is loaded again when a child state is transitioned to. The 'app' states controller
     * will then call handleSystemStatus and will transition to state AUTH which will supersede the transition to
     * 'deviceslist' and we'll end up in the default state (devices-list).
     *
     * Likely caused by:
     * We open the child state with a param. So UIRouter re-initializes the parent state
     * based on that parameter. And when we do not specify a parameter in the 'app' state it does not
     * make sense for the UIRouter to re-initialize if a child state is called with param, since the
     * param is ignored in the 'app' state anyway. So perhaps that the reason the UIRouter does not
     * re-init the parent state in that case.
     *
     * On every transition (StateService.goToState) we (use to) pass at least an empty object as param.
     * If that is changed to undefined if no value is passed, then the parent state does not get re-initialized.
     * So an empty object seems to be sufficient to trigger the UIRouter to re-init the entire state-tree.
     *
     */
    allStates.forEach((state) => {
        // initializes params and allows to pass these params within this state
        // to simplify and to avoid errors during development we add these params to all states.

        // Removing main here will cause the resolves not to be called as often as
        // before. This may cause unexpected behavior.
        if (state.name !== STATES.MAIN && state.name !== STATES.PARENT) {
            state.params = {param: null, id: null, origin: null};
        }

        $stateProvider.state(state);
    });
}
