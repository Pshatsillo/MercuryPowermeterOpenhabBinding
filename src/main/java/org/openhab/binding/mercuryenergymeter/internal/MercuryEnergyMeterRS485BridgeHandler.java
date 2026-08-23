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

import static org.openhab.binding.mercuryenergymeter.discovery.MercuryEnergyMeterDiscoveryService.mercuryEnergyMeterPoolerList;
import static org.openhab.binding.mercuryenergymeter.discovery.MercuryEnergyMeterDiscoveryService.mercuryEnergyMeterRS485BridgeHandlerList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
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
public class MercuryEnergyMeterRS485BridgeHandler extends BaseBridgeHandler
        implements MercuryEnergyMeterRS485Interface {

    private final Logger logger = LoggerFactory.getLogger(MercuryEnergyMeterRS485BridgeHandler.class);
    private final SerialPortManager serialPortManager;

    private @Nullable SerialPort serialPort;
    private @Nullable OutputStream writer;
    private @Nullable InputStream reader;

    private @Nullable Thread senderThread;
    private @Nullable Thread readerThread;

    private final BlockingQueue<MercuryEnergyMeterPooler> sendQueue = new LinkedBlockingQueue<>();
    @Nullable
    MercuryEnergyMeterPooler sendedRequest = null;

    private @Nullable ScheduledFuture<?> pollingTask;

    private List<MercuryEnergyMeter203tdHandler> mercuryEnergyMeter203tdHandlerList = new ArrayList<>();

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
        if (config.serialPort.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port must be set!");
            return;
        } else {
            SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
            if (portId == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "Port " + config.serialPort + " is unknown!");
                return;
            } else {
                updateStatus(ThingStatus.UNKNOWN);
            }
        }
        scheduler.execute(this::connect);
    }

    private synchronized void connect() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        logger.debug("Opening connection to rs485 serial port {}", config.serialPort);

        SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Port " + config.serialPort + " is unknown!");
            return;
        }

        try {
            SerialPort serial = portId.open(getThing().getUID().toString(), 2000);
            serial.setSerialPortParams(config.portSpeed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            OutputStream outputStream = this.writer;
            InputStream inputStream = this.reader;
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            this.writer = serial.getOutputStream();
            this.reader = serial.getInputStream();
            this.serialPort = serial;
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Port is in use!");
            logger.debug("Error opening connection: {}", e.getMessage());
            return;
        } catch (UnsupportedCommOperationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            logger.debug("Error opening connection: {}", e.getMessage());
            return;
        } catch (InterruptedIOException e) {
            logger.debug("Interrupted while establishing connection");
            Thread.currentThread().interrupt();
            return;
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "");
            logger.debug("Error opening connection: {}", e.getMessage());
            disconnect();
            return;
        }

        Thread localReaderThread = new Thread(this::readerThreadJob, "OH-binding-" + getThing().getUID() + "-Reader");
        localReaderThread.setDaemon(true);
        localReaderThread.start();
        this.readerThread = localReaderThread;

        Thread localSenderThread = new Thread(this::senderThreadJob, "OH-binding-" + getThing().getUID() + "-Sender");
        localSenderThread.setDaemon(true);
        localSenderThread.start();
        this.senderThread = localSenderThread;

        updateStatus(ThingStatus.ONLINE);
        mercuryEnergyMeterRS485BridgeHandlerList.add(this);
    }

    private synchronized void disconnect() {
        logger.debug("disconnecting...");
        mercuryEnergyMeterRS485BridgeHandlerList.remove(this);
        Thread localSenderThread = this.senderThread;
        if (localSenderThread != null && localSenderThread.isAlive()) {
            localSenderThread.interrupt();
        }

        Thread localReaderThread = this.readerThread;
        if (localReaderThread != null && localReaderThread.isAlive()) {
            localReaderThread.interrupt();
        }
        SerialPort localSerialPort = this.serialPort;
        if (localSerialPort != null) {
            localSerialPort.close();
            this.serialPort = null;
        }
        InputStream localReader = this.reader;
        if (localReader != null) {
            try {
                localReader.close();
            } catch (IOException e) {
                logger.debug("Error closing reader: {}", e.getMessage());
            }
            this.reader = null;
        }
        OutputStream localWriter = this.writer;
        if (localWriter != null) {
            try {
                localWriter.close();
            } catch (IOException e) {
                logger.debug("Error closing writer: {}", e.getMessage());
            }
            this.writer = null;
        }
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

    private void senderThreadJob() {
        logger.debug("Sender thread started");
        try {
            while (!Thread.currentThread().isInterrupted() && writer != null) {
                sendedRequest = sendQueue.take();
                MercuryEnergyMeterPooler sendedRequest = this.sendedRequest;
                if (sendedRequest != null) {
                    StringBuilder sb = new StringBuilder(sendedRequest.request.length * 2);
                    for (byte b : sendedRequest.request) {
                        sb.append(String.format("%02X ", b));
                    }
                    logger.debug("Sender thread writing command: {}", sb);
                    try {
                        OutputStream localWriter = this.writer;
                        if (localWriter != null) {
                            localWriter.write(sendedRequest.request);
                            localWriter.flush();
                        }
                        Thread.sleep(100);
                    } catch (InterruptedIOException e) {
                        logger.debug("Interrupted while sending command");
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "");
                        break;
                    } catch (IOException e) {
                        logger.debug("Communication error, will try to reconnect. Error: {}", e.getMessage());
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                        // Requeue the command and try to reconnect
                        sendQueue.add(sendedRequest);
                        reconnect();
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            logger.debug("Sender thread exiting");
        }
    }

    private void readerThreadJob() {
        logger.debug("Reader thread started");
        byte[] frame = new byte[26];
        try {
            InputStream localReader = this.reader;
            while (!Thread.interrupted() && localReader != null) {
                while (localReader.available() > 0) {
                    MercuryEnergyMeterPooler sendedRequest = this.sendedRequest;
                    if (sendedRequest != null) {
                        var dataLenght = localReader.read(frame);
                        logger.debug("Receive {} bytes ({})", dataLenght, Arrays.toString(frame));
                        if (sendedRequest.responseLength == dataLenght) {
                            StringBuilder sb = new StringBuilder(frame.length * 2);
                            for (byte b : frame) {
                                sb.append(String.format("%02X ", b));
                            }
                            logger.debug("response {}", sb);
                            byte[] answer = new byte[dataLenght - 2];
                            System.arraycopy(frame, 0, answer, 0, dataLenght - 2);
                            byte[] crc = calcCRC(answer);
                            if (crc[0] == frame[dataLenght - 2] && crc[1] == frame[dataLenght - 1]) {
                                sendedRequest.response = answer;
                                MercuryEnergyMeter203tdHandler mercuryEnergyMeter203tdHandler = sendedRequest.mercuryEnergyMeter203tdHandler;
                                if (mercuryEnergyMeter203tdHandler != null) {
                                    if (mercuryEnergyMeter203tdHandler.serno == frame[0]) {
                                        mercuryEnergyMeter203tdHandler.response(sendedRequest);
                                    }
                                } else {
                                    mercuryEnergyMeterPoolerList.add(sendedRequest);
                                }
                            }
                        } else if (frame[1] == 0x5) {
                            logger.debug("Communication channel is locked");
                            MercuryEnergyMeter203tdHandler mercuryEnergyMeter203tdHandler = sendedRequest.mercuryEnergyMeter203tdHandler;
                            if (mercuryEnergyMeter203tdHandler != null) {
                                mercuryEnergyMeter203tdHandler.openChannel();
                            }
                        }
                        this.sendedRequest = null;
                        frame = new byte[26];
                    } else {
                        localReader.read(frame);
                        frame = new byte[26];
                    }
                }
            }
        } catch (InterruptedIOException e) {
            logger.debug("Interrupted while reading");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "");
        } catch (IOException e) {
            logger.debug("I/O error while reading from serial port: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "");
        } finally {
            logger.debug("Reader thread exiting");
        }
    }

    private void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public List<MercuryEnergyMeterPooler> getRequestsList() {
        return List.of();
    }

    @Override
    public void addRequestsList(MercuryEnergyMeterPooler pooler) {
    }

    public void sendMessage(MercuryEnergyMeterPooler data) {
        logger.debug("sending data {}", data.request);
        byte[] byteStr = calcCRC(data.request);
        byte[] requestString = new byte[data.request.length + 2];
        System.arraycopy(data.request, 0, requestString, 0, data.request.length);
        requestString[requestString.length - 2] = byteStr[0];
        requestString[requestString.length - 1] = byteStr[1];
        data.request = requestString;
        sendQueue.add(data);
    }

    @Override
    public void registerThing(MercuryEnergyMeter203tdHandler mercuryEnergyMeter203tdHandler) {
        mercuryEnergyMeter203tdHandlerList.add(mercuryEnergyMeter203tdHandler);
    }

    @Override
    public void removeThing(MercuryEnergyMeter203tdHandler mercuryEnergyMeter203tdHandler) {
        List<MercuryEnergyMeter203tdHandler> mercuryEnergyMeter203tdHandlerList = this.mercuryEnergyMeter203tdHandlerList;
        mercuryEnergyMeter203tdHandlerList.remove(mercuryEnergyMeter203tdHandler);
    }

    private byte[] calcCRC(byte[] data) {
        MercuryEnergyMeterCRC16Modbus crc = new MercuryEnergyMeterCRC16Modbus();
        for (int d : data) {
            crc.update(d);
        }
        byte[] byteStr = new byte[2];
        byteStr[0] = (byte) ((crc.getValue() & 0x000000ff));
        byteStr[1] = (byte) ((crc.getValue() & 0x0000ff00) >>> 8);
        return byteStr;
    }
}
