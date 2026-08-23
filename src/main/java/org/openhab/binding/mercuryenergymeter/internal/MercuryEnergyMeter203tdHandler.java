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

import static org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterType.CLOSE_CONNECT;
import static org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterType.OPEN_CONNECT;
import static org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterType.PARAMETERS;
import static org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterType.REQUEST;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import org.openhab.core.thing.binding.BridgeHandler;
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
    @Nullable
    MercuryEnergyMeterRS485Interface rs485interface;
    int[] password = new int[6];

    public MercuryEnergyMeter203tdHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        // pass = config.userpassword;
        poll = config.pollPeriod;
        serno = config.netaddress;
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.error("MercuryEnergyMeter203tdHandler bridge is null");
            return;
        }
        BridgeHandler handler = bridge.getHandler();
        if (handler == null) {
            logger.error("MercuryEnergyMeter203tdHandler bridge handler is null");
            return;
        }
        rs485interface = (MercuryEnergyMeterRS485Interface) handler;
        MercuryEnergyMeterRS485Interface in = this.rs485interface;
        if (in != null) {
            in.registerThing(this);
            int reconnect = 0;
            while (!in.getThing().getStatus().equals(ThingStatus.ONLINE)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                if (reconnect == 10) {
                    logger.error("Bridge is offline during 10 seconds");
                    updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                            "Bridge is offline during 10 seconds");
                    break;
                }
                reconnect++;
            }
            String pwdConv = Integer.toString(config.userpassword);
            if (pwdConv.length() == 6) {
                int[] pswd = new int[pwdConv.length()];
                for (int i = 0; i < pwdConv.length(); i++) {
                    pswd[i] = Integer.parseInt(String.valueOf(pwdConv.charAt(i)));
                }
                password = new int[] { pswd[0], pswd[1], pswd[2], pswd[3], pswd[4], pswd[5] };
            } else {
                logger.warn("password must be 6 digits");
                updateStatus(ThingStatus.UNINITIALIZED, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Password must be 6 digits");
            }
            openChannel();
            reconnect = 0;
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Waiting for energy meter serial number");
            while (!getThing().getStatus().equals(ThingStatus.ONLINE)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                if (reconnect == 10) {
                    logger.error("Thing is offline during 10 seconds");
                    updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                            "Device is not answering");
                    break;
                }
                reconnect++;
            }
            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
            pooler.mercuryEnergyMeter203tdHandler = this;
            pooler.request = new byte[] { (byte) serno, 0x08, 0x01 };
            pooler.responseLength = 19;
            pooler.type = PARAMETERS;
            in.sendMessage(pooler);
        }
        pollingTask = scheduler.scheduleWithFixedDelay(this::poll, 0, poll, TimeUnit.SECONDS);
    }

    public void openChannel() {
        MercuryEnergyMeterRS485Interface in = this.rs485interface;
        if (in != null) {
            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
            pooler.mercuryEnergyMeter203tdHandler = this;
            pooler.request = new byte[] { (byte) serno, 0x01, 0x01, (byte) password[0], (byte) password[1],
                    (byte) password[2], (byte) password[3], (byte) password[4], (byte) password[5] };
            pooler.responseLength = 4;
            pooler.type = OPEN_CONNECT;
            in.sendMessage(pooler);
        }
    }

    private void poll() {
        // var status = getThing().getStatus();
        if (getThing().isEnabled()) {
            for (Channel channel : getThing().getChannels()) {
                if (isLinked(channel.getUID().getId())) {
                    MercuryEnergyMeterRS485Interface in = this.rs485interface;
                    if (in != null) {
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_1)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x11 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_2)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x12 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_3)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x13 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_1)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x21 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_2)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x22 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_3)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x23 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_ACTIVE_TOTAL)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x05, 0x00, 0x00 };
                            pooler.responseLength = 19;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_1)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x05, 0x00, 0x01 };
                            pooler.responseLength = 19;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_2)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x05, 0x00, 0x02 };
                            pooler.responseLength = 19;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_3)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x05, 0x00, 0x03 };
                            pooler.responseLength = 19;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_ACTIVE_TOTAL)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x00 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_1)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x01 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_2)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x02 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_3)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x11, 0x03 };
                            pooler.responseLength = 6;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        } else if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_TARIF)) {
                            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                            pooler.request = new byte[] { (byte) serno, 0x08, 0x17 };
                            pooler.responseLength = 5;
                            pooler.mercuryEnergyMeter203tdHandler = this;
                            pooler.channel = channel;
                            pooler.type = REQUEST;
                            in.sendMessage(pooler);
                        }
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
        MercuryEnergyMeterRS485Interface in = this.rs485interface;
        if (in != null) {
            MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
            pooler.mercuryEnergyMeter203tdHandler = this;
            pooler.request = new byte[] { (byte) serno, 0x02 };
            pooler.responseLength = 4;
            pooler.type = CLOSE_CONNECT;
            in.sendMessage(pooler);
        }
        final ScheduledFuture<?> task = pollingTask;
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
            pollingTask = null;
            logger.info("polling stop");
        }
        super.dispose();
    }

    public void response(MercuryEnergyMeterPooler sendedRequest) {
        logger.debug("response {}", Arrays.toString(sendedRequest.response));
        if (sendedRequest.type == OPEN_CONNECT) {
            Map<String, String> properties = new HashMap<>();
            properties.put("Network address:", String.valueOf(sendedRequest.response[0] & 0xFF));
            updateProperties(properties);
            updateStatus(ThingStatus.ONLINE);
        } else if (sendedRequest.type == PARAMETERS) {
            Map<String, String> properties = new HashMap<>();
            properties.put("Serial number:",
                    String.format("%02d%02d%02d%02d", sendedRequest.response[1] & 0xFF,
                            sendedRequest.response[2] & 0xFF, sendedRequest.response[3] & 0xFF,
                            sendedRequest.response[4] & 0xFF));
            properties.put("Manufacturing date:", String.format("%02d.%02d.20%02d", sendedRequest.response[5] & 0xFF,
                    sendedRequest.response[6] & 0xFF, sendedRequest.response[7] & 0xFF));
            updateProperties(properties);
        } else if (sendedRequest.type == REQUEST) {
            if (sendedRequest.channel != null) {
                if (getThing().isEnabled()) {
                    Channel channel = sendedRequest.channel;
                    if (channel != null) {
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_1)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Voltage 1 : {}V", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_2)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Voltage 2 : {}V", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_VOLTAGE_3)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Voltage 3 : {}V", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_1)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("Current 1 : {}V", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_2)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("Current 2 : {}V", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_3)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { 0x00, sendedRequest.response[1],
                                    sendedRequest.response[3], sendedRequest.response[2] }).getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("Current 3 : {}V", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_ACTIVE_TOTAL)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { sendedRequest.response[2],
                                    sendedRequest.response[1], sendedRequest.response[4], sendedRequest.response[3] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("A+ total: {} kWh", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_1)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { sendedRequest.response[2],
                                    sendedRequest.response[1], sendedRequest.response[4], sendedRequest.response[3] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("A+ T1: {} kWh", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_2)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { sendedRequest.response[2],
                                    sendedRequest.response[1], sendedRequest.response[4], sendedRequest.response[3] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("A+ T2: {} kWh", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_ENERGY_3)) {
                            float aPlusTotalnum = ByteBuffer.wrap(new byte[] { sendedRequest.response[2],
                                    sendedRequest.response[1], sendedRequest.response[4], sendedRequest.response[3] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 1000)));
                                logger.debug("A+ T3: {} kWh", aPlusTotalnum / 1000);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId()
                                .equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_ACTIVE_TOTAL)) {
                            float aPlusTotalnum = ByteBuffer
                                    .wrap(new byte[] { 0x00, (byte) (sendedRequest.response[1] & 0x3F),
                                            sendedRequest.response[3], sendedRequest.response[2] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Power total: {} W", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_1)) {
                            float aPlusTotalnum = ByteBuffer
                                    .wrap(new byte[] { 0x00, (byte) (sendedRequest.response[1] & 0x3F),
                                            sendedRequest.response[3], sendedRequest.response[2] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Power T1: {} W", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_2)) {
                            float aPlusTotalnum = ByteBuffer
                                    .wrap(new byte[] { 0x00, (byte) (sendedRequest.response[1] & 0x3F),
                                            sendedRequest.response[3], sendedRequest.response[2] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Power T2: {} W", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_POWER_3)) {
                            float aPlusTotalnum = ByteBuffer
                                    .wrap(new byte[] { 0x00, (byte) (sendedRequest.response[1] & 0x3F),
                                            sendedRequest.response[3], sendedRequest.response[2] })
                                    .getInt();
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf(aPlusTotalnum / 100)));
                                logger.debug("Power T3: {} W", aPlusTotalnum / 100);
                            } catch (Exception ignore) {
                            }
                        }
                        if (channel.getUID().getId().equals(MercuryEnergyMeterBindingConstants.CHANNEL_CURRENT_TARIF)) {
                            try {
                                updateState(channel.getUID().getId(),
                                        DecimalType.valueOf(String.valueOf((sendedRequest.response[2] & 0xF) >> 1)));
                                logger.debug("Tarif: {} ", (sendedRequest.response[2] & 0xF) >> 1);
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }
            }
        }
    }
}
