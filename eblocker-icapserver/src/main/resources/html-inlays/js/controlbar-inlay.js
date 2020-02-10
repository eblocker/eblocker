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
(function() {
    if (this !== top) {
        // make sure no icon is displayed, if window is not top
        hideEverything();
        return;
    }

    var controlbarOpened = false;
    var blockedAdsTrackers = 0;
    var timeoutID;
    var intervalID = window.setInterval(eblockerUpdateBadge, 2000);

    var overlayVisible = false;
    var currentIconPosition = '@EBLOCKER_ICON_POSITION@';

    var welcomeEnabledParam = '@EBLOCKER_WELCOME_ENABLED@';
    var showWelcomePage = welcomeEnabledParam.toString() === 'true';

    var autoHideParam = '@EBLOCKER_HIDE_ICON_ENABLED@';
    var autoHideIcon = autoHideParam.toString() === 'true';
    var hideAfterSeconds = '@EBLOCKER_HIDE_ICON_AFTER_SECONDS@';

    var reminderParam = '@EBLOCKER_REMINDER_ENABLED@';
    var isReminderEnabled = reminderParam.toString() === 'true';

    if (showWelcomePage === true) {
        showEblockerOverlay('@EBLOCKER_CONTROL_BAR_URL@/advice/#!/welcome/@EBLOCKER_PAGE_CONTEXT_ID@');
    }

    if (autoHideIcon === true) {
        timeoutID = window.setTimeout(hideEverything, hideAfterSeconds);
    }

    if (isReminderEnabled === true) {
        showReminder();
    }

    reportTopPage();
    eblockerShowMessageBadge();

    window.addEventListener('message', receiveEblockerMessage, false);
    document.getElementById(currentIconPosition).style.display = 'block';
    document.getElementById('eblocker-icon-left').addEventListener('click', clickListenerIconLeft, false);
    document.getElementById('eblocker-icon-right').addEventListener('click', clickListenerIconRight, false);

    function clickListenerIconLeft() {
        toggleEblockerControlbar('eblocker-icon-left');
    }

    function clickListenerIconRight () {
        toggleEblockerControlbar('eblocker-icon-right');
    }

    function reportTopPage() {
        var req = new XMLHttpRequest();
        req.open('POST', '@EBLOCKER_CONTROL_BAR_URL@/pagecontext/@EBLOCKER_PAGE_CONTEXT_ID@/top', true);
        req.send();
    }

    function showReminder() {
        var req = new XMLHttpRequest();
        req.onload = function(){
            if (!JSON.parse(req.response)){
                showEblockerOverlay('@EBLOCKER_CONTROL_BAR_URL@/advice/#!/reminder/@EBLOCKER_PAGE_CONTEXT_ID@');
                overlayVisible = true;
            }
        };
        req.onerror = function(e){
            console.error('Cannot load device restrictions ', e);
        };
        req.open('GET', '@EBLOCKER_CONTROL_BAR_URL@/controlbar/deviceRestrictions');
        req.setRequestHeader('Accept', 'application/json');
        req.send();
    }

    function eblockerUpdateBadge() {
        if(overlayVisible === false){ //only show badge when overlay is not visible
            var badge = document.getElementById('eblocker-badge');
            var req = new XMLHttpRequest();
            req.onload = function() {
                var response = JSON.parse(req.response);
                var number = response['badge'];
                if (number > blockedAdsTrackers) {
                    blockedAdsTrackers = number;
                    badge.style.display = 'inline';
                    badge.innerHTML = blockedAdsTrackers;
                }
            };
            req.onerror = function(e) {
                console.error('Could not update badge ', e);
                window.clearInterval(intervalID);
            };
            req.open('GET', '@EBLOCKER_CONTROL_BAR_URL@/filter/badge/@EBLOCKER_PAGE_CONTEXT_ID@', true);
            req.setRequestHeader('Accept', 'application/json');
            req.send();
        }
    }

    function eblockerShowMessageBadge() {
        if(overlayVisible === false) { //only show badge when overlay is not visible
            var badge = document.getElementById('eblocker-message-badge');
            var req = new XMLHttpRequest();
            req.onload = function() {
                var iconState = JSON.parse(req.response);
                // 'msg' must accessed via bracket notation. With dot notation minification will replace 'msg'
                // of 'iconState.msg' by some random value, but will not actually replace the property name within the
                // object iconState, because it is aquired at runtime and unknown during build time.
                if (iconState['msg'] !== '') {
                    badge.style.display = 'inline';
                    badge.innerHTML = iconState['msg'];
                } else{
                    badge.style.display = 'none';
                    badge.innerHTML = '';
                }
            };
            req.onerror = function(e) {
                console.error('Could not update message badge ', e);
            };
            req.open('GET', '@EBLOCKER_CONTROL_BAR_URL@/api/icon/state', true);
            req.setRequestHeader('Accept', 'application/json');
            req.send();
        }
    }

    function receiveEblockerMessage(event) {
        if (event.data.type === 'close-eblocker-overlay') {
            closeOverlay(currentIconPosition);
        } else if (event.data.type === 'close-eblocker-overlay-right') {
            closeOverlay('eblocker-icon-right');
        } else if (event.data.type === 'close-eblocker-overlay-left') {
            closeOverlay('eblocker-icon-left');
        }
    }

    function closeOverlay(iconId) {
        hideEblockerOverlay(iconId);
    }

    function toggleEblockerControlbar(iconId) {
        // remember which icon has been clicked.
        currentIconPosition = iconId;
        if (!controlbarOpened) {
            showEblockerOverlay('@EBLOCKER_CONTROL_BAR_URL@/controlbar/index.html#!/@EBLOCKER_PAGE_CONTEXT_ID@');
        } else {
            hideEblockerOverlay(iconId);
        }
    }

    function createEblockerIframe(frameId, parentId, location) {
        var iframe = document.createElement('iframe');
        iframe.setAttribute('id', frameId);
        iframe.setAttribute('src', location);
        iframe.setAttribute('style', 'border:0px; width:100%; height:100%; overflow:hidden; background-color:#ffffff00;');
        iframe.setAttribute('scrolling', 'no');
        document.getElementById(parentId).appendChild(iframe);
    }

    function removeEblockerIframe(frameId, parentId) {
        var iframe = document.getElementById(frameId);
        if (iframe !== null) {
            document.getElementById(parentId).removeChild(iframe);
        }
    }

    function showEblockerOverlay(url) {
        overlayVisible = true;
        controlbarOpened = true;

        if (timeoutID !== undefined) {
            // clear timeout (hide icon) and start a new one, once controlbar is hidden
            // Otherwise opening and closing of controlbar may expire the timeout.
            clearTimeout(timeoutID);
        }

        document.getElementById('eblocker-overlay').style.display = 'block';
        document.getElementById('eblocker-badge').style.display = 'none';
        document.getElementById('eblocker-message-badge').style.display = 'none';
        document.getElementById('eblocker-warning').style.display = 'none';

        // set z-index, so that controlbar can display larger icons
        document.getElementById('eblocker-icon-left').style.zIndex = 2147483637;
        document.getElementById('eblocker-icon-right').style.zIndex = 2147483637;
        createEblockerIframe('eblocker-overlay-iframe', 'eblocker-overlay-container', url);
    }

    function hideEblockerOverlay(iconId) {
        var req = new XMLHttpRequest();

        var iconPosition = iconId === 'eblocker-icon-left' ? 'LEFT' : 'RIGHT';
        req.open('POST', '@EBLOCKER_CONTROL_BAR_URL@/api/device/iconpos/' + iconPosition, true);
        req.send();

        overlayVisible = false;

        var otherIconId = (iconId === 'eblocker-icon-left' ? 'eblocker-icon-right' : 'eblocker-icon-left');

        document.getElementById('eblocker-overlay').style.display = 'none';
        controlbarOpened = false;

        var badge = document.getElementById('eblocker-badge');
        badge.style.display=(badge.innerHTML === '0' ? 'none': 'block');
        badge.className = 'eblocker-semitransparent eblocker-base-badge eblocker-base-badge-background';

        if (document.getElementById('eblocker-message-badge').innerHTML === ''){
            document.getElementById('eblocker-message-badge').style.display = 'none';
        } else {
            document.getElementById('eblocker-message-badge').style.display = 'block';
        }

        document.getElementById('eblocker-message-badge').className = 'eblocker-semitransparent eblocker-base-badge eblocker-message-badge-background';
        document.getElementById('eblocker-warning').style.display = '@EBLOCKER_WARNING@';
        document.getElementById(iconId).className = '@EBLOCKER_CLASS_WHITELISTED@';
        document.getElementById(iconId).style.display = 'block';
        document.getElementById(otherIconId).style.display = 'none';
        // Reset z-index, so that icons are visible on original page
        document.getElementById('eblocker-icon-left').style.zIndex = 2147483647;
        document.getElementById('eblocker-icon-right').style.zIndex = 2147483647;

        positionEblockerBadge(iconId);

        removeEblockerIframe('eblocker-overlay-iframe', 'eblocker-overlay-container');

        if (autoHideIcon === true) {
            timeoutID = window.setTimeout(hideEverything, hideAfterSeconds);
        }
    }

    function positionEblockerBadge(iconId) {
        var badge = document.getElementById('eblocker-badge');
        var msgBadge = document.getElementById('eblocker-message-badge');
        var warning = document.getElementById('eblocker-warning');

        warning.style.display = '@EBLOCKER_WARNING@';

        if (iconId === 'eblocker-icon-left') {
            badge.style.left = '32px';
            badge.style.right = '';
            msgBadge.style.left = '32px';
            msgBadge.style.right = '';
            warning.style.left= '53px';
            warning.style.right= '';

        } else {
            badge.style.right = '32px';
            badge.style.left = 'auto';
            msgBadge.style.right = '32px';
            msgBadge.style.left = 'auto';
            warning.style.right = '53px';
            warning.style.left = 'auto';
        }
    }

    function hideEverything(){
        if (!controlbarOpened) {
            document.getElementById('eblocker-html').style.display = 'none';
            window.clearInterval(intervalID);
        }
    }

})();
