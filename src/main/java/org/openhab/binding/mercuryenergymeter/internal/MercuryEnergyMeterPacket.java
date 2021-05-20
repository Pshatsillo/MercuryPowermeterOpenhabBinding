/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

/**
 * The {@link MercuryEnergyMeterPacket} is responsible for creating things and thing
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
public class MercuryEnergyMeterPacket {
    int[] packetArray;
    int bytecount;
    String channel;

    public int[] getPacketArray() {
        return packetArray;
    }

    public void setPacketArray(int[] packetArray) {
        this.packetArray = packetArray;
    }

    public int getBytecount() {
        return bytecount;
    }

    public void setBytecount(int bytecount) {
        this.bytecount = bytecount;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
