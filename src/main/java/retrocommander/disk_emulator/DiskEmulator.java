package retrocommander.disk_emulator;
import java.io.*;

public class DiskEmulator {
    private File file;
    private RandomAccessFile filestream;
    private boolean binded;
    private short sector_dimension;
    private short spt_number;
    private short tph_number;
    private short head_number;
    static final String extensionName="fdskfile";

    public DiskEmulator() {
        binded=false;
    }
    public DiskEmulator(String filepath) throws IOException{
        binded=false;
        bindDiskFile(filepath);
    }
    public void bindDiskFile(String filepath) throws IOException {
        file=new File(filepath);
        if (!file.isFile()) throw new IOException("File does not exists");
        filestream=new RandomAccessFile(file,"r");
        filestream.seek(8);
        String format="";
        try {
            for (int i=8;i<(extensionName.length()+8);i++) {
                format=format+(char) filestream.readByte();
            }
        } catch (EOFException e) {
            throw new IOException("disk file format error");
        }

        if (!format.equals(extensionName)) throw new IOException("disk file format error");
        filestream.seek(0);
        head_number=filestream.readShort();
        tph_number= filestream.readShort();
        spt_number=filestream.readShort();
        sector_dimension=filestream.readShort();
        filestream.close();
    }
    public static void createDiskFile(String filepath, short sector_dimension, short spt_number, short tph_number, short head_number) throws IOException {
        File file=new File(filepath);
        if (!file.createNewFile()) throw new IOException("File already exists");
        RandomAccessFile filestream=new RandomAccessFile(file,"rw");
        filestream.setLength(((long) sector_dimension *spt_number*tph_number*head_number)+16);
        filestream.seek(0);
        filestream.writeShort(head_number);
        filestream.writeShort(tph_number);
        filestream.writeShort(spt_number);
        filestream.writeShort(sector_dimension);
        filestream.writeBytes(extensionName);
        filestream.close();
    }
    public byte[] readDiskSector(short head, short track, short sector) throws IOException {
        if (!binded) throw new IOException("File not binded");
        if (head>=head_number || track>=tph_number || sector>=spt_number) throw new IOException("Sector out of bound");
        filestream=new RandomAccessFile(file,"r");
        byte[] sectorData=new byte[sector_dimension];
        filestream=new RandomAccessFile(file,"r");
        filestream.seek((sector_dimension*sector)+(track*spt_number)+(head*tph_number*spt_number)+16);
        filestream.read(sectorData);
        filestream.close();
        return sectorData;
    }
    public void writeDiskSector(byte[] sector_data,short head, short track, short sector) throws IOException {
        if (!binded) throw new IOException("File not binded");
        if (head>=head_number || track>=tph_number || sector>=spt_number) throw new IOException("Sector out of bound");
        filestream=new RandomAccessFile(file,"rw");
        filestream.seek((sector_dimension*sector)+(track*spt_number)+(head*tph_number*spt_number)+16);
        filestream.write(sector_data);
        filestream.close();
    }
    public short getSectorDimension() {
        return sector_dimension;
    }
    public short getTph() {
        return tph_number;
    }
    public short getSpt() {
        return spt_number;
    }
    public short getHeadNumber() {
        return head_number;
    }
    public boolean isReadOnly() {
        return file.canWrite();
    }
}
