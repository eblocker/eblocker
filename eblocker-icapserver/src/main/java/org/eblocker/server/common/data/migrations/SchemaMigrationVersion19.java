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
package org.eblocker.server.common.data.migrations;

import com.google.inject.Inject;
import org.eblocker.registration.ProductFeature;
import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.data.DataSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Creates EblockerDnsServerState based on current config
 */
public class SchemaMigrationVersion19 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion19(
            DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "18";
    }

    @Override
    public String getTargetVersion() {
        return "19";
    }

    @Override
    public void migrate() {
        ProductInfo productInfo = dataSource.get(ProductInfo.class, 0);
        if (productInfo != null) {
            List<String> features = new ArrayList<>(Arrays.asList(productInfo.getProductFeatures()));

            // Current users of the FAM features would loose BAS features, so
            // they are added again
            if (features.contains(ProductFeature.FAM.name()) && !features.contains(ProductFeature.BAS.name())) {
                features.add(ProductFeature.BAS.name());
            }

            // Current users of the PRO features would loose BAS features, so
            // they are added again
            if (features.contains(ProductFeature.PRO.name()) && !features.contains(ProductFeature.BAS.name())) {
                features.add(ProductFeature.BAS.name());
            }
            ProductInfo modifiedProductInfo = new ProductInfo(productInfo.getProductId(),
                    productInfo.getProductName(),
                    features.toArray(new String[0]));

            dataSource.save(modifiedProductInfo, 0);
        }
        dataSource.setVersion("19");
    }

}
