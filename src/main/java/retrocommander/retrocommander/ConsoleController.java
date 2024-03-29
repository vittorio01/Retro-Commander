package retrocommander.retrocommander;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import com.fazecast.jSerialComm.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import retrocommander.disk_creator.DiskCreator;
import retrocommander.disk_emulator.DiskEmulator;
import retrocommander.serial.Packet;
import retrocommander.serial.PacketType;
import retrocommander.serial.SerialInterface;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsoleController {
    public static final int[] baudrates={300,600,2400,4800,9600,19200,38400,57600,115200};
    public static final int parity=SerialPort.NO_PARITY;
    public static final int flowControl=SerialPort.FLOW_CONTROL_CTS_ENABLED | SerialPort.FLOW_CONTROL_RTS_ENABLED;
    public static final int stopBits=SerialPort.ONE_STOP_BIT;


    public static final int line_dimension=56;

    @FXML
    private Button startButton;
    @FXML
    private TextArea terminalTextArea;
    @FXML
    private Label targetPortLabel;
    @FXML
    private Label baudrateLabel;

    @FXML
    private ComboBox<String> targetPortCombo;
    @FXML
    private ChoiceBox<Integer> baudrateChoice;

    @FXML
    private CheckBox diskCheck;

    @FXML
    private TextArea logTextArea;
    @FXML
    private Label connectionLabel;
    private static boolean diskCreatorOn;
    private boolean serialOn;
    private boolean diskEmulationOn;
    private boolean diskEmulationSelecting;
    File diskEmulationFile;
    SerialInterface serialChannel;
    int carriage;
    private Thread serialConnection;
    private final Object lock=new Object();
    private ArrayBlockingQueue<Character> charInList;
    private AtomicBoolean allowKey;
    private DiskEmulator disk;
    @FXML
    public void initialize() {
        allowKey=new AtomicBoolean(false);
        charInList=new ArrayBlockingQueue<Character>(line_dimension);
        diskCreatorOn=false;
        carriage=0;
        serialOn=false;
        diskEmulationOn=false;
        diskEmulationSelecting=false;
        for (int baudrate : baudrates) {
            baudrateChoice.getItems().add(baudrate);
        }
        for (SerialPort p: SerialPort.getCommPorts()) {
            targetPortCombo.getItems().add(p.getSystemPortName());
        }
        if (!targetPortCombo.getItems().isEmpty()) {
            targetPortCombo.setValue(targetPortCombo.getItems().get(0));
        }
        baudrateChoice.setValue(baudrates[0]);
    }
    @FXML
    private boolean checkOptions() {
        boolean result=true;
        if (targetPortCombo.getValue().isEmpty()) {
            targetPortLabel.setTextFill(Paint.valueOf("RED"));
            result=false;
        } else {
            targetPortLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        if (baudrateChoice.getValue()==null) {
            result=false;
            baudrateLabel.setTextFill(Paint.valueOf("RED"));
        } else {
            baudrateLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        return result;
    }
    @FXML
    private void startCommunication() {
        if (!serialOn) {
            if(!checkOptions()) {
                connectionLabel.setText("Invalid port settings");
                connectionLabel.setVisible(true);
                connectionLabel.setTextFill(Paint.valueOf("RED"));
                return;

            }
            serialOn=true;
            connectionLabel.setVisible(false);
            connectionLabel.setTextFill(Paint.valueOf("BLACK"));
            startButton.setText("Stop Communication");
            diskCheck.setDisable(true);
            logTextArea.appendText("Starting serial connection... \n");
            serialConnection=new Thread(new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    try {
                        connect(targetPortCombo.getValue(), baudrateChoice.getValue());
                        return null;
                    } catch (InterruptedException ignored) {

                    }  catch (SerialPortIOException e) {
                        if (serialChannel != null) {
                            serialChannel.close();
                        }
                        Platform.runLater(() -> {
                            Platform.runLater(() -> {
                                logTextArea.appendText("Error during communication: " + e.getMessage() + "\n");
                                logTextArea.appendText("Conenction closed \n");
                                connectionLabel.setVisible(true);
                                connectionLabel.setTextFill(Paint.valueOf("RED"));
                            });

                        });
                    }
                    Platform.runLater(() -> {
                        connectionLabel.setText("Connection closed");
                        startButton.setText("Start Communication");
                        diskCheck.setDisable(false);
                    });
                    serialOn = false;
                    return null;
                }
            });
            serialConnection.setDaemon(true);
            serialConnection.start();


        } else {
            try {
                serialChannel.close();
                serialConnection.interrupt();
            } catch (Exception ignored) {}
            serialOn=false;
            logTextArea.appendText("Closing Serial connection... \n");
            diskCheck.setDisable(false);
            startButton.setText("Start Communication");
            connectionLabel.setVisible(false);
        }

    }

    @FXML
    private void launchDiskCreator() throws IOException {
        if (!diskCreatorOn) {

            diskCreatorOn=true;
            Stage stage=new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader(DiskCreator.class.getResource("disk_creator.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            stage.getIcons().add(new Image(String.valueOf(Application.class.getResource("tool-box.png"))));
            stage.setTitle("Disk Creation Tool");
            stage.setResizable(false);
            stage.setScene(scene);
            stage.setOnCloseRequest(event -> diskCreatorOn=false);
            stage.show();
        }

    }
    @FXML
    private void sendCharacter(KeyEvent event) {
        synchronized (lock) {
            if (serialOn && event.getEventType()==KeyEvent.KEY_TYPED) {
                charInList.add(event.getCharacter().charAt(0));
                if (charInList.size()==1) {
                    lock.notify();
                }
            }
        }
    }
    @FXML
    private void clearTerminal() {
        terminalTextArea.clear();
        carriage=0;
    }
    @FXML
    private void selectDiskFile() {
        if (!diskEmulationOn) {
            if (diskEmulationSelecting) {
                diskCheck.setSelected(false);
                return;
            }
            diskEmulationSelecting=true;
            diskCheck.setSelected(false);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select disk file");
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("RCDSK disk file","*.rcdsk","*.RCDSK"));

            diskEmulationFile=chooser.showOpenDialog(new Stage());
            if (diskEmulationFile!=null && diskEmulationFile.exists()) {
                try {
                    disk = new DiskEmulator();
                    disk.bindDiskFile(diskEmulationFile.getPath());
                    diskEmulationOn=true;
                    diskCheck.setSelected(true);
                } catch (Exception e) {
                    diskCheck.setSelected(false);
                    diskEmulationOn = false;
                }
            } else {
                diskCheck.setSelected(false);
            }

        } else {
            diskEmulationOn=false;
            diskCheck.setSelected(false);
        }
        diskEmulationSelecting=false;

    }




    /*
    This application uses the serial protocol to communicate in a master/slave method, for avoid the necessity to implement a multithreading system in the device.
    In this configuration, the slave (the current computer) is used to provide a set of resources like I/O terminal and disk device.
    At this point, there are some assertions to establish:
    - The slave cannot send packets before the master. Every packet that the slave will send are anticipated by a command packet from the master.
    - There are different type of commands:
        * Terminal commands, used for sending and reading ASCII character
        * Disk commands, used for performing disk operations
        * Channel commands, used for controlling the connection

    *** Terminal ***
    These commands are used for I/O terminal communications:
    -   Send command -> the master sends an ASCII character
        body -> (the ASCII character to transmit)

    -   Read command -> the master requests a certain number of ASCII characters from the slave
        body -> (void)
        After the request, the slave sends a second packet which contains the ASCII char
        body -> ASCII char (if the char is not available, the body will be omitted)
    */
    public static final byte terminal_sendString= 0x01;
    public static final byte terminal_readRequest = 0x02;
    /*
    *** Disk **
    These commands are used for all disk operations:
    -   get information -> the master requests the disk information.
        body -> byte per sector (1 byte coded in 128 multiples), sectors per track (one byte), tracks per head (two bytes), number of heads (one byte),disk state (one byte)
    -   read sector -> the master requests a transmission of one specified sector
        body -> sector number (one byte), track number (two bytes), head number (1 byte)
        the slave responds sending the desired sector split in one or more packets
    -   write sector -> the master send a sector to the master (at least two packets)
        body -> sector number (one byte), track number (two bytes), head number (one byte)
        After the request packet, the sector data is split into or more packets
     */
    public static final byte disk_getInformation= 0x11;
    public static final byte disk_readSector = 0x12;
    public static final byte disk_writeSector = 0x13;
    public static final byte disk_insertedMask = (byte) 0b10000000;
    public static final byte disk_readyMask = (byte) 0b01000000;
    public static final byte disk_readOnlyMask = (byte) 0b00100000;
    public static final byte disk_dataTransferErrorMask = (byte) 0b00010000;
    public static final byte disk_seekErrorMask = (byte) 0b00001000;
    public static final byte disk_badSectorMask = (byte) 0b00000100;
    /*
    *** control ***
    These command are used for sending information about the connection:
    -   reset connection -> the master tells the slave that there is a hardware reset
        body -> (void)
    -   board id -> the master sends a packet containing the board id
        body -> (board id char array)
    */
    public static final byte control_resetConnection = 0x21;
    public static final byte control_boardId = 0x22;

    public static final int disk_packet_dimension=16;
    boolean sectorTransferError;
    boolean sectorSeekError;
    private void connect(String serialPortName, int baudrate) throws Exception {
        serialChannel = new SerialInterface(serialPortName, ConsoleController.stopBits,baudrate, ConsoleController.parity, ConsoleController.flowControl);
        serialChannel.open();
        Platform.runLater(() -> logTextArea.appendText("Serial device opened Correctly!\n"));
        Packet p;
        sectorSeekError=false;
        sectorTransferError=false;
        allowKey.set(false);

        while (serialOn) {
            try {
                p = serialChannel.getPacket(false);
                byte[] received_data = p.getData();
                switch (p.getCommand()) {
                    case control_resetConnection:
                        Platform.runLater(() -> {
                            logTextArea.appendText("Connection Reset\n");
                            connectionLabel.setVisible(true);
                            connectionLabel.setTextFill(Paint.valueOf("BLACK"));
                            connectionLabel.setText("Connected: unknown");
                            charInList.clear();
                        });
                        break;

                    case control_boardId:
                        StringBuilder boardid = new StringBuilder();
                        if (received_data != null) {
                            for (byte b : received_data) {
                                boardid.append((char) b);
                            }
                        }
                        Platform.runLater(() -> {
                            connectionLabel.setVisible(true);
                            connectionLabel.setTextFill(Paint.valueOf("BLACK"));
                            connectionLabel.setText("Connected: " + boardid);
                        });
                        break;

                    case terminal_sendString:
                        Platform.runLater(() -> {
                            for (byte b : received_data) {
                                if (b >= (byte) 0x20 && b < (byte) 0x7f) {
                                    //*if (terminalTextArea.getCaretPosition() == terminalTextArea.getLength()) {
                                        terminalTextArea.insertText(terminalTextArea.getLength(), String.valueOf((char) b));
                                    /*} else {
                                        terminalTextArea.replaceText(terminalTextArea.getCaretPosition(), terminalTextArea.getCaretPosition() + 1, String.valueOf((char) b));
                                    }*/
                                } else if (b == (byte) 0x0d) {
                                    terminalTextArea.appendText("\n");
                                } else if (b == (byte) 0x08) {
                                    terminalTextArea.deleteText(terminalTextArea.getLength()-1,terminalTextArea.getLength());
                                    //terminalTextArea.deleteText(terminalTextArea.getCaretPosition() - 1, terminalTextArea.getCaretPosition());
                                }
                            }
                        });
                        break;
                    case terminal_readRequest:
                        synchronized (lock) {
                            while (charInList.isEmpty()) {
                                lock.wait();
                            }
                        }
                        byte[] data = new byte[(charInList.size() % Packet.maxPacketDimension)];
                        for (int i = 0; i < data.length; i++) {
                            data[i] = (byte) charInList.remove().charValue();
                        }
                        serialChannel.sendPacket(new Packet(false, terminal_readRequest, data, PacketType.fast));
                        break;
                    case disk_getInformation:
                        //returns disk status
                        // data[0] = inserted(7) + ready(6) + read only (5) + transfer error (4) + seek error (3) + ...
                        if (diskEmulationOn) {
                            byte[] response = new byte[6];
                            response[0] = disk_insertedMask | disk_readyMask;
                            if (disk.isReadOnly()) {
                                response[0] = (byte) (response[0] | disk_readOnlyMask);
                            }
                            if (sectorTransferError) {
                                response[0] = (byte) (response[0] | disk_dataTransferErrorMask);
                            }
                            if (sectorSeekError) {
                                response[0] = (byte) (response[0] | disk_seekErrorMask);
                            }
                            // if disk emulation is on, next bytes contains disk structure
                            // sector dimension (128 multiple, 1 byte) + sector per track (1 byte) + tracks per head (2 bytes) + heads (1 byte)
                            response[1] = (byte) (disk.getSectorDimension() / 128);
                            response[2] = (byte) disk.getSpt();
                            response[3] = (byte) (disk.getTph() & 0x00ff);
                            response[4] = (byte) (disk.getTph() & 0xff00);
                            response[5] = (byte) disk.getHeadNumber();
                            serialChannel.sendPacket(new Packet(false, disk_getInformation, response, PacketType.slow));
                        } else {
                            // if disk emulation is off, response packet contains a single 0x00 byte as body
                            byte[] response = {0, 0, 0, 0, 0, 0};
                            serialChannel.sendPacket(new Packet(false, disk_getInformation, response, PacketType.slow));
                        }
                        break;

                    case disk_readSector:
                        if (diskEmulationOn) {
                            // if disk emulation is on, slave receive the first packet from master and next sends back all data divided in different packets.
                            short sector_number = received_data[0];
                            short track_number = received_data[1];
                            short head_number = received_data[3];
                            try {
                                byte[] sector = disk.readDiskSector(head_number, track_number, sector_number);
                                for (int byteCounter = 0; byteCounter < disk.getSectorDimension(); byteCounter = byteCounter + disk_packet_dimension) {
                                    byte[] buffer = new byte[disk_packet_dimension];
                                    System.arraycopy(sector, byteCounter, buffer, 0, buffer.length);
                                    serialChannel.sendPacket(new Packet(false, disk_readSector, buffer, PacketType.slow));
                                }
                                Platform.runLater(() -> logTextArea.appendText("Disk read operation: sector=" + sector_number + " track=" + track_number + " head=" + head_number + "\n"));
                            } catch (NullPointerException e) {
                                serialChannel.sendPacket(new Packet(false, disk_readSector, null, PacketType.slow));
                                System.out.println("disk read error: " + e.getMessage());
                            }

                        } else {
                            // if disk emulation is off or there is a read error, slave respond with a void packet.
                            serialChannel.sendPacket(new Packet(false, disk_readSector, null, PacketType.slow));
                        }
                        break;
                    case disk_writeSector:
                        if (diskEmulationOn) {
                            // the slave receive a first packet with all information
                            short sector_number = received_data[0];
                            short track_number = received_data[1];
                            short head_number = received_data[3];

                            byte[] sector = new byte[disk.getSectorDimension()];
                            int index = 0;
                            Packet temp;
                            // when the slave is ready, it receives all packet with disk data divided in different packets from the master
                            while (index < (disk.getSectorDimension())) {
                                temp = serialChannel.getPacket(false);
                                if (temp.getCommand() == disk_writeSector) {
                                    for (int i = 0; i < temp.getData().length; i++) {
                                        sector[index] = temp.getData()[i];
                                        index++;
                                    }
                                }
                            }
                            if (index < disk.getSectorDimension()) {
                                throw new SerialPortIOException("Sector partially received");
                            }
                            disk.writeDiskSector(sector, head_number, track_number, sector_number);
                            Platform.runLater(() -> logTextArea.appendText("Disk write operation: sector=" + sector_number + " track=" + track_number + " head=" + head_number + "\n"));
                        }
                        break;
                }
            } catch (SerialPortIOException e) {
                if (!e.getMessage().equals("All resend attempts consumed")) {
                    serialChannel.close();
                    Platform.runLater(() -> connectionLabel.setVisible(false));
                    throw e;
                }
            }

        }
        serialChannel.close();
        Platform.runLater(() -> connectionLabel.setVisible(false));
    }
}
