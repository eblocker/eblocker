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
package org.eblocker.server.common.data.migrations;

public class DefaultEntities {

    //FIXME: Do not group by schema migations, but by structure and purpose
    // SchemaMigrationVersion1
    public static final String DEFAULT_ADMIN_USER_ID = "1";
    public static final String DEFAULT_ADMIN_USER_NAME = "John Doe";

    // SchemaMigrationVersion3
    public static final int PARENTAL_CONTROL_DEFAULT_PROFILE_ID = 1;
    public static final int PARENTAL_CONTROL_FULL_PROFILE_ID = 2;
    public static final int PARENTAL_CONTROL_MEDIUM_PROFILE_ID = 3;
    public static final int PARENTAL_CONTROL_LOW_PROFILE_ID = 4;
    public static final int PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE = 100;
    public static final String PARENTAL_CONTROL_KEY_USER_PROFILE_ID = "parentalControlProfileId";

    public static final int PARENTAL_CONTROL_FILTER_LIST_VARIOUS_SITES = 0;
    public static final int PARENTAL_CONTROL_FILTER_LIST_FACEBOOK = 1;
    public static final int PARENTAL_CONTROL_FILTER_LIST_GAMBLING = 2;
    public static final int PARENTAL_CONTROL_FILTER_LIST_GAMING = 3;
    public static final int PARENTAL_CONTROL_FILTER_LIST_MUSIC = 4;
    public static final int PARENTAL_CONTROL_FILTER_LIST_PORN = 5;
    public static final int PARENTAL_CONTROL_FILTER_LIST_SOCIAL_MEDIA = 6;
    public static final int PARENTAL_CONTROL_FILTER_LIST_VIDEO = 7;

    // SchemaMigrationVersion5
    public static final int VPN_PROFILE_DELETION = -1;

    // SchemaMigrationVersion7
    public static final String MESSAGE_DELETION_KEY = "message:*";

    // SchemaMigrationVersion8
    public static final String PARENTAL_CONTROL_FILTER_SUMMARY_KEY = "ParentalControlFilterSummaryData:*";
    public static final int PARENTAL_CONTROL_ID_SEQUENCE_FILTER_METADATA = 1000;

    /**
     * @deprecated There is not a single standard user anymore. Instead, each device has its own default system user.
     */
    @Deprecated
    public static final int PARENTAL_CONTROL_DEFAULT_USER_ID = PARENTAL_CONTROL_DEFAULT_PROFILE_ID;

    /**
     * @deprecated There is not a single standard user anymore. Use <code>USER_SYSTEM_DEFAULT_NAME_KEY</code> instead;
     */
    @Deprecated
    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_DEFAULT_PROFILE = "PARENTAL_CONTROL_USER_FOR_DEFAULT_PROFILE";

    //FIXME: Some of these constants are only used in unit tests. Remove them from here!
    public static final int PARENTAL_CONTROL_FULL_USER_ID = PARENTAL_CONTROL_FULL_PROFILE_ID;
    public static final int PARENTAL_CONTROL_MEDIUM_USER_ID = PARENTAL_CONTROL_MEDIUM_PROFILE_ID;
    public static final int PARENTAL_CONTROL_LOW_USER_ID = PARENTAL_CONTROL_LOW_PROFILE_ID;
    public static final int PARENTAL_CONTROL_ID_SEQUENCE_USER_MODULE = PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE;
    public static final String PARENTAL_CONTROL_STANDARD_USER_NAME = "standard";

    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_FULL_PROFILE = "PARENTAL_CONTROL_USER_FOR_FULL_PROFILE";
    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_MEDIUM_PROFILE = "PARENTAL_CONTROL_USER_FOR_MEDIUM_PROFILE";
    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_LOW_PROFILE = "PARENTAL_CONTROL_USER_FOR_LOW_PROFILE";
    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_UNKNOWN_PROFILE = "PARENTAL_CONTROL_USER_FOR_UNKNOWN_PROFILE";
    public static final String PARENTAL_CONTROL_NAME_KEY_USER_FOR_CUSTOMER_CREATED_PROFILE = "PARENTAL_CONTROL_USER_FOR_CUSTOMER_CREATED_PROFILE";

    // SchemaMigrationVersion10
    // FIXME: Name keys do not follow (new) structure definition
    public static final String PARENTAL_CONTROL_LIMBO_USER_NAME_KEY = "PARENTAL_CONTROL_USER_LIMBO_NAME";
    public static final int PARENTAL_CONTROL_LIMBO_USER_ID = 2;
    public static final int PARENTAL_CONTROL_LIMBO_PROFILE_ID = 4;
    public static final String PARENTAL_CONTROL_LIMBO_PROFILE_NAME_KEY = "PARENTAL_CONTROL_USERPROFILE_LIMBO_NAME";
    public static final String PARENTAL_CONTROL_LIMBO_PROFILE_DESCRIPTION_KEY = "PARENTAL_CONTROL_USERPROFILE_LIMBO_DESCRIPTION";

    // SchemaMigrationVersion13
    public static final int PARENTAL_CONTROL_FULL_2_PROFILE_ID = 5;
    public static final String PARENTAL_CONTROL_FULL_2_PROFILE_NAME_KEY = "PARENTAL_CONTROL_FULL_2_PROFILE_NAME";
    public static final String PARENTAL_CONTROL_FULL_2_PROFILE_DESCRIPTION_KEY = "PARENTAL_CONTROL_FULL_2_PROFILE_DESCRIPTION";
    public static final int PARENTAL_CONTROL_MEDIUM_2_PROFILE_ID = 6;
    public static final String PARENTAL_CONTROL_MEDIUM_2_PROFILE_NAME_KEY = "PARENTAL_CONTROL_MED_2_PROFILE_NAME";
    public static final String PARENTAL_CONTROL_MEDIUM_2_PROFILE_DESCRIPTION_KEY = "PARENTAL_CONTROL_MED_2_PROFILE_DESCRIPTION";

    // SchemaMigrationVersion14
    public static final String DNS_ENABLED_KEY = "eblocker_dns_enabled";

    // SchemaMigrationVersion28
    public static final int PARENTAL_CONTROL_FRAG_FINN_PROFILE_ID = 7;
    public static final int PARENTAL_CONTROL_FILTER_LIST_FRAG_FINN = 10;
    public static final String PARENTAL_CONTROL_FRAG_FINN_PROFILE_NAME_KEY = "PARENTAL_CONTROL_FRAG_FINN_PROFILE_NAME";
    public static final String PARENTAL_CONTROL_FRAG_FINN_PROFILE_DESCRIPTION_KEY = "PARENTAL_CONTROL_FRAG_FINN_PROFILE_DESCRIPTION";

    // ---

    //
    // Localization keys
    //
    public static final String USER_SYSTEM_DEFAULT_NAME_KEY = "SHARED.USER.NAME.SYSTEM_DEFAULT";


    private DefaultEntities() {
        // To hide default public constructor
    }
}

