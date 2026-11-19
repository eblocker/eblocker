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
                if (number != blockedAdsTrackers) {
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
        if (!event.data || typeof event.data !== 'object') return;
        var frame = document.getElementById('eblocker-overlay-iframe');
        if (event.data['type'] === 'start-eblocker-element-picker' &&
                frame && event.source === frame.contentWindow &&
                event.origin === new URL('@EBLOCKER_CONTROL_BAR_URL@').origin) {
            closeOverlay(currentIconPosition);
            startEblockerElementPicker();
            return;
        }
        if (event.data.type === 'close-eblocker-overlay') {
            closeOverlay(currentIconPosition);
        } else if (event.data.type === 'close-eblocker-overlay-right') {
            closeOverlay('eblocker-icon-right');
        } else if (event.data.type === 'close-eblocker-overlay-left') {
            closeOverlay('eblocker-icon-left');
        }
    }

    function startEblockerElementPicker() {
        var languages = navigator['languages'];
        var browserLanguage = ((languages && languages.length ? languages[0] : navigator['language']) || 'en').toLowerCase();
        var german = /^de(?:-|$)/.test(browserLanguage);
        function tr(deText, enText) { return german ? deText : enText; }
        var previous = window['__eblockerElementPicker'];
        if (previous) {
            previous.stop();
            return;
        }
        var host = document.createElement('div');
        host.setAttribute('data-eblocker-element-picker', '');
        var shadow = host['attachShadow']({mode: 'open'});
        var style = document.createElement('style');
        style.textContent = ':host{all:initial;position:fixed;inset:0;z-index:2147483647;pointer-events:none;font:14px Arial,sans-serif}' +
            '.capture{position:fixed;inset:0;pointer-events:auto;cursor:crosshair}' +
            '.box{position:fixed;border:2px solid #e9540d;background:#e9540d33;pointer-events:none;box-sizing:border-box}' +
            '.panel{position:fixed;left:16px;bottom:16px;max-width:min(580px,calc(100vw - 32px));padding:12px;border-radius:8px;background:#fff;color:#222;box-shadow:0 4px 24px #0005;pointer-events:auto}' +
            '.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:8px}' +
            '.rule{display:block;width:100%;box-sizing:border-box;margin-top:8px;padding:8px;font:12px monospace}' +
            'button{border:0;border-radius:4px;padding:7px 10px;cursor:pointer;background:#e9540d;color:#fff}' +
            'button.secondary{background:#e9e9e9;color:#222}[hidden]{display:none!important}';
        var capture = document.createElement('div');
        capture.className = 'capture';
        var box = document.createElement('div');
        box.className = 'box';
        box.hidden = true;
        var panel = document.createElement('div');
        panel.className = 'panel';
        var hint = document.createElement('div');
        hint.textContent = tr('Element ausw' + String['fromCharCode'](228) + 'hlen. Esc beendet den Picker.', 'Click an element. Esc exits the picker.');
        var rule = document.createElement('input');
        rule.className = 'rule';
        rule.readOnly = true;
        rule.hidden = true;
        var actions = document.createElement('div');
        actions.className = 'row';
        function makeButton(label, secondary) {
            var button = document.createElement('button');
            button.type = 'button';
            button.textContent = label;
            if (secondary) button.className = 'secondary';
            actions.appendChild(button);
            return button;
        }
        var preview = makeButton(tr('Vorschau', 'Preview'), false);
        var copy = makeButton(tr('Regel kopieren', 'Copy rule'), false);
        var below = makeButton(tr('Darunter', 'Underneath'), true);
        var parent = makeButton(tr('Elternelement', 'Parent element'), true);
        var again = makeButton(tr('Erneut ausw' + String['fromCharCode'](228) + 'hlen', 'Select again'), true);
        var close = makeButton(tr('Schlie' + String['fromCharCode'](223) + 'en', 'Close'), true);
        panel.appendChild(hint);
        panel.appendChild(rule);
        panel.appendChild(actions);
        shadow.appendChild(style);
        shadow.appendChild(capture);
        shadow.appendChild(box);
        shadow.appendChild(panel);
        var saveFrame = document.createElement('iframe');
        saveFrame.setAttribute('title', tr('eBlocker Regel speichern', 'Save eBlocker rule'));
        saveFrame.setAttribute('src', '@EBLOCKER_CONTROL_BAR_URL@/controlbar/index.html#!/picker-save');
        saveFrame.setAttribute('style', 'position:fixed;left:16px;bottom:150px;width:min(580px,calc(100vw - 32px));height:180px;border:0;border-radius:8px;background:#fff;pointer-events:auto');
        saveFrame.hidden = true;
        shadow.appendChild(saveFrame);
        document.documentElement.appendChild(host);

        var selected = null;
        var candidates = [];
        var candidateIndex = 0;
        var previewStyle = null;
        var active = true;
        var saveReady = false;
        var ruleMatches = -1;
        var ruleCoverage = 0;
        preview.hidden = copy.hidden = below.hidden = parent.hidden = again.hidden = true;

        function unique(selector) {
            try { return document.querySelectorAll(selector).length === 1; }
            catch (error) { return false; }
        }
        function segment(element) {
            var tag = element.localName.toLowerCase();
            var classes = Array.prototype.slice.call(element.classList)
                .filter(function(value) { return value.length < 60 && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(value); })
                .slice(0, 2);
            var withClass = tag + classes.map(function(value) { return '.' + value; }).join('');
            if (classes.length && unique(withClass)) return withClass;
            var index = 1;
            for (var sibling = element.previousElementSibling; sibling; sibling = sibling.previousElementSibling) {
                if (sibling.localName === element.localName) index++;
            }
            return withClass + ':nth-of-type(' + index + ')';
        }
        function selectorFor(element) {
            if (element.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(element.id)) {
                var byId = '#' + element.id;
                if (unique(byId)) return byId;
            }
            var selector = '';
            for (var current = element; current && current !== document.documentElement; current = current.parentElement) {
                selector = segment(current) + (selector ? ' > ' + selector : '');
                if (unique(selector)) return selector;
                if (current === document.body) break;
            }
            return selector;
        }
        function elementsAt(x, y) {
            capture.style.pointerEvents = 'none';
            try {
                return document['elementsFromPoint'](x, y).filter(function(element) {
                    return element !== host && !host.contains(element) &&
                        element !== document.body && element !== document.documentElement;
                });
            } finally {
                capture.style.pointerEvents = 'auto';
            }
        }
        function mark(element) {
            if (!element) { box.hidden = true; return; }
            var rect = element.getBoundingClientRect();
            box.hidden = false;
            box.style.left = rect.left + 'px';
            box.style.top = rect.top + 'px';
            box.style.width = rect.width + 'px';
            box.style.height = rect.height + 'px';
        }
        function removePreview() {
            if (previewStyle) { previewStyle.remove(); previewStyle = null; }
            preview.hidden = !selected;
        }
        function select(element) {
            removePreview();
            selected = element;
            var selector = selectorFor(element);
            if (!selector) return;
            rule.value = location.hostname + '##' + selector;
            ruleMatches = document.querySelectorAll(selector).length;
            var rect = element.getBoundingClientRect();
            ruleCoverage = Math.round(Math.min(100, rect.width * rect.height /
                Math.max(1, window.innerWidth * window.innerHeight) * 100));
            rule.hidden = false;
            preview.hidden = copy.hidden = false;
            below.hidden = candidates.length < 2;
            parent.hidden = !element.parentElement || element.parentElement === document.body;
            again.hidden = false;
            saveFrame.hidden = false;
            if (saveReady) saveFrame.contentWindow.postMessage({'type': 'eblocker-picker-rule', 'rule': rule.value,
                'matches': ruleMatches, 'coverage': ruleCoverage}, new URL('@EBLOCKER_CONTROL_BAR_URL@').origin);
            hint.textContent = tr('Regel pr' + String['fromCharCode'](252) + 'fen. ' + String['fromCharCode'](8222) + 'Darunter' + String['fromCharCode'](8220) + ' w' + String['fromCharCode'](228) + 'hlt ein verdecktes Element.', 'Check the rule. Underneath selects an obscured element.');
            mark(element);
        }
        function onMove(event) {
            if (active) mark(elementsAt(event.clientX, event.clientY)[0]);
        }
        function onClick(event) {
            if (!active) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            var stack = elementsAt(event.clientX, event.clientY);
            candidates = stack.filter(function(element, index) {
                return !stack.slice(0, index).some(function(above) { return element.contains(above); });
            });
            if (!candidates.length) return;
            candidateIndex = 0;
            active = false;
            select(candidates[0]);
        }
        function onSaveMessage(event) {
            if (event.source !== saveFrame.contentWindow ||
                    event.origin !== new URL('@EBLOCKER_CONTROL_BAR_URL@').origin || !event.data) return;
            if (event.data['type'] === 'eblocker-picker-save-ready') {
                saveReady = true;
                if (rule.value) saveFrame.contentWindow.postMessage({'type': 'eblocker-picker-rule', 'rule': rule.value,
                    'matches': ruleMatches, 'coverage': ruleCoverage}, event.origin);
            } else if (event.data['type'] === 'eblocker-picker-save-result') {
                var result = event.data['added'] ? tr('Regel in eBlocker gespeichert.', 'Rule saved in eBlocker.') : tr('Regel ist bereits vorhanden.', 'This rule already exists.');
                if (!event.data['enabled']) result += tr(' Eigene Regeln sind deaktiviert.', ' Custom rules are disabled.');
                if (event.data['added']) {
                    resetSelection(result + tr(' N' + String['fromCharCode'](228) + 'chstes Element ausw' + String['fromCharCode'](228) + 'hlen.', ' Select the next element.'));
                } else {
                    hint.textContent = result;
                }
            } else if (event.data['type'] === 'eblocker-picker-save-cancel') {
                saveFrame.hidden = true;
            }
        }
        function stop() {
            removePreview();
            window.removeEventListener('message', onSaveMessage);
            capture.removeEventListener('pointermove', onMove);
            capture.removeEventListener('click', onClick);
            document.removeEventListener('keydown', onKey, true);
            host.remove();
            delete window['__eblockerElementPicker'];
        }
        function onKey(event) { if (event.key === 'Escape') stop(); }
        preview.addEventListener('click', function() {
            if (!selected || previewStyle) return;
            previewStyle = document.createElement('style');
            previewStyle.textContent = rule.value.split('##').slice(1).join('##') + ' {display:none!important;}';
            document.documentElement.appendChild(previewStyle);
            preview.hidden = true;
            hint.textContent = tr('Vorschau aktiv. ' + String['fromCharCode'](8222) + 'Erneut ausw' + String['fromCharCode'](228) + 'hlen' + String['fromCharCode'](8220) + ' entfernt sie.', 'Preview active. Select again removes it.');
        });
        copy.addEventListener('click', function() {
            if (navigator['clipboard'] && navigator['clipboard']['writeText']) {
                navigator['clipboard']['writeText'](rule.value).then(function() {
                    hint.textContent = tr('Regel kopiert. Unter Blocker ' + String['fromCharCode'](8594) + ' Video Ads & Content Blocker einf' + String['fromCharCode'](252) + 'gen.', 'Rule copied. Paste it under Blocker > Video Ads & Content Blocker.');
                }, function() {
                    rule.focus(); rule.select();
                    hint.textContent = tr('Regel markiert. Mit Strg+C kopieren.', 'Rule selected. Press Ctrl+C to copy.');
                });
            } else {
                rule.focus(); rule.select();
                hint.textContent = tr('Regel markiert. Mit Strg+C kopieren.', 'Rule selected. Press Ctrl+C to copy.');
            }
        });
        below.addEventListener('click', function() {
            if (!candidates.length) return;
            candidateIndex = (candidateIndex + 1) % candidates.length;
            select(candidates[candidateIndex]);
        });
        parent.addEventListener('click', function() {
            if (selected && selected.parentElement && selected.parentElement !== document.body) {
                select(selected.parentElement);
            }
        });
        function resetSelection(message) {
            removePreview();
            selected = null;
            candidates = [];
            candidateIndex = 0;
            ruleMatches = -1;
            ruleCoverage = 0;
            active = true;
            rule.hidden = preview.hidden = copy.hidden = below.hidden = parent.hidden = again.hidden = true;
            box.hidden = true;
            saveFrame.hidden = true;
            rule.value = '';
            hint.textContent = message || tr('Element ausw' + String['fromCharCode'](228) + 'hlen. Esc beendet den Picker.', 'Click an element. Esc exits the picker.');
        }
        again.addEventListener('click', function() { resetSelection(); });
        close.addEventListener('click', stop);
        capture.addEventListener('pointermove', onMove);
        capture.addEventListener('click', onClick);
        document.addEventListener('keydown', onKey, true);
        window.addEventListener('message', onSaveMessage);
        window['__eblockerElementPicker'] = {stop: stop};
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
