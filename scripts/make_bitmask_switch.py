
out = "switch (num) {\n"

def rshift(val, n):
    return val>>n if val >= 0 else (val+0x100000000)>>n

for start in range(32):
    for end in range(start, 32):
        mask = start*32 + end;
        bitmask = -1;
        if(end==31):
            bitmask = 0;
        else:
            bitmask = rshift(bitmask, (end+1));
        bitmask2 = -1;
        bitmask2 = rshift(bitmask, start);
        #if(read) {
        out += " case " + str(mask) + ": " + " if(read) {return " + str(bitmask ^ bitmask2) + ";} else {return " + str(~(bitmask ^ bitmask2)) +";} \n"
        #}
        #//for writing
        #return ~(bitmask ^ bitmask2);
print(out)
