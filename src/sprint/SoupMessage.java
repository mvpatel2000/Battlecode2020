package sprint;

import java.util.Arrays;
import java.util.Comparator;

public class SoupMessage extends Message {

    Message m;
    final int soupSchema = 1;   //SoupMessages are message type 1

    public SoupMessage(int myMapHeight, int myMapWidth, int myTeam) {
        m = new Message(myMapHeight, myMapWidth, myTeam);
        m.writeSchema(soupSchema);
    }


    boolean writeTile(int tile) {
        return writeToArray(tile, 6);
    }

    boolean writeSoupOrNot(int presence) {
        return writeToArray(presence, 1);
    }

}
