package retrocommander.serial;

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
    public static final byte channelMask=(byte) 0b01100000;
    public static final byte channelControlIdentifier=(byte) 0b00000000;
    public static final byte channelDiskIdentifier=(byte) 0b00100000;
    public static final byte channelTerminalIdentifier=(byte) 0b01000000;
    public static final byte packetCountMask=(byte) 0b00010000;
    public static final byte dimensionMask=(byte) 0b00001111;
    private boolean acknowledge;
    private final channel channel;
    private byte[] data;
    public Packet(boolean acknowledge, channel channel) {
        this.acknowledge=acknowledge;
        data=null;
        this.channel= channel;
    }
    public Packet(boolean acknowledge, channel channel, byte[] data) {
        this.acknowledge=acknowledge;
        this.channel= channel;
        if (!acknowledge) {
            this.data = data;
        } else {
            this.data=null;
        }
    }
    public channel getChannel() {
        return channel;
    }
    public byte[] getData() {
        return data;
    }
    public boolean verifyChecksum(byte checksum) {
        return getChecksum() == checksum;
    }
    public byte getChecksum() {
        byte sum=0;
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
        String output= "{ack=" + acknowledge + ", channel= " + channel + ", checksum= " + getChecksum() + "}\n";
        if (data!=null) {
            for (int i = 0; i < data.length; i++) {
                output = output.concat(" " + data[i] + ",");
                if (i % 16 == 0 && i != 0) {
                    output = output.concat("\n");
                }
            }
        }
        return output.concat("\n");
    }
}
