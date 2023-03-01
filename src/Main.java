import disk_emulator.DiskEmulator;

public class Main {
    static final String bind_disk_option="--bind-disk";
    static final String bind_disk_option_short="-b";
    static final String help_option="--help";
    static final String help_option_short="-h";
    static final String create_option="--create";
    static final String create_option_short="-c";
    private DiskEmulator disk;
    public static void main(String[] args) {
        boolean disk_bind_option_select=false;
        boolean serial_port_inserted=false;
        boolean create_option=false;
        short sector_dimension;
        short spt_number;
        short tph_number;
        short heads_number;
        String disk_bind_path="";
        String serial_port="";

        for (int i=0;i<args.length;i++) {
            if (args[i].charAt(0)=='-') {
                switch (args[i]) {
                    case help_option, help_option_short -> {
                        System.out.println("Arguments: [-b/--bind-disk {path-to-disk}] [-h/-help] [-c/--create {s-dim} {spt} {tph} {h-num] {serial-port}");
                        System.out.println("Available arguments:    -h  --help              print this message");
                        System.out.println("                        -b  --bind-disk         Use a file as a memory support for all disk device operations");
                        System.out.println("                        -c  --create            Create a new disk file with specific characteristics");
                        System.out.println("                                                The user must specify four dimensions:  sector dimension (multiple of 128 bytes)");
                        System.out.println("                                                                                        sectors per track");
                        System.out.println("                                                                                        track per head");
                        System.out.println("                                                                                        heads number");
                        return;
                    }
                    case bind_disk_option, bind_disk_option_short -> {
                        if ((args.length - 1) != i && (args[i + 1].charAt(0) != '-')) {
                            i = i + 1;
                            disk_bind_path = args[i];
                            disk_bind_option_select = true;
                        } else {
                            System.out.println("You have to specify the path of the file with the option"+args[i]);
                            return;
                        }
                    }
                    case create_option, create_option_short -> {
                        if ((args.length - 1)!=(i+3) && (args[i + 1].charAt(0) != '-') && (args[i + 2].charAt(0) != '-') && (args[i + 3].charAt(0) != '-') && (args[i + 4].charAt(0) != '-')) {
                            try {
                                sector_dimension=(short) Integer.parseInt(args[i+1]);
                                spt_number=(short) Integer.parseInt(args[i+2]);
                                tph_number=(short) Integer.parseInt(args[i+3]);
                                heads_number=(short) Integer.parseInt(args[i+4]);
                            } catch (NumberFormatException e) {
                                System.out.println("Disk format error");
                                return;
                            }
                            i=i+4;
                            create_option=true;
                        } else {
                            System.out.println("You have to specify four parameters to create a new disk file");
                            return;
                        }
                    }
                    default -> {
                        System.out.println("Invalid option: " + args[i]);
                        return;
                    }
                }
            } else {
                if (!serial_port_inserted) {
                    serial_port=args[i];
                    serial_port_inserted=true;
                } else {
                    System.out.println("Invalid option: "+args[i]);
                    return;
                }
            }
        }
        if (!serial_port_inserted) {
            System.out.println("Serial port not specified");
            return;
        }
        if (!disk_bind_option_select) {
            System.out.println("You have not selected a disk bind path.");
            System.out.println("The program execution will continue but without disk operation capabilities");
        } else {
            if (create_option) {
                try {

                }
            }
        }
        System.out.println("Connecting to "+serial_port+" ...");
    }
}