import java.lang.Math;
import java.lang.StringBuilder; 

public class Comms {
   
   static int writtenTo = 0;
   
   public static void main(String args[]) {
      int first = 98234*(0+1)*41*32 % ((1 << 16) - 1);
      System.out.println(intToString(first, 4));
      System.out.println(first);
      int[] actualMessage = {-1594993405, 1210056704, 0, 0, 0, 0, 0};
      readFromArray2(actualMessage, 0, 16);
      readFromArray2(actualMessage, 16, 3);
      readFromArray2(actualMessage, 19, 6);
      readFromArray2(actualMessage, 25, 6);
      readFromArray2(actualMessage, 31, 6);
      readFromArray2(actualMessage, 37, 6);

   }
 
    public static int bitmask(int start, int end, boolean read) {
        int bitmask = -1;
        if(end==31) {
            bitmask = 0;
        } else {
            bitmask = bitmask >>> (end+1);
        }
        int bitmask2 = -1;
        bitmask2 = bitmask2 >>> (start);
        //for   reading
        if(read) {
            return (bitmask ^ bitmask2);
        }
        //for writing
        return ~(bitmask ^ bitmask2);
   }
   
   public static void readFromArray2(int[] actualMessage, int beginBit, int numBits) {
      int arrIndexStart = intIndex(beginBit);
      int arrIndexEnd = intIndex(numBits+beginBit-1);
      int integerBitBegin = whichBit(arrIndexStart, beginBit);
      int integerBitEnd = whichBit(arrIndexEnd, numBits+beginBit-1);
      int output = 0;
      if(arrIndexStart==arrIndexEnd) {
          int bitm = bitmask(integerBitBegin, integerBitEnd, true);
          output = (actualMessage[arrIndexStart] & bitm) >>> (32 - integerBitBegin - numBits);
      } else {
            int bitm = bitmask(integerBitBegin, 31, true);
            int bitm2 = bitmask(0, integerBitEnd, true);
            output = (actualMessage[arrIndexStart] & bitm) >>> (32 - integerBitBegin - numBits + integerBitEnd+1);
            output = output << integerBitEnd+1;
            output |= (actualMessage[arrIndexEnd] & bitm2) >>> (32 - numBits + 32 - integerBitBegin);
      }
      System.out.println(output);
   }
   
    public static int[] writeToArray(int[] actualMessage, int value, int numBits) {
        int arrIndexStart = intIndex(writtenTo);
        int arrIndexEnd = intIndex(numBits+writtenTo-1);
        int integerBitBegin =   whichBit(arrIndexStart, writtenTo);
        int integerBitEnd   = whichBit(arrIndexEnd, numBits+writtenTo-1);
        
        if(arrIndexStart==arrIndexEnd) {
              int bitm = bitmask(integerBitBegin, integerBitEnd, false);
              value = value << (32-integerBitBegin-numBits);
              actualMessage[arrIndexStart] &= bitm;
              actualMessage[arrIndexStart] |= value;
        } else {
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
        return actualMessage;
    }
     
   public static void readFromArray(int[] actualMessage, int beginBit, int numBits) {
      int arrIndexStart = intIndex(beginBit);
      int arrIndexEnd = intIndex(numBits+beginBit);
      int alreadyUsed = 0;
      int output = 0;
      for(int i=arrIndexStart; i<=arrIndexEnd; i++) {
          System.out.println(output);
          System.out.println("Written to: " + Integer.toString(beginBit));
          int integerBitBegin = whichBit(i, beginBit);
          System.out.println("Integer bit Begin: " + Integer.toString(integerBitBegin));
          int integerBitEnd = whichBit(i, numBits+beginBit-1);
          System.out.println("Integer bit End: " + Integer.toString(integerBitEnd));
          int lenNum = integerBitEnd - integerBitBegin + 1;
          int nRemaining = numBits - alreadyUsed - lenNum;
          int bitmask = -1;
          if(integerBitEnd==31) {
            bitmask = 0;
          } else {
            bitmask = bitmask >>> (integerBitEnd+1);
          }
          int bitmask2 = -1;
          bitmask2 = bitmask2 >>> (integerBitBegin);
          int bitmask3 = (bitmask ^ bitmask2);
          System.out.println(intToString(bitmask3, 4));
          System.out.println("-----Final array element-----:");
          System.out.println(intToString(actualMessage[i], 4));
          System.out.println("^^^^^Final array element^^^^^:");
          int bob = (actualMessage[i] & bitmask3);
          System.out.println(intToString(bob, 4));
          output |= (bob >>> (32 - integerBitBegin - numBits + alreadyUsed + nRemaining));
          alreadyUsed += lenNum;
          output = output << nRemaining;
         if(nRemaining==0) {
            break;
         }
      }
      System.out.println("OUTPUT  " + Integer.toString(output));
   }
   
   public static int[] addToArr(int[] actualMessage, int value, int numBits) {
      int arrIndexStart = intIndex(writtenTo);
      int arrIndexEnd = intIndex(numBits+writtenTo);
      //System.out.println("Index Start: " + Integer.toString(arrIndexStart));
      //System.out.println("Index End: " + Integer.toString(arrIndexEnd));

      int alreadyUsed = 0;
      for(int i=arrIndexStart; i<=arrIndexEnd; i++) {
          //System.out.println("Written to: " + Integer.toString(writtenTo));
          int integerBitBegin = whichBit(i, writtenTo);
          //System.out.println("Integer bit Begin: " + Integer.toString(integerBitBegin));
          int integerBitEnd = whichBit(i, numBits+writtenTo-1);
          //System.out.println("Integer bit End: " + Integer.toString(integerBitEnd));
          int lenNum = integerBitEnd - integerBitBegin + 1;
          int nRemaining = numBits - alreadyUsed - lenNum;
          int val = value;
          //System.out.println("Begin values");
          //System.out.println(intToString(val, 4));

          val = val << (32 - numBits + alreadyUsed);
          //System.out.println(intToString(val, 4));

          val = val >>> (32 - numBits + alreadyUsed + nRemaining);
          //System.out.println(intToString(val, 4));
          val =  val << (32-integerBitBegin-numBits + nRemaining + alreadyUsed);
          //System.out.println(intToString(val, 4));

          //System.out.println("Begin bitmask");
          int bitmask = -1;
          if(integerBitEnd==31) {
            bitmask = 0;
          } else {
            bitmask = bitmask >>> (integerBitEnd+1);
          }
          //System.out.println(intToString(bitmask, 4));

          int bitmask2 = -1;
          bitmask2 = bitmask2 >>> (integerBitBegin);
          //System.out.println(intToString(bitmask2, 4));
          int bitmask3 = ~(bitmask ^ bitmask2);
          //System.out.println(intToString(bitmask3, 4));
          actualMessage[i] &= bitmask3;
          actualMessage[i] |= val;
          alreadyUsed += lenNum;
          //System.out.println("-----Final array element-----:");
          //System.out.println(intToString(actualMessage[i], 4));
          //System.out.println("^^^^^Final array element^^^^^:");
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
    
  
   public static void printArray(int[] arr) {
      for (int i=0; i<arr.length; i++) {
         System.out.println(intToString(arr[i], 4));
      }
      System.out.println();
   }   
   
   public static String intToString(int number, int groupSize) {
       StringBuilder result = new StringBuilder();
   
       for(int i = 31; i >= 0 ; i--) {
           int mask = 1 << i;
           result.append((number & mask) != 0 ? "1" : "0");
   
           if (i % groupSize == 0)
               result.append(" ");
       }
       result.replace(result.length() - 1, result.length(), "");
   
       return result.toString();
   }
}