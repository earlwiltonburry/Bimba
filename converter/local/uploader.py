import requests
import msgpack
import re
import os
import hashlib

import sign_key


def upload():
    with open('metadata.yml', 'r') as file:
        metadata = file.read()
    timetablesIds = [filename.split('.')[0] for filename in os.listdir('.')
                     if re.match('^.*\\.db\\.gz$', filename)]
    timetables = {}
    for tId in timetablesIds:
        with open(f'{tId}.db.gz', 'rb') as f:
            timetables[tId] = {'t': f.read(), 'sha': ''}
            timetables[tId]['sha'] = hashlib.sha256(timetables[tId]['t']).\
                hexdigest()

    signature = sign_key.key.sign(bytes(metadata, 'utf-8'))

    data = msgpack.packb({'metadata': metadata, 'timetables': timetables,
                          'signature': signature.signature}, use_bin_type=True)

    session = requests.Session()
    response = session.post('http://localhost:8000/upload.php', data)
    print(response)
    print(response.text)
