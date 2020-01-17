from pathlib import Path
import shutil
import sys
import re
import os

if __name__ == '__main__':
	bot_dir = Path('./src/') / sys.argv[1]
	out_dir_name = 'copy_of_' + sys.argv[1]
	if len(sys.argv) > 2:
		out_dir_name = sys.argv[2]
	out_name = Path('./src/') / out_dir_name
	if out_name.exists():
		shutil.rmtree(out_name)
	out_name.mkdir()
	for f in bot_dir.iterdir():
		out = out_name / f.name
		Path.touch(out)
		with open(f, 'r') as file:
			content = file.read()
		out_content = re.sub('package [^\n]*;', 'package ' + out_dir_name + ';', content)
		with open(out, 'w') as out_file:
			out_file.write(out_content)