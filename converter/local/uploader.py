import hashlib
import msgpack
import os
import re
import requests

import config


def upload():
    with open('metadata.yml', 'r') as file:
        metadata = file.read()
    timetablesIds = [filename.split('.')[0] for filename in os.listdir('.')
                     if re.match('^.*\\.db\\.gz$', filename)]
    timetables = {}
    for tId in timetablesIds:
        with open('{}.db.gz'.format(tId), 'rb') as f:
            timetables[tId] = {'t': '', 'sha': ''}
            t = f.read()
            timetables[tId]['t'] = config.storage.format(tId)
            timetables[tId]['sha'] = hashlib.sha256(t).hexdigest()

    signature = config.key.sign(bytes(metadata, 'utf-8'))

    data = msgpack.packb({'metadata': metadata, 'timetables': timetables,
                          'signature': signature.signature}, use_bin_type=True)

    session = requests.Session()
    response = session.post(config.receiver, data)
    print(response)
    print(response.text)
