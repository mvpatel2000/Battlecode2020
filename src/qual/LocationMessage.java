package qual;

public class LocationMessage extends Message {

    final int locMessage = 4;   //HoldProductionMessages are message type 3
    int xLoc;
    int yLoc;
    int bitsPerLoc = 6;
    int unitType;   //0 is our HQ, 1 is enemy HQ
    int bitsPerUnitType = 6;

    public LocationMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(locMessage);
        xLoc = -1;
        yLoc = -1;
        unitType = -1;
    }

    //Use for recieved message.
    //use .tile and .soupThere to get information
    public LocationMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.schema=locMessage;
        xLoc = -1;
        yLoc = -1;
        unitType = -1;
        readLocation();
        readUnitType();
    }

    boolean writeInformation(int x, int y, int t) {
        return writeLocation(x, y) && writeToArray(t, bitsPerUnitType);
    }
    boolean writeLocation(int x, int y) {
        return writeToArray(x, bitsPerLoc) && writeToArray(y, bitsPerLoc);
    }

    void readLocation() {
        xLoc = readFromArray(headerLen + schemaLen, bitsPerLoc);
        yLoc = readFromArray(headerLen+schemaLen+bitsPerLoc, bitsPerLoc);
    }

    void readUnitType() {
        unitType = readFromArray(headerLen + schemaLen + bitsPerLoc + bitsPerLoc, bitsPerUnitType);
    }

}
