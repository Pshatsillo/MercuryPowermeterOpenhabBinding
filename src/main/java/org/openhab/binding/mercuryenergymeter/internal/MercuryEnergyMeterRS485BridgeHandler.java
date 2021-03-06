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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercuryEnergyMeterRS485BridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MercuryEnergyMeterRS485BridgeHandler extends BaseBridgeHandler implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(MercuryEnergyMeterRS485BridgeHandler.class);
    private @Nullable SerialPort serialPort;
    private @Nullable InputStream inputStream;
    private @Nullable OutputStream outputStream;
    private final SerialPortManager serialPortManager;

    private @Nullable ScheduledFuture<?> pollingTask;

    public MercuryEnergyMeterRS485BridgeHandler(Bridge thing, final SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        if (config.serialPort.length() < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port must be set!");
            return;
        } else {
            SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
            if (portId == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "Port " + config.serialPort + " is unknown!");
                serialPort = null;
            } else {
                updateStatus(ThingStatus.UNKNOWN);
            }

        }
        scheduler.execute(this::connect);
    }

    private synchronized void connect() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Port " + config.serialPort + " is unknown!");
            serialPort = null;
            disconnect();
        } else if (!isConnected()) {
            try {
                SerialPort serial = portId.open(getThing().getUID().toString(), 2000);
                serial.setSerialPortParams(config.portSpeed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    inputStream = null;
                }
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    outputStream = null;
                }

                inputStream = serial.getInputStream();
                outputStream = serial.getOutputStream();
                serialPort = serial;

                int[] data = new int[] { 0x00, 0x00 }; // Test connection
                if (sendPacket(data, 4, 111111)[1] == 0x00) {
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Powermeter does not answer");
                }

            } catch (final IOException ex) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "I/O error!");
                logger.error("{}", ex.getMessage());
            } catch (PortInUseException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "Port is in use!");
                logger.error("{}", e.getMessage());
            } catch (UnsupportedCommOperationException e) {
                logger.error("{}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    private boolean isConnected() {
        return serialPort != null && inputStream != null && outputStream != null;
    }

    private void disconnect() {
        logger.debug("disconnecting port...");
        if (thing.getStatus() != ThingStatus.REMOVING) {
            updateStatus(ThingStatus.OFFLINE);
        }
        synchronized (this) {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                inputStream = null;
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                outputStream = null;
            }

            SerialPort serial = serialPort;
            if (serial != null) {
                serial.close();
            }
            try {
                serialPort.close();
                serialPort = null;
                logger.debug("disconnected port");
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized void serialEvent(SerialPortEvent serialPortEvent) {
    }

    public synchronized void stopPolling() {
        logger.debug("stopping polling");
        final ScheduledFuture<?> task = pollingTask;
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
            pollingTask = null;
            logger.debug("polling stop");
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing...");
        stopPolling();
        disconnect();
        super.dispose();
    }

    public byte[] sendPacket(int[] data, int answerLenght, int password) {
        byte[] answer = new byte[answerLenght];
        String pwdConv = Integer.toString(password);
        if (pwdConv.length() == 6) {
            int[] pswd = new int[pwdConv.length()];
            for (int i = 0; i < pwdConv.length(); i++) {
                pswd[i] = Integer.parseInt(String.valueOf(pwdConv.charAt(i)));
            }
            // int[] getpass = new int[] { 0x00, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02 };
            int[] getpass = new int[] { 0x00, 0x01, 0x01, pswd[0], pswd[1], pswd[2], pswd[3], pswd[4], pswd[5] };
            byte[] pwdanswer = send(getpass, 4);
            if (pwdanswer[1] == 0) {
                answer = send(data, answerLenght);
            }
        }
        return answer;
    }

    private byte[] send(int[] data, int answerLenght) {
        MercuryEnergyMeterCRC16Modbus crc = new MercuryEnergyMeterCRC16Modbus();
        for (int d : data) {
            crc.update(d);
        }
        byte[] byteStr = new byte[2];
        byteStr[0] = (byte) ((crc.getValue() & 0x000000ff));
        byteStr[1] = (byte) ((crc.getValue() & 0x0000ff00) >>> 8);
        byte[] reqestString = new byte[data.length + 2];
        for (int i = 0; i < data.length; i++) {
            reqestString[i] = (byte) data[i];
        }
        reqestString[reqestString.length - 2] = byteStr[0];
        reqestString[reqestString.length - 1] = byteStr[1];
        StringBuilder sb = new StringBuilder(reqestString.length * 2);
        for (byte b : reqestString)
            sb.append(String.format("%02X ", b));
        logger.debug("   send: {}", sb);

        try {
            OutputStream out = outputStream;
            if (out != null) {
                out.write(reqestString);
                out.flush();
                Thread.sleep(200);
            }
        } catch (IOException | InterruptedException e) {

        }

        byte[] frame = new byte[answerLenght];
        InputStream in = inputStream;
        if (in != null) {
            try {
                while (in.available() > 0) {
                    in.read(frame);
                }
                StringBuilder sbl = new StringBuilder(frame.length * 2);
                for (byte b : frame)
                    sbl.append(String.format("%02X ", b));
                logger.debug("receive: {}", sbl);
            } catch (IOException e1) {
                logger.debug("Error reading from serial port: {}", e1.getMessage(), e1);
            }
        }
        return frame;
    }
}
