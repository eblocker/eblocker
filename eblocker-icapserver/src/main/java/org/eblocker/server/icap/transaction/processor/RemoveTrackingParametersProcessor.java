package org.eblocker.server.icap.transaction.processor;

import com.google.inject.Singleton;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class RemoveTrackingParametersProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(RemoveTrackingParametersProcessor.class);
    private static final String REFERER_HEADER = "Referer";

    private static final Set<String> TRACKING_PARAMS = Set.of("gclid", "msclkid", "fbclid", "utm_campaign", "utm_term",
            "utm_medium", "utm_source", "utm_content", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
            "ga_source", "ga_medium", "ga_term", "ga_content", "ga_campaign", "ga_place", "action_object_map",
            "action_type_map", "action_ref_map", "gs_l", "mkt_tok", "hmb_campaign", "hmb_source", "hmb_medium", "aff",
            "KNC", "oq", "prmd");

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.isResponse()) {
            return true;
        }
        FullHttpRequest request = transaction.getRequest();
        String requestUri = request.uri();
        try {
            Optional<String> withoutTrackingParameters = removeTrackingParameters(requestUri);
            if (withoutTrackingParameters.isPresent()) {
                request.setUri(withoutTrackingParameters.get());
                transaction.setHeadersChanged(true);
                log.warn("Removed tracking parameter from >>" + requestUri + "<< to >>" + withoutTrackingParameters.get() + "<<");
            }

            HttpHeaders headers = request.headers();
            String existingReferrer = headers.get(REFERER_HEADER);
            if (existingReferrer != null && !existingReferrer.isBlank()) {
                Optional<String> refererWithoutTrackingParameters = removeTrackingParameters(existingReferrer);
                if (refererWithoutTrackingParameters.isPresent()) {
                    headers.set(REFERER_HEADER, refererWithoutTrackingParameters.get());
                    transaction.setHeadersChanged(true);
                    log.warn("Removed tracking parameter from Referer >>" + existingReferrer + "<< to >>" +
                            refererWithoutTrackingParameters.get() + "<<");
                }
            }
        } catch (Exception e) {
            log.error("Cannot parse " + requestUri, e);
        }
        return true;
    }

    private Optional<String> removeTrackingParameters(String uri) throws MalformedURLException {
        URL url = new URL(uri);
        String query = url.getQuery();

        if (query != null) {
            String[] params = query.split("&");
            List<String> nonTrackingParams = Arrays.stream(params)
                    .filter(param -> !TRACKING_PARAMS.contains(param.split("=")[0]))
                    .collect(Collectors.toList());

            if (params.length != nonTrackingParams.size()) {
                String newQuery = String.join("&", nonTrackingParams);
                String newUrl = url.getProtocol() + "://" +
                        url.getAuthority() +
                        url.getPath() +
                        (newQuery.isBlank() ? "" : "?" + newQuery) +
                        (url.getRef() == null ? "" : "#" + url.getRef());
                return Optional.of(newUrl);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }
}
