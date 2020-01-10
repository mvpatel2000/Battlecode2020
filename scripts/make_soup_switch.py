out = "switch ((int)mask) {\n"

for i in range(2**10):
  out += "    case "+str(i)+":" +"\n"
  binstr = '{0:10b}'.format(i)[::-1]
  shifts = []
  for z in range(10):
    if binstr[z] == "0":
        shifts.append(str(z))
  out += "        return new int[]{" + ','.join(shifts) + "};" +"\n"

out += "}"

print(out)
