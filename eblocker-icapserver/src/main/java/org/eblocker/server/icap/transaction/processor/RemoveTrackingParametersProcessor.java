package org.eblocker.server.icap.transaction.processor;

import com.google.inject.Singleton;
import io.netty.handler.codec.http.FullHttpRequest;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class RemoveTrackingParametersProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RemoveTrackingParametersProcessor.class);

    private static final Set<String> TRACKING_PARAMS = Set.of("gclid", "msclkid", "fbclid", "utm_campaign", "utm_term", "utm_medium", "utm_source", "utm_content", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref", "ga_source", "ga_medium",
            "ga_term",
            "ga_content", "ga_campaign", "ga_place", "action_object_map", "action_type_map", "action_ref_map", "gs_l", "mkt_tok", "hmb_campaign", "hmb_source", "hmb_medium", "aff", "KNC", "oq", "prmd");

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.isResponse()) {//just for requests, because they contain the http header
            return true;
        }
        FullHttpRequest request = transaction.getRequest();

        String requestUri = request.uri();
        try {
            URL url = new URL(requestUri);
            String query = url.getQuery();

            if (query != null) {
                String[] params = query.split("&");
                List<String> filteredParams = new ArrayList<>(params.length);
                boolean removed = false;
                for (String param : params) {
                    if (TRACKING_PARAMS.contains(param.split("=")[0])) {
                        removed = true;
                    } else {
                        filteredParams.add(param);
                    }
                }
                if (removed) {
                    String newQuery = String.join("&", filteredParams);
                    String newUrl = url.getProtocol() + "://" +
                            url.getAuthority() +
                            url.getPath() +
                            (newQuery.isBlank() ? "" : "?" + newQuery) +
                            (url.getRef() == null ? "" : "#" + url.getRef());
                    request.setUri(newUrl);
                    transaction.setHeadersChanged(true);
                    log.warn("Removed tracking parameter from >>" + requestUri + "<< to >>" + newUrl + "<<");
                }
            }
        } catch (Exception e) {
            log.error("Cannot parse " + requestUri, e);
        }
        return true;
    }
}
