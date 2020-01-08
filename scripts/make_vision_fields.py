R = 34
D = set()
r = (int) (R**0.5 + 1)

s = '{'
l = 0
for a in range(0, R+1):
	for x in range(-r, r+1):
		for y in range(-r, r+1):
			if 100*x+y not in D and x**2 + y**2 <= a:
				D.add(100*x+y)
				s += f'[{x},{y}], '
				l += 1

s = s[:-2]
s += '}'
print(l)
print(s)