"""Check explicit UI literal coverage and formatting without Android or secrets."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent

def unique(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, f'Duplicate translation: {key}'
        result[key] = value
    return result

strings = json.loads((ROOT / 'res/raw/strings_en.json').read_text(encoding='utf8'), object_pairs_hook=unique)
chinese = re.compile(r'[\u4e00-\u9fff]')
printf = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')
for key, value in strings.items():
    assert isinstance(value, str) and value.strip(), key
    assert not chinese.search(value), f'Untranslated English value: {key}'
    assert printf.findall(key) == printf.findall(value), f'Changed formatting placeholders: {key}'
    assert key.count('\n') == value.count('\n'), f'Changed line breaks: {key}'

tokens = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', re.S)
canonical_arrays = ('String[] pages =', 'String[] RESOURCE_NAMES=', 'String[] RESOURCE_HINTS=', 'String[] POLICY_LABELS=', 'String[] LABELS =')
for path in (ROOT / 'src/io/github/quan0505/daednext').glob('*.java'):
    if path.name == 'L10n.java':
        continue  # The language picker intentionally uses language self-names.
    source = path.read_text(encoding='utf8')
    for match in tokens.finditer(source):
        token = match.group()
        if not token.startswith('"') or not chinese.search(token):
            continue
        literal = json.loads(token)
        assert literal in strings, f'Missing translation in {path.name}: {literal}'
        line = source[source.rfind('\n', 0, match.start()) + 1:source.find('\n', match.end())]
        if path.name == 'ResourceText.java' or any(marker in line for marker in canonical_arrays):
            continue
        assert source[:match.start()].endswith('L10n.tr('), f'UI literal not translated in {path.name}: {literal}'

print(f'English UI literal coverage, unique keys and format preservation passed ({len(strings)} entries)')
