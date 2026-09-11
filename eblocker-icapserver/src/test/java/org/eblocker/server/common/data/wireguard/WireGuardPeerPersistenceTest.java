package org.eblocker.server.common.data.wireguard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.crypto.json.JsonEncrypt;
import org.eblocker.server.common.data.JedisDataSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WireGuardPeerPersistenceTest {

    private Jedis jedis;
    private JedisDataSource dataSource;

    @Before
    public void setUp() {
        JedisPool jedisPool = Mockito.mock(JedisPool.class);
        jedis = Mockito.mock(Jedis.class);

        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        dataSource = new JedisDataSource(jedisPool, new ObjectMapper());
    }

    @Test
    public void nextIdUsesWireGuardPeerSequence() {
        Mockito.when(jedis.incr("WireGuardPeer:sequence")).thenReturn(7L);

        int id = dataSource.nextId(WireGuardPeer.class);

        assertEquals(7, id);
        Mockito.verify(jedis).incr("WireGuardPeer:sequence");
    }

    @Test
    public void saveUsesNumericWireGuardPeerKey() {
        WireGuardPeer peer = createPeer(7);

        dataSource.save(peer, peer.getId());

        Mockito.verify(jedis).set(
                Mockito.eq("WireGuardPeer:7"),
                Mockito.anyString()
        );
    }

    @Test
    public void loadUsesNumericWireGuardPeerKeyAndRestoresId() {
        Mockito.when(jedis.get("WireGuardPeer:7")).thenReturn(
                "{"
                        + "\"id\":7,"
                        + "\"name\":\"Phone\","
                        + "\"privateKey\":\"private\","
                        + "\"publicKey\":\"public\","
                        + "\"presharedKey\":\"psk\","
                        + "\"allowedIp\":\"10.13.13.2/32\""
                        + "}"
        );

        WireGuardPeer peer = dataSource.get(WireGuardPeer.class, 7);

        assertNotNull(peer);
        assertEquals(7, peer.getId());
        assertEquals("Phone", peer.getName());
        assertEquals("10.13.13.2/32", peer.getAllowedIp());

        // Backward compatibility: existing Redis JSON does not contain
        // deviceId or allowLanAccess. Both therefore retain their safe
        // defaults for peers created before dashboard device binding.
        assertEquals(null, peer.getDeviceId());
        assertFalse(peer.isAllowLanAccess());
    }

    @Test
    public void savePersistsLanAccessPolicy() {
        WireGuardPeer peer = createPeer(7);
        peer.setAllowLanAccess(true);

        dataSource.save(peer, peer.getId());

        Mockito.verify(jedis).set(
                Mockito.eq("WireGuardPeer:7"),
                Mockito.argThat(json ->
                        json != null
                                && json.contains(
                                        "\"allowLanAccess\":true"
                                )
                )
        );

        assertTrue(peer.isAllowLanAccess());
    }

    @Test
    public void savePersistsOptionalDeviceAssociation() {
        WireGuardPeer peer = createPeer(7);
        peer.setDeviceId("device:001122334455");

        dataSource.save(peer, peer.getId());

        Mockito.verify(jedis).set(
                Mockito.eq("WireGuardPeer:7"),
                Mockito.argThat(json ->
                        json != null
                                && json.contains(
                                        "\"deviceId\":\"device:001122334455\""
                                )
                )
        );

        assertEquals(
                "device:001122334455",
                peer.getDeviceId()
        );
    }

    @Test
    public void deleteUsesNumericWireGuardPeerKey() {
        dataSource.delete(WireGuardPeer.class, 7);

        Mockito.verify(jedis).del("WireGuardPeer:7");
    }

    @Test
    public void secretGettersAreMarkedForEncryptedJsonSerialization()
            throws NoSuchMethodException {

        assertNotNull(
                WireGuardPeer.class
                        .getMethod("getPrivateKey")
                        .getAnnotation(JsonEncrypt.class)
        );

        assertNotNull(
                WireGuardPeer.class
                        .getMethod("getPrivateKey")
                        .getAnnotation(JsonProperty.class)
        );

        assertNotNull(
                WireGuardPeer.class
                        .getMethod("getPresharedKey")
                        .getAnnotation(JsonEncrypt.class)
        );

        assertNotNull(
                WireGuardPeer.class
                        .getMethod("getPresharedKey")
                        .getAnnotation(JsonProperty.class)
        );
    }

    private WireGuardPeer createPeer(int id) {
        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(id);
        peer.setName("Phone");
        peer.setPrivateKey("private");
        peer.setPublicKey("public");
        peer.setPresharedKey("psk");
        peer.setAllowedIp("10.13.13.2/32");
        return peer;
    }
}
