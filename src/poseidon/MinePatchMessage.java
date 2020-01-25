package poseidon;

public class MinePatchMessage extends Message {

    final int mpmSchema = 2; //MinePatchMessages are message type 2
    final int MAX_PATCHES;
    int numPatchesWritten;
    int[] patches;
    int[] weights;
    int bitsPerPatch = 9;
    int bitsPerWeight = 5;
    int totalBitsPerElement = bitsPerPatch+bitsPerWeight;

    public MinePatchMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(mpmSchema);
        MAX_PATCHES = this.getBitsRemaining()/totalBitsPerElement;
        numPatchesWritten = 0;
        patches = new int[MAX_PATCHES];
        weights = new int[MAX_PATCHES];
    }

    public MinePatchMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.schema = mpmSchema;
        MAX_PATCHES = (msgLen-headerLen-schemaLen)/totalBitsPerElement;
        numPatchesWritten = MAX_PATCHES;
        //patches = new int[MAX_PATCHES];
        //weights = new int[MAX_PATCHES];
        //readPatches();
    }

    void readPatches() {
        for(int i=0; i<MAX_PATCHES; i++) {
            patches[i] = readFromArray(i*totalBitsPerElement + headerLen + schemaLen, bitsPerPatch);
            weights[i] = readFromArray(i*totalBitsPerElement + bitsPerPatch + headerLen + schemaLen, bitsPerWeight);
        }
    }

    int readPatch(int index) {
        return readFromArray(index*totalBitsPerElement + headerLen + schemaLen, bitsPerPatch);
    }

    int readWeight(int index) {
        return readFromArray(index*totalBitsPerElement + bitsPerPatch + headerLen + schemaLen, bitsPerWeight);
    }


    boolean writePatch(int tile, int weight) {
        if(numPatchesWritten<MAX_PATCHES) {
            if(writeToArray(tile, bitsPerPatch) && writeToArray(weight, bitsPerWeight)) {
                patches[numPatchesWritten] = tile;
                weights[numPatchesWritten] = weight;
                numPatchesWritten+=1;
                return true;
            }
        }
        return false;
    }

}
