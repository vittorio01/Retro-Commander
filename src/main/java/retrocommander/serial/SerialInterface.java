package retrocommander.serial;
import com.fazecast.jSerialComm.*;


public class SerialInterface {
    /*
    The transmission interface is used for communicating with connected device. This class implements the algorithm for managing transmissions and
    receptions:
    -   Everytime a packet is sent by the sender, the receiver has to respond with an acknowledgment packet (ACK=1, length=0 and channel equal to the transmitted packet).
        If the sender doesn't receive the acknowledgment in a certain time interval, the packet has to be retransmitted (there is a retransmission number limit)
    -   A received packet is automatically dripped if the checksum is not valid, all pieces cannot be received in a certain time interval or start/stop byte are not valid
    -   the count bit is used for distinguish a normal packet with a retransmission. If the received packet has the count bit equal as the before it will automatically dropped.
     */

    private final int stopBits;
    private final int baudrate;
    private final int parity;
    private final int flowControl;
    public final int resendTimeout=0;
    public final int resendAttempts=3;
    private final SerialPort port;
    private byte sendBitCount;
    private byte receiveBitCount;
    private boolean startSendBitCount;
    private boolean startReceiveBitCount;

    public SerialInterface(String serialPortName, int stopBits, int baudrate, int parity, int flowControl) throws SerialPortIOException, SerialPortInvalidPortException {
        port=SerialPort.getCommPort(serialPortName);
        this.stopBits=stopBits;
        this.baudrate=baudrate;
        this.parity=parity;
        this.flowControl=flowControl;
    }
    public void open() throws SerialPortIOException {

        port.setComPortParameters(baudrate,8,stopBits,parity,false);
        port.setFlowControl(flowControl);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING,0,0);
        if (!port.openPort()) throw new SerialPortIOException("Error opening serial device");
        startSendBitCount=false;
        startReceiveBitCount=false;
        sendBitCount=0;
        if (flowControl==(SerialPort.FLOW_CONTROL_DTR_ENABLED | SerialPort.FLOW_CONTROL_DSR_ENABLED)) {
            port.setDTR();
        }
    }
    public void close() {
        if (flowControl==(SerialPort.FLOW_CONTROL_DTR_ENABLED | SerialPort.FLOW_CONTROL_DSR_ENABLED)) {
            port.clearDTR();
        }
        port.closePort();
    }

    public Packet getPacket() throws SerialPortIOException {
        if (flowControl==(SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED)) {
            port.setRTS();
        }
        int timeoutBackup = port.getReadTimeout();
        byte[] buffer = new byte[1];
        channel packetChannel = channel.undefined;
        byte checksum;
        boolean acknowledge;
        byte bitCount;
        try {
            while (true) {
                if (port.readBytes(buffer, 1) <= 0) throw new SerialPortIOException("Timeout error");
                if (buffer[0] != Packet.startByte) continue;
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, resendTimeout, 0);
                if (port.readBytes(buffer, 1) <= 0) continue;
                acknowledge = (buffer[0] & Packet.acknowledgeBitMask) != 0;
                switch (buffer[0] & Packet.channelMask) {
                    case Packet.channelControlIdentifier -> packetChannel = channel.control;
                    case Packet.channelDiskIdentifier -> packetChannel = channel.disk;
                    case Packet.channelTerminalIdentifier -> packetChannel = channel.terminal;
                }
                bitCount= (byte) (buffer[0] & Packet.packetCountMask);
                byte[] inputData = new byte[buffer[0] & Packet.dimensionMask];
                if (port.readBytes(buffer, 1) <= 0) continue;
                checksum = buffer[0];
                if (inputData.length != 0) {
                    for (int i = 0; i < inputData.length; i++) {
                        if (port.readBytes(inputData, 1, i) <= 0) continue;
                    }
                }
                if (port.readBytes(buffer, 1) <= 0) continue;
                if (buffer[0] != Packet.stopByte) continue;
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutBackup, 0);
                Packet receivedPacket = new Packet(acknowledge, packetChannel, inputData);
                if (!acknowledge) {
                    if (receivedPacket.verifyChecksum(checksum)) {
                        sendPacket(new Packet(true,packetChannel));
                    } else {
                        continue;
                    }
                }
                if (!startReceiveBitCount) {
                    receiveBitCount=bitCount;
                    startReceiveBitCount=true;
                    if (flowControl == (SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED)) {
                        port.clearRTS();
                    }
                    return receivedPacket;
                } else {
                    if (receiveBitCount!=bitCount) {
                        receiveBitCount=bitCount;
                        if (flowControl == (SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED)) {
                            port.clearRTS();
                        }
                        return receivedPacket;
                    }
                }

            }
        } catch (SerialPortIOException e) {
            if (flowControl==(SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED)) {
                port.clearRTS();
            }
            throw e;
        }
    }
    public void sendPacket(Packet p) throws SerialPortIOException {
        int timeoutBackup=port.getReadTimeout();
        byte[] buffer=new byte[4];
        buffer[0]=Packet.startByte;
        buffer[3]=Packet.stopByte;
        switch (p.getChannel()) {
            case control -> buffer[1]=Packet.channelControlIdentifier;
            case disk -> buffer[1]=Packet.channelDiskIdentifier;
            case terminal -> buffer[1]=Packet.channelTerminalIdentifier;
        }
        byte[] outputData=p.getData();
        if (outputData!=null) {
            buffer[1]= (byte) (buffer[1] | (byte) (outputData.length & Packet.dimensionMask));
        }
        if (p.isAcknowledge()) buffer[1]=(byte) (buffer[1] | Packet.acknowledgeBitMask);
        buffer[1]=(byte)(buffer[1]|sendBitCount);
        buffer[2]=p.getChecksum();
        boolean directive=false;
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,resendTimeout,0);
        if (!p.isAcknowledge()) {
            for (int i = 0; i < resendAttempts; i++) {
                port.writeBytes(buffer, 3);
                if (outputData != null && outputData.length!=0) {
                    port.writeBytes(outputData, outputData.length);
                }
                port.writeBytes(buffer, 1, 3);
                try {
                    Packet temp=getPacket();
                    if (temp.isAcknowledge() && temp.getChannel()==p.getChannel()) {
                        directive = true;
                        break;
                    }
                } catch (SerialPortIOException e) {
                    if (!e.getMessage().equals("Timeout error")) {
                        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutBackup, 0);
                        throw e;
                    }
                }
            }
            if (!directive) {
                throw new SerialPortIOException("All resend attempts consumed");
            }
        } else {
            port.writeBytes(buffer, 4);
        }
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutBackup, 0);
        if (!startSendBitCount) {
            startSendBitCount=true;
        }
        sendBitCount= (byte) ((byte)(sendBitCount^0xff) & Packet.packetCountMask);
    }

}
