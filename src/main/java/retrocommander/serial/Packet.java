package retrocommander.serial;

import java.util.Arrays;

public class Packet {
    /*
    A serial packet is composed with multiple bytes with different functions:
    -   start marker -> is a byte with a predefined value. It's used for understanding if there is an incoming transmission from the
                        receive line.
    -   header -> one byte which contains four different information:
                    *   ACK -> a bit setted if the packet is an acknowledge
                    *   count -> a bit used for distinguish a packet from a retransmission
                    *   type -> a bit that indicates a slow or a fast transmission
                    *   lenght -> dimension of the body of the packet
    -   checksum -> a byte used for verify the integrity of the packet.
    -   body -> it can assume different dimension (from 0 to 16 bytes)
    -   end marker -> is a byte with a predefined value used for identifying the end of the transmitted packet



    */
    public static final byte startByte= (byte) 0b10101010;
    public static final byte stopByte= (byte) 0b11110000;
    private static final byte acknowledgeBitMask=(byte) 0b10000000;
    private static final byte packetCountMask=(byte) 0b01000000;
    private static final byte packetTypeMask=(byte) 0b00100000;
    private static final byte dimensionMask=(byte) 0b00011111;
    public static final int maxPacketDimension=31;
    private boolean acknowledge;
    private final byte command;
    private byte[] data;
    private PacketType type;
    private boolean count;
    public Packet(boolean acknowledge) {
        this.acknowledge=acknowledge;
        data=null;
        command=0x00;
        type=PacketType.slow;
    }
    public Packet(byte header, byte command, byte[] data) {
        this.acknowledge= (header & acknowledgeBitMask) != 0;
        if ((header & packetTypeMask)!= 0) {
            this.type=PacketType.slow;
        } else {
            this.type=PacketType.fast;
        }
        this.count= (header & packetCountMask) != 0;
        this.data=data;
        this.command=command;

    }
    public Packet(boolean acknowledge, byte command, byte[] data, PacketType type) {

        this.acknowledge=acknowledge;
        this.command=command;
        if (!acknowledge) {
            this.data = data;
            this.type=type;
        } else {
            this.data=null;
            this.type=PacketType.slow;
        }
        count=false;
    }

    public void setCount(boolean count) {
        this.count = count;
    }
    public boolean getCount() {
        return count;
    }
    public PacketType getType() {
        return type;
    }

    public byte getCommand() {
        return command;
    }
    public byte[] getData() {
        return data;
    }
    public boolean verifyChecksum(byte checksum) {
        return getChecksum() == checksum;
    }
    public byte getChecksum() {
        byte sum=(byte)(startByte+stopByte+command+composeHeader());
        if (data==null) return sum;
        for (byte b:data) {
            sum = (byte) ((sum+b)& 0xff);
        }
        return sum;
    }
    public boolean isAcknowledge() {
        return acknowledge;
    }
    public byte composeHeader() {
        byte header=(byte) 0x00;
        if (acknowledge) {
            header=(byte) (header | acknowledgeBitMask);
        }
        if (count) {
            header=(byte) (header | packetCountMask);
        }
        if (type==PacketType.slow) {
            header=(byte) (header | packetTypeMask);
        }
        if (data!=null) {
            header=(byte) (header | (byte)(data.length & dimensionMask));
        }
        return header;
    }
    @Override
    public String toString() {
        String output="{type=";
        switch (type) {
            case fast:
                output=output+"fast, ";
                break;
            case slow:
                output=output+"slow, ";
        }
        output=output+
                "acknowledge=" + acknowledge +
                ", count="+count +
                ", command=" + String.format("%02X",command) +
                ", data=" + Arrays.toString(data) +
                '}';
        return output;
    }
    public static int decodeLength(byte header) {
        return (header & dimensionMask);
    }
}
