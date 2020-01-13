package sprint;

public class Message {

    final int numIntsPerMessage = 7;
    final int msgLen = numIntsPerMessage*32;
    int[] actualMessage;
    int writtenTo;  // number of bits written to so far.

    //for generating header
    final int MAP_HEIGHT;
    final int MAP_WIDTH;
    final int team;
    final int arbitraryConstant = 89560;

    int headerLen = 16;
    int schema = 0; //default message type
    int schemaLen = 3;

    //origin true if our sent by our team, false if sent by enemy.
    //Only matters for recieved messages
    boolean origin;

    public Message(int myMapHeight, int myMapWidth, int myTeam) {
        actualMessage = new int[7];
        writtenTo = 0;
        MAP_HEIGHT = myMapHeight;
        MAP_WIDTH = myMapWidth;
        team = myTeam;
        origin = true;
        generateHeader();
    }

    //Use this constructor for messages recieved
    public Message(int[] recieved, int myMapHeight, int myMapWidth, int myTeam) {
        if(recieved.length==7) {
            actualMessage = recieved;
        } else {
            //this should never happen
            //only call this constructor on a message of valid length
            int k= Math.min(recieved.length, 7);
            actualMessage = new int[7];
            for (int i=0; i<k; i++) {
                actualMessage[i] = recieved[i];
            }
        }
        //cannot write to recieved messages for safety
        writtenTo = msgLen-1;
        MAP_HEIGHT = myMapHeight;
        MAP_WIDTH = myMapWidth;
        team = myTeam;
        origin = getOrigin();
        schema = getSchema();
    }


    /**
     * The following methods are helpful for reading messages.
     *
     */
     //Returns true if header matches our team's header
     //Always check before using message content.
     boolean getOrigin() {
         return readFromArray(0, 16) == arbitraryConstant*(team+1)*MAP_HEIGHT*MAP_WIDTH % ((1 << headerLen) - 1);
     }

     //Type of message
     //After recieving a message, use this
     //Then construct a message of a subclass
     //depending on the schema (e.g. create a SoupMessage if getSchema() = 1).
     int getSchema() {
         return readFromArray(headerLen, schemaLen);
     }

    /**
     * The following methods are helpful for writing messages.
     *
     */

    //Set and write the type of message.
    //0 is Default. 1 is SoupMessage. 2 is MinePatchmessage.
    //Can only handle 2^schemaLen types of messages.
    boolean writeSchema(int mySchema) {
        schema = mySchema;
        return writeToArray(schema, schemaLen);
    }

    boolean generateHeader() {
        int header = arbitraryConstant*(team+1)*MAP_HEIGHT*MAP_WIDTH % ((1 << headerLen) - 1);
        return writeToArray(header, 16);
    }

    int[] getMessage() {
        return actualMessage;
    }

    int getBitsRemaining() {
        return msgLen - writtenTo;
    }

    /*
     * Low-level read and write methods based on bit masking.
     * Reading and writing are supported for any number of length 0-32 bits (inclusive)
     * Takes constant time regardless of length of number written.
     */

    //Can only write numbers of length 0 to 32
    //It is up to the caller to provide enough bits to write the number
    //Otherwise, the function will not work. It will only write the first numBits
    //digits.
    //If providing a number with excess bits (numBits >> 2^value), the number will be
    //at the right end of the slot (the excess bits will be turned into leading zeros).
    boolean writeToArray(int value, int numBits) {
        if (numBits+writtenTo > msgLen-1) {
            return false;
        } else {
            int arrIndexStart = intIndex(writtenTo);
            int arrIndexEnd = intIndex(numBits+writtenTo-1);
            int integerBitBegin = whichBit(arrIndexStart, writtenTo);
            int integerBitEnd = whichBit(arrIndexEnd, numBits+writtenTo-1);
            //if write is contained in single integer
            if(arrIndexStart==arrIndexEnd) {
                  int bitm = bitmask(integerBitBegin, integerBitEnd, false);
                  value = value << (32-integerBitBegin-numBits);
                  actualMessage[arrIndexStart] &= bitm;
                  actualMessage[arrIndexStart] |= value;
            } else {
                //if write spans two integers
                int bitm = bitmask(integerBitBegin, 31, false);
                int bitm2 = bitmask(0, integerBitEnd, false);

                int part1 = value;
                int part2 = value;
                int part1len = 32-integerBitBegin;
                int part2len = integerBitEnd+1;

                part1 = part1 >>> part2len;
                part2 = part2 << (32-part2len);

                actualMessage[arrIndexStart] &= bitm;
                actualMessage[arrIndexStart] |= part1;
                actualMessage[arrIndexEnd] &= bitm2;
                actualMessage[arrIndexEnd] |= part2;
            }
            writtenTo += numBits;
        }
        return true;
    }

     //Bits are zero indexed. Put the bit you want to begin reading read from,
     //_ _ _ 0 1 0 _ , so reading 010, starting from the 0 would be readfromArray(3, 3)
     //beginBit can be anywhere in [0, 32*7-1].
    int readFromArray(int beginBit, int numBits) {
        int arrIndexStart = intIndex(beginBit);
        int arrIndexEnd = intIndex(numBits+beginBit-1);
        int integerBitBegin = whichBit(arrIndexStart, beginBit);
        int integerBitEnd = whichBit(arrIndexEnd, numBits+beginBit-1);
        int output = 0;
        //if read is contained in a single integer
        if(arrIndexStart==arrIndexEnd) {
            int bitm = bitmask(integerBitBegin, integerBitEnd, true);
            output = (actualMessage[arrIndexStart] & bitm) >>> (32 - integerBitBegin - numBits);
        } else {
              //if the read spans two integers
              int bitm = bitmask(integerBitBegin, 31, true);
              int bitm2 = bitmask(0, integerBitEnd, true);
              output = (actualMessage[arrIndexStart] & bitm) >>> (32 - integerBitBegin - numBits + integerBitEnd+1);
              output = output << integerBitEnd+1;
              output |= (actualMessage[arrIndexEnd] & bitm2) >>> (32 - numBits + 32 - integerBitBegin);
        }
        return output;
    }
    /**
     * Helper methods for read and write functions.
     */
    //generates bitmask given start and end index of digit in integer
    public static int bitmask(int start, int end, boolean read) {
        int bitmask = -1;
        if(end==31) {
            bitmask = 0;
        } else {
            bitmask = bitmask >>> (end+1);
        }
        int bitmask2 = -1;
        bitmask2 = bitmask2 >>> (start);
        //for reading
        if(read) {
            return (bitmask ^ bitmask2);
        }
        //for writing
        return ~(bitmask ^ bitmask2);
    }


    public static int whichBit(int index, int bitloc) {
         if(bitloc<index*32) {
             return 0;    //first bit of the number
         }
         if(bitloc>=(index+1)*32) {
             return 31;  //last bit of the number
         }
         return bitloc%32;
     }

     public static int intIndex(int bitlocation) {
         return (bitlocation)/32;
     }


}
