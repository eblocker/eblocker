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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.AccessRestriction;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UsageAccount;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.DeviceService.DeviceChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class ParentalControlAccessRestrictionsService implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ParentalControlAccessRestrictionsService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");
    private final DeviceService deviceService;
    private final SettingsService settingsService;
    private final ParentalControlService parentalControlService;
    private final ProductInfoService productInfoService;
    private final ParentalControlUsageService parentalControlUsageService;
    private final UserService userService;

    private List<AccessRestrictionsChangeListener> listeners = new ArrayList<>();
    private Map<Device, List<AccessRestriction>> restrictionsByDevice = new HashMap<>();

    @Inject
    public ParentalControlAccessRestrictionsService(
            DeviceService deviceService,
            SettingsService settingsService,
            ParentalControlService parentalControlService,
            ProductInfoService productInfoService,
            ParentalControlUsageService parentalControlUsageService,
            UserService userService
    ) {
        this.deviceService = deviceService;
        this.settingsService = settingsService;
        this.parentalControlService = parentalControlService;
        this.productInfoService = productInfoService;
        this.parentalControlUsageService = parentalControlUsageService;
        this.userService = userService;
    }

    @SubSystemInit
    public void init() {
        deviceService.addListener(new DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                if (updateAccessContingents(LocalDateTime.now(), device)) {
                    notifyListeners();
                }
            }

            @Override
            public void onDelete(Device device) {
                // Nothing to do here
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });
        this.parentalControlService.addListener(p -> updateAccessContingents());
        this.parentalControlUsageService.addChangeListener(this::updateAccessContingents);
    }

    public void addListener(AccessRestrictionsChangeListener listener) {
        listeners.add(listener);
    }

    public void run() {
        // Catchall to see if something goes wrong
        try {
            updateAccessContingents();
        } catch (Exception e) {
            LOG.error("Catchall", e);
            throw new EblockerException("Cannot enforce access control by time: " + e.getMessage());
        }
    }

    public boolean isAccessPermitted(Device device) {
        return !restrictionsByDevice.containsKey(device);
    }

    public List<AccessRestriction> getAccessRestrictions(Device device) {
        List<AccessRestriction> restrictions = restrictionsByDevice.get(device);
        return restrictions != null ? Collections.unmodifiableList(restrictions) : Collections.emptyList();
    }

    private boolean isContingentApplicable(InternetAccessContingent contingent, int day, int minutes) {
        return (
                // Is it the right day?
                (
                        // Is the contingent for a specific day?
                        contingent.getOnDay() == day
                                // Is the contingent for a weekday?
                                || contingent.getOnDay() == InternetAccessContingent.CONTINGENT_DAY.WEEKDAY.value
                                && day >= InternetAccessContingent.CONTINGENT_DAY.MONDAY.value
                                && day <= InternetAccessContingent.CONTINGENT_DAY.FRIDAY.value
                                // Is the contingent for a weekend?
                                || contingent.getOnDay() == InternetAccessContingent.CONTINGENT_DAY.WEEKEND.value
                                && day >= InternetAccessContingent.CONTINGENT_DAY.SATURDAY.value
                                && day <= InternetAccessContingent.CONTINGENT_DAY.SUNDAY.value
                )
                        &&
                        // Is it the right time?
                        (
                                contingent.getFromMinutes() <= minutes
                                        && contingent.getTillMinutes() >= minutes
                        )
        );
    }

    private void notifyListeners() {
        listeners.forEach(listener -> listener.onChange(new HashSet<>(restrictionsByDevice.keySet())));
    }

    private void updateAccessContingents() {
        LocalDateTime now = LocalDateTime.now(settingsService.getTimeZone());
        updateAccessContingents(now);
    }

    synchronized void updateAccessContingents(LocalDateTime now) {
        boolean devicesChanged = deviceService.getDevices(false).stream()
                .map(device -> updateAccessContingents(now, device))
                .reduce(Boolean::logicalOr)
                .orElse(false);
        if (devicesChanged) {
            notifyListeners();
        }
    }

    synchronized boolean updateAccessContingents(LocalDateTime now, Device device) {
        // Get current day
        int day = now.getDayOfWeek().getValue();
        // Get current minute in that day
        int minute = now.getHour() * 60 + now.getMinute();

        // Dictionary of User Profiles to look up when iterating over all devices
        Map<Integer, UserProfileModule> userProfiles = new HashMap<>();
        for (UserProfileModule profile : parentalControlService.getProfiles()) {
            // Sanity check - if access is restricted based on time, contingents must be present
            if (profile.isControlmodeTime() && profile.getInternetAccessContingents().size() == 0) {
                profile.setControlmodeTime(false);
            }
            userProfiles.put(profile.getId(), profile);
        }

        // Is Parental Control feature at all available?
        boolean hasFamilyFeature = productInfoService.hasFeature(ProductFeature.FAM);

        // account usage
        Collection<Device> devices = deviceService.getDevices(false);
        parentalControlUsageService.accountUsages(devices);

        boolean deviceStateChanged = false;

        LOG.debug("-- start of access restrictions evaluations ----------------------");

        UserModule user = userService.getUserById(device.getOperatingUser());
        UserProfileModule profile = user != null ? userProfiles.get(user.getAssociatedProfileId()) : null;

        boolean shouldBePermitted;
        List<AccessRestriction> restrictions = new ArrayList<>(0);

        if (!hasFamilyFeature) {
            // No Family feature - no restriction rules
            shouldBePermitted = true;
        } else if (user == null) {
            // No user - no restriction rules
            LOG.info("Device {} has no or not existing user {} logged in. Skipping device.", device.getId(), device.getOperatingUser());
            shouldBePermitted = true;
        } else if (profile == null) {
            // No user profile - no restriction rules
            LOG.info("User {} loggin in to device {} has no or not existing profile {} assigned. Skipping device.", user.getId(), device.getId(), user.getAssociatedProfileId());
            shouldBePermitted = true;
        } else if (!profile.isControlmodeTime() && !profile.isControlmodeMaxUsage() && !profile.isInternetBlocked()) {
            // no restriction rules - no restriction
            shouldBePermitted = true;
        } else {
            // have restriction rules - restricted by default, unless there is a matching time and/or usage contingent
            boolean permittedTime = !profile.isControlmodeTime();
            boolean permittedUsage = !profile.isControlmodeMaxUsage();

            if (profile.isControlmodeTime()) {
                for (InternetAccessContingent contingent : profile.getInternetAccessContingents()) {
                    if (isContingentApplicable(contingent, day, minute)) {
                        permittedTime = true;
                        break;
                    }
                }
                if (!permittedTime) {
                    restrictions.add(AccessRestriction.TIME_FRAME);
                }
            }

            if (profile.isControlmodeMaxUsage()) {
                UsageAccount account = parentalControlUsageService.getUsageAccount(device);
                permittedUsage = account.isAllowed() && account.isActive();
                if (!account.isAllowed()) {
                    restrictions.add(AccessRestriction.MAX_USAGE_TIME);
                } else if (!account.isActive()) {
                    restrictions.add(AccessRestriction.USAGE_TIME_DISABLED);
                }
            }

            if (profile.isInternetBlocked()) {
                restrictions.add(AccessRestriction.INTERNET_ACCESS_BLOCKED);
            }

            shouldBePermitted = permittedTime && permittedUsage && !profile.isInternetBlocked();
        }

        // Write down any device that is restricted (not only when its
        // status changed otherwise it will not be written into the ACL file
        // when its status did not change but another device's status
        // changed)
        boolean currentlyPermitted = !restrictionsByDevice.containsKey(device);
        if (shouldBePermitted) {
            restrictionsByDevice.remove(device);
        } else {
            restrictionsByDevice.put(device, restrictions);
        }

        // has access permission changed?
        deviceStateChanged = currentlyPermitted != shouldBePermitted || !restrictions.equals(getAccessRestrictions(device)) || (currentlyPermitted && profile != null && profile.isInternetBlocked());
        if (deviceStateChanged) {
            STATUS.info("Access for device {} changed and is {} now based on parental control rules", device.getUserFriendlyName(), shouldBePermitted ? "GRANTED" : "DENIED");
            LOG.debug("{} user: {} prev: {} now: {} restrictions: {} *** CHANGE ***", device, user != null ? user.getId() : "<null>", currentlyPermitted, shouldBePermitted, getRestrictions(restrictions));
            if (!shouldBePermitted && profile.isControlmodeMaxUsage()) {
                // Device just reached the end of a slot and has maximum usage
                // for a day, therefore disable usage
                parentalControlUsageService.stopUsage(device);
            }
        } else {
            //				LOG.info("Access for device {} is still {} based on parental control access rules", device.getUserFriendlyName(), shouldBePermitted ? "GRANTED" : "DENIED");
            LOG.debug("{} user: {} prev: {} now: {} restrictions: {}", device, user != null ? user.getId() : "<null>", currentlyPermitted, shouldBePermitted, getRestrictions(restrictions), shouldBePermitted);
        }

        LOG.debug("-- end of access restrictions evaluations ------------------------");
        return deviceStateChanged;
    }

    public interface AccessRestrictionsChangeListener {
        void onChange(Set<Device> blockedDevices);
    }

    private String getRestrictions(Collection<AccessRestriction> restrictions) {
        if (restrictions == null) {
            return "<NULL>";
        }

        return restrictions.stream()
                .map(AccessRestriction::toString)
                .collect(Collectors.joining(","));
    }
}
