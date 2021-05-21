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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MercuryEnergyMeterBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MercuryEnergyMeterBindingConstants {

    public static final String CHANNEL_VOLTAGE_1 = "voltage1";
    public static final String CHANNEL_VOLTAGE_2 = "voltage2";
    public static final String CHANNEL_VOLTAGE_3 = "voltage3";
    public static final String CHANNEL_CURRENT_1 = "current1";
    public static final String CHANNEL_CURRENT_2 = "current2";
    public static final String CHANNEL_CURRENT_3 = "current3";
    public static final String CHANNEL_ENERGY_ACTIVE_TOTAL = "energyactivetotal";
    public static final String CHANNEL_ENERGY_1 = "energyactive1";
    public static final String CHANNEL_ENERGY_2 = "energyactive2";
    public static final String CHANNEL_ENERGY_3 = "energyactive3";
    public static final String CHANNEL_POWER_ACTIVE_TOTAL = "poweractivetotal";
    public static final String CHANNEL_POWER_1 = "poweractive1";
    public static final String CHANNEL_POWER_2 = "poweractive2";
    public static final String CHANNEL_POWER_3 = "poweractive3";

    private static final String BINDING_ID = "mercuryenergymeter";

    // List of all Thing Type UIDs
    public static final ThingTypeUID RS485_BRIDGE = new ThingTypeUID(BINDING_ID, "rs485");
    public static final ThingTypeUID MERCURY_POWERMETER_THING = new ThingTypeUID(BINDING_ID, "energymeter203td");

    // List of all Channel ids
}
