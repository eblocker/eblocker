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
package org.eblocker.server.icap.transaction;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.resources.OnePixelImage;
import org.eblocker.server.icap.service.SurrogateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map.Entry;
import java.util.Optional;

public abstract class AbstractTransaction implements Transaction, TransactionIdentifier {
	private static final Logger log = LoggerFactory.getLogger(AbstractTransaction.class);

	private Session session = null;
    private boolean isRequest = false;
    private boolean isResponse = false;

    protected boolean contentChanged = false;
    protected boolean headersChanged = false;

    private boolean complete = false;
    private String domain = null;
    private String referrer = null;
    private String referrerHostname = null;
    private String accept = null;
    private Boolean thirdParty = null;
    private String userAgent = null;
    private String redirectTarget = null;
    private Decision decision = Decision.NO_DECISION;
    private String baseUrl = null;
    private FilterResult filterResult = null;
    private PageContext pageContext;
    private Injections injections;
    private ContentEncoding contentEncoding;
    private StringBuilder content;
    private static SurrogateService surrogateService = new SurrogateService();

    public AbstractTransaction(boolean isRequest, boolean isResponse) {
        this.isRequest = isRequest;
        this.isResponse = isResponse;
    }

    @Override
    public String getSessionId() {
        return session.getSessionId();
	}

    @Override
    public Session getSession() {
        return session;
    }

    @Override
	public void setSession(Session session) {
		this.session = session;
	}

	@Override
    public String getUrl() {
        if (getRequest() != null){
            return getRequest().getUri();
        }
        return null;
    }

    @Override
    public String getReferrer() {
        if (referrer == null && getRequest() != null){
        	referrer = getRequest().headers().get(HttpHeaders.Names.REFERER);
        }
        return referrer;
    }

    @Override
	public String getAccept() {
		if (accept == null && getRequest() != null) {
			accept = getRequest().headers().get(HttpHeaders.Names.ACCEPT);
		}
		return accept;
	}

    @Override
    public String getUserAgent() {
        if (userAgent == null && getRequest() != null){
        	userAgent = getRequest().headers().get(HttpHeaders.Names.USER_AGENT);
        }
        return userAgent;
    }

    @Override
    public String getDomain() {
    	if (domain == null) {
    		domain = UrlUtils.getDomain(UrlUtils.getHostname(getUrl()));
    	}
    	return domain;
    }

	@Override
	public String getReferrerHostname() {
    	if (referrerHostname == null) {
    		getReferrer();
    		if (referrer != null) {
    		    try {
                    referrerHostname = UrlUtils.getHostname(referrer);
                } catch (EblockerException e) {
    		        // ignore malformed referrer header
                    log.debug("malformed referrer header", e);
                }
    		}
    	}
    	return referrerHostname;
	}

    @Override
    public boolean isRequest() {
        return isRequest;
    }

    @Override
    public boolean isResponse() {
        return isResponse;
    }

    @Override
    public String getUserReference() {
    	HttpHeaders headers = getRequest().headers();
    	return headers.get("Client");
    }

    protected abstract void doSetHttpResponse(FullHttpResponse httpResponse);

    protected abstract void doSetHttpRequest(FullHttpRequest httpRequest);

    @Override
    public void setRequest(FullHttpRequest httpRequest) {
        doSetHttpRequest(httpRequest);
        isRequest = true;
        isResponse = false;
        headersChanged = true;
        contentChanged = true;
    }

    @Override
    public void setResponse(FullHttpResponse httpResponse) {
        doSetHttpResponse(httpResponse);
        isRequest = false;
        isResponse = true;
        headersChanged = true;
        contentChanged = true;
    }

	@Override
	public String getContentType() {
		if (getResponse() == null) {
			return null;
		}
		return getResponse().headers().get(HttpHeaders.Names.CONTENT_TYPE);
	}

    @Override
    public boolean isContentChanged() {
        return contentChanged;
    }

    @Override
    public void setContentChanged(boolean contentChanged) {
        this.contentChanged = contentChanged;
    }

    @Override
    public boolean isHeadersChanged() {
        return headersChanged;
    }

    @Override
    public void setHeadersChanged(boolean headersChanged) {
        this.headersChanged = headersChanged;
    }

    @Override
	public boolean isComplete() {
		return complete;
	}

	@Override
	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	@Override
	public void block() {
        if (session.isWhatIfMode()) {
            return;
        }

        if (log.isDebugEnabled()) {
            logHeaders();
        }

        String url = getUrl();
        Optional<FullHttpResponse> surrogate = surrogateService.surrogateForBlockedUrl(url);
        if (surrogate.isPresent()) {
            log.info("Returning surrogate for " + url);
            setResponse(surrogate.get());
        } else {
            byte[] image = OnePixelImage.get(baseUrl + "/redirect/prepare?url=" + UrlUtils.urlEncode(url));

            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(image));
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, OnePixelImage.MIME_TYPE);
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, image.length);
            setResponse(httpResponse);
        }

        this.complete = true;
        this.headersChanged = true;
        this.contentChanged = true;
    }

	@Override
	public void noContent() {
		if (log.isDebugEnabled()) {
			logHeaders();
		}
		FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
		httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, 0);
		httpResponse.headers().add("Access-Control-Allow-Origin", "*");
		setResponse(httpResponse);
		this.complete = true;
        this.headersChanged = true;
        this.contentChanged = true;
	}

	@Override
	public void redirect(String targetUrl) {
        if (session.isWhatIfMode()) {
            return;
        }
		if (log.isDebugEnabled()) {
			logHeaders();
		}
		FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SEE_OTHER);
		httpResponse.headers().add(HttpHeaders.Names.LOCATION, targetUrl);
		setResponse(httpResponse);
		this.complete = true;
        this.headersChanged = true;
        this.contentChanged = true;
	}

	@Override
	public String getRedirectTarget() {
		return redirectTarget;
	}

	@Override
	public void setRedirectTarget(String redirectTarget) {
		this.redirectTarget = redirectTarget;
	}

	@Override
	public Decision getDecision() {
		return decision;
	}

	@Override
	public void setDecision(Decision decision) {
		this.decision = decision;
	}

	@Override
	public boolean isThirdParty() {
		if (thirdParty == null) {
			thirdParty = !UrlUtils.isSameDomain(getDomain(), getReferrerHostname());
		}
		return thirdParty;
	}

	@Override
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Override
	public FilterResult getFilterResult() {
		return filterResult;
	}

	@Override
	public void setFilterResult(FilterResult filterResult) {
		this.filterResult = filterResult;
	}

    @Override
    public PageContext getPageContext() {
        return pageContext;
    }

    @Override
    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    @Override
    public Injections getInjections() {
        return injections;
    }

    @Override
    public void setInjections(Injections injections) {
        this.injections = injections;
    }

    @Override
    public ContentEncoding getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public void setContentEncoding(ContentEncoding contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    @Override
    public StringBuilder getContent() {
        return content;
    }

    @Override
    public void setContent(StringBuilder content) {
        this.content = content;
    }

    private void logHeaders() {
		if (log.isDebugEnabled()) {
			for (Entry<String, String> header: getRequest().headers()) {
				log.debug("    {} => {}", header.getKey(), header.getValue());
			}
		}
	}

}
