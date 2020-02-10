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
package org.eblocker.server.icap.ch.mimo.icap;

import org.eblocker.server.common.data.IpAddress;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eblocker.server.icap.transaction.AbstractTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.mimo.netty.handler.codec.icap.IcapMethod;
import ch.mimo.netty.handler.codec.icap.IcapRequest;

public class IcapTransaction extends AbstractTransaction {
	private static final Logger log = LoggerFactory.getLogger(IcapTransaction.class);

    private IcapRequest icapRequest;

    public IcapTransaction(IcapRequest icapRequest) {
        super(icapRequest.getMethod().equals(IcapMethod.REQMOD), icapRequest.getMethod().equals(IcapMethod.RESPMOD));
        this.icapRequest = icapRequest;
    }

    public IcapRequest getIcapRequest() {
        return icapRequest;
    }

    @Override
    public FullHttpRequest getRequest() {
        return icapRequest.getHttpRequest();
    }

    @Override
    public FullHttpResponse getResponse() {
    	return icapRequest.getHttpResponse();
    }

	@Override
	protected void doSetHttpRequest(FullHttpRequest httpRequest) {
        icapRequest.setHttpRequest(httpRequest);
	}

    @Override
    protected void doSetHttpResponse(FullHttpResponse httpResponse) {
        icapRequest.setHttpResponse(httpResponse);
     }

    @Override
    public String toString() {
        return icapRequest.toString();
    }

	@Override
	public boolean isPreview() {
		if (!icapRequest.isPreviewMessage()) return false;
		//
		//FIXME: This is not the correct approach!
		//       A continued message (i.e. a preview, for which we requested the complete content)
		//       is still marked as "preview", because the header remains the same.
		//       We need a better way to distinguish between actual preview and continued message.
		//
		int actualContentSize = 0;
		if (icapRequest.getMethod().equals(IcapMethod.REQMOD)) {
			actualContentSize = icapRequest.getHttpRequest().content().readableBytes();
		} else if (icapRequest.getMethod().equals(IcapMethod.RESPMOD)) {
			actualContentSize = icapRequest.getHttpResponse().content().readableBytes();
		}
		return actualContentSize <= icapRequest.getPreviewAmount();
	}

	@Override
	public IpAddress getOriginalClientIP() {
        String originalClientIP = icapRequest.getHeader("X-Client-IP");
        if (originalClientIP == null || originalClientIP.isEmpty()) {
            log.warn("Cannot determine original client IP from X-Client-IP header. Using 0.0.0.0 as work around");
            return IpAddress.parse("0.0.0.0");
        }
		return IpAddress.parse(originalClientIP);
	}

}
