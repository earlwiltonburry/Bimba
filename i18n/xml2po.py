#!/bin/python3

import sys

ids = {}
msgs = {}

with open('ids') as ids_file:
    for line in ids_file.readlines():
        id, msg = line.split(' = ')
        ids[id] = msg.strip()

for line in sys.stdin.readlines():
    id, msg = line.split(' = ')
    msgs[id] = msg.strip()

print('''#, fuzzy
msgid ""
msgstr ""
"MIME-Version: 1.0\\n"
"Content-Transfer-Encoding: 8bit\\n"
"Content-Type: text/plain; charset=UTF-8\\n"
''')

for id, msg in msgs.items():
    print(f'msgid "{ids[id]}"')
    print(f'msgstr "{msg}"')
