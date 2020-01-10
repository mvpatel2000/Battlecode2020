package sprint;

import java.util.Arrays;
import java.util.Comparator;

public class SoupMessage extends Message {

    Message m;
    final int soupSchema = 1;   //SoupMessages are message type 1

    public SoupMessage(int myMAP_HEIGHT, int myMAP_WIDTH, int myTeam) {
        m = new Message(myMAP_HEIGHT, myMAP_WIDTH, myTeam);
        m.writeSchema(soupSchema);
    }


    boolean writeTile(int tile) {
        return writeToArray(tile, 6);
    }

    boolean writeSoupOrNot(int presence) {
        return writeToArray(presence, 1);
    }

}
