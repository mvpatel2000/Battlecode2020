package qual;

public class RushCommitMessage extends Message {

    final int rSchema = 7;   //BuiltMessages are message type 5
    int typeOfCommit; // 1 is rush commit standard,
                      // 2 is enemy is rushing/being aggressive,
                      // 3 is enemy has stopped rushing,
                      // 4 is individual drone success
                      // 5 is team drone success
    int bitsPerType = 3;

    public RushCommitMessage(int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(myMapHeight, myMapWidth, myTeam, roundNum);
        this.writeSchema(rSchema);
        typeOfCommit = -1;
    }

    //Use for recieved message.
    public RushCommitMessage(int[] recieved, int myMapHeight, int myMapWidth, int myTeam, int roundNum) {
        super(recieved, myMapHeight, myMapWidth, myTeam, roundNum);
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
