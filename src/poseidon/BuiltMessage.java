package poseidon;

public class BuiltMessage extends Message {

    final int bSchema = 5;   //BuiltMessages are message type 5
    int typeBuilt; // 1 is fulfillment center, 2 is d.school, 3 is refinery
    int bitsPerType = 3;

    public BuiltMessage(int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(myMapHeight, myMapWidth, myTeam, roundNum);
        this.writeSchema(bSchema);
        typeBuilt = -1;
    }

    //Use for recieved message.
    public BuiltMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(recieved, myMapHeight, myMapWidth, myTeam, roundNum);
        this.schema=bSchema;
        readTypeBuilt();
    }

    boolean writeTypeBuilt(int type) {
        typeBuilt = type;
        return writeToArray(typeBuilt, bitsPerType);
    }

    void readTypeBuilt() {
        typeBuilt = readFromArray(headerLen + schemaLen, bitsPerType);
    }

}
