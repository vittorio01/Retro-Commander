package retrocommander.retrocommander;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import com.fazecast.jSerialComm.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import retrocommander.disk_creator.DiskCreator;
import retrocommander.disk_emulator.DiskEmulator;
import retrocommander.serial.Packet;
import retrocommander.serial.SerialInterface;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public class ConsoleController {
    public static final int[] baudrates={300,600,2400,4800,9600,19200,38400,57600,115200};
    public static final String[] parities={"None","Odd","Even"};
    public static final String[] stopBits={"1","1.5","2"};
    public static final String[] flowControls={"None","RTS/CTS","DTR/DSR"};

    public static final int line_dimension=64;
    public static final int line_tab_number=8;


    @FXML
    private Button clearButton;
    @FXML
    private Button startButton;
    @FXML
    private TextArea terminalTextArea;
    @FXML
    private Label targetPortLabel;
    @FXML
    private Label baudrateLabel;
    @FXML
    private Label stopBitsLabel;
    @FXML
    private Label parityLabel;
    @FXML
    private TextField targetPortField;
    @FXML
    private ChoiceBox<Integer> baudrateChoice;
    @FXML
    private ChoiceBox<String> parityChoice;
    @FXML
    private ChoiceBox<String> stopBitsChoice;
    @FXML
    private CheckBox diskCheck;
    @FXML
    private ChoiceBox<String> flowControlChoice;
    @FXML
    private Label flowControlLabel;
    @FXML
    private TextArea logTextArea;
    @FXML
    private TabPane tabPane;
    @FXML
    private Label errorLabel;
    private static boolean diskCreatorOn;
    private boolean serialOn;
    private boolean masterConnectionOpened;
    private boolean diskEmulationOn;
    private boolean diskEmulationSelecting;
    File diskEmulationFile;
    SerialInterface serialChannel;
    int carriage;
    private BlockingQueue<Character> toSend;
    @FXML
    public void initialize() {
        diskCreatorOn=false;
        carriage=0;
        serialOn=false;
        diskEmulationOn=false;
        diskEmulationSelecting=false;
        masterConnectionOpened=false;
        for (int baudrate : baudrates) {
            baudrateChoice.getItems().add(baudrate);
        }
        for (String s : parities) {
            parityChoice.getItems().add(s);
        }
        for (String s : stopBits) {
            stopBitsChoice.getItems().add(s);
        }
        for (String s:flowControls) {
            flowControlChoice.getItems().add(s);
        }

    }
    @FXML
    private boolean checkOptions() {
        boolean result=true;
        if (targetPortField.getText().equals("")) {
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
        if (parityChoice.getValue()==null) {
            result=false;
            parityLabel.setTextFill(Paint.valueOf("RED"));
        } else {
            parityLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        if (stopBitsChoice.getValue()==null) {
            result=false;
            stopBitsLabel.setTextFill(Paint.valueOf("RED"));
        } else {
            stopBitsLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        if (flowControlChoice.getValue()==null) {
            result=false;
            flowControlLabel.setTextFill(Paint.valueOf("RED"));
        } else {
            flowControlLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        return result;
    }
    @FXML
    private void startCommunication() {
        if (!serialOn) {
            if(!checkOptions()) {
                errorLabel.setText("Invalid port settings");
                errorLabel.setVisible(true);
                return;

            }
            serialOn=true;
            errorLabel.setVisible(false);
            startButton.setText("Stop Communication");
            diskCheck.setDisable(true);
            int parity=0;
            int stopBit=0;
            int flowControl=0;
            if (parityChoice.getValue().equals(parities[0])) {
                parity=SerialPort.NO_PARITY;
            } else if (parityChoice.getValue().equals(parities[1])) {
                parity=SerialPort.EVEN_PARITY;
            } else if (parityChoice.getValue().equals(parities[2])) {
                parity=SerialPort.ODD_PARITY;
            }
            if (stopBitsChoice.getValue().equals(stopBits[0])) {
                stopBit=SerialPort.ONE_STOP_BIT;
            } else if (stopBitsChoice.getValue().equals(stopBits[1])) {
                stopBit=SerialPort.ONE_POINT_FIVE_STOP_BITS;
            } else if (stopBitsChoice.getValue().equals(stopBits[2])) {
                stopBit=SerialPort.TWO_STOP_BITS;
            }
            if (flowControlChoice.getValue().equals(flowControls[0])) {
                flowControl=SerialPort.FLOW_CONTROL_DISABLED;
            } else if (flowControlChoice.getValue().equals(flowControls[1])) {
                flowControl=SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            } else if (flowControlChoice.getValue().equals(flowControls[2])) {
                flowControl=SerialPort.FLOW_CONTROL_DSR_ENABLED | SerialPort.FLOW_CONTROL_DTR_ENABLED;
            } else if (flowControlChoice.getValue().equals(flowControls[3])) {
                flowControl=SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
            }

            logTextArea.appendText("Starting serial connection... \n");

            int finalStopBit = stopBit;
            int finalParity = parity;
            int finalFlowControl = flowControl;
            Thread connection= new Thread(() -> {
                try {
                    if (diskEmulationOn) {
                        connect(targetPortField.getText(), finalStopBit, baudrateChoice.getValue(), finalParity, finalFlowControl, diskEmulationOn, diskEmulationFile.getPath());
                    } else {
                        connect(targetPortField.getText(), finalStopBit, baudrateChoice.getValue(), finalParity, finalFlowControl, diskEmulationOn, null);
                    }
                } catch (Exception e) {
                    if (serialChannel!=null) {
                        serialChannel.close();
                    }
                    logTextArea.appendText("Fatal error: "+e.getMessage() + "\nConnection Closed\n");
                    serialOn = false;
                    startButton.setText("Start Communication");
                    diskCheck.setDisable(false);
                }
            });
            connection.setDaemon(true);
            connection.start();
        } else {
            serialOn=false;
            diskCheck.setDisable(false);
            masterConnectionOpened=false;
            startButton.setText("Start Communication");
            logTextArea.appendText("Closing Serial connection... \n");
        }

    }

    @FXML
    private void launchDiskCreator() throws IOException {
        if (!diskCreatorOn) {
            diskCreatorOn=true;
            Stage stage=new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader(DiskCreator.class.getResource("disk_creator.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            stage.setTitle("Disk Creation Tool");
            stage.setResizable(false);
            stage.setScene(scene);
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    diskCreatorOn=false;
                }
            });
            stage.show();
        }

    }
    @FXML
    private void sendCharacter(KeyEvent event) {
        if (serialOn && masterConnectionOpened) {
            toSend.add(event.getCode().getChar().charAt(0));
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

            diskEmulationFile=chooser.showOpenDialog(new Stage());
            if (diskEmulationFile!=null && diskEmulationFile.exists()) {
                diskEmulationOn=true;
                diskCheck.setSelected(true);
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
        body -> ASCII char (if the char is not available, the body will be obmitted)
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
    -   reset connection -> the master tells the slave that there is an hardware reset
        body -> (void)
    -   board id -> the master sends a packet containing the board id
        body -> board id char array)
    */
    public static final byte control_resetConnection = 0x21;
    public static final byte control_boardId = 0x22;


    boolean sectorTransferError;
    boolean sectorSeekError;
    private void connect(String serialPortName, int stopBits, int baudrate, int parity, int flowControl, boolean bindDisk,String diskFile) throws Exception {

        DiskEmulator disk=null;
        if (bindDisk) {
            disk=new DiskEmulator(diskFile);
        }
        serialChannel = new SerialInterface(serialPortName, stopBits,baudrate, parity, flowControl);
        serialChannel.open();
        logTextArea.appendText("Connection opened correctly \n");
        Packet p;
        sectorSeekError=false;
        sectorTransferError=false;
        while (serialOn) {
            p = serialChannel.getPacket();
            byte[] received_data=p.getData();
            if (p.getCommand() == control_resetConnection) {
                masterConnectionOpened = true;
                logTextArea.appendText("The external device has started a new connection!\n");
            } else if (p.getCommand() == control_boardId) {
                StringBuilder boardid = new StringBuilder();
                if (received_data!=null) {
                    for (byte receivedDatum : received_data) {
                        boardid.append((char) receivedDatum);
                    }
                }
                logTextArea.setText("The external device has sent his ID:" + boardid.toString() + "\n");
            }

                    /*
                case disk:
                    if (receivedCommand[0] == disk_getInformation) {
                        byte[] response = new byte[6];
                        if (bindDisk) {
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
                            response[1] = (byte) (disk.getSectorDimension() / 128);
                            response[2] = (byte) disk.getSpt();
                            response[3] = (byte) (disk.getTph() & 0x00ff);
                            response[4] = (byte) (disk.getTph() & 0xff00);
                            response[5] = (byte) disk.getHeadNumber();

                        } else {
                            response[0] = (byte) 0;
                            response[1] = (byte) 0;
                            response[2] = (byte) 0;
                            response[3] = (byte) 0;
                            response[4] = (byte) 0;
                            response[5] = (byte) 0;
                        }
                        serialChannel.sendPacket(new Packet(false, channel.disk, response));

                    } else if (receivedCommand[0] == disk_readSector && diskEmulationOn) {
                        short sector_number = receivedCommand[1];
                        short track_number = receivedCommand[2];
                        short head_number = receivedCommand[4];
                        byte[] sector = disk.readDiskSector(head_number, track_number, sector_number);
                        for (int byteCounter = 0; byteCounter < disk.getSectorDimension(); byteCounter = byteCounter + Packet.dimensionMask) {
                            byte[] buffer = new byte[Packet.dimensionMask];
                            System.arraycopy(sector, byteCounter, buffer, 0, buffer.length);
                            serialChannel.sendPacket(new Packet(false, channel.disk, buffer));
                        }
                        logTextArea.appendText("Disk read operation: sector=" + sector_number + " track=" + track_number + " head=" + head_number + "\n");
                    } else if (receivedCommand[0] == disk_writeSector && diskEmulationOn) {
                        short sector_number = receivedCommand[1];
                        short track_number = receivedCommand[2];
                        short head_number = receivedCommand[4];

                        byte[] sector = new byte[disk.getSectorDimension()];
                        int index = 0;
                        Packet temp;
                        while (index < disk.getSectorDimension()) {
                            temp = serialChannel.getPacket();
                            for (int i = 0; i < Packet.dimensionMask; i++) {
                                sector[index + i] = temp.getData()[i];
                                index++;
                            }
                        }
                        disk.writeDiskSector(sector, head_number, track_number, sector_number);
                        logTextArea.appendText("Disk write operation: sector=" + sector_number + " track=" + track_number + " head=" + head_number + "\n");
                    }

                    break;

                     */
            if (p.getCommand() == terminal_sendString) {
                for (int i = 0; i < received_data.length; i++) {
                    if (received_data[i] >= (byte) 0x20 && received_data[i] < (byte) 0x7f) {
                        if (terminalTextArea.getCaretPosition() == terminalTextArea.getLength()) {
                            terminalTextArea.insertText(terminalTextArea.getCaretPosition(), String.valueOf((char) received_data[i]));
                        } else {
                            terminalTextArea.replaceText(terminalTextArea.getCaretPosition(), terminalTextArea.getCaretPosition() + 1, String.valueOf((char) received_data[i]));
                        }
                    } else if (received_data[i] == (byte) 0x0a) {
                        terminalTextArea.positionCaret(terminalTextArea.getLength() - (terminalTextArea.getLength() % line_dimension));
                    } else if (received_data[i] == (byte) 0x0d) {
                        String s = "";
                        if (terminalTextArea.getCaretPosition() == terminalTextArea.getLength()) {
                            for (int j = 0; j < line_dimension; j++) s = s.concat(" ");
                        } else {
                            for (int j = 0; j < (line_dimension - (terminalTextArea.getCaretPosition() % line_dimension)); j++)
                                s = s.concat(" ");
                        }
                        terminalTextArea.appendText(s);
                    } else if (received_data[i] == (byte) 0x08) {
                        terminalTextArea.deleteText(terminalTextArea.getCaretPosition() - 1, terminalTextArea.getCaretPosition());
                    }
                }
            } else if (p.getCommand() == terminal_readRequest) {
                if (toSend.isEmpty()) {
                    byte[] data=new byte[1];
                    data[1]=(byte)(toSend.remove().charValue());
                    serialChannel.sendPacket(new Packet(false, terminal_readRequest, data));
                } else {
                    serialChannel.sendPacket(new Packet(false, terminal_readRequest, null));
                }

            }
            serialChannel.close();
        }
    }
}
