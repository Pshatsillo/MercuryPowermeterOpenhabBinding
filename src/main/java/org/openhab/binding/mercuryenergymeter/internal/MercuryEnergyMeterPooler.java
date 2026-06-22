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
    @Nullable
    public MercuryEnergyMeterRS485TCPBridgeHandler tcpbridge;
    public byte[] request;
    public byte[] response;
    public int responseLength;
    @Nullable
    Channel channel;
    @Nullable
    MercuryEnergyMeterType type;

    public MercuryEnergyMeterPooler() {
        mercuryEnergyMeter203tdHandler = null;
        request = new byte[0];
        response = new byte[0];
        channel = null;
        tcpbridge = null;
        responseLength = 0;
        type = null;
    }
}

/**
 * The {@link MercuryEnergyMeterType} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
enum MercuryEnergyMeterType {
    OPEN_CONNECT,
    CLOSE_CONNECT,
    PARAMETERS,
    REQUEST
}
