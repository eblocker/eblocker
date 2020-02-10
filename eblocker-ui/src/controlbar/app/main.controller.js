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
export default function  MainController(logger, $translate, locale, $sce, $window, $timeout, UserService,  // jshint ignore: line
                                        RegistrationService, userProfile, allMessages) { // jshint ignore: line
    'ngInject';
    logger.info('Showing main screen, setting language to \'' + locale.language + '\'');
    $translate.use(locale.language);

    // The controlbar is restricted if the user is a child:
    const restrictUser = userProfile.hidden || UserService.getOperatingUser().userRole === 'CHILD';

    const showOnlineTime = userProfile.controlmodeTime || userProfile.controlmodeMaxUsage;

    const registrationInfo = RegistrationService.getRegistrationInfo();

    const isRegistered = angular.isUndefined(registrationInfo) ||
        angular.isUndefined(registrationInfo.isRegistered) ||
        registrationInfo.isRegistered;

    const isLicenseAboutToExpire = angular.isUndefined(registrationInfo) ||
        angular.isUndefined(registrationInfo.licenseAboutToExpire) ||
        registrationInfo.licenseAboutToExpire;

    const noMessages = !angular.isArray(allMessages) || allMessages.length <= 0;

    const vm = this;

    // Used to decide whether to page or not.
    // Accounts for eblocker icons. All entries (maxWidth) plus this
    // margin (2xIcons and little more) need to fit into the controlbar.
    // Otherwise controlbar is paginated.
    const maxPixelWhenToPaginateControlBar = 130; //100;

    // Used to decide whether next elements still fits on the page.
    // Accounts for margin between entries and possible arrows. We need
    // to reduce size of page, so that we can still use controlbar on
    // touch devices.
    const maxPixelWhenToBreakPage = 250; //230;

    const dashboardLink = {
        name: 'Dashboard',
        html: $sce.trustAsHtml('<dashboard-link layout-fill></dashboard-link>'),
        licensed: true, // WOL
        maxWidth: 88
    };

    const user = {
        name: 'User',
        html: $sce.trustAsHtml('<user layout-fill></user>'),
        hide: UserService.getAssignedUser().system,
        licensed: RegistrationService.hasProductKey('FAM'),
        maxWidth: 88
    };

    const trackers = {
        name: 'Trackers',
        html: $sce.trustAsHtml('<trackers layout-fill></trackers>'),
        hide: restrictUser,
        licensed: RegistrationService.hasProductKey('PRO'),
        maxWidth: 88
    };

    const ads = {
        name: 'Ads',
        html: $sce.trustAsHtml('<ads layout-fill></ads>'),
        hide: restrictUser,
        licensed: RegistrationService.hasProductKey('PRO'),
        maxWidth: 88
    };

    const ipAnon = {
        name: 'IP-Anon',
        html: $sce.trustAsHtml('<ip-anon layout-fill></ip-anon>'),
        hide: restrictUser,
        licensed: RegistrationService.hasProductKey('BAS'),
        maxWidth: 143
    };

    const cloaking = {
        name: 'Cloaking',
        html: $sce.trustAsHtml('<cloaking layout-fill></cloaking>'),
        hide: restrictUser,
        licensed: RegistrationService.hasProductKey('PRO'),
        maxWidth: 105
    };

    const pause = {
        name: 'Pause',
        html: $sce.trustAsHtml('<pause layout-fill></pause>'),
        hide: restrictUser,
        licensed: RegistrationService.hasProductKey('BAS'),
        maxWidth: 88
    };

    const messages = {
        name: 'Messages',
        html: $sce.trustAsHtml('<messages layout-fill></messages>'),
        hide: restrictUser || noMessages,
        licensed: true, // WOL
        maxWidth: 92
    };

    const settings = {
        name: 'Settings',
        html: $sce.trustAsHtml('<settings layout-fill></settings>'),
        hide: restrictUser,
        licensed: true, // WOL
        maxWidth: 101
    };

    const onlineTime = {
        name: 'OnlineTime',
        html: $sce.trustAsHtml('<online-time layout-fill></online-time>'),
        hide: !showOnlineTime,
        licensed: RegistrationService.hasProductKey('FAM'), // FAM
        maxWidth: 101
    };

    const help = {
        name: 'Help',
        html: $sce.trustAsHtml('<help layout-fill></help>'),
        licensed: true, // WOL
        maxWidth: 88
    };

    const activation = {
        name: 'Activation',
        html: $sce.trustAsHtml('<activation layout-fill></activation>'),
        hide: restrictUser || isRegistered,
        licensed: true, // WOL
        maxWidth: 89
    };

    const renew = {
        name: 'Renew',
        html: $sce.trustAsHtml('<renew-license layout-fill></renew-license>'),
        hide: restrictUser || !isLicenseAboutToExpire,
        licensed: true, // WOL
        noTruncation: true,
        maxWidth: 117
    };

    vm.allPages = [];
    vm.currentPage = 0;
    vm.getCurrentPage = getCurrentPage;
    vm.nextPage = nextPage;
    vm.previousPage = previousPage;
    vm.isPaginationActive = isPaginationActive;
    vm.getCurrentPageNumber = getCurrentPageNumber;
    vm.getOverallWidth = getOverallWidth;
    vm.doesElementFit = doesElementFit;
    vm.getAllPages = getAllPages;
    vm.getSinglePage = getSinglePage;
    vm.showPaginatorLeft = showPaginatorLeft;
    vm.showPaginatorRight = showPaginatorRight;

    function isPaginationActive() {
        return vm.allPages.length > 1;
    }

    function nextPage() {
        if (vm.currentPage < vm.allPages.length - 1) {
            vm.currentPage++;
        }
    }

    function previousPage() {
        if (vm.currentPage > 0) {
            vm.currentPage--;
        }
    }

    function getCurrentPage() {
        return vm.allPages[vm.currentPage];
    }

    function showPaginatorRight() {
        return isPaginationActive() && vm.currentPage !== vm.allPages.length - 1;
    }

    function showPaginatorLeft() {
        return isPaginationActive() && vm.currentPage !== 0;
    }

    vm.$onInit = function() {
        const allEntries = [
            dashboardLink, user, trackers, ads,
            ipAnon, cloaking, onlineTime, pause, messages,
            settings, help, activation, renew
        ];

        vm.entries = [];

        allEntries.forEach((entry) => {
            if (!entry.hide && entry.licensed) {
                vm.entries.push(entry);
            }
        });

        // initial rendering of controlbar
        calculatePagination();
    };

    let inProgress;
    // e.g. Firefox in iOS sometimes calls resize event when for some
    // reason width differs
    let oldWidth = -51;

    angular.element($window).on('resize', resizeWrapper);

    vm.$onDestroy = function() {
        angular.element($window).off('resize', resizeWrapper);
    };

    // ** Wraps the rearrange function in a timeout, so that
    // the dashboard is not constantly rearranged.
    function resizeWrapper() {
        if (!inProgress && (Math.abs($window.innerWidth - oldWidth) > 50)) {
            oldWidth = $window.innerWidth;
            inProgress = true;
            $timeout(calculatePagination, 0);
        }
    }

    function calculatePagination() {
        // get and remember first item on current page
        const farMostLeftItem = getFirstElement(vm.currentPage, vm.allPages);

        // ** Only re-render if width has changes; ignore height
        const currentWidth = $window.innerWidth;
        const overallEntryWidth = getOverallWidth(vm.entries, 'maxWidth');

        if ( (overallEntryWidth + maxPixelWhenToPaginateControlBar) > currentWidth) {
            // screen to small, activate paginator
            vm.allPages.length = 0;
            vm.allPages = getAllPages(vm.entries, currentWidth);
        } else {
            vm.allPages = [ angular.copy(vm.entries) ];
        }

        vm.currentPage = getCurrentPageNumber(farMostLeftItem, vm.allPages);
        inProgress = false;
    }

    function getFirstElement(currentPage, allPages) {
        return angular.isArray(allPages[currentPage]) ? allPages[currentPage][0] : dashboardLink;
    }

    function getCurrentPageNumber(firstItem, allPages) {
        let newPageNum = 0;
        allPages.forEach((page, index) => {
            page.forEach((item) => {
                if (item.name === firstItem.name) {
                    newPageNum = index;
                }
            });
        });
        return newPageNum;
    }

    function getAllPages(array, width) {
        const allPages = [];

        const page = getSinglePage(array, width);
        allPages.push(page);

        if (page.length === array.length) {
            return allPages;
        } else {
            const tmp = getAllPages(array.slice(page.length, array.length), width);
            tmp.forEach((item) => {
                allPages.push(item);
            });
            return allPages;
        }
    }

    function getSinglePage(array, widthPerPage) {
        const page = [];
        let full = false;
        array.forEach((item, index) => {
            if (index === 0) {
                page.push(item);
            } else if (!full && doesElementFit(page, item, widthPerPage - maxPixelWhenToBreakPage)) {
                // minus maxPixelWhenToBreakPage to account for eBlocker Icons and some margin/paginator
                page.push(item);
            } else {
                // keep order: don't add anymore elements after this one, even if it fits.
                // -> otherwise we would simply leave out larger elements and break order of entries
                full = true;
            }
        });
        return page;
    }

    function doesElementFit(array, element, availableWidth) {
        let overallWidth = getOverallWidth(array, 'maxWidth');
        return (overallWidth + element.maxWidth) <= availableWidth;
    }

    function getOverallWidth(array, property) {
        let width = 0;
        array.forEach(function(entry) {
            width = width + entry[property];
        });
        return width;
    }
}
