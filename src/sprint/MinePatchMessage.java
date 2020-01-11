package sprint;

import java.util.Arrays;
import java.util.Comparator;

public class MinePatchMessage extends Message {

    Message m;
    final int mpmSchema = 2; //MinePatchMessages are message type 1
    final int MAX_PATCHES;
    int numPatchesWritten;
    int[] patches;
    int[] weights;

    public MinePatchMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(mpmSchema);
        MAX_PATCHES = this.getBitsRemaining()/12;
        numPatchesWritten = 0;
        patches = new int[MAX_PATCHES];
        weights = new int[MAX_PATCHES];
    }

    public MinePatchMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.writeSchema(mpmSchema);
        MAX_PATCHES = this.getBitsRemaining()/12;
        numPatchesWritten = MAX_PATCHES;
        patches = new int[MAX_PATCHES];
        weights = new int[MAX_PATCHES];
        readPatches();
    }

    void readPatches() {
        for(int i=0; i<MAX_PATCHES; i++) {
            patches[i] = readFromArray(i*12 + headerLen + schemaLen, 6);
            weights[i] = readFromArray(i*12 + 6 + headerLen + schemaLen, 6);
        }
    }

    boolean writePatch(int tile, int weight) {
        if(numPatchesWritten<MAX_PATCHES) {
            if(writeToArray(tile, 6) && writeToArray(weight, 6)) {
                patches[numPatchesWritten] = tile;
                weights[numPatchesWritten] = weight;
                numPatchesWritten+=1;
                return true;
            }
        }
        return false;
    }

}
