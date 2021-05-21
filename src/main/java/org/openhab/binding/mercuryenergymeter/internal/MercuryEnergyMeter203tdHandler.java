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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercuryEnergyMeter203tdHandler} is responsible for creating things and thing
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MercuryEnergyMeter203tdHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(MercuryEnergyMeter203tdHandler.class);

    private @Nullable ScheduledFuture<?> pollingTask;
    int poll;
    int pass;
    int serno;
    @Nullable
    MercuryEnergyMeterRS485BridgeHandler bridgeHandler;

    public MercuryEnergyMeter203tdHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        bridgeHandler = getBridgeHandler();
        pass = config.userpassword;
        poll = config.pollPeriod;
        scheduler.execute(this::getserial);
        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Waiting for energy meter serial number");
    }

    private void getserial() {
        logger.debug("getserial");
        int[] data = new int[] { 0x00, 0x08, 0x05 }; // Getting network address

        int trytoget = 0;
        while (serno == 0) {
            serno = bridgeHandler.sendPacket(data, 4, pass)[2];
            if (trytoget > 4) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Cannot get network address");
                break;
            }
            trytoget++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        if (serno != 0) {
            if (poll > 0) {
                pollingTask = scheduler.scheduleWithFixedDelay(this::poll, 0, poll, TimeUnit.SECONDS);
            } else {
                pollingTask = null;
            }
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Network address is " + serno);
        }
    }

    private @Nullable MercuryEnergyMeterRS485BridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            if (bridge.getHandler() instanceof MercuryEnergyMeterRS485BridgeHandler) {
                return (MercuryEnergyMeterRS485BridgeHandler) bridge.getHandler();
            }
        }
        return null;
    }

    private void poll() {
        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID().getId())) {
                if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_1)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x11 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    if (val.get(0) != 0) {
                        try {
                            updateState(channel.getUID().getId(),
                                    DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                            logger.debug("Voltage 1 : {}V", val.get(0) / 100);
                        } catch (Exception ignore) {
                        }
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_2)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x12 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Voltage 2 : {}V", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_3)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x13 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Voltage 1 : {}V", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_1)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x21 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        if (val.get(0) != 0) {
                            updateState(channel.getUID().getId(),
                                    DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                            logger.debug("Current 1 : {}A", val.get(0) / 1000);
                        }
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_2)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x22 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("Current 2 : {}A", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_3)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x08, 0x11, 0x23 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("Current 3 : {}A", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId()
                        .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_ACTIVE_TOTAL)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x05, 0x00, 0x00 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 19, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { pd[2], pd[1], pd[4], pd[3] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("A+ total: {} kWh", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_1)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x05, 0x00, 0x01 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 19, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { pd[2], pd[1], pd[4], pd[3] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("A+ T1: {} kWh", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_2)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x05, 0x00, 0x02 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 19, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { pd[2], pd[1], pd[4], pd[3] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("A+ T2: {} kWh", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_3)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        int[] data = new int[] { serno, 0x05, 0x00, 0x03 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 19, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { pd[2], pd[1], pd[4], pd[3] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 1000)));
                        logger.debug("A+ T3: {} kWh", val.get(0) / 1000);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId()
                        .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_ACTIVE_TOTAL)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        int[] data = new int[] { serno, 0x08, 0x11, 0x00 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Power total: {} W", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_1)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        int[] data = new int[] { serno, 0x08, 0x11, 0x01 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Power T1: {} W", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_2)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        int[] data = new int[] { serno, 0x08, 0x11, 0x02 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Power T2: {} W", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                } else if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_3)) {
                    ArrayList<Float> val = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        int[] data = new int[] { serno, 0x08, 0x11, 0x03 }; // Mercury 236 test connection
                        byte[] pd = bridgeHandler.sendPacket(data, 6, pass);
                        float AplusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, pd[1], pd[3], pd[2] }).getInt();
                        val.add(AplusTotalnum);
                    }
                    val.remove(Collections.max(val));
                    val.remove(Collections.min(val));
                    try {
                        updateState(channel.getUID().getId(), DecimalType.valueOf(String.valueOf(val.get(0) / 100)));
                        logger.debug("Power T3: {} W", val.get(0) / 100);
                    } catch (Exception ignore) {
                    }
                }
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void dispose() {
        logger.info("disposing");
        final ScheduledFuture<?> task = pollingTask;
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
            pollingTask = null;
            logger.info("polling stop");
        }
        super.dispose();
    }
}
