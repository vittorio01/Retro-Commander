package retrocommander.serial;
import com.fazecast.jSerialComm.*;
import retrocommander.retrocommander.ConsoleController;

import java.util.concurrent.ArrayBlockingQueue;


public class SerialInterface {
    /*
    The transmission interface is used for communicating with connected device. This class implements the algorithm for managing transmissions and
    receptions.
    A single packet is composed by start byte + header + command + checksum + stop byte. In particular:
    - start and stop bytes have two values predefined in prior (0xAA for start and 0xF0 for end)
    - the header contains all packet's information such as ACK bit, count bit, packet type and header dimension (max 64 bit).
        * A received packet is automatically dropped if the checksum is not valid, all pieces cannot be received in a certain time interval or start/stop byte are not valid.
        * the count bit is used for distinguish a normal packet with a retransmission. If the received packet has the count bit equal as the before it will automatically dropped.
    - the command byte is used to identify what the packet is designed for
    - the checksum is a byte obtained by a sum of all packet's byte (also start and stop bit)

    In base of the bit packet type contained in the header, are two different types of packets:
    - A slow packet requires that the device that receive the packet has to send an ACK to signal a correct reception.
      In particular, everytime a packet is sent by the sender, the receiver has to respond with an acknowledgment packet (ACK=1, length=0 and channel equal to the transmitted packet).
      If the sender doesn't receive the acknowledgment in a certain time interval, the packet has to be retransmitted (there is a retransmission number limit)
    - A fast packet does not require an ack from the other device. In fact, the device has only to send the packet and continue with the normal execution.
    Both devices can transmit packet that requires or not an ACK but is suggested to send fast packets for less important purposes.

     */

    private final int stopBits;
    private final int baudrate;
    private final int parity;
    private final int flowControl;
    public final int resendTimeout=750;
    public final int resendAttempts=5;
    private final SerialPort port;
    private boolean lineBitCount;
    private boolean validBitCount;
    private final Object lock=new Object();
    private volatile int packetByteCount;
    private volatile int packetByteDataCount;
    private byte[] packetHeader;
    private byte[] packetData;
    private boolean timeout;
    private volatile boolean receiving;
    private boolean opened;
    public SerialInterface(String serialPortName, int stopBits, int baudrate, int parity, int flowControl) throws SerialPortInvalidPortException {
        port=SerialPort.getCommPort(serialPortName);
        this.stopBits=stopBits;
        this.baudrate=baudrate;
        this.parity=parity;
        this.flowControl=flowControl;
        packetByteCount=0;
        packetByteDataCount=0;
        packetHeader=new byte[5];
        opened=false;
    }
    public void open() throws SerialPortIOException {
        port.setComPortParameters(baudrate,8,stopBits,parity,false);
        port.setFlowControl(flowControl);
        if (!port.openPort()) throw new SerialPortIOException("Serial device not available");
        lineBitCount=false;
        validBitCount=false;
        port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
                receiving=true;
                port.clearRTS();
                while (port.bytesAvailable()>0 && (packetByteCount!=5)) {

                    synchronized (lock) {
                        switch (packetByteCount) {
                            case 0:
                                port.readBytes(packetHeader, 1);
                                packetData = null;
                                packetByteDataCount = 0;
                                if (packetHeader[0] == Packet.startByte) {
                                    packetByteCount = 1;
                                }
                                break;
                            case 1:
                                port.readBytes(packetHeader, 1, 1);
                                if (Packet.decodeLength(packetHeader[1]) != 0) {
                                    packetData = new byte[Packet.decodeLength((packetHeader[1]))];
                                }
                                packetByteCount = 2;
                                break;
                            case 2:
                                port.readBytes(packetHeader, 1, 2);
                                packetByteCount = 3;
                                break;
                            case 3:
                                port.readBytes(packetHeader, 1, 3);
                                packetByteCount = 4;
                                break;
                            case 4:
                                if (packetData == null) {
                                    port.readBytes(packetHeader, 1, 4);
                                    if (packetHeader[4] == Packet.stopByte) {
                                        packetByteCount = 5;
                                    } else {
                                        packetByteCount = 0;

                                    }
                                } else {
                                    if (packetByteDataCount < packetData.length) {
                                        port.readBytes(packetData, 1, packetByteDataCount);
                                        packetByteDataCount = packetByteDataCount + 1;
                                    } else {
                                        port.readBytes(packetHeader, 1, 4);
                                        if (packetHeader[4] == Packet.stopByte) {
                                            packetByteCount = 5;
                                        } else {
                                            packetByteCount = 0;

                                        }
                                        break;
                                    }
                                }
                                break;
                            case 5:
                                break;
                            default:
                                packetByteCount = 0;
                        }
                        lock.notify();
                    }
                }
                port.setRTS();
                receiving=false;

            }
        });
        port.setDTR();
        opened=true;
    }
    public void close() {
        synchronized (lock) {
            lock.notifyAll();
            port.clearDTR();
            port.closePort();
            opened=false;
        }

    }

    public Packet getPacket(boolean ReceiveTimeout) throws SerialPortIOException, InterruptedException {
        timeout=true;
        int previusByteDataCount=-1;
        Packet receivedPacket;
        while (true) {
            synchronized (lock) {
                if (timeout) {
                    while (receiving) Thread.onSpinWait();
                    packetByteDataCount=0;
                    packetByteCount=0;
                    previusByteDataCount=-1;
                    timeout=false;
                    port.setRTS();
                }
                switch (packetByteCount) {
                    case 0:

                        if (ReceiveTimeout) {
                            lock.wait(resendTimeout);
                        } else {
                            lock.wait();
                        }
                        if (packetByteCount == 0 && opened==true) {
                            timeout = true;
                            port.clearRTS();
                            throw new SerialPortIOException("Timeout error");
                        }


                        continue;
                    case 1:
                        lock.wait(resendTimeout);
                        if (packetByteCount == 1) timeout = true;

                        continue;
                    case 2:
                        lock.wait(resendTimeout);
                        if (packetByteCount == 2) timeout = true;
                        continue;
                    case 3:
                        lock.wait(resendTimeout);
                        if (packetByteCount == 3) timeout = true;
                        continue;
                    case 4:
                        lock.wait(resendTimeout);
                        if (packetByteCount==4) {
                            if (packetByteDataCount==0) {
                                timeout=true;
                            } else {
                                if (previusByteDataCount>=packetByteDataCount) {
                                    timeout=true;
                                } else {
                                    previusByteDataCount++;
                                }
                            }
                        }
                        continue;
                    case 5:
                        timeout=true;
                        port.clearRTS();

                        System.out.println("Received packet from the serial line!");
                        receivedPacket=new Packet(packetHeader[1],packetHeader[2],packetData);
                        if (!receivedPacket.verifyChecksum(packetHeader[3])) {
                            System.out.println("The received packet is not valid");
                            continue;
                        }
                        System.out.println("The received packet is valid");
                        if (!receivedPacket.isAcknowledge()) {
                            try {
                                if (!validBitCount || (packetHeader[2] == ConsoleController.control_resetConnection)) {
                                    lineBitCount = receivedPacket.getCount();
                                    validBitCount = true;
                                } else {
                                    if (lineBitCount != receivedPacket.getCount()) {
                                        lineBitCount = !lineBitCount;
                                        sendPacket(new Packet(true));
                                        System.out.println("Count bit not valid");
                                        lineBitCount = !lineBitCount;
                                        continue;
                                    }
                                }
                                if (receivedPacket.getType() == PacketType.slow) {
                                    sendPacket(new Packet(true));
                                }
                                lineBitCount = !lineBitCount;
                                port.clearRTS();
                                System.out.println("---> Packet Received from master: " + receivedPacket);
                                return receivedPacket;
                            } catch (SerialPortIOException e) {
                                System.out.println("Error during ACK sending: " + e.getMessage());
                            }
                        } else {
                            if (!validBitCount) {
                                lineBitCount = receivedPacket.getCount();
                                validBitCount = true;
                            } else {
                                if (lineBitCount != receivedPacket.getCount()) {
                                    System.out.println("This ACK is for the previous packet");
                                    continue;
                                }
                            }
                            port.clearRTS();
                            System.out.println("ACK received from master");
                            return receivedPacket;
                        }
                }

            }
        }
    }

    public void sendPacket(Packet p) throws SerialPortIOException {
        port.flushIOBuffers();
        if (!validBitCount) {
            validBitCount = true;
            lineBitCount=false;
        }
        byte[] buffer=new byte[5];
        p.setCount(lineBitCount);
        buffer[0]=Packet.startByte;
        buffer[1]=p.composeHeader();
        buffer[2]=p.getCommand();
        buffer[3]=p.getChecksum();
        buffer[4]=Packet.stopByte;
        byte[] outputData=p.getData();
        boolean directive=false;
        if (!p.isAcknowledge()) {
            for (int i = 0; i < resendAttempts; i++) {
                port.writeBytes(buffer, 4);

                if (outputData != null && outputData.length!=0) {
                    port.writeBytes(outputData, outputData.length);
                }

                port.writeBytes(buffer, 1, 4);
                System.out.println("Packet sent to serial line");
                try {
                    if (p.getType()==PacketType.slow) {
                        System.out.println("Waiting an ACK from the master");
                        Packet temp = getPacket(true);
                        if (temp.isAcknowledge()) {
                            directive = true;
                            lineBitCount = !lineBitCount;
                            break;
                        }
                    } else {
                        directive = true;
                        lineBitCount = !lineBitCount;
                        break;
                    }
                } catch (SerialPortIOException e) {
                    if (!e.getMessage().equals("Timeout error")) {
                        throw e;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!directive) {
                System.out.println("All resend attempts comsumed");
                throw new SerialPortIOException("All resend attempts consumed");
            }
            System.out.println("---> packet sent to master: "+p);
        } else {
            port.writeBytes(buffer, 5);
            System.out.println("ACK sent to master "+lineBitCount);
        }
    }
}