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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;

/**
 * The {@link MercuryEnergyMeterPooler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */

@NonNullByDefault
public class MercuryEnergyMeterPooler {

    @Nullable
    MercuryEnergyMeter203tdHandler mercuryEnergyMeter203tdHandler;
    public byte[] request;
    public byte[] response;
    @Nullable
    Channel channel;

    public MercuryEnergyMeterPooler() {
        mercuryEnergyMeter203tdHandler = null;
        request = new byte[0];
        response = new byte[0];
        channel = null;
    }
}
