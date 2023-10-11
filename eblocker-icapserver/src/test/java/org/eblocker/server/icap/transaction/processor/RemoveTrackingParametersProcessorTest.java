package org.eblocker.server.icap.transaction.processor;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class RemoveTrackingParametersProcessorTest {

    private RemoveTrackingParametersProcessor testee;
    private Transaction transaction;

    @Before
    public void setup() {
        testee = new RemoveTrackingParametersProcessor();
        transaction = Mockito.mock(Transaction.class);
    }

    @Test
    public void process_removes_tracking_params() {

        FullHttpRequest request = makeRequest("https://foo.com/?gclid=ItrackU&x=y&ga_term=trackingTerm");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/?x=y", request.uri());
    }

    private DefaultFullHttpRequest makeRequest(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    @Test
    public void process_removes_query_for_single_tracking_param() {
        FullHttpRequest request = makeRequest("https://foo.com/?gclid=ItrackU");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/", request.uri());
    }

    @Test
    public void process_preserves_escapes() {
        FullHttpRequest request = makeRequest("https://foo.com/path%20%C3%BC?gclid=ItrackU&a%20=b%21&x=y");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/path%20%C3%BC?a%20=b%21&x=y", request.uri());
    }

    @Test
    public void process_preserves_fragment() {
        FullHttpRequest request = makeRequest("https://foo.com/path?gclid=ItrackU&a=b#ABC");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/path?a=b#ABC", request.uri());
    }

    @Test
    public void process_preserves_port() {
        FullHttpRequest request = makeRequest("https://foo.com:8443/path?gclid=ItrackU&a=b");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com:8443/path?a=b", request.uri());
    }

    @Test
    public void process_preserves_userinfo() {
        FullHttpRequest request = makeRequest("https://user:password@foo.com/path?gclid=ItrackU&a=b");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://user:password@foo.com/path?a=b", request.uri());
    }

    @Test
    public void process_preserves_empty_key_value() {
        FullHttpRequest request = makeRequest("https://foo.com/?=");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/?=", request.uri());
    }

    @Test
    public void process_untouched() {
        FullHttpRequest request = makeRequest("https://foo.com/?a=b&c=d");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://foo.com/?a=b&c=d", request.uri());
    }

    @Test
    public void process_removes_tracking_params_from_referer() {
        FullHttpRequest request = makeRequest("https://foo.com/");
        request.headers().add("Referer", "https://bar.com/?gclid=ItrackU&x=y&ga_term=trackingTerm");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://bar.com/?x=y", request.headers().get("Referer"));
    }

    @Test
    public void process_referer_untouched() {
        FullHttpRequest request = makeRequest("https://foo.com/");
        request.headers().add("Referer", "https://bar.com/?x=y");
        Mockito.when(transaction.getRequest()).thenReturn(request);
        testee.process(transaction);

        assertEquals("https://bar.com/?x=y", request.headers().get("Referer"));
    }
}