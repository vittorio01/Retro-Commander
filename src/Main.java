import disk_emulator.DiskEmulator;

import java.io.IOException;

public class Main {
    static final String bind_disk_option="--bind-disk";
    static final String bind_disk_option_short="-b";
    static final String help_option="--help";
    static final String help_option_short="-h";
    static final String create_option="--create";
    static final String create_option_short="-c";

    public static void main(String[] args) {
        DiskEmulator disk=new DiskEmulator();

        boolean disk_bind_option_select=false;
        boolean serial_port_inserted=false;
        boolean create_file=false;
        short sector_dimension=0;
        short spt_number=0;
        short tph_number=0;
        short heads_number=0;
        String disk_bind_path="";
        String serial_port="";

        for (int i=0;i<args.length;i++) {
            if (args[i].charAt(0)=='-') {
                switch (args[i]) {
                    case help_option, help_option_short -> {
                        System.out.println("Arguments: [-b/--bind-disk {path-to-disk}] [-h/-help] [-c/--create {s-dim} {h:t:s:d} {path-to-disk}");
                        System.out.println("Available arguments:    -h  --help              print this message");
                        System.out.println("                        -b  --bind-disk         Use a file as a memory support for all disk device operations");
                        System.out.println("                        -c  --create            Create a new disk file with specific characteristics");
                        System.out.println("                                                The user must specify four dimensions:  sector dimension d (bytes)");
                        System.out.println("                                                                                        sectors per track s");
                        System.out.println("                                                                                        track per head t");
                        System.out.println("                                                                                        heads number h");
                        System.out.println("                                                With this option, -b/--bind-disk is ignored");
                        return;
                    }
                    case bind_disk_option, bind_disk_option_short -> {
                        if ((args.length - 1) != i && (args[i + 1].charAt(0) != '-')) {
                            i = i + 1;
                            disk_bind_path = args[i];
                            disk_bind_option_select = true;
                        } else {
                            System.err.println("You have to specify the path of the file with the option"+args[i]);
                            return;
                        }
                    }
                    case create_option, create_option_short -> {
                        if ((args.length - 1)!=(i+1) && (args[i + 1].charAt(0) != '-') && (args[i + 2].charAt(0) != '-')) {
                            int c1=0;
                            int c2=0;
                            while (c2<(args[i+1].length()) && args[i+1].charAt(c2)!=':') c2++;
                            try {
                                heads_number=(short) Integer.parseInt(args[i+1].substring(c1,c2));
                            } catch (NumberFormatException e) {
                                System.err.println("Disk format syntax error");
                                return;
                            }
                            c2=c2+1;
                            c1=c2;
                            while (c2<(args[i+1].length()) && args[i+1].charAt(c2)!=':') c2++;
                            try {
                                tph_number=(short) Integer.parseInt(args[i+1].substring(c1,c2));
                            } catch (NumberFormatException e) {
                                System.err.println("Disk format syntax error");
                                return;
                            }
                            c2=c2+1;
                            c1=c2;
                            while (c2<(args[i+1].length()) && args[i+1].charAt(c2)!=':') c2++;
                            try {
                                spt_number=(short) Integer.parseInt(args[i+1].substring(c1,c2));
                            } catch (NumberFormatException e) {
                                System.err.println("Disk format syntax error");
                                return;
                            }
                            c2=c2+1;
                            c1=c2;
                            while (c2<(args[i+1].length()) && args[i+1].charAt(c2)!=':') c2++;
                            try {
                                sector_dimension=(short) Integer.parseInt(args[i+1].substring(c1,c2));
                            } catch (NumberFormatException e) {
                                System.err.println("Disk format syntax error");
                                return;
                            }
                            disk_bind_path=args[i+2];
                            i=i+2;
                            create_file=true;

                        } else {
                            System.err.println("You have to specify two other parameters to create a new disk file");
                            return;
                        }
                    }
                    default -> {
                        System.err.println("Invalid option: " + args[i]);
                        return;
                    }
                }
            } else {
                if (!serial_port_inserted) {
                    serial_port = args[i];
                    serial_port_inserted = true;
                } else {
                    System.err.println("Invalid option: " + args[i]);
                    return;
                }
            }
        }
        if (!serial_port_inserted) {
            System.err.println("Serial port not specified");
            return;
        }
        if (!create_file) {
            if (disk_bind_option_select)  {
                System.out.println("Binding disk file...");
                try {
                    disk.bindDiskFile(disk_bind_path);
                } catch (IOException e) {
                    System.err.println("Error during binding disk file: "+e.getMessage());
                    return;
                }
                System.out.println("Disk file binded successfully");
            } else {
                System.out.println("You have not selected a disk bind path.");
                System.out.println("The program execution will continue but without disk operation capabilities");
            }
        } else {
            System.out.println("Creating a new disk file...");
            try {
                disk.createDiskFile(disk_bind_path, sector_dimension, spt_number, tph_number, heads_number);
            } catch (IOException e) {
                System.err.println("Error during disk file creation: " + e.getMessage());
                return;
            }
            System.out.println("Disk file created successfully");
        }
        System.out.println("Connecting to "+serial_port+" ...");
    }
}