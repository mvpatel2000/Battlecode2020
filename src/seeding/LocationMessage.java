package seeding;

public class LocationMessage extends Message {

    Message m;
    final int locMessage = 4;   //HoldProductionMessages are message type 3
    int xLoc;
    int yLoc;
    int bitsPerLoc = 6;

    public LocationMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(locMessage);
        xLoc = -1;
        yLoc = -1;
    }

    //Use for recieved message.
    //use .tile and .soupThere to get information
    public LocationMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        xLoc = -1;
        yLoc = -1;
        readLocation();
    }

    boolean writeLocation(int x, int y) {
        return writeToArray(x, bitsPerLoc) && writeToArray(y, bitsPerLoc);
    }

    void readLocation() {
        xLoc = readFromArray(headerLen + schemaLen, bitsPerLoc);
        yLoc = readFromArray(headerLen+schemaLen+bitsPerLoc, bitsPerLoc);
    }

}
