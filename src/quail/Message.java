package quail;

public class Message {

    final int numIntsPerMessage = 7;
    final int msgLen = numIntsPerMessage*32;
    int[] actualMessage;
    int writtenTo;  // number of bits written to so far.

    //for generating header
    final int MAP_HEIGHT;
    final int MAP_WIDTH;
    final int team;
    final int arbitraryConstant = 64556; //make sure this is the same constant in Robot.java

    int headerLen = 16;
    final int header;

    int schema = 0; //default message type
    int schemaLen = 3;

    //origin true if our sent by our team, false if sent by enemy.
    //Only matters for recieved messages
    boolean origin;

    public Message(int myMapHeight, int myMapWidth, int myTeam, int roundNumber) {
        actualMessage = new int[7];
        writtenTo = 0;
        MAP_HEIGHT = myMapHeight;
        MAP_WIDTH = myMapWidth;
        team = myTeam;
        origin = true;
        header = Math.floorMod(arbitraryConstant*(team+1)*MAP_HEIGHT*MAP_WIDTH*roundNumber, ((1 << headerLen) - 1));
        generateHeader();
    }

    //Use this constructor for messages recieved
    //Assuming the only messages using this constructor are ally messages
    //Check origin of incoming messages using method in Robot.java
    public Message(int[] recieved, int myMapHeight, int myMapWidth, int myTeam, int roundNumber) {
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
        origin = true;
        header = Math.floorMod(arbitraryConstant*(team+1)*MAP_HEIGHT*MAP_WIDTH*roundNumber, ((1 << headerLen) - 1));
        //origin = getOrigin();
        //schema = getSchema();
    }


    /**
     * The following methods are helpful for reading messages.
     *
     */
     //Returns true if header matches our team's header
     //Always check before using message content.
     boolean getOrigin() {
         return readFromArray(0, 16) == header;
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
            int arrIndexStart = (writtenTo)>>>5;
            int arrIndexEnd = (numBits+writtenTo-1)>>>5;
            int integerBitBegin = whichBit2(arrIndexStart, writtenTo);
            int integerBitEnd = whichBit2(arrIndexEnd, numBits+writtenTo-1);
            //if write is contained in single integer
            if(arrIndexStart==arrIndexEnd) {
                  int bitm = bitmask2(integerBitBegin, integerBitEnd, false);
                  value = value << (32-integerBitBegin-numBits);
                  actualMessage[arrIndexStart] &= bitm;
                  actualMessage[arrIndexStart] |= value;
            } else {
                //if write spans two integers
                int bitm = bitmask2(integerBitBegin, 31, false);
                int bitm2 = bitmask2(0, integerBitEnd, false);

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
        int arrIndexStart = beginBit>>>5;
        int arrIndexEnd = (numBits+beginBit-1)>>>5;
        int integerBitBegin = whichBit2(arrIndexStart, beginBit);
        int integerBitEnd = whichBit2(arrIndexEnd, numBits+beginBit-1);
        int output = 0;
        //if read is contained in a single integer
        if(arrIndexStart==arrIndexEnd) {
            int bitm = bitmask2(integerBitBegin, integerBitEnd, true);
            output = (actualMessage[arrIndexStart] & bitm) >>> (32 - integerBitBegin - numBits);
        } else {
              //if the read spans two integers
              int bitm = bitmask2(integerBitBegin, 31, true);
              int bitm2 = bitmask2(0, integerBitEnd, true);
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

    public static int bitmask2(int start, int end, boolean read) {
        int num = start*32+end;
        switch (num) {
            case 0:  if(read) {return -2147483648;} else {return 2147483647;}
             case 1:  if(read) {return -1073741824;} else {return 1073741823;}
             case 2:  if(read) {return -536870912;} else {return 536870911;}
             case 3:  if(read) {return -268435456;} else {return 268435455;}
             case 4:  if(read) {return -134217728;} else {return 134217727;}
             case 5:  if(read) {return -67108864;} else {return 67108863;}
             case 6:  if(read) {return -33554432;} else {return 33554431;}
             case 7:  if(read) {return -16777216;} else {return 16777215;}
             case 8:  if(read) {return -8388608;} else {return 8388607;}
             case 9:  if(read) {return -4194304;} else {return 4194303;}
             case 10:  if(read) {return -2097152;} else {return 2097151;}
             case 11:  if(read) {return -1048576;} else {return 1048575;}
             case 12:  if(read) {return -524288;} else {return 524287;}
             case 13:  if(read) {return -262144;} else {return 262143;}
             case 14:  if(read) {return -131072;} else {return 131071;}
             case 15:  if(read) {return -65536;} else {return 65535;}
             case 16:  if(read) {return -32768;} else {return 32767;}
             case 17:  if(read) {return -16384;} else {return 16383;}
             case 18:  if(read) {return -8192;} else {return 8191;}
             case 19:  if(read) {return -4096;} else {return 4095;}
             case 20:  if(read) {return -2048;} else {return 2047;}
             case 21:  if(read) {return -1024;} else {return 1023;}
             case 22:  if(read) {return -512;} else {return 511;}
             case 23:  if(read) {return -256;} else {return 255;}
             case 24:  if(read) {return -128;} else {return 127;}
             case 25:  if(read) {return -64;} else {return 63;}
             case 26:  if(read) {return -32;} else {return 31;}
             case 27:  if(read) {return -16;} else {return 15;}
             case 28:  if(read) {return -8;} else {return 7;}
             case 29:  if(read) {return -4;} else {return 3;}
             case 30:  if(read) {return -2;} else {return 1;}
             case 31:  if(read) {return -1;} else {return 0;}
             case 33:  if(read) {return 1073741824;} else {return -1073741825;}
             case 34:  if(read) {return 1610612736;} else {return -1610612737;}
             case 35:  if(read) {return 1879048192;} else {return -1879048193;}
             case 36:  if(read) {return 2013265920;} else {return -2013265921;}
             case 37:  if(read) {return 2080374784;} else {return -2080374785;}
             case 38:  if(read) {return 2113929216;} else {return -2113929217;}
             case 39:  if(read) {return 2130706432;} else {return -2130706433;}
             case 40:  if(read) {return 2139095040;} else {return -2139095041;}
             case 41:  if(read) {return 2143289344;} else {return -2143289345;}
             case 42:  if(read) {return 2145386496;} else {return -2145386497;}
             case 43:  if(read) {return 2146435072;} else {return -2146435073;}
             case 44:  if(read) {return 2146959360;} else {return -2146959361;}
             case 45:  if(read) {return 2147221504;} else {return -2147221505;}
             case 46:  if(read) {return 2147352576;} else {return -2147352577;}
             case 47:  if(read) {return 2147418112;} else {return -2147418113;}
             case 48:  if(read) {return 2147450880;} else {return -2147450881;}
             case 49:  if(read) {return 2147467264;} else {return -2147467265;}
             case 50:  if(read) {return 2147475456;} else {return -2147475457;}
             case 51:  if(read) {return 2147479552;} else {return -2147479553;}
             case 52:  if(read) {return 2147481600;} else {return -2147481601;}
             case 53:  if(read) {return 2147482624;} else {return -2147482625;}
             case 54:  if(read) {return 2147483136;} else {return -2147483137;}
             case 55:  if(read) {return 2147483392;} else {return -2147483393;}
             case 56:  if(read) {return 2147483520;} else {return -2147483521;}
             case 57:  if(read) {return 2147483584;} else {return -2147483585;}
             case 58:  if(read) {return 2147483616;} else {return -2147483617;}
             case 59:  if(read) {return 2147483632;} else {return -2147483633;}
             case 60:  if(read) {return 2147483640;} else {return -2147483641;}
             case 61:  if(read) {return 2147483644;} else {return -2147483645;}
             case 62:  if(read) {return 2147483646;} else {return -2147483647;}
             case 63:  if(read) {return 2147483647;} else {return -2147483648;}
             case 66:  if(read) {return 536870912;} else {return -536870913;}
             case 67:  if(read) {return 805306368;} else {return -805306369;}
             case 68:  if(read) {return 939524096;} else {return -939524097;}
             case 69:  if(read) {return 1006632960;} else {return -1006632961;}
             case 70:  if(read) {return 1040187392;} else {return -1040187393;}
             case 71:  if(read) {return 1056964608;} else {return -1056964609;}
             case 72:  if(read) {return 1065353216;} else {return -1065353217;}
             case 73:  if(read) {return 1069547520;} else {return -1069547521;}
             case 74:  if(read) {return 1071644672;} else {return -1071644673;}
             case 75:  if(read) {return 1072693248;} else {return -1072693249;}
             case 76:  if(read) {return 1073217536;} else {return -1073217537;}
             case 77:  if(read) {return 1073479680;} else {return -1073479681;}
             case 78:  if(read) {return 1073610752;} else {return -1073610753;}
             case 79:  if(read) {return 1073676288;} else {return -1073676289;}
             case 80:  if(read) {return 1073709056;} else {return -1073709057;}
             case 81:  if(read) {return 1073725440;} else {return -1073725441;}
             case 82:  if(read) {return 1073733632;} else {return -1073733633;}
             case 83:  if(read) {return 1073737728;} else {return -1073737729;}
             case 84:  if(read) {return 1073739776;} else {return -1073739777;}
             case 85:  if(read) {return 1073740800;} else {return -1073740801;}
             case 86:  if(read) {return 1073741312;} else {return -1073741313;}
             case 87:  if(read) {return 1073741568;} else {return -1073741569;}
             case 88:  if(read) {return 1073741696;} else {return -1073741697;}
             case 89:  if(read) {return 1073741760;} else {return -1073741761;}
             case 90:  if(read) {return 1073741792;} else {return -1073741793;}
             case 91:  if(read) {return 1073741808;} else {return -1073741809;}
             case 92:  if(read) {return 1073741816;} else {return -1073741817;}
             case 93:  if(read) {return 1073741820;} else {return -1073741821;}
             case 94:  if(read) {return 1073741822;} else {return -1073741823;}
             case 95:  if(read) {return 1073741823;} else {return -1073741824;}
             case 99:  if(read) {return 268435456;} else {return -268435457;}
             case 100:  if(read) {return 402653184;} else {return -402653185;}
             case 101:  if(read) {return 469762048;} else {return -469762049;}
             case 102:  if(read) {return 503316480;} else {return -503316481;}
             case 103:  if(read) {return 520093696;} else {return -520093697;}
             case 104:  if(read) {return 528482304;} else {return -528482305;}
             case 105:  if(read) {return 532676608;} else {return -532676609;}
             case 106:  if(read) {return 534773760;} else {return -534773761;}
             case 107:  if(read) {return 535822336;} else {return -535822337;}
             case 108:  if(read) {return 536346624;} else {return -536346625;}
             case 109:  if(read) {return 536608768;} else {return -536608769;}
             case 110:  if(read) {return 536739840;} else {return -536739841;}
             case 111:  if(read) {return 536805376;} else {return -536805377;}
             case 112:  if(read) {return 536838144;} else {return -536838145;}
             case 113:  if(read) {return 536854528;} else {return -536854529;}
             case 114:  if(read) {return 536862720;} else {return -536862721;}
             case 115:  if(read) {return 536866816;} else {return -536866817;}
             case 116:  if(read) {return 536868864;} else {return -536868865;}
             case 117:  if(read) {return 536869888;} else {return -536869889;}
             case 118:  if(read) {return 536870400;} else {return -536870401;}
             case 119:  if(read) {return 536870656;} else {return -536870657;}
             case 120:  if(read) {return 536870784;} else {return -536870785;}
             case 121:  if(read) {return 536870848;} else {return -536870849;}
             case 122:  if(read) {return 536870880;} else {return -536870881;}
             case 123:  if(read) {return 536870896;} else {return -536870897;}
             case 124:  if(read) {return 536870904;} else {return -536870905;}
             case 125:  if(read) {return 536870908;} else {return -536870909;}
             case 126:  if(read) {return 536870910;} else {return -536870911;}
             case 127:  if(read) {return 536870911;} else {return -536870912;}
             case 132:  if(read) {return 134217728;} else {return -134217729;}
             case 133:  if(read) {return 201326592;} else {return -201326593;}
             case 134:  if(read) {return 234881024;} else {return -234881025;}
             case 135:  if(read) {return 251658240;} else {return -251658241;}
             case 136:  if(read) {return 260046848;} else {return -260046849;}
             case 137:  if(read) {return 264241152;} else {return -264241153;}
             case 138:  if(read) {return 266338304;} else {return -266338305;}
             case 139:  if(read) {return 267386880;} else {return -267386881;}
             case 140:  if(read) {return 267911168;} else {return -267911169;}
             case 141:  if(read) {return 268173312;} else {return -268173313;}
             case 142:  if(read) {return 268304384;} else {return -268304385;}
             case 143:  if(read) {return 268369920;} else {return -268369921;}
             case 144:  if(read) {return 268402688;} else {return -268402689;}
             case 145:  if(read) {return 268419072;} else {return -268419073;}
             case 146:  if(read) {return 268427264;} else {return -268427265;}
             case 147:  if(read) {return 268431360;} else {return -268431361;}
             case 148:  if(read) {return 268433408;} else {return -268433409;}
             case 149:  if(read) {return 268434432;} else {return -268434433;}
             case 150:  if(read) {return 268434944;} else {return -268434945;}
             case 151:  if(read) {return 268435200;} else {return -268435201;}
             case 152:  if(read) {return 268435328;} else {return -268435329;}
             case 153:  if(read) {return 268435392;} else {return -268435393;}
             case 154:  if(read) {return 268435424;} else {return -268435425;}
             case 155:  if(read) {return 268435440;} else {return -268435441;}
             case 156:  if(read) {return 268435448;} else {return -268435449;}
             case 157:  if(read) {return 268435452;} else {return -268435453;}
             case 158:  if(read) {return 268435454;} else {return -268435455;}
             case 159:  if(read) {return 268435455;} else {return -268435456;}
             case 165:  if(read) {return 67108864;} else {return -67108865;}
             case 166:  if(read) {return 100663296;} else {return -100663297;}
             case 167:  if(read) {return 117440512;} else {return -117440513;}
             case 168:  if(read) {return 125829120;} else {return -125829121;}
             case 169:  if(read) {return 130023424;} else {return -130023425;}
             case 170:  if(read) {return 132120576;} else {return -132120577;}
             case 171:  if(read) {return 133169152;} else {return -133169153;}
             case 172:  if(read) {return 133693440;} else {return -133693441;}
             case 173:  if(read) {return 133955584;} else {return -133955585;}
             case 174:  if(read) {return 134086656;} else {return -134086657;}
             case 175:  if(read) {return 134152192;} else {return -134152193;}
             case 176:  if(read) {return 134184960;} else {return -134184961;}
             case 177:  if(read) {return 134201344;} else {return -134201345;}
             case 178:  if(read) {return 134209536;} else {return -134209537;}
             case 179:  if(read) {return 134213632;} else {return -134213633;}
             case 180:  if(read) {return 134215680;} else {return -134215681;}
             case 181:  if(read) {return 134216704;} else {return -134216705;}
             case 182:  if(read) {return 134217216;} else {return -134217217;}
             case 183:  if(read) {return 134217472;} else {return -134217473;}
             case 184:  if(read) {return 134217600;} else {return -134217601;}
             case 185:  if(read) {return 134217664;} else {return -134217665;}
             case 186:  if(read) {return 134217696;} else {return -134217697;}
             case 187:  if(read) {return 134217712;} else {return -134217713;}
             case 188:  if(read) {return 134217720;} else {return -134217721;}
             case 189:  if(read) {return 134217724;} else {return -134217725;}
             case 190:  if(read) {return 134217726;} else {return -134217727;}
             case 191:  if(read) {return 134217727;} else {return -134217728;}
             case 198:  if(read) {return 33554432;} else {return -33554433;}
             case 199:  if(read) {return 50331648;} else {return -50331649;}
             case 200:  if(read) {return 58720256;} else {return -58720257;}
             case 201:  if(read) {return 62914560;} else {return -62914561;}
             case 202:  if(read) {return 65011712;} else {return -65011713;}
             case 203:  if(read) {return 66060288;} else {return -66060289;}
             case 204:  if(read) {return 66584576;} else {return -66584577;}
             case 205:  if(read) {return 66846720;} else {return -66846721;}
             case 206:  if(read) {return 66977792;} else {return -66977793;}
             case 207:  if(read) {return 67043328;} else {return -67043329;}
             case 208:  if(read) {return 67076096;} else {return -67076097;}
             case 209:  if(read) {return 67092480;} else {return -67092481;}
             case 210:  if(read) {return 67100672;} else {return -67100673;}
             case 211:  if(read) {return 67104768;} else {return -67104769;}
             case 212:  if(read) {return 67106816;} else {return -67106817;}
             case 213:  if(read) {return 67107840;} else {return -67107841;}
             case 214:  if(read) {return 67108352;} else {return -67108353;}
             case 215:  if(read) {return 67108608;} else {return -67108609;}
             case 216:  if(read) {return 67108736;} else {return -67108737;}
             case 217:  if(read) {return 67108800;} else {return -67108801;}
             case 218:  if(read) {return 67108832;} else {return -67108833;}
             case 219:  if(read) {return 67108848;} else {return -67108849;}
             case 220:  if(read) {return 67108856;} else {return -67108857;}
             case 221:  if(read) {return 67108860;} else {return -67108861;}
             case 222:  if(read) {return 67108862;} else {return -67108863;}
             case 223:  if(read) {return 67108863;} else {return -67108864;}
             case 231:  if(read) {return 16777216;} else {return -16777217;}
             case 232:  if(read) {return 25165824;} else {return -25165825;}
             case 233:  if(read) {return 29360128;} else {return -29360129;}
             case 234:  if(read) {return 31457280;} else {return -31457281;}
             case 235:  if(read) {return 32505856;} else {return -32505857;}
             case 236:  if(read) {return 33030144;} else {return -33030145;}
             case 237:  if(read) {return 33292288;} else {return -33292289;}
             case 238:  if(read) {return 33423360;} else {return -33423361;}
             case 239:  if(read) {return 33488896;} else {return -33488897;}
             case 240:  if(read) {return 33521664;} else {return -33521665;}
             case 241:  if(read) {return 33538048;} else {return -33538049;}
             case 242:  if(read) {return 33546240;} else {return -33546241;}
             case 243:  if(read) {return 33550336;} else {return -33550337;}
             case 244:  if(read) {return 33552384;} else {return -33552385;}
             case 245:  if(read) {return 33553408;} else {return -33553409;}
             case 246:  if(read) {return 33553920;} else {return -33553921;}
             case 247:  if(read) {return 33554176;} else {return -33554177;}
             case 248:  if(read) {return 33554304;} else {return -33554305;}
             case 249:  if(read) {return 33554368;} else {return -33554369;}
             case 250:  if(read) {return 33554400;} else {return -33554401;}
             case 251:  if(read) {return 33554416;} else {return -33554417;}
             case 252:  if(read) {return 33554424;} else {return -33554425;}
             case 253:  if(read) {return 33554428;} else {return -33554429;}
             case 254:  if(read) {return 33554430;} else {return -33554431;}
             case 255:  if(read) {return 33554431;} else {return -33554432;}
             case 264:  if(read) {return 8388608;} else {return -8388609;}
             case 265:  if(read) {return 12582912;} else {return -12582913;}
             case 266:  if(read) {return 14680064;} else {return -14680065;}
             case 267:  if(read) {return 15728640;} else {return -15728641;}
             case 268:  if(read) {return 16252928;} else {return -16252929;}
             case 269:  if(read) {return 16515072;} else {return -16515073;}
             case 270:  if(read) {return 16646144;} else {return -16646145;}
             case 271:  if(read) {return 16711680;} else {return -16711681;}
             case 272:  if(read) {return 16744448;} else {return -16744449;}
             case 273:  if(read) {return 16760832;} else {return -16760833;}
             case 274:  if(read) {return 16769024;} else {return -16769025;}
             case 275:  if(read) {return 16773120;} else {return -16773121;}
             case 276:  if(read) {return 16775168;} else {return -16775169;}
             case 277:  if(read) {return 16776192;} else {return -16776193;}
             case 278:  if(read) {return 16776704;} else {return -16776705;}
             case 279:  if(read) {return 16776960;} else {return -16776961;}
             case 280:  if(read) {return 16777088;} else {return -16777089;}
             case 281:  if(read) {return 16777152;} else {return -16777153;}
             case 282:  if(read) {return 16777184;} else {return -16777185;}
             case 283:  if(read) {return 16777200;} else {return -16777201;}
             case 284:  if(read) {return 16777208;} else {return -16777209;}
             case 285:  if(read) {return 16777212;} else {return -16777213;}
             case 286:  if(read) {return 16777214;} else {return -16777215;}
             case 287:  if(read) {return 16777215;} else {return -16777216;}
             case 297:  if(read) {return 4194304;} else {return -4194305;}
             case 298:  if(read) {return 6291456;} else {return -6291457;}
             case 299:  if(read) {return 7340032;} else {return -7340033;}
             case 300:  if(read) {return 7864320;} else {return -7864321;}
             case 301:  if(read) {return 8126464;} else {return -8126465;}
             case 302:  if(read) {return 8257536;} else {return -8257537;}
             case 303:  if(read) {return 8323072;} else {return -8323073;}
             case 304:  if(read) {return 8355840;} else {return -8355841;}
             case 305:  if(read) {return 8372224;} else {return -8372225;}
             case 306:  if(read) {return 8380416;} else {return -8380417;}
             case 307:  if(read) {return 8384512;} else {return -8384513;}
             case 308:  if(read) {return 8386560;} else {return -8386561;}
             case 309:  if(read) {return 8387584;} else {return -8387585;}
             case 310:  if(read) {return 8388096;} else {return -8388097;}
             case 311:  if(read) {return 8388352;} else {return -8388353;}
             case 312:  if(read) {return 8388480;} else {return -8388481;}
             case 313:  if(read) {return 8388544;} else {return -8388545;}
             case 314:  if(read) {return 8388576;} else {return -8388577;}
             case 315:  if(read) {return 8388592;} else {return -8388593;}
             case 316:  if(read) {return 8388600;} else {return -8388601;}
             case 317:  if(read) {return 8388604;} else {return -8388605;}
             case 318:  if(read) {return 8388606;} else {return -8388607;}
             case 319:  if(read) {return 8388607;} else {return -8388608;}
             case 330:  if(read) {return 2097152;} else {return -2097153;}
             case 331:  if(read) {return 3145728;} else {return -3145729;}
             case 332:  if(read) {return 3670016;} else {return -3670017;}
             case 333:  if(read) {return 3932160;} else {return -3932161;}
             case 334:  if(read) {return 4063232;} else {return -4063233;}
             case 335:  if(read) {return 4128768;} else {return -4128769;}
             case 336:  if(read) {return 4161536;} else {return -4161537;}
             case 337:  if(read) {return 4177920;} else {return -4177921;}
             case 338:  if(read) {return 4186112;} else {return -4186113;}
             case 339:  if(read) {return 4190208;} else {return -4190209;}
             case 340:  if(read) {return 4192256;} else {return -4192257;}
             case 341:  if(read) {return 4193280;} else {return -4193281;}
             case 342:  if(read) {return 4193792;} else {return -4193793;}
             case 343:  if(read) {return 4194048;} else {return -4194049;}
             case 344:  if(read) {return 4194176;} else {return -4194177;}
             case 345:  if(read) {return 4194240;} else {return -4194241;}
             case 346:  if(read) {return 4194272;} else {return -4194273;}
             case 347:  if(read) {return 4194288;} else {return -4194289;}
             case 348:  if(read) {return 4194296;} else {return -4194297;}
             case 349:  if(read) {return 4194300;} else {return -4194301;}
             case 350:  if(read) {return 4194302;} else {return -4194303;}
             case 351:  if(read) {return 4194303;} else {return -4194304;}
             case 363:  if(read) {return 1048576;} else {return -1048577;}
             case 364:  if(read) {return 1572864;} else {return -1572865;}
             case 365:  if(read) {return 1835008;} else {return -1835009;}
             case 366:  if(read) {return 1966080;} else {return -1966081;}
             case 367:  if(read) {return 2031616;} else {return -2031617;}
             case 368:  if(read) {return 2064384;} else {return -2064385;}
             case 369:  if(read) {return 2080768;} else {return -2080769;}
             case 370:  if(read) {return 2088960;} else {return -2088961;}
             case 371:  if(read) {return 2093056;} else {return -2093057;}
             case 372:  if(read) {return 2095104;} else {return -2095105;}
             case 373:  if(read) {return 2096128;} else {return -2096129;}
             case 374:  if(read) {return 2096640;} else {return -2096641;}
             case 375:  if(read) {return 2096896;} else {return -2096897;}
             case 376:  if(read) {return 2097024;} else {return -2097025;}
             case 377:  if(read) {return 2097088;} else {return -2097089;}
             case 378:  if(read) {return 2097120;} else {return -2097121;}
             case 379:  if(read) {return 2097136;} else {return -2097137;}
             case 380:  if(read) {return 2097144;} else {return -2097145;}
             case 381:  if(read) {return 2097148;} else {return -2097149;}
             case 382:  if(read) {return 2097150;} else {return -2097151;}
             case 383:  if(read) {return 2097151;} else {return -2097152;}
             case 396:  if(read) {return 524288;} else {return -524289;}
             case 397:  if(read) {return 786432;} else {return -786433;}
             case 398:  if(read) {return 917504;} else {return -917505;}
             case 399:  if(read) {return 983040;} else {return -983041;}
             case 400:  if(read) {return 1015808;} else {return -1015809;}
             case 401:  if(read) {return 1032192;} else {return -1032193;}
             case 402:  if(read) {return 1040384;} else {return -1040385;}
             case 403:  if(read) {return 1044480;} else {return -1044481;}
             case 404:  if(read) {return 1046528;} else {return -1046529;}
             case 405:  if(read) {return 1047552;} else {return -1047553;}
             case 406:  if(read) {return 1048064;} else {return -1048065;}
             case 407:  if(read) {return 1048320;} else {return -1048321;}
             case 408:  if(read) {return 1048448;} else {return -1048449;}
             case 409:  if(read) {return 1048512;} else {return -1048513;}
             case 410:  if(read) {return 1048544;} else {return -1048545;}
             case 411:  if(read) {return 1048560;} else {return -1048561;}
             case 412:  if(read) {return 1048568;} else {return -1048569;}
             case 413:  if(read) {return 1048572;} else {return -1048573;}
             case 414:  if(read) {return 1048574;} else {return -1048575;}
             case 415:  if(read) {return 1048575;} else {return -1048576;}
             case 429:  if(read) {return 262144;} else {return -262145;}
             case 430:  if(read) {return 393216;} else {return -393217;}
             case 431:  if(read) {return 458752;} else {return -458753;}
             case 432:  if(read) {return 491520;} else {return -491521;}
             case 433:  if(read) {return 507904;} else {return -507905;}
             case 434:  if(read) {return 516096;} else {return -516097;}
             case 435:  if(read) {return 520192;} else {return -520193;}
             case 436:  if(read) {return 522240;} else {return -522241;}
             case 437:  if(read) {return 523264;} else {return -523265;}
             case 438:  if(read) {return 523776;} else {return -523777;}
             case 439:  if(read) {return 524032;} else {return -524033;}
             case 440:  if(read) {return 524160;} else {return -524161;}
             case 441:  if(read) {return 524224;} else {return -524225;}
             case 442:  if(read) {return 524256;} else {return -524257;}
             case 443:  if(read) {return 524272;} else {return -524273;}
             case 444:  if(read) {return 524280;} else {return -524281;}
             case 445:  if(read) {return 524284;} else {return -524285;}
             case 446:  if(read) {return 524286;} else {return -524287;}
             case 447:  if(read) {return 524287;} else {return -524288;}
             case 462:  if(read) {return 131072;} else {return -131073;}
             case 463:  if(read) {return 196608;} else {return -196609;}
             case 464:  if(read) {return 229376;} else {return -229377;}
             case 465:  if(read) {return 245760;} else {return -245761;}
             case 466:  if(read) {return 253952;} else {return -253953;}
             case 467:  if(read) {return 258048;} else {return -258049;}
             case 468:  if(read) {return 260096;} else {return -260097;}
             case 469:  if(read) {return 261120;} else {return -261121;}
             case 470:  if(read) {return 261632;} else {return -261633;}
             case 471:  if(read) {return 261888;} else {return -261889;}
             case 472:  if(read) {return 262016;} else {return -262017;}
             case 473:  if(read) {return 262080;} else {return -262081;}
             case 474:  if(read) {return 262112;} else {return -262113;}
             case 475:  if(read) {return 262128;} else {return -262129;}
             case 476:  if(read) {return 262136;} else {return -262137;}
             case 477:  if(read) {return 262140;} else {return -262141;}
             case 478:  if(read) {return 262142;} else {return -262143;}
             case 479:  if(read) {return 262143;} else {return -262144;}
             case 495:  if(read) {return 65536;} else {return -65537;}
             case 496:  if(read) {return 98304;} else {return -98305;}
             case 497:  if(read) {return 114688;} else {return -114689;}
             case 498:  if(read) {return 122880;} else {return -122881;}
             case 499:  if(read) {return 126976;} else {return -126977;}
             case 500:  if(read) {return 129024;} else {return -129025;}
             case 501:  if(read) {return 130048;} else {return -130049;}
             case 502:  if(read) {return 130560;} else {return -130561;}
             case 503:  if(read) {return 130816;} else {return -130817;}
             case 504:  if(read) {return 130944;} else {return -130945;}
             case 505:  if(read) {return 131008;} else {return -131009;}
             case 506:  if(read) {return 131040;} else {return -131041;}
             case 507:  if(read) {return 131056;} else {return -131057;}
             case 508:  if(read) {return 131064;} else {return -131065;}
             case 509:  if(read) {return 131068;} else {return -131069;}
             case 510:  if(read) {return 131070;} else {return -131071;}
             case 511:  if(read) {return 131071;} else {return -131072;}
             case 528:  if(read) {return 32768;} else {return -32769;}
             case 529:  if(read) {return 49152;} else {return -49153;}
             case 530:  if(read) {return 57344;} else {return -57345;}
             case 531:  if(read) {return 61440;} else {return -61441;}
             case 532:  if(read) {return 63488;} else {return -63489;}
             case 533:  if(read) {return 64512;} else {return -64513;}
             case 534:  if(read) {return 65024;} else {return -65025;}
             case 535:  if(read) {return 65280;} else {return -65281;}
             case 536:  if(read) {return 65408;} else {return -65409;}
             case 537:  if(read) {return 65472;} else {return -65473;}
             case 538:  if(read) {return 65504;} else {return -65505;}
             case 539:  if(read) {return 65520;} else {return -65521;}
             case 540:  if(read) {return 65528;} else {return -65529;}
             case 541:  if(read) {return 65532;} else {return -65533;}
             case 542:  if(read) {return 65534;} else {return -65535;}
             case 543:  if(read) {return 65535;} else {return -65536;}
             case 561:  if(read) {return 16384;} else {return -16385;}
             case 562:  if(read) {return 24576;} else {return -24577;}
             case 563:  if(read) {return 28672;} else {return -28673;}
             case 564:  if(read) {return 30720;} else {return -30721;}
             case 565:  if(read) {return 31744;} else {return -31745;}
             case 566:  if(read) {return 32256;} else {return -32257;}
             case 567:  if(read) {return 32512;} else {return -32513;}
             case 568:  if(read) {return 32640;} else {return -32641;}
             case 569:  if(read) {return 32704;} else {return -32705;}
             case 570:  if(read) {return 32736;} else {return -32737;}
             case 571:  if(read) {return 32752;} else {return -32753;}
             case 572:  if(read) {return 32760;} else {return -32761;}
             case 573:  if(read) {return 32764;} else {return -32765;}
             case 574:  if(read) {return 32766;} else {return -32767;}
             case 575:  if(read) {return 32767;} else {return -32768;}
             case 594:  if(read) {return 8192;} else {return -8193;}
             case 595:  if(read) {return 12288;} else {return -12289;}
             case 596:  if(read) {return 14336;} else {return -14337;}
             case 597:  if(read) {return 15360;} else {return -15361;}
             case 598:  if(read) {return 15872;} else {return -15873;}
             case 599:  if(read) {return 16128;} else {return -16129;}
             case 600:  if(read) {return 16256;} else {return -16257;}
             case 601:  if(read) {return 16320;} else {return -16321;}
             case 602:  if(read) {return 16352;} else {return -16353;}
             case 603:  if(read) {return 16368;} else {return -16369;}
             case 604:  if(read) {return 16376;} else {return -16377;}
             case 605:  if(read) {return 16380;} else {return -16381;}
             case 606:  if(read) {return 16382;} else {return -16383;}
             case 607:  if(read) {return 16383;} else {return -16384;}
             case 627:  if(read) {return 4096;} else {return -4097;}
             case 628:  if(read) {return 6144;} else {return -6145;}
             case 629:  if(read) {return 7168;} else {return -7169;}
             case 630:  if(read) {return 7680;} else {return -7681;}
             case 631:  if(read) {return 7936;} else {return -7937;}
             case 632:  if(read) {return 8064;} else {return -8065;}
             case 633:  if(read) {return 8128;} else {return -8129;}
             case 634:  if(read) {return 8160;} else {return -8161;}
             case 635:  if(read) {return 8176;} else {return -8177;}
             case 636:  if(read) {return 8184;} else {return -8185;}
             case 637:  if(read) {return 8188;} else {return -8189;}
             case 638:  if(read) {return 8190;} else {return -8191;}
             case 639:  if(read) {return 8191;} else {return -8192;}
             case 660:  if(read) {return 2048;} else {return -2049;}
             case 661:  if(read) {return 3072;} else {return -3073;}
             case 662:  if(read) {return 3584;} else {return -3585;}
             case 663:  if(read) {return 3840;} else {return -3841;}
             case 664:  if(read) {return 3968;} else {return -3969;}
             case 665:  if(read) {return 4032;} else {return -4033;}
             case 666:  if(read) {return 4064;} else {return -4065;}
             case 667:  if(read) {return 4080;} else {return -4081;}
             case 668:  if(read) {return 4088;} else {return -4089;}
             case 669:  if(read) {return 4092;} else {return -4093;}
             case 670:  if(read) {return 4094;} else {return -4095;}
             case 671:  if(read) {return 4095;} else {return -4096;}
             case 693:  if(read) {return 1024;} else {return -1025;}
             case 694:  if(read) {return 1536;} else {return -1537;}
             case 695:  if(read) {return 1792;} else {return -1793;}
             case 696:  if(read) {return 1920;} else {return -1921;}
             case 697:  if(read) {return 1984;} else {return -1985;}
             case 698:  if(read) {return 2016;} else {return -2017;}
             case 699:  if(read) {return 2032;} else {return -2033;}
             case 700:  if(read) {return 2040;} else {return -2041;}
             case 701:  if(read) {return 2044;} else {return -2045;}
             case 702:  if(read) {return 2046;} else {return -2047;}
             case 703:  if(read) {return 2047;} else {return -2048;}
             case 726:  if(read) {return 512;} else {return -513;}
             case 727:  if(read) {return 768;} else {return -769;}
             case 728:  if(read) {return 896;} else {return -897;}
             case 729:  if(read) {return 960;} else {return -961;}
             case 730:  if(read) {return 992;} else {return -993;}
             case 731:  if(read) {return 1008;} else {return -1009;}
             case 732:  if(read) {return 1016;} else {return -1017;}
             case 733:  if(read) {return 1020;} else {return -1021;}
             case 734:  if(read) {return 1022;} else {return -1023;}
             case 735:  if(read) {return 1023;} else {return -1024;}
             case 759:  if(read) {return 256;} else {return -257;}
             case 760:  if(read) {return 384;} else {return -385;}
             case 761:  if(read) {return 448;} else {return -449;}
             case 762:  if(read) {return 480;} else {return -481;}
             case 763:  if(read) {return 496;} else {return -497;}
             case 764:  if(read) {return 504;} else {return -505;}
             case 765:  if(read) {return 508;} else {return -509;}
             case 766:  if(read) {return 510;} else {return -511;}
             case 767:  if(read) {return 511;} else {return -512;}
             case 792:  if(read) {return 128;} else {return -129;}
             case 793:  if(read) {return 192;} else {return -193;}
             case 794:  if(read) {return 224;} else {return -225;}
             case 795:  if(read) {return 240;} else {return -241;}
             case 796:  if(read) {return 248;} else {return -249;}
             case 797:  if(read) {return 252;} else {return -253;}
             case 798:  if(read) {return 254;} else {return -255;}
             case 799:  if(read) {return 255;} else {return -256;}
             case 825:  if(read) {return 64;} else {return -65;}
             case 826:  if(read) {return 96;} else {return -97;}
             case 827:  if(read) {return 112;} else {return -113;}
             case 828:  if(read) {return 120;} else {return -121;}
             case 829:  if(read) {return 124;} else {return -125;}
             case 830:  if(read) {return 126;} else {return -127;}
             case 831:  if(read) {return 127;} else {return -128;}
             case 858:  if(read) {return 32;} else {return -33;}
             case 859:  if(read) {return 48;} else {return -49;}
             case 860:  if(read) {return 56;} else {return -57;}
             case 861:  if(read) {return 60;} else {return -61;}
             case 862:  if(read) {return 62;} else {return -63;}
             case 863:  if(read) {return 63;} else {return -64;}
             case 891:  if(read) {return 16;} else {return -17;}
             case 892:  if(read) {return 24;} else {return -25;}
             case 893:  if(read) {return 28;} else {return -29;}
             case 894:  if(read) {return 30;} else {return -31;}
             case 895:  if(read) {return 31;} else {return -32;}
             case 924:  if(read) {return 8;} else {return -9;}
             case 925:  if(read) {return 12;} else {return -13;}
             case 926:  if(read) {return 14;} else {return -15;}
             case 927:  if(read) {return 15;} else {return -16;}
             case 957:  if(read) {return 4;} else {return -5;}
             case 958:  if(read) {return 6;} else {return -7;}
             case 959:  if(read) {return 7;} else {return -8;}
             case 990:  if(read) {return 2;} else {return -3;}
             case 991:  if(read) {return 3;} else {return -4;}
             case 1023:  if(read) {return 1;} else {return -2;}
        }
        return 0;
    }


    public static int whichBit(int index, int bitloc) {
        int alpha = bitloc*10 + index;
         if(bitloc<index*32) {
             return 0;    //first bit of the number
         }
         if(bitloc>=(index+1)*32) {
             return 31;  //last bit of the number
         }
         return bitloc%32;
     }

     public static int whichBit2(int index, int bitloc) {
         int num = bitloc*10 + index;
         switch (num) {
             case 0: return 0;
             case 10: return 1;
             case 20: return 2;
             case 30: return 3;
             case 40: return 4;
             case 50: return 5;
             case 60: return 6;
             case 70: return 7;
             case 80: return 8;
             case 90: return 9;
             case 100: return 10;
             case 110: return 11;
             case 120: return 12;
             case 130: return 13;
             case 140: return 14;
             case 150: return 15;
             case 160: return 16;
             case 170: return 17;
             case 180: return 18;
             case 190: return 19;
             case 200: return 20;
             case 210: return 21;
             case 220: return 22;
             case 230: return 23;
             case 240: return 24;
             case 250: return 25;
             case 260: return 26;
             case 270: return 27;
             case 280: return 28;
             case 290: return 29;
             case 300: return 30;
             case 310: return 31;
             case 320: return 31;
             case 330: return 31;
             case 340: return 31;
             case 350: return 31;
             case 360: return 31;
             case 370: return 31;
             case 380: return 31;
             case 390: return 31;
             case 400: return 31;
             case 410: return 31;
             case 420: return 31;
             case 430: return 31;
             case 440: return 31;
             case 450: return 31;
             case 460: return 31;
             case 470: return 31;
             case 480: return 31;
             case 490: return 31;
             case 500: return 31;
             case 510: return 31;
             case 520: return 31;
             case 530: return 31;
             case 540: return 31;
             case 550: return 31;
             case 560: return 31;
             case 570: return 31;
             case 580: return 31;
             case 590: return 31;
             case 600: return 31;
             case 610: return 31;
             case 620: return 31;
             case 630: return 31;
             case 640: return 31;
             case 650: return 31;
             case 660: return 31;
             case 670: return 31;
             case 680: return 31;
             case 690: return 31;
             case 700: return 31;
             case 710: return 31;
             case 720: return 31;
             case 730: return 31;
             case 740: return 31;
             case 750: return 31;
             case 760: return 31;
             case 770: return 31;
             case 780: return 31;
             case 790: return 31;
             case 800: return 31;
             case 810: return 31;
             case 820: return 31;
             case 830: return 31;
             case 840: return 31;
             case 850: return 31;
             case 860: return 31;
             case 870: return 31;
             case 880: return 31;
             case 890: return 31;
             case 900: return 31;
             case 910: return 31;
             case 920: return 31;
             case 930: return 31;
             case 940: return 31;
             case 950: return 31;
             case 960: return 31;
             case 970: return 31;
             case 980: return 31;
             case 990: return 31;
             case 1000: return 31;
             case 1010: return 31;
             case 1020: return 31;
             case 1030: return 31;
             case 1040: return 31;
             case 1050: return 31;
             case 1060: return 31;
             case 1070: return 31;
             case 1080: return 31;
             case 1090: return 31;
             case 1100: return 31;
             case 1110: return 31;
             case 1120: return 31;
             case 1130: return 31;
             case 1140: return 31;
             case 1150: return 31;
             case 1160: return 31;
             case 1170: return 31;
             case 1180: return 31;
             case 1190: return 31;
             case 1200: return 31;
             case 1210: return 31;
             case 1220: return 31;
             case 1230: return 31;
             case 1240: return 31;
             case 1250: return 31;
             case 1260: return 31;
             case 1270: return 31;
             case 1280: return 31;
             case 1290: return 31;
             case 1300: return 31;
             case 1310: return 31;
             case 1320: return 31;
             case 1330: return 31;
             case 1340: return 31;
             case 1350: return 31;
             case 1360: return 31;
             case 1370: return 31;
             case 1380: return 31;
             case 1390: return 31;
             case 1400: return 31;
             case 1410: return 31;
             case 1420: return 31;
             case 1430: return 31;
             case 1440: return 31;
             case 1450: return 31;
             case 1460: return 31;
             case 1470: return 31;
             case 1480: return 31;
             case 1490: return 31;
             case 1500: return 31;
             case 1510: return 31;
             case 1520: return 31;
             case 1530: return 31;
             case 1540: return 31;
             case 1550: return 31;
             case 1560: return 31;
             case 1570: return 31;
             case 1580: return 31;
             case 1590: return 31;
             case 1600: return 31;
             case 1610: return 31;
             case 1620: return 31;
             case 1630: return 31;
             case 1640: return 31;
             case 1650: return 31;
             case 1660: return 31;
             case 1670: return 31;
             case 1680: return 31;
             case 1690: return 31;
             case 1700: return 31;
             case 1710: return 31;
             case 1720: return 31;
             case 1730: return 31;
             case 1740: return 31;
             case 1750: return 31;
             case 1760: return 31;
             case 1770: return 31;
             case 1780: return 31;
             case 1790: return 31;
             case 1800: return 31;
             case 1810: return 31;
             case 1820: return 31;
             case 1830: return 31;
             case 1840: return 31;
             case 1850: return 31;
             case 1860: return 31;
             case 1870: return 31;
             case 1880: return 31;
             case 1890: return 31;
             case 1900: return 31;
             case 1910: return 31;
             case 1920: return 31;
             case 1930: return 31;
             case 1940: return 31;
             case 1950: return 31;
             case 1960: return 31;
             case 1970: return 31;
             case 1980: return 31;
             case 1990: return 31;
             case 2000: return 31;
             case 2010: return 31;
             case 2020: return 31;
             case 2030: return 31;
             case 2040: return 31;
             case 2050: return 31;
             case 2060: return 31;
             case 2070: return 31;
             case 2080: return 31;
             case 2090: return 31;
             case 2100: return 31;
             case 2110: return 31;
             case 2120: return 31;
             case 2130: return 31;
             case 2140: return 31;
             case 2150: return 31;
             case 2160: return 31;
             case 2170: return 31;
             case 2180: return 31;
             case 2190: return 31;
             case 2200: return 31;
             case 2210: return 31;
             case 2220: return 31;
             case 2230: return 31;
             case 1: return 0;
             case 11: return 0;
             case 21: return 0;
             case 31: return 0;
             case 41: return 0;
             case 51: return 0;
             case 61: return 0;
             case 71: return 0;
             case 81: return 0;
             case 91: return 0;
             case 101: return 0;
             case 111: return 0;
             case 121: return 0;
             case 131: return 0;
             case 141: return 0;
             case 151: return 0;
             case 161: return 0;
             case 171: return 0;
             case 181: return 0;
             case 191: return 0;
             case 201: return 0;
             case 211: return 0;
             case 221: return 0;
             case 231: return 0;
             case 241: return 0;
             case 251: return 0;
             case 261: return 0;
             case 271: return 0;
             case 281: return 0;
             case 291: return 0;
             case 301: return 0;
             case 311: return 0;
             case 321: return 0;
             case 331: return 1;
             case 341: return 2;
             case 351: return 3;
             case 361: return 4;
             case 371: return 5;
             case 381: return 6;
             case 391: return 7;
             case 401: return 8;
             case 411: return 9;
             case 421: return 10;
             case 431: return 11;
             case 441: return 12;
             case 451: return 13;
             case 461: return 14;
             case 471: return 15;
             case 481: return 16;
             case 491: return 17;
             case 501: return 18;
             case 511: return 19;
             case 521: return 20;
             case 531: return 21;
             case 541: return 22;
             case 551: return 23;
             case 561: return 24;
             case 571: return 25;
             case 581: return 26;
             case 591: return 27;
             case 601: return 28;
             case 611: return 29;
             case 621: return 30;
             case 631: return 31;
             case 641: return 31;
             case 651: return 31;
             case 661: return 31;
             case 671: return 31;
             case 681: return 31;
             case 691: return 31;
             case 701: return 31;
             case 711: return 31;
             case 721: return 31;
             case 731: return 31;
             case 741: return 31;
             case 751: return 31;
             case 761: return 31;
             case 771: return 31;
             case 781: return 31;
             case 791: return 31;
             case 801: return 31;
             case 811: return 31;
             case 821: return 31;
             case 831: return 31;
             case 841: return 31;
             case 851: return 31;
             case 861: return 31;
             case 871: return 31;
             case 881: return 31;
             case 891: return 31;
             case 901: return 31;
             case 911: return 31;
             case 921: return 31;
             case 931: return 31;
             case 941: return 31;
             case 951: return 31;
             case 961: return 31;
             case 971: return 31;
             case 981: return 31;
             case 991: return 31;
             case 1001: return 31;
             case 1011: return 31;
             case 1021: return 31;
             case 1031: return 31;
             case 1041: return 31;
             case 1051: return 31;
             case 1061: return 31;
             case 1071: return 31;
             case 1081: return 31;
             case 1091: return 31;
             case 1101: return 31;
             case 1111: return 31;
             case 1121: return 31;
             case 1131: return 31;
             case 1141: return 31;
             case 1151: return 31;
             case 1161: return 31;
             case 1171: return 31;
             case 1181: return 31;
             case 1191: return 31;
             case 1201: return 31;
             case 1211: return 31;
             case 1221: return 31;
             case 1231: return 31;
             case 1241: return 31;
             case 1251: return 31;
             case 1261: return 31;
             case 1271: return 31;
             case 1281: return 31;
             case 1291: return 31;
             case 1301: return 31;
             case 1311: return 31;
             case 1321: return 31;
             case 1331: return 31;
             case 1341: return 31;
             case 1351: return 31;
             case 1361: return 31;
             case 1371: return 31;
             case 1381: return 31;
             case 1391: return 31;
             case 1401: return 31;
             case 1411: return 31;
             case 1421: return 31;
             case 1431: return 31;
             case 1441: return 31;
             case 1451: return 31;
             case 1461: return 31;
             case 1471: return 31;
             case 1481: return 31;
             case 1491: return 31;
             case 1501: return 31;
             case 1511: return 31;
             case 1521: return 31;
             case 1531: return 31;
             case 1541: return 31;
             case 1551: return 31;
             case 1561: return 31;
             case 1571: return 31;
             case 1581: return 31;
             case 1591: return 31;
             case 1601: return 31;
             case 1611: return 31;
             case 1621: return 31;
             case 1631: return 31;
             case 1641: return 31;
             case 1651: return 31;
             case 1661: return 31;
             case 1671: return 31;
             case 1681: return 31;
             case 1691: return 31;
             case 1701: return 31;
             case 1711: return 31;
             case 1721: return 31;
             case 1731: return 31;
             case 1741: return 31;
             case 1751: return 31;
             case 1761: return 31;
             case 1771: return 31;
             case 1781: return 31;
             case 1791: return 31;
             case 1801: return 31;
             case 1811: return 31;
             case 1821: return 31;
             case 1831: return 31;
             case 1841: return 31;
             case 1851: return 31;
             case 1861: return 31;
             case 1871: return 31;
             case 1881: return 31;
             case 1891: return 31;
             case 1901: return 31;
             case 1911: return 31;
             case 1921: return 31;
             case 1931: return 31;
             case 1941: return 31;
             case 1951: return 31;
             case 1961: return 31;
             case 1971: return 31;
             case 1981: return 31;
             case 1991: return 31;
             case 2001: return 31;
             case 2011: return 31;
             case 2021: return 31;
             case 2031: return 31;
             case 2041: return 31;
             case 2051: return 31;
             case 2061: return 31;
             case 2071: return 31;
             case 2081: return 31;
             case 2091: return 31;
             case 2101: return 31;
             case 2111: return 31;
             case 2121: return 31;
             case 2131: return 31;
             case 2141: return 31;
             case 2151: return 31;
             case 2161: return 31;
             case 2171: return 31;
             case 2181: return 31;
             case 2191: return 31;
             case 2201: return 31;
             case 2211: return 31;
             case 2221: return 31;
             case 2231: return 31;
             case 2: return 0;
             case 12: return 0;
             case 22: return 0;
             case 32: return 0;
             case 42: return 0;
             case 52: return 0;
             case 62: return 0;
             case 72: return 0;
             case 82: return 0;
             case 92: return 0;
             case 102: return 0;
             case 112: return 0;
             case 122: return 0;
             case 132: return 0;
             case 142: return 0;
             case 152: return 0;
             case 162: return 0;
             case 172: return 0;
             case 182: return 0;
             case 192: return 0;
             case 202: return 0;
             case 212: return 0;
             case 222: return 0;
             case 232: return 0;
             case 242: return 0;
             case 252: return 0;
             case 262: return 0;
             case 272: return 0;
             case 282: return 0;
             case 292: return 0;
             case 302: return 0;
             case 312: return 0;
             case 322: return 0;
             case 332: return 0;
             case 342: return 0;
             case 352: return 0;
             case 362: return 0;
             case 372: return 0;
             case 382: return 0;
             case 392: return 0;
             case 402: return 0;
             case 412: return 0;
             case 422: return 0;
             case 432: return 0;
             case 442: return 0;
             case 452: return 0;
             case 462: return 0;
             case 472: return 0;
             case 482: return 0;
             case 492: return 0;
             case 502: return 0;
             case 512: return 0;
             case 522: return 0;
             case 532: return 0;
             case 542: return 0;
             case 552: return 0;
             case 562: return 0;
             case 572: return 0;
             case 582: return 0;
             case 592: return 0;
             case 602: return 0;
             case 612: return 0;
             case 622: return 0;
             case 632: return 0;
             case 642: return 0;
             case 652: return 1;
             case 662: return 2;
             case 672: return 3;
             case 682: return 4;
             case 692: return 5;
             case 702: return 6;
             case 712: return 7;
             case 722: return 8;
             case 732: return 9;
             case 742: return 10;
             case 752: return 11;
             case 762: return 12;
             case 772: return 13;
             case 782: return 14;
             case 792: return 15;
             case 802: return 16;
             case 812: return 17;
             case 822: return 18;
             case 832: return 19;
             case 842: return 20;
             case 852: return 21;
             case 862: return 22;
             case 872: return 23;
             case 882: return 24;
             case 892: return 25;
             case 902: return 26;
             case 912: return 27;
             case 922: return 28;
             case 932: return 29;
             case 942: return 30;
             case 952: return 31;
             case 962: return 31;
             case 972: return 31;
             case 982: return 31;
             case 992: return 31;
             case 1002: return 31;
             case 1012: return 31;
             case 1022: return 31;
             case 1032: return 31;
             case 1042: return 31;
             case 1052: return 31;
             case 1062: return 31;
             case 1072: return 31;
             case 1082: return 31;
             case 1092: return 31;
             case 1102: return 31;
             case 1112: return 31;
             case 1122: return 31;
             case 1132: return 31;
             case 1142: return 31;
             case 1152: return 31;
             case 1162: return 31;
             case 1172: return 31;
             case 1182: return 31;
             case 1192: return 31;
             case 1202: return 31;
             case 1212: return 31;
             case 1222: return 31;
             case 1232: return 31;
             case 1242: return 31;
             case 1252: return 31;
             case 1262: return 31;
             case 1272: return 31;
             case 1282: return 31;
             case 1292: return 31;
             case 1302: return 31;
             case 1312: return 31;
             case 1322: return 31;
             case 1332: return 31;
             case 1342: return 31;
             case 1352: return 31;
             case 1362: return 31;
             case 1372: return 31;
             case 1382: return 31;
             case 1392: return 31;
             case 1402: return 31;
             case 1412: return 31;
             case 1422: return 31;
             case 1432: return 31;
             case 1442: return 31;
             case 1452: return 31;
             case 1462: return 31;
             case 1472: return 31;
             case 1482: return 31;
             case 1492: return 31;
             case 1502: return 31;
             case 1512: return 31;
             case 1522: return 31;
             case 1532: return 31;
             case 1542: return 31;
             case 1552: return 31;
             case 1562: return 31;
             case 1572: return 31;
             case 1582: return 31;
             case 1592: return 31;
             case 1602: return 31;
             case 1612: return 31;
             case 1622: return 31;
             case 1632: return 31;
             case 1642: return 31;
             case 1652: return 31;
             case 1662: return 31;
             case 1672: return 31;
             case 1682: return 31;
             case 1692: return 31;
             case 1702: return 31;
             case 1712: return 31;
             case 1722: return 31;
             case 1732: return 31;
             case 1742: return 31;
             case 1752: return 31;
             case 1762: return 31;
             case 1772: return 31;
             case 1782: return 31;
             case 1792: return 31;
             case 1802: return 31;
             case 1812: return 31;
             case 1822: return 31;
             case 1832: return 31;
             case 1842: return 31;
             case 1852: return 31;
             case 1862: return 31;
             case 1872: return 31;
             case 1882: return 31;
             case 1892: return 31;
             case 1902: return 31;
             case 1912: return 31;
             case 1922: return 31;
             case 1932: return 31;
             case 1942: return 31;
             case 1952: return 31;
             case 1962: return 31;
             case 1972: return 31;
             case 1982: return 31;
             case 1992: return 31;
             case 2002: return 31;
             case 2012: return 31;
             case 2022: return 31;
             case 2032: return 31;
             case 2042: return 31;
             case 2052: return 31;
             case 2062: return 31;
             case 2072: return 31;
             case 2082: return 31;
             case 2092: return 31;
             case 2102: return 31;
             case 2112: return 31;
             case 2122: return 31;
             case 2132: return 31;
             case 2142: return 31;
             case 2152: return 31;
             case 2162: return 31;
             case 2172: return 31;
             case 2182: return 31;
             case 2192: return 31;
             case 2202: return 31;
             case 2212: return 31;
             case 2222: return 31;
             case 2232: return 31;
             case 3: return 0;
             case 13: return 0;
             case 23: return 0;
             case 33: return 0;
             case 43: return 0;
             case 53: return 0;
             case 63: return 0;
             case 73: return 0;
             case 83: return 0;
             case 93: return 0;
             case 103: return 0;
             case 113: return 0;
             case 123: return 0;
             case 133: return 0;
             case 143: return 0;
             case 153: return 0;
             case 163: return 0;
             case 173: return 0;
             case 183: return 0;
             case 193: return 0;
             case 203: return 0;
             case 213: return 0;
             case 223: return 0;
             case 233: return 0;
             case 243: return 0;
             case 253: return 0;
             case 263: return 0;
             case 273: return 0;
             case 283: return 0;
             case 293: return 0;
             case 303: return 0;
             case 313: return 0;
             case 323: return 0;
             case 333: return 0;
             case 343: return 0;
             case 353: return 0;
             case 363: return 0;
             case 373: return 0;
             case 383: return 0;
             case 393: return 0;
             case 403: return 0;
             case 413: return 0;
             case 423: return 0;
             case 433: return 0;
             case 443: return 0;
             case 453: return 0;
             case 463: return 0;
             case 473: return 0;
             case 483: return 0;
             case 493: return 0;
             case 503: return 0;
             case 513: return 0;
             case 523: return 0;
             case 533: return 0;
             case 543: return 0;
             case 553: return 0;
             case 563: return 0;
             case 573: return 0;
             case 583: return 0;
             case 593: return 0;
             case 603: return 0;
             case 613: return 0;
             case 623: return 0;
             case 633: return 0;
             case 643: return 0;
             case 653: return 0;
             case 663: return 0;
             case 673: return 0;
             case 683: return 0;
             case 693: return 0;
             case 703: return 0;
             case 713: return 0;
             case 723: return 0;
             case 733: return 0;
             case 743: return 0;
             case 753: return 0;
             case 763: return 0;
             case 773: return 0;
             case 783: return 0;
             case 793: return 0;
             case 803: return 0;
             case 813: return 0;
             case 823: return 0;
             case 833: return 0;
             case 843: return 0;
             case 853: return 0;
             case 863: return 0;
             case 873: return 0;
             case 883: return 0;
             case 893: return 0;
             case 903: return 0;
             case 913: return 0;
             case 923: return 0;
             case 933: return 0;
             case 943: return 0;
             case 953: return 0;
             case 963: return 0;
             case 973: return 1;
             case 983: return 2;
             case 993: return 3;
             case 1003: return 4;
             case 1013: return 5;
             case 1023: return 6;
             case 1033: return 7;
             case 1043: return 8;
             case 1053: return 9;
             case 1063: return 10;
             case 1073: return 11;
             case 1083: return 12;
             case 1093: return 13;
             case 1103: return 14;
             case 1113: return 15;
             case 1123: return 16;
             case 1133: return 17;
             case 1143: return 18;
             case 1153: return 19;
             case 1163: return 20;
             case 1173: return 21;
             case 1183: return 22;
             case 1193: return 23;
             case 1203: return 24;
             case 1213: return 25;
             case 1223: return 26;
             case 1233: return 27;
             case 1243: return 28;
             case 1253: return 29;
             case 1263: return 30;
             case 1273: return 31;
             case 1283: return 31;
             case 1293: return 31;
             case 1303: return 31;
             case 1313: return 31;
             case 1323: return 31;
             case 1333: return 31;
             case 1343: return 31;
             case 1353: return 31;
             case 1363: return 31;
             case 1373: return 31;
             case 1383: return 31;
             case 1393: return 31;
             case 1403: return 31;
             case 1413: return 31;
             case 1423: return 31;
             case 1433: return 31;
             case 1443: return 31;
             case 1453: return 31;
             case 1463: return 31;
             case 1473: return 31;
             case 1483: return 31;
             case 1493: return 31;
             case 1503: return 31;
             case 1513: return 31;
             case 1523: return 31;
             case 1533: return 31;
             case 1543: return 31;
             case 1553: return 31;
             case 1563: return 31;
             case 1573: return 31;
             case 1583: return 31;
             case 1593: return 31;
             case 1603: return 31;
             case 1613: return 31;
             case 1623: return 31;
             case 1633: return 31;
             case 1643: return 31;
             case 1653: return 31;
             case 1663: return 31;
             case 1673: return 31;
             case 1683: return 31;
             case 1693: return 31;
             case 1703: return 31;
             case 1713: return 31;
             case 1723: return 31;
             case 1733: return 31;
             case 1743: return 31;
             case 1753: return 31;
             case 1763: return 31;
             case 1773: return 31;
             case 1783: return 31;
             case 1793: return 31;
             case 1803: return 31;
             case 1813: return 31;
             case 1823: return 31;
             case 1833: return 31;
             case 1843: return 31;
             case 1853: return 31;
             case 1863: return 31;
             case 1873: return 31;
             case 1883: return 31;
             case 1893: return 31;
             case 1903: return 31;
             case 1913: return 31;
             case 1923: return 31;
             case 1933: return 31;
             case 1943: return 31;
             case 1953: return 31;
             case 1963: return 31;
             case 1973: return 31;
             case 1983: return 31;
             case 1993: return 31;
             case 2003: return 31;
             case 2013: return 31;
             case 2023: return 31;
             case 2033: return 31;
             case 2043: return 31;
             case 2053: return 31;
             case 2063: return 31;
             case 2073: return 31;
             case 2083: return 31;
             case 2093: return 31;
             case 2103: return 31;
             case 2113: return 31;
             case 2123: return 31;
             case 2133: return 31;
             case 2143: return 31;
             case 2153: return 31;
             case 2163: return 31;
             case 2173: return 31;
             case 2183: return 31;
             case 2193: return 31;
             case 2203: return 31;
             case 2213: return 31;
             case 2223: return 31;
             case 2233: return 31;
             case 4: return 0;
             case 14: return 0;
             case 24: return 0;
             case 34: return 0;
             case 44: return 0;
             case 54: return 0;
             case 64: return 0;
             case 74: return 0;
             case 84: return 0;
             case 94: return 0;
             case 104: return 0;
             case 114: return 0;
             case 124: return 0;
             case 134: return 0;
             case 144: return 0;
             case 154: return 0;
             case 164: return 0;
             case 174: return 0;
             case 184: return 0;
             case 194: return 0;
             case 204: return 0;
             case 214: return 0;
             case 224: return 0;
             case 234: return 0;
             case 244: return 0;
             case 254: return 0;
             case 264: return 0;
             case 274: return 0;
             case 284: return 0;
             case 294: return 0;
             case 304: return 0;
             case 314: return 0;
             case 324: return 0;
             case 334: return 0;
             case 344: return 0;
             case 354: return 0;
             case 364: return 0;
             case 374: return 0;
             case 384: return 0;
             case 394: return 0;
             case 404: return 0;
             case 414: return 0;
             case 424: return 0;
             case 434: return 0;
             case 444: return 0;
             case 454: return 0;
             case 464: return 0;
             case 474: return 0;
             case 484: return 0;
             case 494: return 0;
             case 504: return 0;
             case 514: return 0;
             case 524: return 0;
             case 534: return 0;
             case 544: return 0;
             case 554: return 0;
             case 564: return 0;
             case 574: return 0;
             case 584: return 0;
             case 594: return 0;
             case 604: return 0;
             case 614: return 0;
             case 624: return 0;
             case 634: return 0;
             case 644: return 0;
             case 654: return 0;
             case 664: return 0;
             case 674: return 0;
             case 684: return 0;
             case 694: return 0;
             case 704: return 0;
             case 714: return 0;
             case 724: return 0;
             case 734: return 0;
             case 744: return 0;
             case 754: return 0;
             case 764: return 0;
             case 774: return 0;
             case 784: return 0;
             case 794: return 0;
             case 804: return 0;
             case 814: return 0;
             case 824: return 0;
             case 834: return 0;
             case 844: return 0;
             case 854: return 0;
             case 864: return 0;
             case 874: return 0;
             case 884: return 0;
             case 894: return 0;
             case 904: return 0;
             case 914: return 0;
             case 924: return 0;
             case 934: return 0;
             case 944: return 0;
             case 954: return 0;
             case 964: return 0;
             case 974: return 0;
             case 984: return 0;
             case 994: return 0;
             case 1004: return 0;
             case 1014: return 0;
             case 1024: return 0;
             case 1034: return 0;
             case 1044: return 0;
             case 1054: return 0;
             case 1064: return 0;
             case 1074: return 0;
             case 1084: return 0;
             case 1094: return 0;
             case 1104: return 0;
             case 1114: return 0;
             case 1124: return 0;
             case 1134: return 0;
             case 1144: return 0;
             case 1154: return 0;
             case 1164: return 0;
             case 1174: return 0;
             case 1184: return 0;
             case 1194: return 0;
             case 1204: return 0;
             case 1214: return 0;
             case 1224: return 0;
             case 1234: return 0;
             case 1244: return 0;
             case 1254: return 0;
             case 1264: return 0;
             case 1274: return 0;
             case 1284: return 0;
             case 1294: return 1;
             case 1304: return 2;
             case 1314: return 3;
             case 1324: return 4;
             case 1334: return 5;
             case 1344: return 6;
             case 1354: return 7;
             case 1364: return 8;
             case 1374: return 9;
             case 1384: return 10;
             case 1394: return 11;
             case 1404: return 12;
             case 1414: return 13;
             case 1424: return 14;
             case 1434: return 15;
             case 1444: return 16;
             case 1454: return 17;
             case 1464: return 18;
             case 1474: return 19;
             case 1484: return 20;
             case 1494: return 21;
             case 1504: return 22;
             case 1514: return 23;
             case 1524: return 24;
             case 1534: return 25;
             case 1544: return 26;
             case 1554: return 27;
             case 1564: return 28;
             case 1574: return 29;
             case 1584: return 30;
             case 1594: return 31;
             case 1604: return 31;
             case 1614: return 31;
             case 1624: return 31;
             case 1634: return 31;
             case 1644: return 31;
             case 1654: return 31;
             case 1664: return 31;
             case 1674: return 31;
             case 1684: return 31;
             case 1694: return 31;
             case 1704: return 31;
             case 1714: return 31;
             case 1724: return 31;
             case 1734: return 31;
             case 1744: return 31;
             case 1754: return 31;
             case 1764: return 31;
             case 1774: return 31;
             case 1784: return 31;
             case 1794: return 31;
             case 1804: return 31;
             case 1814: return 31;
             case 1824: return 31;
             case 1834: return 31;
             case 1844: return 31;
             case 1854: return 31;
             case 1864: return 31;
             case 1874: return 31;
             case 1884: return 31;
             case 1894: return 31;
             case 1904: return 31;
             case 1914: return 31;
             case 1924: return 31;
             case 1934: return 31;
             case 1944: return 31;
             case 1954: return 31;
             case 1964: return 31;
             case 1974: return 31;
             case 1984: return 31;
             case 1994: return 31;
             case 2004: return 31;
             case 2014: return 31;
             case 2024: return 31;
             case 2034: return 31;
             case 2044: return 31;
             case 2054: return 31;
             case 2064: return 31;
             case 2074: return 31;
             case 2084: return 31;
             case 2094: return 31;
             case 2104: return 31;
             case 2114: return 31;
             case 2124: return 31;
             case 2134: return 31;
             case 2144: return 31;
             case 2154: return 31;
             case 2164: return 31;
             case 2174: return 31;
             case 2184: return 31;
             case 2194: return 31;
             case 2204: return 31;
             case 2214: return 31;
             case 2224: return 31;
             case 2234: return 31;
             case 5: return 0;
             case 15: return 0;
             case 25: return 0;
             case 35: return 0;
             case 45: return 0;
             case 55: return 0;
             case 65: return 0;
             case 75: return 0;
             case 85: return 0;
             case 95: return 0;
             case 105: return 0;
             case 115: return 0;
             case 125: return 0;
             case 135: return 0;
             case 145: return 0;
             case 155: return 0;
             case 165: return 0;
             case 175: return 0;
             case 185: return 0;
             case 195: return 0;
             case 205: return 0;
             case 215: return 0;
             case 225: return 0;
             case 235: return 0;
             case 245: return 0;
             case 255: return 0;
             case 265: return 0;
             case 275: return 0;
             case 285: return 0;
             case 295: return 0;
             case 305: return 0;
             case 315: return 0;
             case 325: return 0;
             case 335: return 0;
             case 345: return 0;
             case 355: return 0;
             case 365: return 0;
             case 375: return 0;
             case 385: return 0;
             case 395: return 0;
             case 405: return 0;
             case 415: return 0;
             case 425: return 0;
             case 435: return 0;
             case 445: return 0;
             case 455: return 0;
             case 465: return 0;
             case 475: return 0;
             case 485: return 0;
             case 495: return 0;
             case 505: return 0;
             case 515: return 0;
             case 525: return 0;
             case 535: return 0;
             case 545: return 0;
             case 555: return 0;
             case 565: return 0;
             case 575: return 0;
             case 585: return 0;
             case 595: return 0;
             case 605: return 0;
             case 615: return 0;
             case 625: return 0;
             case 635: return 0;
             case 645: return 0;
             case 655: return 0;
             case 665: return 0;
             case 675: return 0;
             case 685: return 0;
             case 695: return 0;
             case 705: return 0;
             case 715: return 0;
             case 725: return 0;
             case 735: return 0;
             case 745: return 0;
             case 755: return 0;
             case 765: return 0;
             case 775: return 0;
             case 785: return 0;
             case 795: return 0;
             case 805: return 0;
             case 815: return 0;
             case 825: return 0;
             case 835: return 0;
             case 845: return 0;
             case 855: return 0;
             case 865: return 0;
             case 875: return 0;
             case 885: return 0;
             case 895: return 0;
             case 905: return 0;
             case 915: return 0;
             case 925: return 0;
             case 935: return 0;
             case 945: return 0;
             case 955: return 0;
             case 965: return 0;
             case 975: return 0;
             case 985: return 0;
             case 995: return 0;
             case 1005: return 0;
             case 1015: return 0;
             case 1025: return 0;
             case 1035: return 0;
             case 1045: return 0;
             case 1055: return 0;
             case 1065: return 0;
             case 1075: return 0;
             case 1085: return 0;
             case 1095: return 0;
             case 1105: return 0;
             case 1115: return 0;
             case 1125: return 0;
             case 1135: return 0;
             case 1145: return 0;
             case 1155: return 0;
             case 1165: return 0;
             case 1175: return 0;
             case 1185: return 0;
             case 1195: return 0;
             case 1205: return 0;
             case 1215: return 0;
             case 1225: return 0;
             case 1235: return 0;
             case 1245: return 0;
             case 1255: return 0;
             case 1265: return 0;
             case 1275: return 0;
             case 1285: return 0;
             case 1295: return 0;
             case 1305: return 0;
             case 1315: return 0;
             case 1325: return 0;
             case 1335: return 0;
             case 1345: return 0;
             case 1355: return 0;
             case 1365: return 0;
             case 1375: return 0;
             case 1385: return 0;
             case 1395: return 0;
             case 1405: return 0;
             case 1415: return 0;
             case 1425: return 0;
             case 1435: return 0;
             case 1445: return 0;
             case 1455: return 0;
             case 1465: return 0;
             case 1475: return 0;
             case 1485: return 0;
             case 1495: return 0;
             case 1505: return 0;
             case 1515: return 0;
             case 1525: return 0;
             case 1535: return 0;
             case 1545: return 0;
             case 1555: return 0;
             case 1565: return 0;
             case 1575: return 0;
             case 1585: return 0;
             case 1595: return 0;
             case 1605: return 0;
             case 1615: return 1;
             case 1625: return 2;
             case 1635: return 3;
             case 1645: return 4;
             case 1655: return 5;
             case 1665: return 6;
             case 1675: return 7;
             case 1685: return 8;
             case 1695: return 9;
             case 1705: return 10;
             case 1715: return 11;
             case 1725: return 12;
             case 1735: return 13;
             case 1745: return 14;
             case 1755: return 15;
             case 1765: return 16;
             case 1775: return 17;
             case 1785: return 18;
             case 1795: return 19;
             case 1805: return 20;
             case 1815: return 21;
             case 1825: return 22;
             case 1835: return 23;
             case 1845: return 24;
             case 1855: return 25;
             case 1865: return 26;
             case 1875: return 27;
             case 1885: return 28;
             case 1895: return 29;
             case 1905: return 30;
             case 1915: return 31;
             case 1925: return 31;
             case 1935: return 31;
             case 1945: return 31;
             case 1955: return 31;
             case 1965: return 31;
             case 1975: return 31;
             case 1985: return 31;
             case 1995: return 31;
             case 2005: return 31;
             case 2015: return 31;
             case 2025: return 31;
             case 2035: return 31;
             case 2045: return 31;
             case 2055: return 31;
             case 2065: return 31;
             case 2075: return 31;
             case 2085: return 31;
             case 2095: return 31;
             case 2105: return 31;
             case 2115: return 31;
             case 2125: return 31;
             case 2135: return 31;
             case 2145: return 31;
             case 2155: return 31;
             case 2165: return 31;
             case 2175: return 31;
             case 2185: return 31;
             case 2195: return 31;
             case 2205: return 31;
             case 2215: return 31;
             case 2225: return 31;
             case 2235: return 31;
             case 6: return 0;
             case 16: return 0;
             case 26: return 0;
             case 36: return 0;
             case 46: return 0;
             case 56: return 0;
             case 66: return 0;
             case 76: return 0;
             case 86: return 0;
             case 96: return 0;
             case 106: return 0;
             case 116: return 0;
             case 126: return 0;
             case 136: return 0;
             case 146: return 0;
             case 156: return 0;
             case 166: return 0;
             case 176: return 0;
             case 186: return 0;
             case 196: return 0;
             case 206: return 0;
             case 216: return 0;
             case 226: return 0;
             case 236: return 0;
             case 246: return 0;
             case 256: return 0;
             case 266: return 0;
             case 276: return 0;
             case 286: return 0;
             case 296: return 0;
             case 306: return 0;
             case 316: return 0;
             case 326: return 0;
             case 336: return 0;
             case 346: return 0;
             case 356: return 0;
             case 366: return 0;
             case 376: return 0;
             case 386: return 0;
             case 396: return 0;
             case 406: return 0;
             case 416: return 0;
             case 426: return 0;
             case 436: return 0;
             case 446: return 0;
             case 456: return 0;
             case 466: return 0;
             case 476: return 0;
             case 486: return 0;
             case 496: return 0;
             case 506: return 0;
             case 516: return 0;
             case 526: return 0;
             case 536: return 0;
             case 546: return 0;
             case 556: return 0;
             case 566: return 0;
             case 576: return 0;
             case 586: return 0;
             case 596: return 0;
             case 606: return 0;
             case 616: return 0;
             case 626: return 0;
             case 636: return 0;
             case 646: return 0;
             case 656: return 0;
             case 666: return 0;
             case 676: return 0;
             case 686: return 0;
             case 696: return 0;
             case 706: return 0;
             case 716: return 0;
             case 726: return 0;
             case 736: return 0;
             case 746: return 0;
             case 756: return 0;
             case 766: return 0;
             case 776: return 0;
             case 786: return 0;
             case 796: return 0;
             case 806: return 0;
             case 816: return 0;
             case 826: return 0;
             case 836: return 0;
             case 846: return 0;
             case 856: return 0;
             case 866: return 0;
             case 876: return 0;
             case 886: return 0;
             case 896: return 0;
             case 906: return 0;
             case 916: return 0;
             case 926: return 0;
             case 936: return 0;
             case 946: return 0;
             case 956: return 0;
             case 966: return 0;
             case 976: return 0;
             case 986: return 0;
             case 996: return 0;
             case 1006: return 0;
             case 1016: return 0;
             case 1026: return 0;
             case 1036: return 0;
             case 1046: return 0;
             case 1056: return 0;
             case 1066: return 0;
             case 1076: return 0;
             case 1086: return 0;
             case 1096: return 0;
             case 1106: return 0;
             case 1116: return 0;
             case 1126: return 0;
             case 1136: return 0;
             case 1146: return 0;
             case 1156: return 0;
             case 1166: return 0;
             case 1176: return 0;
             case 1186: return 0;
             case 1196: return 0;
             case 1206: return 0;
             case 1216: return 0;
             case 1226: return 0;
             case 1236: return 0;
             case 1246: return 0;
             case 1256: return 0;
             case 1266: return 0;
             case 1276: return 0;
             case 1286: return 0;
             case 1296: return 0;
             case 1306: return 0;
             case 1316: return 0;
             case 1326: return 0;
             case 1336: return 0;
             case 1346: return 0;
             case 1356: return 0;
             case 1366: return 0;
             case 1376: return 0;
             case 1386: return 0;
             case 1396: return 0;
             case 1406: return 0;
             case 1416: return 0;
             case 1426: return 0;
             case 1436: return 0;
             case 1446: return 0;
             case 1456: return 0;
             case 1466: return 0;
             case 1476: return 0;
             case 1486: return 0;
             case 1496: return 0;
             case 1506: return 0;
             case 1516: return 0;
             case 1526: return 0;
             case 1536: return 0;
             case 1546: return 0;
             case 1556: return 0;
             case 1566: return 0;
             case 1576: return 0;
             case 1586: return 0;
             case 1596: return 0;
             case 1606: return 0;
             case 1616: return 0;
             case 1626: return 0;
             case 1636: return 0;
             case 1646: return 0;
             case 1656: return 0;
             case 1666: return 0;
             case 1676: return 0;
             case 1686: return 0;
             case 1696: return 0;
             case 1706: return 0;
             case 1716: return 0;
             case 1726: return 0;
             case 1736: return 0;
             case 1746: return 0;
             case 1756: return 0;
             case 1766: return 0;
             case 1776: return 0;
             case 1786: return 0;
             case 1796: return 0;
             case 1806: return 0;
             case 1816: return 0;
             case 1826: return 0;
             case 1836: return 0;
             case 1846: return 0;
             case 1856: return 0;
             case 1866: return 0;
             case 1876: return 0;
             case 1886: return 0;
             case 1896: return 0;
             case 1906: return 0;
             case 1916: return 0;
             case 1926: return 0;
             case 1936: return 1;
             case 1946: return 2;
             case 1956: return 3;
             case 1966: return 4;
             case 1976: return 5;
             case 1986: return 6;
             case 1996: return 7;
             case 2006: return 8;
             case 2016: return 9;
             case 2026: return 10;
             case 2036: return 11;
             case 2046: return 12;
             case 2056: return 13;
             case 2066: return 14;
             case 2076: return 15;
             case 2086: return 16;
             case 2096: return 17;
             case 2106: return 18;
             case 2116: return 19;
             case 2126: return 20;
             case 2136: return 21;
             case 2146: return 22;
             case 2156: return 23;
             case 2166: return 24;
             case 2176: return 25;
             case 2186: return 26;
             case 2196: return 27;
             case 2206: return 28;
             case 2216: return 29;
             case 2226: return 30;
             case 2236: return 31;
        }
        return 0;
     }


}
