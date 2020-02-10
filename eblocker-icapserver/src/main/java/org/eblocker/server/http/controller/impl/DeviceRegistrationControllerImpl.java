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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.eblocker.server.common.exceptions.NetworkConnectionException;
import org.eblocker.server.common.registration.DeviceRegistrationInfo;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.http.controller.DeviceRegistrationController;
import org.eblocker.server.http.service.CustomerInfoService;
import org.eblocker.server.http.service.RegistrationServiceAvailabilityCheck;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.ProductMigrationService;
import org.eblocker.server.http.service.RegistrationService;
import org.eblocker.server.http.service.ReminderService;
import org.eblocker.registration.ProductInfo;
import org.eblocker.registration.error.ClientRequestError;
import org.eblocker.registration.error.ClientRequestException;
import org.eblocker.server.common.update.SystemUpdater;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DeviceRegistrationParameters;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.restexpress.Request;
import org.restexpress.Response;

import org.eblocker.registration.DeviceRegistrationRequest;
import org.eblocker.registration.DeviceRegistrationResponse;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Inject;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interacts with the device registration portal.
 */
public class DeviceRegistrationControllerImpl implements DeviceRegistrationController {

	private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationControllerImpl.class);

	private final String fallbackProductId;
	private final String fallbackProductName;
	private final String[] fallbackProductFeatures;
	private final DeviceRegistrationProperties deviceRegistrationProperties;
	private final DeviceRegistrationClient deviceRegistrationClient;
	private final ProductInfoService productInfoService;
	private int productInfoLoadAttempts = 10;
	private final ReminderService reminderService;
	private final SslService sslService;
	private final ExecutorService executorService;
	private final SystemUpdater systemUpdater;
	private final ProductMigrationService productMigrationService;
	private final DataSource dataSource;
	private final CustomerInfoService customerInfoService;
	private final RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck;

	@Inject
	public DeviceRegistrationControllerImpl(
        @Named("registration.fallback.product.id") String fallbackProductId,
        @Named("registration.fallback.product.name") String fallbackProductName,
	    @Named("registration.fallback.product.features") String fallbackProductFeatures,
        DeviceRegistrationProperties deviceRegistrationProperties,
        DeviceRegistrationClient deviceRegistrationClient,
        ProductInfoService productInfoService,
        ReminderService reminderService,
        RegistrationService registrationService,
        SslService sslService,
        SystemUpdater systemUpdater,
        @Named("lowPrioScheduledExecutor")ScheduledExecutorService executorService,
        ProductMigrationService productMigrationService,
        DataSource dataSource,
        CustomerInfoService customerInfoService,
        RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck) {

	    this.fallbackProductId = fallbackProductId;
	    this.fallbackProductName = fallbackProductName;
	    this.fallbackProductFeatures = Iterables.toArray(Splitter.on(',').trimResults().split(fallbackProductFeatures), String.class);

		this.deviceRegistrationProperties = deviceRegistrationProperties;
		this.deviceRegistrationClient = deviceRegistrationClient;
		this.productInfoService = productInfoService;
		this.reminderService = reminderService;
		this.sslService = sslService;
        this.executorService = executorService;
        this.systemUpdater = systemUpdater;
        this.productMigrationService = productMigrationService;
        this.dataSource = dataSource;
        this.customerInfoService = customerInfoService;
        this.registrationServiceAvailabilityCheck = registrationServiceAvailabilityCheck;

        // Since the lists pinning version might have changed after a software update,
        // we repeat the pinning at every restart:
        eBlockerListHandling(getProductInfo());
	}

	@Override
	public DeviceRegistrationInfo registrationStatus(Request request, Response response) {
		ProductInfo productInfo = null;
		if (deviceRegistrationProperties.getRegistrationState() != RegistrationState.NEW) {
			productInfo = getProductInfo();
		}
		return new DeviceRegistrationInfo(deviceRegistrationProperties, productInfo, null);
	}

	@Override
	public String licenseNotValidAfter(Request request, Response response){
		Date validUntil = deviceRegistrationProperties.getLicenseNotValidAfter();
		if(validUntil == null)
		    return null;
		String formattedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(validUntil);
		return formattedDate;
	}

    @Override
    public DeviceRegistrationInfo register(Request request, Response response) { //FIXME test preprocessing of email address and license key here!!!
        // Check the current activation is not invalid
        if (deviceRegistrationProperties.getRegistrationState() == RegistrationState.INVALID) {
            throw new BadRequestException("error.invalid");// TODO: make sure this is a 400-html-code (was/is a 500 now)
        }

        DeviceRegistrationParameters parameters = request.getBodyAs(DeviceRegistrationParameters.class);
        if (Boolean.TRUE.equals(parameters.getFallback())) {
            if (registrationServiceAvailabilityCheck.isRegistrationAvailable()) {
                throw new BadRequestException("can not activate fallback license if license server is available!");
            }

            deviceRegistrationProperties.registrationFallback(parameters.getDeviceName());
            ProductInfo productInfo = new ProductInfo(fallbackProductId, fallbackProductName, fallbackProductFeatures);
            productInfoService.save(productInfo);
            return new DeviceRegistrationInfo(deviceRegistrationProperties, productInfo, null);
        }

        DeviceRegistrationRequest registrationRequest = deviceRegistrationProperties.generateRequest(
                parameters.getEmailAddress().trim(),
                parameters.getDeviceName(),
                parameters.getLicenseKey().replaceAll(" ",""),
                parameters.getSerialNumber(),
                parameters.isConfirmed(),
                parameters.getTosVersion()
        );

        try {
            DeviceRegistrationResponse registrationResponse = deviceRegistrationClient.register(registrationRequest);

            //
            // Reset current promo info (possibly from prior activation)
            //
            customerInfoService.delete();

            //
            // Do we have additional promo info for the setup wizard?
            //
            Object postRegistrationInformation = null;
            if (registrationResponse.getCustomerInfo() != null
                && registrationResponse.getCustomerInfo().getContent() != null
                && registrationResponse.getCustomerInfo().getContent().containsKey("SETUP_WIZARD_PROMO_1")) {
                postRegistrationInformation = registrationResponse.getCustomerInfo().getContent().get("SETUP_WIZARD_PROMO_1");
            }

            DeviceRegistrationInfo deviceRegistrationInfo;

            if (!registrationResponse.isNeedsConfirmation()) {
                deviceRegistrationProperties.processResponse(registrationResponse);
                // Call ReminderService to calculate time for next reminder
                reminderService.setReminder();
                // We must update the product info, as it might have changed due to the registration
                ProductInfo productInfo = updateProductInfo();
                deviceRegistrationInfo = new DeviceRegistrationInfo(
                    deviceRegistrationProperties,
                    productInfo,
                    postRegistrationInformation
                );
                eBlockerListHandling(productInfo);
            } else {
                deviceRegistrationInfo = new DeviceRegistrationInfo(
                    registrationResponse.isNeedsConfirmation(),
                    registrationResponse.getConfirmationMsgKeys(),
                    postRegistrationInformation
                );
            }

            if (RegistrationState.OK == deviceRegistrationProperties.getRegistrationState() && !sslService.isCaAvailable()) {
                generateCaInBackground();
            }

            //FIXME: Get rid of datasource in this class and use the ReminderService for this task
            dataSource.setDoNotShowReminder(false);

            return deviceRegistrationInfo;

        } catch (NetworkConnectionException exception) {
            throw new ServiceException("error.network");

        } catch (ClientRequestException exception) {

            ClientRequestError requestError = exception.getError();

            String errorMessage;
            if (requestError != null) {
                errorMessage = requestError.getMessage();
            } else {
                throw new ServiceException("No content in request error for '" + exception.getMessage() + "'");
            }

            throw new BadRequestException(errorMessage);
        }
    }

    private void eBlockerListHandling(ProductInfo productInfo) {
        try {
            if (productInfoService.hasFeature(ProductFeature.PRO)) {
                systemUpdater.unpinEblockerListsPackage();
            } else {
                systemUpdater.pinEblockerListsPackage();
            }
        } catch (Exception e) {
            LOG.error("Error enabling / disabling eblocker-lists package pinning. productInfo: {}", productInfo , e);
        }
    }

	@Override
	public void resetRegistration(Request request, Response response) throws IOException, InterruptedException{
		LOG.info("Remove registration request");

		ProductInfo productInfo = productInfoService.get();
		if (productInfo == null) {
		    throw new BadRequestException("eBlocker is not yet activated");
        }
		for (String feature: productInfo.getProductFeatures()) {
		    if (feature.startsWith("EVL_")) {
                throw new BadRequestException("Cannot reset evaluation license");
            }
        }

        //
        // Reset current promo info
        //
        customerInfoService.delete();

		// The current license, about to become the old license
		DeviceRegistrationInfo oldLicense = new DeviceRegistrationInfo(deviceRegistrationProperties, productInfoService.get(), null);

		productInfoService.clear();
		deviceRegistrationProperties.reset();

		// The now current license
		// TODO: Is this used for the registration fo the next license? If so, add tosContainer?
		DeviceRegistrationInfo newLicense = new DeviceRegistrationInfo(deviceRegistrationProperties, productInfoService.get(), null);

		// Deactivate any now unlicensed features
		productMigrationService.changeProduct(oldLicense, newLicense);
	}

	private ProductInfo getProductInfo() {
		ProductInfo productInfo = productInfoService.get();
		if (productInfo == null) {
			// No product info yet, so try to update it
			productInfo = updateProductInfo();
		}
		return productInfo;
	}

	private ProductInfo updateProductInfo() {
		ProductInfo productInfo = null;
		try {
			if (productInfoLoadAttempts > 0) {
				productInfo = deviceRegistrationClient.getProductInfo();
				productInfoService.save(productInfo);
			} else {
				LOG.debug("Not trying to load product info till the next reboot");
			}
		} catch (Exception e) {
			productInfoLoadAttempts--;
			LOG.error("Cannot load product info from backend. Will try {} more times. ({})", productInfoLoadAttempts, e);
		}
		return productInfo;
	}

	private void generateCaInBackground() {
        executorService.submit(() -> {
            try {
                sslService.generateCa(sslService.getDefaultCaOptions());
            } catch (SslService.PkiException e) {
                LOG.error("failed to generate ca", e);
            }
        });
    }
}
