package retrocommander.serial;

import java.util.Arrays;

public class Packet {
    /*
    A serial packet is composed with multiple bytes with different functions:
    -   start marker -> is a byte with a predefined value. It's used for understanding if there is an incoming transmission from the
                        receive line.
    -   header -> one byte which contains four different information:
                    *   ACK -> a bit setted if the packet is an acknowledge
                    *   channel -> two bits that identifies che destination channel
                    *   count -> a bit used for distinguish a packet from a retransmission
                    *   lenght -> dimension of the body of the packet
    -   checksum -> a byte used for verify the integrity of the packet.
    -   body -> it can assume different dimension (from 0 to 16 bytes)
    -   end marker -> is a byte with a predefined value used for identifying the end of the transmitted packet



    */
    public static final byte startByte= (byte) 0b10101010;
    public static final byte stopByte= (byte) 0b11110000;
    public static final byte acknowledgeBitMask=(byte) 0b10000000;
    public static final byte packetCountMask=(byte) 0b01000000;
    public static final byte dimensionMask=(byte) 0b00111111;
    private boolean acknowledge;
    private final byte command;
    private byte[] data;
    public Packet(boolean acknowledge) {
        this.acknowledge=acknowledge;
        data=null;
        command=0x00;
    }
    public Packet(boolean acknowledge, byte command, byte[] data) {
        this.acknowledge=acknowledge;
        this.command=command;
        if (!acknowledge) {
            this.data = data;
        } else {
            this.data=null;
        }
    }
    public byte getCommand() {
        return command;
    }
    public byte[] getData() {
        return data;
    }
    public boolean verifyChecksum(byte checksum, boolean packetCount) {
        return getChecksum(packetCount) == checksum;
    }
    public byte getChecksum(boolean packetCount) {
        byte sum=0;
        if (acknowledge) {
            sum=(byte)(sum + Packet.acknowledgeBitMask);
        }
        if (packetCount) {
            sum=(byte)(sum + Packet.packetCountMask);
        }
        if (data!=null) {
            sum=(byte)(sum + (data.length & dimensionMask));
        }
        sum=(byte)(sum+startByte+stopByte+command);
        if (data==null) return sum;
        for (byte b:data) {
            sum = (byte) ((sum+b)& 0xff);
        }
        return sum;
    }
    public boolean isAcknowledge() {
        return acknowledge;
    }

    @Override
    public String toString() {
        return "{" +
                "acknowledge=" + acknowledge +
                ", command=" + String.format("%02X",command) +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
