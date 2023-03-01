package disk_emulator;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiskEmulator {
    private File file;
    private RandomAccessFile filestream;
    private boolean binded;
    private short sector_dimension;
    private short spt_number;
    private short tph_number;
    private short head_number;
    static final String extensionName=".fdsk";

    DiskEmulator() {
        binded=false;
    }
    DiskEmulator(String filepath) {
        binded=false;
        bindDiskFile(filepath);
    }
    void bindDiskFile(String filepath) throws IOException {
        file=new File(filepath);
        if (!file.isFile()) throw new IOException("File does not exists");
        filestream=new RandomAccessFile(file,"r");
        filestream.seek(8);
        if (!filestream.readUTF().equals(extensionName)) throw new IOException("File format error");
        filestream.seek(0);
        head_number=filestream.readShort();
        tph_number= filestream.readShort();
        spt_number=filestream.readShort();
        sector_dimension=filestream.readShort();
    }
    boolean createDiskFile(String filepath, short sector_dimension, short spt_number, short tph_number, short head_number) throws IOException {
        sector_dimension= (short) (sector_dimension*128);
        int point=0;
        while (point<filepath.length() && filepath.charAt(point)!='.') point=point+1;
        if (point<(filepath.length()-1)) filepath=filepath.substring(0,point);
        filepath=filepath+extensionName;
        file=new File(filepath);
        if (!file.createNewFile()) throw new IOException("File already exists");
        filestream=new RandomAccessFile(file,"rw");
        filestream.setLength((long) sector_dimension *spt_number*tph_number*head_number);
        filestream.seek(0);
        filestream.writeShort(head_number);
        filestream.writeShort(tph_number);
        filestream.writeShort(spt_number);
        filestream.writeShort(sector_dimension);
        filestream.writeUTF(extensionName.substring(1,(extensionName.length()-1));
        this.head_number=head_number;
        this.sector_dimension=sector_dimension;
        this.spt_number=spt_number;
        this.tph_number=tph_number;
        filestream.close();
        binded=true;
    }
}
