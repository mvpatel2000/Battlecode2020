package sprint;

import java.util.Arrays;
import java.util.Comparator;

public class MinePatchMessage extends Message {

    Message m;
    final int mpmSchema = 2; //MinePatchMessages are message type 1
    int numPatches;
    int numPatchesLeft;

    public MinePatchMessage(int myMAP_HEIGHT, int myMAP_WIDTH, int myTeam) {
        super(myMAP_HEIGHT, myMAP_WIDTH, myTeam);
        this.writeSchema(mpmSchema);
        numPatches = this.getBitsRemaining()/12;
        numPatchesLeft = numPatches;
    }

    boolean writePatch(int tile, int weight) {
        if(numPatchesLeft>0) {
            if(writeToArray(tile, 6) && writeToArray(weight, 6)) {
                numPatchesLeft-=1;
                return true;
            }
        }
        return false;
    }

}
