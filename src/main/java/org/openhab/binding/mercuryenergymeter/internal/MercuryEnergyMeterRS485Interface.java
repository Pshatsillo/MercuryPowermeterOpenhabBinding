/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mercuryenergymeter.internal;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.binding.BridgeHandler;

/**
 * The {@link MercuryEnergyMeterRS485Interface} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */

@NonNullByDefault
public interface MercuryEnergyMeterRS485Interface extends BridgeHandler {
    List<MercuryEnergyMeterPooler> getRequestsList();

    void addRequestsList(MercuryEnergyMeterPooler pooler);

    void sendMessage(MercuryEnergyMeterPooler data);

    void registerThing(MercuryEnergyMeter203tdHandler dooyaCurtainsHandler);

    void removeThing(MercuryEnergyMeter203tdHandler dooyaCurtainsHandler);
}
