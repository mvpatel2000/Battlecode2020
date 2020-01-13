package sprint;

public class HoldProductionMessage extends Message {

    Message m;
    final int prodMessage = 3;   //HoldProductionMessages are message type 3
    int enemyHQTile;
    int bitsPerTile = 8;

    public HoldProductionMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(prodMessage);
        enemyHQTile = -1;
    }

    //Use for recieved message.
    //use .tile and .soupThere to get information
    public HoldProductionMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        readEnemyHQTile();
    }

    boolean writeEnemyHQTile(int tileNum) {
        enemyHQTile = tileNum;
        return writeToArray(tileNum, bitsPerTile);
    }

    void readEnemyHQTile() {
        enemyHQTile = readFromArray(headerLen + schemaLen, bitsPerTile);
    }

}
