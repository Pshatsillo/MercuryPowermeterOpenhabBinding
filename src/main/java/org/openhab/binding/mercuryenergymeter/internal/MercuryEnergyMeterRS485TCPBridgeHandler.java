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
import static org.openhab.binding.mercuryenergymeter.discovery.MercuryEnergyMeterDiscoveryService.mercuryEnergyMeterRS485TCPBridgeHandlerList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MercuryEnergyMeterRS485TCPBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class MercuryEnergyMeterRS485TCPBridgeHandler extends BaseBridgeHandler
        implements MercuryEnergyMeterRS485Interface {

    private final Logger logger = LoggerFactory.getLogger(MercuryEnergyMeterRS485TCPBridgeHandler.class);
    private @Nullable Socket socket;
    private @Nullable OutputStream writer;
    private @Nullable InputStream reader;

    private @Nullable Thread senderThread;
    private @Nullable Thread readerThread;

    private final BlockingQueue<MercuryEnergyMeterPooler> sendQueue = new LinkedBlockingQueue<>();
    @Nullable
    MercuryEnergyMeterPooler sendedRequest = null;

    private @Nullable ScheduledFuture<?> pollingTask;

    private List<MercuryEnergyMeter203tdHandler> mercuryEnergyMeter203tdHandlerList = new ArrayList<>();

    public MercuryEnergyMeterRS485TCPBridgeHandler(Bridge thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        if (config.host.isEmpty() || config.port.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "IP address and port must be set!");
            return;
        } else {
            updateStatus(ThingStatus.UNKNOWN);
        }
        scheduler.execute(this::connect);
    }

    private synchronized void connect() {
        MercuryEnergyMeterConfiguration config = getConfigAs(MercuryEnergyMeterConfiguration.class);
        logger.debug("Opening connection to rs485 TCP host {} on port {}", config.host, config.port);

        try {
            Socket socket = new Socket(config.host, Integer.parseInt(config.port));
            OutputStream outputStream = this.writer;
            InputStream inputStream = this.reader;
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            this.writer = socket.getOutputStream();
            this.reader = socket.getInputStream();
            this.socket = socket;
        } catch (UnknownHostException | IllegalArgumentException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "");
            return;
        } catch (InterruptedIOException e) {
            logger.debug("Interrupted while establishing connection");
            Thread.currentThread().interrupt();
            return;
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "");
            logger.debug("Error opening connection: {}", e.getMessage());
            disconnect();
            // scheduleConnectRetry(reconnectIntervalMinutes);
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
        mercuryEnergyMeterRS485TCPBridgeHandlerList.add(this);
    }

    // private boolean isConnected() {
    // return serialPort != null && inputStream != null && outputStream != null;
    // }

    private synchronized void disconnect() {
        logger.debug("disconnecting...");
        mercuryEnergyMeterRS485TCPBridgeHandlerList.remove(this);
        Thread localSenderThread = this.senderThread;
        if (localSenderThread != null && localSenderThread.isAlive()) {
            localSenderThread.interrupt();
        }

        Thread localReaderThread = this.readerThread;
        if (localReaderThread != null && localReaderThread.isAlive()) {
            localReaderThread.interrupt();
        }
        Socket localSocket = this.socket;
        if (localSocket != null) {
            try {
                localSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing socket: {}", e.getMessage());
            }
            this.socket = null;
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

    // public byte[] sendPacket(int[] data, int answerLenght, int password) {
    // byte[] answer = new byte[answerLenght];
    // String pwdConv = Integer.toString(password);
    // if (pwdConv.length() == 6) {
    // int[] pswd = new int[pwdConv.length()];
    // for (int i = 0; i < pwdConv.length(); i++) {
    // pswd[i] = Integer.parseInt(String.valueOf(pwdConv.charAt(i)));
    // }
    // // int[] getpass = new int[] { 0x00, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02 };
    // int[] getpass = new int[] { 0x00, 0x01, 0x01, pswd[0], pswd[1], pswd[2], pswd[3], pswd[4], pswd[5] };
    // byte[] pwdanswer = send(getpass, 4);
    // if (pwdanswer[1] == 0) {
    // answer = send(data, answerLenght);
    // }
    // }
    // return answer;
    // }

    // private byte[] send(int[] data, int answerLenght) {
    // MercuryEnergyMeterCRC16Modbus crc = new MercuryEnergyMeterCRC16Modbus();
    // for (int d : data) {
    // crc.update(d);
    // }
    // byte[] byteStr = new byte[2];
    // byteStr[0] = (byte) ((crc.getValue() & 0x000000ff));
    // byteStr[1] = (byte) ((crc.getValue() & 0x0000ff00) >>> 8);
    // byte[] reqestString = new byte[data.length + 2];
    // for (int i = 0; i < data.length; i++) {
    // reqestString[i] = (byte) data[i];
    // }
    // reqestString[reqestString.length - 2] = byteStr[0];
    // reqestString[reqestString.length - 1] = byteStr[1];
    // StringBuilder sb = new StringBuilder(reqestString.length * 2);
    // for (byte b : reqestString) {
    // sb.append(String.format("%02X ", b));
    // }
    // logger.debug(" send: {}", sb);
    //
    // try {
    // OutputStream out = outputStream;
    // if (out != null) {
    // out.write(reqestString);
    // out.flush();
    // Thread.sleep(200);
    // }
    // } catch (IOException | InterruptedException ignored) {
    // }
    //
    // byte[] frame = new byte[answerLenght];
    // InputStream in = inputStream;
    // if (in != null) {
    // try {
    // while (in.available() > 0) {
    // in.read(frame);
    // }
    // StringBuilder sbl = new StringBuilder(frame.length * 2);
    // for (byte b : frame) {
    // sbl.append(String.format("%02X ", b));
    // }
    // logger.debug("receive: {}", sbl);
    // } catch (IOException e1) {
    // logger.debug("Error reading from serial port: {}", e1.getMessage(), e1);
    // }
    // }
    // return frame;
    // }

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
            logger.debug("I/O error while reading from socket: {}", e.getMessage());
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
