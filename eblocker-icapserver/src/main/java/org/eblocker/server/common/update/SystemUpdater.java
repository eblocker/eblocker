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
package org.eblocker.server.common.update;

import org.eblocker.server.common.data.UpdatingStatus;
import org.eblocker.server.common.exceptions.EblockerException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs system updates. Only one instance should exist.
 * <p>
 * System updates usually take a few seconds (or minutes), so this is an asynchronous interface.
 * You can start an update and ask if an update is in progress. Updates can also be cancelled.
 */
public interface SystemUpdater {
    public enum State {
        IDLING, UPDATING, CANCELLING_UPDATE, DOWNLOADING, CHECKING;
    }

    /**
     * Starts an update. If the current state is not State.NOT_UPDATING or the device is not registered or the subscription is not valid,
     * calling this method has no effect.
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws EblockerException
     */
    void startUpdate() throws IOException, InterruptedException, EblockerException;

    /**
     * @return the update state
     * @throws InterruptedException
     * @throws IOException
     */
    State getUpdateStatus() throws IOException, InterruptedException;

    /**
     * Get the last time an update was executed.
     *
     * @return
     */
    LocalDateTime getLastUpdateTime();

    /**
     * Starts downloading new updates
     *
     * @return the update state
     * @throws InterruptedException
     * @throws IOException
     */
    void startDownload() throws IOException, InterruptedException;

    /**
     * Checks whether new updates are available.
     *
     * @return the update state
     * @throws InterruptedException
     * @throws IOException
     */
    boolean updatesAvailable() throws IOException, InterruptedException;

    /**
     * Returns a list of package updates
     *
     * @return A list of new updates. The list is empty if no updates are available.
     */
    List<String> updatesAvailablePackages();

    /**
     * Returns a list of notable update progress events
     *
     * @return A list like  "Unpacked ...,  Installed ..." or an empty list if no progress was made.
     */
    List<String> getUpdateProgress();

    /**
     * Checks whether the license is invalid, i.e. invalid registration credentials or license revoked. No new revocation test call will be made but cached data will be
     * used.
     *
     * @return True if the license is invalid, false if not.
     */
    boolean invalidLicense();

    /**
     * Creates a apt-pinning file named for eblocker lists to prevent upgrading eblocker lists.
     *
     * @return True if the successful, otherwise false.
     */
    boolean pinEblockerListsPackage() throws IOException, InterruptedException;

    /**
     * Delete the pinning of the eblocker-lists package
     *
     * @return True if the successful, otherwise false.
     */
    boolean unpinEblockerListsPackage() throws IOException, InterruptedException;


    UpdatingStatus getUpdatingStatus() throws IOException, InterruptedException;
}
