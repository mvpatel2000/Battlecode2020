package sprint;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

public class Message {

    final int numIntsPerMessage = 7;
    final int msgLen = numIntsPerMessage*32;
    int[] actualMessage;
    int writtenTo;  // number of bits written to so far.

    int MAP_HEIGHT;
    int MAP_WIDTH;
    int team;

    int headerlen = 16;
    int schema = 0; //default message type
    int schemalen = 3;

    public Message(int myMAP_HEIGHT, int myMAP_WIDTH, int myTeam) {
        actualMessage = new int[7];
        writtenTo = 0;
        MAP_HEIGHT = myMAP_HEIGHT;
        MAP_WIDTH = myMAP_WIDTH;
        team = myTeam;
        generateHeader();
    }

    //Set and write the type of message.
    //0 is Default. 1 is SoupMessage. 2 is MinePatchmessage.
    //Can only handle 2^schemaLen types of messages.
    boolean writeSchema(int mySchema) {
        schema = mySchema;
        return writeToArray(schema, schemaLen);
    }

    boolean generateHeader() {
        int header = (team+1)*MAP_HEIGHT*MAP_WIDTH % (1 << headerlen+1) - 1;
        return writeToArray(header, 16);
    }

    int[] getMessage() {
        return actualMessage;
    }

    int getBitsRemaining() {
        return msgLen - writtenTo;
    }

    //Can only write numbers of length 0 to 32
    //It is up to the caller to provide enough bits to write the number
    //Otherwise, the function will not work. It will only write the first n
    //digits.
    //If providing a number with excess bits (numBits >> 2^value), the number will be
    //at the right end of the slot (the excess bits will be turned into leading zeros).
    boolean writeToArray(int value, int numBits) {
        if (numBits+writtenTo > msgLen) {
            return false;
        } else {
            int arrIndexStart = intIndex(writtenTo);
            int arrIndexEnd = intIndex(numBits+writtenTo);
            int alreadyUsed = 0;
            for(int i=arrIndexStart; i<=arrIndexEnd; i++) {
                //calculate where the new number should go
                //and how many bits can be written to each integer
                int integerBitBegin = whichBit(i, writtenTo);
                int integerBitEnd = whichBit(i, numBits+writtenTo-1);
                int lenNum = integerBitEnd - integerBitBegin + 1;
                int nRemaining = numBits - alreadyUsed - lenNum;

                //shifting the new number to the next open slot
                int val = value;
                val = val << (32 - numBits + alreadyUsed);
                val = val >>> (32 - numBits + alreadyUsed + nRemaining);
                val =  val << (32-integerBitBegin-numBits + nRemaining + alreadyUsed);

                //bitmask to zero out new number's spot
                int bitmask = -1;
                if(integerBitEnd==31) {
                  bitmask = 0;
                } else {
                  bitmask = bitmask >>> (integerBitEnd+1);
                }
                int bitmask2 = -1;
                bitmask2 = bitmask2 >>> (integerBitBegin);
                int bitmask3 = ~(bitmask ^ bitmask2);

                //applying bitmask and new number to proper location in message int array
                actualMessage[i] &= bitmask3;
                actualMessage[i] |= val;
                alreadyUsed += lenNum;
            }
            writtenTo += numBits;
        }
        return true;
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

     //Bits are zero indexed. Put the bit you want to begin reading read from,
     //_ _ _ 0 1 0 _ , so reading 010, starting from the 0 would be readfromArray(4, 3)
     //Reading from the second integer requires a beginBit>32.
    int readFromArray(int beginBit, int numBits) {
        int arrIndexStart = intIndex(beginBit);
        int arrIndexEnd = intIndex(numBits+beginBit);
        int alreadyUsed = 0;
        int output = 0;
        for(int i=arrIndexStart; i<=arrIndexEnd; i++) {
             int integerBitBegin = whichBit(i, beginBit);
             int integerBitEnd = whichBit(i, numBits+beginBit-1);
             int lenNum = integerBitEnd - integerBitBegin + 1;
             int nRemaining = numBits - alreadyUsed - lenNum;

             //mask off proper digits for reading
             int bitmask = -1;
             if(integerBitEnd==31) {
               bitmask = 0;
             } else {
               bitmask = bitmask >>> (integerBitEnd+1);
             }
             int bitmask2 = -1;
             bitmask2 = bitmask2 >>> (integerBitBegin);
             int bitmask3 = (bitmask ^ bitmask2);
             int bob = (actualMessage[i] & bitmask3);
             output |= (bob >>> (32 - integerBitBegin - numBits + alreadyUsed + nRemaining));

             alreadyUsed += lenNum;
             output = output << nRemaining;

            if(nRemaining==0) {
               break;
            }
        }
        return output;
    }

}
