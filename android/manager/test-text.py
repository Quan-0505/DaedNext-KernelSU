"""Run pure Java span-editor checks without Android or signing secrets."""
import os
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
with tempfile.TemporaryDirectory(prefix='daednext-text-') as build:
    subprocess.run([str(java / ('javac' + suffix)), '-encoding', 'UTF-8', '-d', build,
                    str(root / 'src/io/github/quan0505/daednext/ResourceText.java'),
                    str(root / 'tests/ResourceTextTest.java')], check=True)
    subprocess.run([str(java / ('java' + suffix)), '-cp', build,
                    'io.github.quan0505.daednext.ResourceTextTest'], check=True)
