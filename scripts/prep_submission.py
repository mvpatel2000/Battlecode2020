from pathlib import Path
import shutil
import sys
import re
import os

if __name__ == '__main__':
	bot_dir = Path('./src/') / sys.argv[1]
	smite = Path('./src/smite')
	if smite.exists():
		shutil.rmtree(smite)
	smite.mkdir()
	for f in bot_dir.iterdir():
		out = smite / f.name
		Path.touch(out)
		with open(f, 'r') as file:
			content = file.read()
		out_content = re.sub('System.out.println', '//System.out.println', content)
		out_content = re.sub('package [^\n]*;', 'package smite;', out_content)
		with open(out, 'w') as out_file:
			out_file.write(out_content)
	os.system('zip -r ./src/smite.zip ./src/smite')