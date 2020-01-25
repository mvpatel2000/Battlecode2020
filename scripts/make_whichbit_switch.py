
out = "switch (num) {\n"

for index in range(7):
    for bitloc in range(32*7):
        num =  bitloc*10 + index
        if(bitloc<index*32):
            out += " case " + str(num) + ": " + "return 0;" + "\n"
        elif(bitloc>=(index+1)*32):
            out += " case " + str(num) + ": " + "return 31;" + "\n"
        else:
            x = bitloc%32;
            out += " case " + str(num) + ": " + "return " + str(x) + "; \n"

print(out)
