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
package org.eblocker.server.icap.transaction.processor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DisplayIconMode;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.util.StringReplacer;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ReminderService;
import org.eblocker.server.icap.transaction.Injections;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InsertToolbarProcessor implements TransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(InsertToolbarProcessor.class);

    private static final String TAG_EBLOCKER_BASEURL = "@EBLOCKER_BASEURL@";
    private static final String TAG_CONTROL_BAR_URL = "@EBLOCKER_CONTROL_BAR_URL@";
    private static final String TAG_EBLOCKER_ICON_POSITION = "@EBLOCKER_ICON_POSITION@";
    private static final String TAG_EBLOCKER_ICON_POSITION_ATTRIBUTE = "@EBLOCKER_ICON_POSITION_ATTRIBUTE@";
    private static final String TAG_EBLOCKER_PAGE_CONTEXT_ID = "@EBLOCKER_PAGE_CONTEXT_ID@";
    private static final String TAG_EBLOCKER_CLASS_WHITELISTED = "@EBLOCKER_CLASS_WHITELISTED@";
    private static final String TAG_EBLOCKER_SHOW_WARNING = "@EBLOCKER_WARNING@";
    private static final String TAG_EBLOCKER_ICON_HIDE = "@EBLOCKER_HIDE_ICON_ENABLED@";
    private static final String TAG_EBLOCKER_ICON_HIDE_AFTER_SECONDS = "@EBLOCKER_HIDE_ICON_AFTER_SECONDS@";
    private static final String TAG_EBLOCKER_REMINDER_ENABLED = "@EBLOCKER_REMINDER_ENABLED@";
    private static final String TAG_EBLOCKER_WELCOME_ENABLED = "@EBLOCKER_WELCOME_ENABLED@";
    private static final String ICON_POSITION_LEFT = "eblocker-icon-left";
    private static final String ICON_POSITION_RIGHT = "eblocker-icon-right";
    private static final String ICON_POSITION_ATTRIBUTE_LEFT = "left";
    private static final String ICON_POSITION_ATTRIBUTE_RIGHT = "right";

    private final String template;
    private final BaseURLs baseURLs;
    private final DeviceService deviceService;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final ReminderService reminderService;

    @Inject
    public InsertToolbarProcessor(@Named("toolbarInlayTemplate") String template,
                                  @Named("toolbarInlayMinJs") String minJs,
                                  @Named("toolbarInlayMinCss") String minCss,
                                  BaseURLs baseURLs,
                                  DeviceService deviceService,
                                  DeviceRegistrationProperties deviceRegistrationProperties,
                                  ReminderService reminderService
    ) {
        this.template = "<style id=\"eblocker-style\" type=\"text/css\">" + minCss + "</style>\n" +
                template +
                "<script id=\"eblocker-script\" type=\"text/javascript\">" + minJs + "</script>";
        this.baseURLs = baseURLs;
        this.deviceService = deviceService;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.reminderService = reminderService;
    }

    private Device.DisplayIconPosition getIconPosition(Device device) {
        if (device != null) {
            return device.getIconPosition();
        }
        return Device.DisplayIconPosition.getDefault();
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!isHTML(transaction)) {
            // do not handle javascript, skip instead
            return true;
        }

        Session session = transaction.getSession();
        PageContext pageContext = transaction.getPageContext();
        Injections injections = transaction.getInjections();

        Device device = deviceService.getDeviceById(session.getDeviceId());
        if (pageContext != null && baseURLs.isSetupUrl(pageContext.getUrl())) {//if the user goes to the setup.eblocker.org, always show the icon
            injections.inject(insertIcon(0, false, getIconPosition(device), transaction, session, pageContext));
        } else {
            if (device != null) {
                DisplayIconMode displayIconMode = device.getIconMode();

                boolean isBrowser = session.getUserAgentInfo().isBrowser();

                //
                // If we should show the welcome page, the icon cannot be switched off.
                // So set it to the least intrusive mode.
                //
                boolean showWelcome = isBrowser && deviceService.showWelcomePageForDevice(device);
                if (displayIconMode == DisplayIconMode.OFF && showWelcome) {
                    displayIconMode = DisplayIconMode.FIVE_SECONDS_BROWSER_ONLY;
                }

                switch (displayIconMode) {
                    case OFF:
                        break;
                    case FIVE_SECONDS_BROWSER_ONLY:
                        if (!isBrowser) {
                            break;
                        }
                    case FIVE_SECONDS:
                        injections.inject(insertIcon(5, showWelcome, device.getIconPosition(), transaction, session, pageContext));
                        break;
                    case ON:
                        if (!isBrowser) {
                            break;
                        }
                    default://=0N_ALL_DEVICES
                        injections.inject(insertIcon(0, showWelcome, device.getIconPosition(), transaction, session, pageContext));
                }
                return true;
            } else {
                //
                // This is just to handle strange error conditions that should not happen in real live.
                // But if they do: Insert the icon, instead of throwing an NPE.
                //
                injections.inject(insertIcon(0, false, Device.DisplayIconPosition.getDefault(), transaction, session, pageContext));
                return true;
            }
        }

        return true;
    }

    /**
     * Check whether this response is HTML or not
     *
     * @param transaction
     * @return
     */
    private boolean isHTML(Transaction transaction) {
        String contentType = transaction.getContentType();
        //FIXME check whether there are websites that deliver HTML without a proper contentType (contentType ==null)
        return (contentType != null) && (contentType.contains("text/html") || contentType.contains("text/xhtml"));
    }

    /**
     * Insert the eBlocker icon (and control bar) into the html content
     *
     * @param showForSeconds -1 for never; 0 for always; and positive value for amount of second
     * @param transaction
     * @param session
     * @param pageContext
     * @return
     */
    private String insertIcon(int showForSeconds, boolean showWelcome, Device.DisplayIconPosition iconPosition, Transaction transaction, Session session, PageContext pageContext) {
        if (showForSeconds == -1) {
            //do nothing
            return null;
        }

        //show icon
        String controlBarUrl = baseURLs.selectURLForPage(transaction.getRequest().getUri());
        log.debug("Inserting toolbar with link to {}", controlBarUrl);
        boolean reminderEnabled =
                deviceRegistrationProperties.isLicenseAboutToExpire() &&
                        reminderService.isReminderNeeded();

        return getInlay(
                pageContext == null ? "no-page" : pageContext.getId(),
                template,
                controlBarUrl,
                controlBarUrl,
                session.isWarningState(),
                showForSeconds,
                iconPosition,
                reminderEnabled,
                showWelcome
        );
    }

    private static String getIconPositionString(Device.DisplayIconPosition position) {
        if (position == Device.DisplayIconPosition.LEFT) {
            return ICON_POSITION_LEFT;
        }
        return ICON_POSITION_RIGHT;
    }

    private static String getIconPositionAttributeString(Device.DisplayIconPosition position) {
        if (position == Device.DisplayIconPosition.LEFT) {
            return ICON_POSITION_ATTRIBUTE_LEFT;
        }
        return ICON_POSITION_ATTRIBUTE_RIGHT;
    }

    protected static String getInlay(
            String pageContextId,
            String template,
            String baseUrl,
            String controlBarUrl,
            boolean warningState,
            int showForSeconds,
            Device.DisplayIconPosition iconPosition,
            boolean reminderEnabled,
            boolean showWelcome
    ) {
        StringReplacer replacer = new StringReplacer()
                .add(TAG_EBLOCKER_BASEURL, baseUrl)
                .add(TAG_CONTROL_BAR_URL, controlBarUrl)
                .add(TAG_EBLOCKER_ICON_POSITION, getIconPositionString(iconPosition))// "eblocker-icon-left" or "eblocker-icon-right"
                .add(TAG_EBLOCKER_ICON_POSITION_ATTRIBUTE, getIconPositionAttributeString(iconPosition))// "left" or "right"
                .add(TAG_EBLOCKER_PAGE_CONTEXT_ID, pageContextId)
                .add(TAG_EBLOCKER_CLASS_WHITELISTED, "eblocker-semitransparent")
                .add(TAG_EBLOCKER_SHOW_WARNING, warningState ? "block" : "none")
                .add(TAG_EBLOCKER_REMINDER_ENABLED, reminderEnabled ? "true" : "false")
                .add(TAG_EBLOCKER_WELCOME_ENABLED, showWelcome ? "true" : "false");

        if (showForSeconds == 0) {
            //always show, so comment the start of the timeout, which will hide everything
            replacer
                    .add(TAG_EBLOCKER_ICON_HIDE, "false")
                    .add(TAG_EBLOCKER_ICON_HIDE_AFTER_SECONDS, "---");
        } else if (showForSeconds > 0) {
            replacer
                    .add(TAG_EBLOCKER_ICON_HIDE, "true")
                    .add(TAG_EBLOCKER_ICON_HIDE_AFTER_SECONDS, Integer.toString(showForSeconds * 1000));
        }
        return replacer.replace(template);
    }

}
