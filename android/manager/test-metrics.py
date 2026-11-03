"""Exercise native metric stream framing without Android or credentials."""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
with tempfile.TemporaryDirectory(prefix='daednext-metrics-') as build:
    subprocess.run([str(java / ('javac' + suffix)), '-encoding', 'UTF-8', '-d', build,
                    str(root / 'src/io/github/quan0505/daednext/OverviewEvents.java'),
                    str(root / 'tests/OverviewEventsTest.java')], check=True)
    subprocess.run([str(java / ('java' + suffix)), '-cp', build,
                    'io.github.quan0505.daednext.OverviewEventsTest'], check=True)
