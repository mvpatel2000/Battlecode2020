package seeding2;

public class HoldProductionMessage extends Message {

    final int prodMessage = 3;   //HoldProductionMessages are message type 3
    //We are no longer sending the enemyHQLocation through the HoldProductionMessage
    //int enemyHQx;
    //int enemyHQy;
    //int bitsPerCoord = 6;

    public HoldProductionMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(prodMessage);
        //enemyHQx = -1;
        //enemyHQy = -1;
    }

    //Use for recieved message.
    public HoldProductionMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.schema=prodMessage;
        //readEnemyHQLocation();
    }

    /**
    boolean writeEnemyHQLocation(int x, int y) {
        enemyHQx = x;
        enemyHQy = y;
        return writeToArray(enemyHQx, bitsPerCoord) && writeToArray(enemyHQy, bitsPerCoord);
    }

    void readEnemyHQLocation() {
        enemyHQx = readFromArray(headerLen + schemaLen, bitsPerCoord);
        enemyHQy = readFromArray(headerLen + schemaLen + bitsPerCoord, bitsPerCoord);
    }*/

}
