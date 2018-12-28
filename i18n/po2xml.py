#!/bin/python3

import sys

ids = {}
msgs = {}
last_id = ''

with open('ids') as ids_file:
    for line in ids_file.readlines():
        id, msg = line.split(' = ')
        ids[msg.strip()] = id.strip()

for line in sys.stdin:
    line = line.split('"')
    if line[0].strip() == 'msgid':
        last_id = line[1]
    if line[0].strip() == 'msgstr' and last_id != '':
        msgs[last_id] = line[1]

print("<resources>")
for id, msg in msgs.items():
    print(f'    <string name="{ids[id]}">{msg}</string>')
print("</resources>")
