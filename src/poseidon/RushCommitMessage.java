package poseidon;

public class RushCommitMessage extends Message {

    final int rSchema = 7;   //BuiltMessages are message type 5
    int typeOfCommit; // 1 is rush commit standard, 2 is enemy is rushing/being aggressive.
    int bitsPerType = 3;

    public RushCommitMessage(int myMapHeight, int myMapWidth, int myTeam) {
        super(myMapHeight, myMapWidth, myTeam);
        this.writeSchema(rSchema);
        typeOfCommit = -1;
    }

    //Use for recieved message.
    public RushCommitMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        super(recieved, myMapHeight, myMapWidth, myTeam);
        this.schema=rSchema;
        readTypeOfCommit();
    }

    boolean writeTypeOfCommit(int type) {
        typeOfCommit = type;
        return writeToArray(typeOfCommit, bitsPerType);
    }

    void readTypeOfCommit() {
        typeOfCommit = readFromArray(headerLen + schemaLen, bitsPerType);
    }

}
