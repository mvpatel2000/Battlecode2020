package hades;

import java.util.Arrays;
import java.util.Comparator;

public class SoupMessage extends Message {

    final int soupSchema = 1;   //SoupMessages are message type 1
    int tile;
    int soupThere;
    int bitsPerTile = 8;
    int bitsPerPresence = 6;

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
        this.schema = soupSchema;
        tile = -1;
        soupThere = -1;
        readTile();
    }


    boolean writeTile(int myTile) {
        tile = myTile;
        return writeToArray(myTile, bitsPerTile);
    }

    boolean writeSoupAmount(int presence) {
        soupThere = presence;
        return writeToArray(presence, bitsPerPresence);
    }

    void readTile() {
        tile = readFromArray(headerLen + schemaLen, bitsPerTile);
        soupThere = readFromArray(headerLen + schemaLen+bitsPerTile, bitsPerPresence);
    }

}
