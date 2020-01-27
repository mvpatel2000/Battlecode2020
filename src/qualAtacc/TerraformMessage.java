package qualAtacc;

public class TerraformMessage extends Message {

    final int tSchema = 6;   //TerraformMessages are message type 5
    int type; // 1 is landscaper
    int bitsPerType = 3;
    int id; //last 3 digits (up to 10 bits) of the target robot's id
    int bitsPerID = 10;

    public TerraformMessage(int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(myMapHeight, myMapWidth, myTeam, roundNum);
        this.writeSchema(tSchema);
        type = -1;
        id = -1;
    }

    //Use for recieved message.
    public TerraformMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(recieved, myMapHeight, myMapWidth, myTeam, roundNum);
        this.schema=tSchema;
        readType();
        readID();
    }

    boolean writeTypeAndID(int t, int i) {
        type = t;
        id = i;
        return writeToArray(type, bitsPerType) && writeToArray(id, bitsPerID);
    }

    void readType() {
        type = readFromArray(headerLen + schemaLen, bitsPerType);
    }

    void readID() {
        id = readFromArray(headerLen + schemaLen + bitsPerType, bitsPerID);
    }

}
