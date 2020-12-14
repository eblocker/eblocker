package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateInterval;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.dashboard.ParentalControlCard;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.icap.filter.FilterDefinitionFormat;
import org.eblocker.server.icap.filter.FilterLearningMode;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

public class SchemaMigrationVersion47Test {
    private SchemaMigration migration;
    private DataSource dataSource;
    private JedisPool jedisPool;
    private Jedis jedis;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        jedisPool = Mockito.mock(JedisPool.class);
        jedis = Mockito.mock(Jedis.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
        migration = new SchemaMigrationVersion47(dataSource, jedisPool);
    }

    @Test
    public void testUiCard() {
        String key = "UiCard:1";
        Mockito.when(dataSource.getIds(UiCard.class)).thenReturn(new TreeSet<>(Collections.singleton(key)));
        String uiCard = "{\"@class\":\"com.brightmammoth.moonshine.common.data.dashboard.UiCard\",\"id\":1,\"name\":\"MESSAGE\",\"requiredFeature\":\"WOL\",\"requiredUserRoles\":[\"PARENT\",\"OTHER\"],\"requiredAccessRights\":[\"MESSAGE\"]}";
        String uiCardExpected = "{\"@class\":\"org.eblocker.server.common.data.dashboard.UiCard\",\"id\":1,\"name\":\"MESSAGE\",\"requiredFeature\":\"WOL\",\"requiredUserRoles\":[\"PARENT\",\"OTHER\"],\"requiredAccessRights\":[\"MESSAGE\"]}";
        Mockito.when(jedis.get(key)).thenReturn(uiCard);
        migration.migrate();
        Mockito.verify(jedis).set(key, uiCardExpected);
    }

    @Test
    public void testParentalControlCard() {
        String key = "ParentalControlCard:16";
        Mockito.when(dataSource.getIds(ParentalControlCard.class)).thenReturn(new TreeSet<>(Collections.singleton(key)));
        String pcCard = "{\"@class\":\"com.brightmammoth.moonshine.common.data.dashboard.ParentalControlCard\",\"id\":16,\"name\":\"PARENTAL_CONTROL\",\"requiredFeature\":\"FAM\",\"requiredUserRoles\":[\"PARENT\"],\"requiredAccessRights\":null,\"referencingUserId\":128}";
        String pcCardExpected = "{\"@class\":\"org.eblocker.server.common.data.dashboard.ParentalControlCard\",\"id\":16,\"name\":\"PARENTAL_CONTROL\",\"requiredFeature\":\"FAM\",\"requiredUserRoles\":[\"PARENT\"],\"requiredAccessRights\":null,\"referencingUserId\":128}";
        Mockito.when(jedis.get(key)).thenReturn(pcCard);
        migration.migrate();
        Mockito.verify(jedis).set(key, pcCardExpected);
    }

    @Test
    public void testExternalDefinition() {
        ExternalDefinition definition = createExternalDefinition("/opt/moonshine-icap/conf/filter/1001:DOMAIN");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(Collections.singletonList(definition));
        migration.migrate();
        ExternalDefinition definitionExpected = createExternalDefinition("/opt/eblocker-icap/conf/filter/1001:DOMAIN");
        Mockito.verify(dataSource).save(definitionExpected, definition.getId());
    }

    @Test
    public void testFilterStoreConfiguration() {
        FilterStoreConfiguration configuration = createFilterStoreConfiguration(new String[]{
            "/opt/moonshine-icap/conf/easylist/easylist.txt",
            "/opt/moonshine-icap/conf/easylist/easylistgermany.txt",
            "/opt/moonshine-icap/conf/easylist/easyprivacy.txt"
        });
        FilterStoreConfiguration configurationExpected = createFilterStoreConfiguration(new String[]{
            "/opt/eblocker-icap/conf/easylist/easylist.txt",
            "/opt/eblocker-icap/conf/easylist/easylistgermany.txt",
            "/opt/eblocker-icap/conf/easylist/easyprivacy.txt"
        });
        Mockito.when(dataSource.getAll(FilterStoreConfiguration.class)).thenReturn(Collections.singletonList(configuration));
        migration.migrate();
        Mockito.verify(dataSource).save(configurationExpected, configuration.getId());
    }

    @Test
    public void testPCFilterMetaData() {
        List<String> filenames = Arrays.asList(
            "/opt/moonshine-icap/conf/customercreated/1004.filter",
            "/opt/moonshine-icap/conf/customercreated/1004.bloom"
        );
        List<String> filenamesExpected = Arrays.asList(
            "/opt/eblocker-icap/conf/customercreated/1004.filter",
            "/opt/eblocker-icap/conf/customercreated/1004.bloom"
        );
        ParentalControlFilterMetaData metaData = createPCFilterMetaData(filenames);
        Mockito.when(dataSource.getAll(ParentalControlFilterMetaData.class)).thenReturn(Collections.singletonList(metaData));
        ParentalControlFilterMetaData metaDataExpected = createPCFilterMetaData(filenamesExpected);
        migration.migrate();
        Mockito.verify(dataSource).save(metaDataExpected, metaData.getId());
    }

    private ExternalDefinition createExternalDefinition(String file) {
        return new ExternalDefinition(
            1001,
            "My Filter",
            "",
            Category.PARENTAL_CONTROL,
            Type.DOMAIN,
            1004,
            Format.DOMAINS,
            null,
            UpdateInterval.NEVER,
            UpdateStatus.READY,
            null,
            file,
            true,
            "whitelist");
    }

    private FilterStoreConfiguration createFilterStoreConfiguration(String[] resources) {
        return new FilterStoreConfiguration(
            0,
            "Content Security Policies",
            org.eblocker.server.icap.filter.Category.CONTENT_SECURITY_POLICIES,
            true,
            1L,
            resources,
            FilterLearningMode.SYNCHRONOUS,
            FilterDefinitionFormat.EASYLIST,
            true,
            new String[]{ "csp" },
            true
        );
    }

    private ParentalControlFilterMetaData createPCFilterMetaData(List<String> filenames) {
        return new ParentalControlFilterMetaData(
            1004,
            null,
            null,
            org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL,
            filenames,
            null,
            Date.from(Instant.ofEpochSecond(1591457694L)),
            "domainblacklist/string",
            "whitelist",
            false,
            false,
            null,
            "My whitelist",
            "");
    }
}
