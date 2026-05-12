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
package org.openhab.binding.mercuryenergymeter.discovery;

import static org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterBindingConstants.MERCURY_POWERMETER_THING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterPooler;
import org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterRS485BridgeHandler;
import org.openhab.binding.mercuryenergymeter.internal.MercuryEnergyMeterRS485TCPBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for mercurypowermeter
 *
 * @author Petr Shatsillo - Initial contribution
 *
 */
@Component(service = DiscoveryService.class, configurationPid = "discovery.mercurypowermeter")
@NonNullByDefault
public class MercuryEnergyMeterDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(MercuryEnergyMeterDiscoveryService.class);
    public static @Nullable List<MercuryEnergyMeterRS485BridgeHandler> mercuryEnergyMeterRS485BridgeHandlerList = new ArrayList<>();
    public static List<MercuryEnergyMeterRS485TCPBridgeHandler> mercuryEnergyMeterRS485TCPBridgeHandlerList = new ArrayList<>();
    public static List<MercuryEnergyMeterPooler> mercuryEnergyMeterPoolerList = new ArrayList<>();

    public MercuryEnergyMeterDiscoveryService() {
        super(Collections.singleton(MERCURY_POWERMETER_THING), 30, false);
    }

    @Override
    protected void startScan() {
        mercuryEnergyMeterPoolerList.clear();
        if (!mercuryEnergyMeterRS485TCPBridgeHandlerList.isEmpty()) {
            mercuryEnergyMeterRS485TCPBridgeHandlerList.forEach(tcpbridge -> {
                for (int i = 1; i < 241; i++) {
                    MercuryEnergyMeterPooler pooler = new MercuryEnergyMeterPooler();
                    pooler.request = new byte[] { (byte) i, 0x00 };
                    tcpbridge.sendMessage(pooler);
                }
            });
        }
    }

    @Override
    protected synchronized void stopScan() {
        if (!mercuryEnergyMeterPoolerList.isEmpty()) {
            mercuryEnergyMeterPoolerList.forEach(pooler -> {

                ThingUID thingUID = new ThingUID(MERCURY_POWERMETER_THING,
                        "energyMeter_" + String.format("%d", pooler.response[0] & 0xFF));
                DiscoveryResult resultS = DiscoveryResultBuilder.create(thingUID)
                        .withLabel("Mercury net address " + String.format("%d", pooler.response[0] & 0xFF))
                        .withProperty("netaddress", pooler.response[0] & 0xFF).build();
                thingDiscovered(resultS);
            });

        }
        super.stopScan();
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("startBackgroundDiscovery");
        super.startBackgroundDiscovery();
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("stopBackgroundDiscovery");
        super.stopBackgroundDiscovery();
    }
}
