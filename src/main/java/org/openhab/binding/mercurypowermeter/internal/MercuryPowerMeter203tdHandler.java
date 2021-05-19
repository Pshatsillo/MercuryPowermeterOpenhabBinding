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
package org.openhab.binding.mercurypowermeter.internal;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
 * The {@link MercuryPowerMeter203tdHandler} is responsible for creating things and thing
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MercuryPowerMeter203tdHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(MercuryPowerMeter203tdHandler.class);

    private @Nullable ScheduledFuture<?> pollingTask;
    int poll;
    int pass;
    int serno;
    @Nullable
    MercuryPowerMeterRS485BridgeHandler bridgeHandler;

    public MercuryPowerMeter203tdHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        MercuryPowerMeterConfiguration config = getConfigAs(MercuryPowerMeterConfiguration.class);
        bridgeHandler = getBridgeHandler();
        pass = config.userpassword;
        poll = config.pollPeriod;
        int[] data = new int[] { 0x00, 0x08, 0x05 }; // Getting network address
        serno = bridgeHandler.sendPacket(data, 4, pass)[2];
        while (serno == 0) {
        }
        if (poll > 0) {
            pollingTask = scheduler.scheduleWithFixedDelay(this::poll, 0, poll, TimeUnit.SECONDS);
        } else {
            pollingTask = null;
        }
        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Network address is " + serno);
    }

    private @Nullable MercuryPowerMeterRS485BridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            if (bridge.getHandler() instanceof MercuryPowerMeterRS485BridgeHandler) {
                return (MercuryPowerMeterRS485BridgeHandler) bridge.getHandler();
            }
        }
        return null;
    }

    private void poll() {
        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID().getId())) {
                if (channel.getUID().getId().equals(MercuryPowerMeterBindingConstants.CHANNEL_VOLTAGE)) {
                    logger.debug("voltage channel");
                }
            }
        }
    }

    public void updateChannel() {
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
