import java.lang.Math;
import java.lang.StringBuilder;

public class Mask {

   static int writtenTo = 0;

   public static void main(String args[]) {
      int[] actualMessage = new int[7];
      actualMessage = addToArr(actualMessage, 2, 3);
      actualMessage = addToArr(actualMessage, 12, 32);
      actualMessage = addToArr(actualMessage, 6, 6);
      actualMessage = addToArr(actualMessage, 1, 1);
      actualMessage = addToArr(actualMessage, 1, 1);
      actualMessage = addToArr(actualMessage, 3, 3);
      actualMessage = addToArr(actualMessage, 9, 5);
      actualMessage = addToArr(actualMessage, 3, 3);
      actualMessage = addToArr(actualMessage, 63, 6);
      printArray(actualMessage);
   }

   public static int[] addToArr(int actualMessage[], int value, int numBits) {
      int arrIndexStart = intIndex(writtenTo);
      int arrIndexEnd = intIndex(numBits+writtenTo);
      int alreadyUsed = 0;
      for(int i=arrIndexStart; i<=arrIndexEnd; i++) {
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

      return actualMessage;
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
