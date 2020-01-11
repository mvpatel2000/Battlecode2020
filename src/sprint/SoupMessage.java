package sprint;

import java.util.Arrays;
import java.util.Comparator;

public class SoupMessage extends Message {

    Message m;
    final int soupSchema = 1;   //SoupMessages are message type 1
    int tile;
    int soupThere;

    public SoupMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(soupSchema);
        tile = -1;
        soupThere = -1;
    }

    //Use for recieved message.
    //use .tile and .soupThere to get information
    public SoupMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        tile = -1;
        soupThere = -1;
        readTile();
    }


    boolean writeTile(int myTile) {
        tile = myTile;
        return writeToArray(myTile, 6);
    }

    boolean writeSoupOrNot(int presence) {
        soupThere = presence;
        return writeToArray(presence, 1);
    }

    void readTile() {
        tile = readFromArray(headerLen + schemaLen + , 6);
        soupThere = readFromArray(headerLen + schemaLen +6, 1);
    }

}
