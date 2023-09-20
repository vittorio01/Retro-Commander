package retrocommander.disk_creator;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import retrocommander.disk_emulator.DiskEmulator;

import java.io.File;
import java.io.IOException;

public class DiskCreator {
    @FXML
    private ChoiceBox<String> sectorChoice;
    @FXML
    private Spinner<Integer> tphSpinner;
    @FXML
    private Spinner<Integer> sptSpinner;
    @FXML
    private Spinner<Integer> headSpinner;
    @FXML
    private Label sectorLabel;
    @FXML
    private Label sptLabel;
    @FXML
    private Label tphLabel;
    @FXML
    private Label headLabel;
    public static final String[] sectorDimensions={"128 bytes","256 bytes","512 bytes","1k bytes","2K bytes","4K bytes"};
    public static final int sptMax=256;
    public static final int tphMax=65536;
    public static final int headMax=256;
    @FXML
    public void initialize() {
        for (String s:sectorDimensions) {
            sectorChoice.getItems().add(s);
        }
        sptSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,sptMax,1));
        tphSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,tphMax,1));
        headSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,headMax,1));
    }
    @FXML
    private void createDisk() throws IOException {
        if (checkDiskCreation()) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select disk file path");
            File createdFile = chooser.showSaveDialog(new Stage());
            short sectorDimension=0;
            for (int i=0;i<sectorDimensions.length;i++) {
                if (sectorDimensions[i].equals(sectorChoice.getValue())) {
                    sectorDimension= (short) ((Math.pow(2,i))*128);
                    break;
                }
            }
            DiskEmulator.createDiskFile(createdFile.getPath(),sectorDimension,sptSpinner.getValue().shortValue(),tphSpinner.getValue().shortValue(),headSpinner.getValue().shortValue());
        }
    }
    private boolean checkDiskCreation() {
        boolean result=true;
        if (sectorChoice.getValue()==null) {
            result=false;
            sectorLabel.setTextFill(Paint.valueOf("RED"));
        } else {
            sectorLabel.setTextFill(Paint.valueOf("BLACK"));
        }
        return result;
    }

}
