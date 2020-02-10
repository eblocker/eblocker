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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.registration.ProductFeature;
import org.eblocker.registration.ProductInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = false)
public class ProductInfoService {

    static final int KEY = 0;

    private final DataSource dataSource;
    private ProductInfo productInfo;

    @Inject
    public ProductInfoService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SubSystemInit
    public void init(){
        productInfo = dataSource.get(ProductInfo.class, KEY);
    }

    public void clear() {
        dataSource.delete(ProductInfo.class, KEY);
        productInfo = null;
    }

    public ProductInfo get() {
        return productInfo;
    }

    public void save(ProductInfo productInfo) {
        this.productInfo = productInfo;
        dataSource.save(productInfo, KEY);
    }

    public boolean hasFeature(ProductFeature feature) {
        if (productInfo == null || productInfo.getProductFeatures() == null) {
            return false;
        }
        for (String productFeature: productInfo.getProductFeatures()) {
            if (productFeature.equalsIgnoreCase(feature.name())) {
                return true;
            }
        }
        return false;
    }

    public void removeFeature(ProductFeature feature) {
        List<String> features = new ArrayList<>(Arrays.asList(productInfo.getProductFeatures()));
        features.remove(feature.name());
        ProductInfo reducedProductInfo = new ProductInfo(
            productInfo.getProductId(),
            productInfo.getProductName(),
            features.toArray(new String[features.size()])
        );
        save(reducedProductInfo);
    }
}
