package org.eblocker.server.icap.transaction.processor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.AutoTrustAppService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SuccessfulSSLDetector implements TransactionProcessor {

    private final AutoTrustAppService autoTrustAppService;
    private static final Logger log = LoggerFactory.getLogger(SuccessfulSSLDetector.class);

    @Inject
    public SuccessfulSSLDetector(AutoTrustAppService autoTrustAppService) {
        this.autoTrustAppService = autoTrustAppService;
    }

    @Override
    public boolean process(Transaction transaction) {
        String url = transaction.getUrl();
        if (url != null && url.startsWith("https")) {
            log.debug("Processing HTTPS URL " + url);
            autoTrustAppService.recordSuccessfulSSL(UrlUtils.getHostname(url));
        }
        return true;
    }
}
