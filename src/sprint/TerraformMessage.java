package sprint;

public class TerraformMessage extends Message {

    final int tSchema = 6;   //TerraformMessages are message type 5
    int type; // 1 is landscaper
    int bitsPerType = 3;

    public TerraformMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(tSchema);
        type = -1;
    }

    //Use for recieved message.
    public TerraformMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.schema=tSchema;
        readType();
    }

    boolean writeType(int t) {
        type = t;
        return writeToArray(type, bitsPerType);
    }

    void readType() {
        type = readFromArray(headerLen + schemaLen, bitsPerType);
    }

}
