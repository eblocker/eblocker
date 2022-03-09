package org.eblocker.server.http.backup;

import org.eblocker.server.common.exceptions.EblockerException;

public class DecryptionFailedException extends EblockerException {
    public DecryptionFailedException(String message) {
        super(message);
    }
}
