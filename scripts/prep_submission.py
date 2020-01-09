from pathlib import Path
import shutil
import sys
import re
import os

if __name__ == '__main__':
	bot_dir = Path('./src/') / sys.argv[1]
	submit = Path('./src/submit')
	if submit.exists():
		shutil.rmtree(submit)
	submit.mkdir()
	for f in bot_dir.iterdir():
		out = submit / f.name
		Path.touch(out)
		with open(f, 'r') as file:
			content = file.read()
		out_content = re.sub('System.out.println', '//System.out.println', content)
		with open(out, 'w') as out_file:
			out_file.write(out_content)
	os.system('zip -r ./src/submit.zip ./src/submit')