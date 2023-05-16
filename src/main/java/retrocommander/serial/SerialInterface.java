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
    public final int resendTimeout=10000;
    public final int resendAttempts=3;
    private final SerialPort port;
    private byte sendBitCount;
    private byte receiveBitCount;
    private boolean startSendBitCount;
    private boolean startReceiveBitCount;

    public SerialInterface(String serialPortName, int stopBits, int baudrate, int parity, int flowControl) throws SerialPortInvalidPortException {
        port=SerialPort.getCommPort(serialPortName);
        this.stopBits=stopBits;
        this.baudrate=baudrate;
        this.parity=parity;
        this.flowControl=flowControl;
    }
    public void open() throws SerialPortIOException {
        port.setComPortParameters(baudrate,8,stopBits,parity,false);
        port.setFlowControl(flowControl);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,0,0);
        if (!port.openPort()) throw new SerialPortIOException("Error opening serial device");
        startSendBitCount=false;
        startReceiveBitCount=false;
        sendBitCount=0;
        port.setDTR();
    }
    public void close() {
        port.clearDTR();
        port.closePort();
    }

    public Packet getPacket(boolean timeout) throws SerialPortIOException {
        port.setRTS();
        byte[] buffer = new byte[1];
        byte checksum;
        boolean acknowledge;
        byte bitCount;
        byte command;
        try {
            while (true) {
                if (timeout) {
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,resendTimeout,0);
                } else {
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
                }
                port.flushIOBuffers();
                if (port.readBytes(buffer, 1) < 0) {
                    throw new SerialPortIOException("Timeout error");
                }
                System.out.println("Received a byte from the serial line");
                if (buffer[0] != Packet.startByte) continue;

                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, resendTimeout, 0);

                if (port.readBytes(buffer, 1) <= 0) continue;
                acknowledge = (buffer[0] & Packet.acknowledgeBitMask) != 0;
                bitCount= (byte) (buffer[0] & Packet.packetCountMask);

                byte[] inputData = null;
                if ((buffer[0] & Packet.dimensionMask)!=0) inputData=new byte[buffer[0] & Packet.dimensionMask];

                if (port.readBytes(buffer, 1) <= 0) continue;
                command = buffer[0];

                if (port.readBytes(buffer, 1) <= 0) continue;
                checksum = buffer[0];

                if (inputData != null) {
                    int dataNumber;
                    for (dataNumber = 0; dataNumber < inputData.length; dataNumber++) {
                        if (port.readBytes(inputData, 1, dataNumber) <= 0) break;
                    }
                    if (dataNumber < inputData.length) continue;
                }

                if (port.readBytes(buffer, 1) <= 0) continue;
                if (buffer[0] != Packet.stopByte) continue;

                Packet receivedPacket = new Packet(acknowledge, command, inputData);
                if (!receivedPacket.verifyChecksum(checksum,bitCount!=0)) continue;
                System.out.println("The received data is a valid packet !");
                if (!acknowledge) {
                    try {
                        sendPacket(new Packet(true));

                    } catch (SerialPortIOException e) {
                        System.out.println("Error during ACK sending: "+e.getMessage());
                    }
                }
                if (!startReceiveBitCount) {
                    receiveBitCount=bitCount;
                    startReceiveBitCount=true;
                    port.clearRTS();
                    System.out.println("---> Packet Received from master: "+receivedPacket);
                    return receivedPacket;
                } else {
                    if (receiveBitCount!=bitCount) {
                        receiveBitCount=bitCount;
                        port.clearRTS();
                        System.out.println("---> Packet Received from master: "+receivedPacket);
                        return receivedPacket;
                    }
                }
                System.out.println("This packet is already received");



            }
        } catch (SerialPortIOException e) {
            port.clearRTS();
            throw e;
        }
    }
    public void sendPacket(Packet p) throws SerialPortIOException {
        if (!startSendBitCount) {
            startSendBitCount = true;
            sendBitCount=0x00;
        }
        byte[] buffer=new byte[5];
        buffer[0]=Packet.startByte;
        buffer[4]=Packet.stopByte;
        byte[] outputData=p.getData();
        if (outputData!=null) {
            buffer[1]= (byte) (buffer[1] | (byte) (outputData.length & Packet.dimensionMask));
        }
        if (p.isAcknowledge()) buffer[1]=(byte) (buffer[1] | Packet.acknowledgeBitMask);
        buffer[1]=(byte)(buffer[1]|sendBitCount);
        buffer[2]=p.getCommand();
        buffer[3]=p.getChecksum(sendBitCount!=0);
        boolean directive=false;
        if (!p.isAcknowledge()) {
            for (int i = 0; i < resendAttempts; i++) {
                port.writeBytes(buffer, 5);

                if (outputData != null && outputData.length!=0) {
                    port.writeBytes(outputData, outputData.length);
                }

                port.writeBytes(buffer, 1, 4);
                System.out.println("Packet sent to serial line");
                try {
                    System.out.println("Waiting an ACK from the master");
                    Packet temp=getPacket(true);
                    if (temp.isAcknowledge()) {
                        directive = true;
                        break;
                    }
                } catch (SerialPortIOException e) {
                    if (!e.getMessage().equals("Timeout error")) {
                        throw e;
                    }
                }
            }
            if (!directive) {
                System.out.println("All resend attempts comsumed");
                throw new SerialPortIOException("All resend attempts consumed");
            }
            System.out.println("---> packet sent to master: "+p);
        } else {
            port.writeBytes(buffer, 5);
            System.out.println("ACK sent to master");
        }
        sendBitCount = (byte) ((byte) (sendBitCount ^ 0xff) & Packet.packetCountMask);


    }
    public void resetConnection() {
        startSendBitCount=false;
        startReceiveBitCount=false;
        sendBitCount=0;
    }

}
