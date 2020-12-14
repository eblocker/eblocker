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
package org.eblocker.server.common.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtil {

    private static final String RFC1123_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private static final String DEFAULT_LANGUAGE = "en";

    private static final String DEFAULT_COUNTRY = "US";

    private static final String DEFAULT_TIMEZONE_ID = "GMT";

    private static final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            DateFormat dateFormat = new SimpleDateFormat(RFC1123_PATTERN, new Locale(DEFAULT_LANGUAGE, DEFAULT_COUNTRY));
            dateFormat.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE_ID));
            return dateFormat;
        }
    };

    public static String format(Date date) {
        return dateFormat.get().format(date);
    }

    public static String formatCurrentTime() {
        return dateFormat.get().format(new Date());
    }

    public static boolean isBeforeDays(Date date, Date now, int days) {
        return date.getTime() < now.getTime() + 1000L * 3600 * 24 * days;
    }

}
