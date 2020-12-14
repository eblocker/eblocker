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

import com.google.common.collect.Range;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.BonusTimeUsage;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TrafficAccount;
import org.eblocker.server.common.data.UsageAccount;
import org.eblocker.server.common.data.UsageChangeEvent;
import org.eblocker.server.common.data.UsageChangeEvents;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.TrafficAccounter;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = true)
public class ParentalControlUsageService {

    private static final Logger logger = LoggerFactory.getLogger(ParentalControlUsageService.class);

    private final Duration usageMinimumTime;
    private final Duration usageTimeOutAutoOff;

    private final Clock clock;
    private final DataSource dataSource;
    private final ParentalControlService parentalControlService;
    private final TrafficAccounter trafficAccounter;
    private final UserService userService;

    private Map<Integer, Deque<UsageChangeEvent>> eventsById = Collections.emptyMap();
    private final Map<Integer, UsageAccount> accountsById = new HashMap<>();
    private final List<ParentalControlUsageChangeListener> listeners = new ArrayList<>();

    @Inject
    public ParentalControlUsageService(@Named("parentalcontrol.usage.minimumTime") Integer usageMinimumTime,
                                       @Named("parentalcontrol.usage.timeOutAutoOff") Integer usageTimeOutAutoOff,
                                       Clock clock,
                                       DataSource dataSource,
                                       ParentalControlService parentalControlService,
                                       TrafficAccounter trafficAccounter,
                                       UserService userService) {
        this.usageMinimumTime = Duration.of(usageMinimumTime, ChronoUnit.MINUTES);
        this.usageTimeOutAutoOff = Duration.of(usageTimeOutAutoOff, ChronoUnit.MINUTES);
        this.clock = clock;
        this.dataSource = dataSource;
        this.parentalControlService = parentalControlService;
        this.trafficAccounter = trafficAccounter;
        this.userService = userService;
    }

    @SubSystemInit
    public void init() {
        eventsById = loadStoredEvents();
    }

    public boolean startUsage(Device device) {
        boolean notifyListeners = false;
        synchronized (this) {
            UserModule user = getUser(device);
            if (user == null) {
                return false;
            }
            Integer id = user.getId();

            Deque<UsageChangeEvent> events = eventsById.get(id);
            if (events == null) {
                events = new LinkedList<>();
                eventsById.put(id, events);
            }

            if (!accountUsage(user)) {
                return false;
            }

            if (events.isEmpty() || !events.peekLast().isActive()) {
                events.add(new UsageChangeEvent(LocalDateTime.now(clock), true));
                saveEvents(id, events);
                notifyListeners = true;
                logger.info("internet access for {} enabled by user request", device.getHardwareAddress(false));
            }
        }

        if (notifyListeners) {
            notifyListeners();
        }

        return true;
    }

    public void stopUsage(Device device) {
        boolean notifyListeners = false;

        synchronized (this) {
            UserModule user = getUser(device);
            if (user == null) {
                return;
            }
            Integer id = user.getId();

            Deque<UsageChangeEvent> events = eventsById.get(id);
            if (events == null) {
                return;
            }

            UsageChangeEvent event = events.peekLast();
            if (event != null && event.isActive()) {
                events.add(new UsageChangeEvent(LocalDateTime.now(clock), false));
                saveEvents(id, events);
                accountUsage(user);
                notifyListeners = true;
                logger.info("internet access for {} disabled by user request", device.getHardwareAddress(false));
            }
        }

        if (notifyListeners) {
            notifyListeners();
        }
    }

    public synchronized UsageAccount getUsageAccount(Device device) {
        return getUsageAccount(getUser(device));
    }

    public synchronized UsageAccount getUsageAccount(Integer userId) {
        UserModule user = userService.getUserById(userId);
        // always accountUsage, so that bonus / blocked internet is also accounted for each time we poll for
        // the UsageAccount of a given user
        accountUsage(user);
        return accountsById.get(user.getId());
    }

    public synchronized UsageAccount getUsageAccount(UserModule user) {
        if (user == null) {
            return null;
        }

        Integer id = user.getId();
        if (!accountsById.containsKey(id)) {
            accountUsage(user);
        }
        return accountsById.get(id);
    }

    /**
     * Allows to add a positive or negative number as bonus time for a given profile. The overall bonus time must
     * be positive. A negative bonus could result in bad user experience. If bonus is large negative number
     * The parent would have to continuously click on "+10 min" until the value is positive again. So to reduce
     * the actual online time for that day, the parent has to update the profile's online time in the settings.
     *
     * @param profileId the profile to which the bonus is added
     * @param min       bonus time in minutes
     * @return the updated user profile
     */
    public UserProfileModule addBonusTimeForToday(int profileId, int min) {
        UserProfileModule profile = parentalControlService.getProfile(profileId);
        BonusTimeUsage currentBonus = profile.getBonusTimeUsage();
        LocalDateTime now = LocalDateTime.now(clock);
        int totalBonus;

        if (currentBonus != null && isToday(currentBonus.getDateTime())) {
            totalBonus = currentBonus.getBonusMinutes() + min;
        } else {
            totalBonus = min;
        }
        // add bonus, but keep total bonus positive
        profile.setBonusTimeUsage(new BonusTimeUsage(now, totalBonus > 0 ? totalBonus : 0));
        return profile;
    }

    private boolean isToday(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now(clock);
        return dateTime.getYear() == now.getYear() &&
                dateTime.getMonthValue() == now.getMonthValue() &&
                dateTime.getDayOfMonth() == now.getDayOfMonth();
    }

    /**
     * MUST NOT BE CALLED BY ANYONE BESIDES ParentalControlContingentEnforcerService !
     * <p>
     * Checks all devices if internet access is allowed. If a device exceeded its limit while being active a new
     * event will be inserted. This is to avoid instant exhaustion if a limited is raised at a later point. The newly
     * inserted event will _not_ cause any listener to be notified!
     */
    synchronized void accountUsages(Collection<Device> devices) {
        // check idle timeouts
        Map<Integer, List<Device>> devicesByUsers = devices.stream().collect(Collectors.groupingBy(Device::getOperatingUser));
        eventsById.keySet().forEach(id -> checkIdleTimeOut(id, devicesByUsers.get(id)));

        // account usage
        devicesByUsers.keySet().stream()
                .map(userService::getUserById)
                .filter(Objects::nonNull)
                .forEach(this::accountUsage);
    }

    private void checkIdleTimeOut(int userId, List<Device> devices) {
        Deque<UsageChangeEvent> events = eventsById.get(userId);
        if (events.peekLast() == null || !events.peekLast().isActive()) {
            return;
        }

        if (devices == null) {
            // no active devices known for user - disable usage
            events.add(new UsageChangeEvent(LocalDateTime.now(clock), false));
            saveEvents(userId, events);
        }

        // get latest instant of activity of any device assigned to this user
        ZonedDateTime lastActivity =
                devices != null ?
                        devices.stream()
                                .map(trafficAccounter::getTrafficAccount)
                                .filter(Objects::nonNull)
                                .map(TrafficAccount::getLastActivity)
                                .map(Date::toInstant)
                                .map(i -> i.atZone(clock.getZone()))
                                .max(ZonedDateTime::compareTo)
                                .orElse(ZonedDateTime.now(clock))
                        : ZonedDateTime.now(clock);

        // disable access if enough time has elasped since last activity
        UsageChangeEvent event = events.peekLast();
        if (event.getTime().isBefore(lastActivity.toLocalDateTime())) {
            ZonedDateTime nowZoned = ZonedDateTime.now(clock);
            Duration idle = Duration.between(lastActivity, nowZoned).abs();
            if (idle.compareTo(usageTimeOutAutoOff) >= 1) {
                events.add(new UsageChangeEvent(lastActivity.toLocalDateTime(), false));
                saveEvents(userId, events);
                logger.info("internet access for user {} disabled due to inactivity for {} minutes.", userId, idle.toMinutes());
            }
        }
    }

    private boolean accountUsage(UserModule user) {
        Integer id = user.getId();

        LocalDateTime now = LocalDateTime.now(clock);
        Deque<UsageChangeEvent> events = eventsById.get(id);
        boolean active = events != null && !events.isEmpty() && events.peekLast().isActive();

        UsageDuration duration = getUsage(events, now);
        Duration limit = getUsageLimit(user, now.toLocalDate());

        if (limit == null) {
            // no usage restrictions apply
            return true;
        }

        // disable access if limit has been reached
        boolean allowed = duration.used.compareTo(limit) < 0;
        if (!allowed && active) {
            events.add(new UsageChangeEvent(now, false));
            saveEvents(id, events);
            active = false;
        }

        UsageAccount usageAccount = new UsageAccount();
        usageAccount.setAllowed(allowed);
        usageAccount.setActive(active);
        usageAccount.setAccountedTime(duration.accounted);
        usageAccount.setUsedTime(duration.used);
        usageAccount.setMaxUsageTime(limit);
        accountsById.put(id, usageAccount);

        logger.info("internet usage account user: {}: active: {} accounted time: {} used time: {} limit: {}",
                user.getId(), active, duration.accounted.toMinutes(), duration.used.toMinutes(),
                limit);

        return allowed;
    }

    void addChangeListener(ParentalControlUsageChangeListener listener) {
        this.listeners.add(listener);
    }

    private Map<Integer, Deque<UsageChangeEvent>> loadStoredEvents() {
        return dataSource.getAll(UsageChangeEvents.class).stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getEvents()));
    }

    private void notifyListeners() {
        listeners.forEach(ParentalControlUsageChangeListener::onChange);
    }

    private UsageDuration getUsage(Deque<UsageChangeEvent> events, LocalDateTime now) {
        if (events == null) {
            return new UsageDuration();
        }

        removeOldEvents(events, now.toLocalDate());
        List<Range<LocalDateTime>> intervals = getUsageRanges(events, now);
        List<Range<LocalDateTime>> minimumIntervals = createMinimumUsageIntervals(intervals);

        UsageDuration usageDuration = new UsageDuration();
        if (!minimumIntervals.isEmpty()) {
            // duration which will be accounted possibly including a currently running
            // interval ending in the future (in case it is shorter than minimum usage).
            usageDuration.accounted = sumIntervals(minimumIntervals);

            // duration which includes all already finished intervals and
            // actual used minutes of the current one (if active)
            int i = minimumIntervals.size() - 1;
            Range<LocalDateTime> lastInterval = minimumIntervals.get(i);
            if (now.isBefore(lastInterval.upperEndpoint())) {
                minimumIntervals.set(i, Range.closedOpen(lastInterval.lowerEndpoint(), now));
            }
            usageDuration.used = sumIntervals(minimumIntervals);
        }

        return usageDuration;
    }

    private Duration getUsageLimit(UserModule user, LocalDate today) {
        UserProfileModule profile = parentalControlService.getProfile(user.getAssociatedProfileId());

        if (profile != null && profile.isInternetBlocked()) {
            return Duration.ZERO;
        }

        if (profile == null || (!profile.isControlmodeMaxUsage())) {
            return null;
        }

        // Make sure if Internet access is blocked to return 0
        Integer maxUsage = profile.getMaxUsageTimeByDay().get(today.getDayOfWeek());
        BonusTimeUsage bonusTimeUsage = profile.getBonusTimeUsage();
        if (bonusTimeUsage != null && isToday(bonusTimeUsage.getDateTime())) {
            maxUsage = maxUsage == null ? bonusTimeUsage.getBonusMinutes() : maxUsage + bonusTimeUsage.getBonusMinutes();
        }

        return maxUsage != null ? Duration.of(maxUsage, ChronoUnit.MINUTES) : Duration.ZERO;
    }

    /**
     * Removes all event prior to a given date
     */
    private void removeOldEvents(Deque<UsageChangeEvent> events, LocalDate today) {
        Iterator<UsageChangeEvent> i = events.iterator();
        while (i.hasNext()) {
            UsageChangeEvent event = i.next();
            if (event.getTime().isBefore(today.atStartOfDay())) {
                i.remove();
            }
        }
    }

    /**
     * Converts queue of start/stop events into lists of ranges in which usage has been enabled. If last event is
     * a start event a range ending now will be included.
     */
    private List<Range<LocalDateTime>> getUsageRanges(Queue<UsageChangeEvent> events, LocalDateTime now) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        UsageChangeEvent start = null;
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        if (!events.peek().isActive() && startOfDay.isBefore(events.peek().getTime())) {
            start = new UsageChangeEvent(startOfDay, true);
        }

        List<Range<LocalDateTime>> ranges = new ArrayList<>();
        Iterator<UsageChangeEvent> i = events.iterator();
        while (i.hasNext()) {
            UsageChangeEvent event = i.next();
            if (!event.isActive()) {
                ranges.add(Range.closedOpen(start.getTime(), event.getTime())); //NOSONAR: start cannot be null
                start = null;
            } else {
                start = event;
            }
        }
        if (start != null) {
            ranges.add(Range.closedOpen(start.getTime(), now));
        }

        return ranges;
    }

    /**
     * Creates intervals confirming to the minimum usage rule based on given intervals.
     */
    private List<Range<LocalDateTime>> createMinimumUsageIntervals(List<Range<LocalDateTime>> intervals) {
        List<Range<LocalDateTime>> minimumIntervals = new ArrayList<>(intervals);
        for (int i = 0; i < minimumIntervals.size(); ) {
            Range<LocalDateTime> current = minimumIntervals.get(i);
            Range<LocalDateTime> next = i + 1 < minimumIntervals.size() ? minimumIntervals.get(i + 1) : null;

            // check if interval is less than minimum usage
            if (Duration.between(current.lowerEndpoint(), current.upperEndpoint()).compareTo(usageMinimumTime) == -1) {
                // calculate minimum-interval with respect to end-of-day
                LocalDateTime minimumUsageEndPoint = current.lowerEndpoint().plus(usageMinimumTime);
                if (!minimumUsageEndPoint.toLocalDate().equals(current.lowerEndpoint().toLocalDate())) {
                    minimumUsageEndPoint = minimumUsageEndPoint.toLocalDate().atStartOfDay();
                }
                Range<LocalDateTime> currentMinimum = Range.closedOpen(current.lowerEndpoint(), minimumUsageEndPoint);

                if (next != null && Duration.between(currentMinimum.upperEndpoint(), next.lowerEndpoint()).compareTo(usageMinimumTime) <= 0) {
                    // merge with next interval
                    minimumIntervals.set(i, current.span(next));
                    minimumIntervals.remove(i + 1);
                } else {
                    // expand interval
                    minimumIntervals.set(i, currentMinimum);
                    ++i;
                }
            } else {
                ++i;
            }
        }
        return minimumIntervals;
    }

    private UserModule getUser(Device device) {
        return userService.getUserById(device.getOperatingUser());
    }

    private void saveEvents(int id, Deque<UsageChangeEvent> events) {
        UsageChangeEvents model = new UsageChangeEvents();
        model.setId(id);
        model.setEvents(events);
        dataSource.save(model, id);
    }

    private Duration sumIntervals(List<Range<LocalDateTime>> intervals) {
        return Duration.of(intervals.stream()
                .map(r -> Duration.between(
                        r.lowerEndpoint().atZone(clock.getZone()),
                        r.upperEndpoint().atZone(clock.getZone())))
                .mapToLong(Duration::toNanos)
                .sum(), ChronoUnit.NANOS);
    }

    private class UsageDuration {
        public Duration used = Duration.ZERO;
        public Duration accounted = Duration.ZERO;
    }

    interface ParentalControlUsageChangeListener {
        void onChange();
    }
}
