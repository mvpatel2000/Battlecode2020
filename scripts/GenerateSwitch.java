import java.lang.Math;
import java.lang.StringBuilder; 

public class GenerateSwitch {
    public static void main(String args[]) {
    
        String out = "";
        
        for(int start = 0; start<32; start++) {
            for(int end = start; end<32; end++) {
                out += bitmask(start, end);  
            }
         }
         System.out.println(out);
    }
            
    public static String bitmask(int start, int end) {
        int mask = 32*start + end;
        int bitmask = -1;
        if(end==31) {
            bitmask = 0;
        } else {
            bitmask = bitmask >>> (end+1);
        }
        int bitmask2 = -1;
        bitmask2 = bitmask2 >>> (start);
        
        int read = (bitmask ^ bitmask2);
        int write = ~(bitmask ^ bitmask2);
        
        String out = " case " + Integer.toString(mask) + ": " + " if(read) {return " + Integer.toString(read) + ";} else {return " + Integer.toString(write) +";} \n";
        
        return out;
    }
}