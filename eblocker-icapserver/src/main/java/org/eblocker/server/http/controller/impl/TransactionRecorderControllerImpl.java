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
package org.eblocker.server.http.controller.impl;

import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.recorder.RecordedTransaction;
import org.eblocker.server.common.recorder.TransactionRecorder;
import org.eblocker.server.common.recorder.TransactionRecorderInfo;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.TransactionRecorderController;
import org.eblocker.server.http.model.WhatIfModeDTO;
import org.eblocker.server.http.server.SessionContextController;
import com.google.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionRecorderControllerImpl extends SessionContextController implements TransactionRecorderController {
	private static final Logger LOG = LoggerFactory.getLogger(TransactionRecorderControllerImpl.class);

	TransactionRecorder transactionRecorder;

	@Inject
	public TransactionRecorderControllerImpl(
            SessionStore sessionStore, PageContextStore pageContextStore,
            TransactionRecorder transactionRecorder
	) {
        super(sessionStore, pageContextStore);
        this.transactionRecorder = transactionRecorder;
	}

	@Override
	public void start(Request request, Response response) {
		LOG.info("Starting transaction recorder");
		TransactionRecorderInfo transactionRecorderInfo = request.getBodyAs(TransactionRecorderInfo.class);
		transactionRecorder.activate(transactionRecorderInfo);
	}

	@Override
	public void stop(Request request, Response response) {
		LOG.info("Stopping transaction recorder");
		transactionRecorder.deactivate();
	}

	@Override
	public TransactionRecorderInfo info(Request request, Response response) {
		return transactionRecorder.getInfo();
	}

	@Override
	public List<RecordedTransaction> getAll(Request request, Response response) {
		LOG.info("Loading transaction recorder results");
		return transactionRecorder.getRecordedTransactions();
	}

	@Override
	public String getAllAsCSV(Request request, Response response) {
		LOG.info("Loading transaction recorder results as CSV");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		try (StringWriter out = new StringWriter()) {
			CSVPrinter csv = new CSVPrinter(out, CSVFormat.EXCEL.withDelimiter(';').withQuoteMode(QuoteMode.ALL));
			List<RecordedTransaction> recordedTransactions = transactionRecorder.getRecordedTransactions();
			recordedTransactions.forEach(t -> {
				String[] record = {
						Integer.toString(t.getId()),
						dateFormat.format(t.getTimestamp()),
						t.getMethod(),
						t.getUrl(),
						t.getDomain(),
						t.getReferrer(),
						t.getDecision(),
						t.getDecider(),
						t.getHeaders().entrySet().stream().map(header -> header.getKey() + ": " + header.getValue()).collect(Collectors.joining("\n"))
				};
				try {
					csv.printRecord(record);

				} catch (IOException e) {
					LOG.warn("Cannot write recorded transaction to CSV.",  e);
				}
			});
			csv.flush();
			csv.close();
			response.setContentType("application/csv");
			response.addHeader("Content-Disposition", "attachment; filename=recordedTransactions.csv");
			response.addHeader("Pragma", "no-cache");
			response.addHeader("Expires", "0");
			return out.toString();

		} catch (IOException e) {
            LOG.warn("Cannot generate CSV file with recorded transactions:",  e);
			throw new ServiceException("Cannot generate CSV file with recorded transactions: " + e.getMessage());
		}
	}

	@Override
	public List<RecordedTransaction> get(Request request, Response response) {
		String domain = request.getHeader("domain");
		return transactionRecorder.getRecordedTransactions(domain);
	}

    @Override
    public WhatIfModeDTO getWhatIfMode(Request request, Response response) {
	    Session session = getSession(request);
        return new WhatIfModeDTO(session.isWhatIfMode());
    }

    @Override
    public void setWhatIfMode(Request request, Response response) {
        WhatIfModeDTO whatIfMode = request.getBodyAs(WhatIfModeDTO.class);
        Session session = getSession(request);
        session.setWhatIfMode(whatIfMode.isMode());
    }

}
