#!/bin/python3
# -*- coding: UTF-8 -*-
import yaml
import csv
import sqlite3
import requests
from bs4 import BeautifulSoup
import re
import os
from datetime import date
import zipfile
import io
from pathlib import Path
import hashlib
import gzip
import shutil
import dateutil.parser
import msgpack
import base64

import config


class TimetableDownloader:
    def __init__(self):
        self.__converter = TimetableConverter()

    def __call__(self):
        self.__download()
        self.__tidy_up()

        print(self.__metadata)

        with open('metadata.yml', 'w') as metadata_file:
            yaml.dump(self.__metadata, metadata_file, default_flow_style=False)

    def __download(self):
        self.__session = requests.session()
        html = self.__session.get(
            'https://www.ztm.poznan.pl/pl/dla-deweloperow/gtfsFiles',
            verify='bundle.pem')
        soup = BeautifulSoup(html.text, 'html.parser')
        names_table = [x.string.replace('.zip', '') for x in
                       soup.find_all('table')[-1]
                        .tbody
                        .find_all('td',
                                  string=re.compile('[0-9]{8}_[0-9]{8}'))]

        if not os.path.isfile('metadata.yml'):
            self.__metadata = []
        else:
            with open('metadata.yml', 'r+') as metadata_file:
                self.__metadata = yaml.load(metadata_file.read())

        to_download = [x for x in names_table if
                       self.__is_valid(x) or self.__will_valid(x)]

        to_download = self.__clean_overlapping(to_download)
        to_download = self.__select_not_had(to_download)

        for file in to_download:
            print('getting {}.zip'.format(file))
            try:
                self.__get_timetable(file)
            except zipfile.BadZipFile:
                print('ERROR: file {} is not a zip'.format(file))
            else:
                checksum = self.__converter()
                for p in Path('.').glob('*.txt'):
                    p.unlink()
                size_u = os.path.getsize('timetable.db')
                self.__compress(checksum)
                meta = self.__archive(file, checksum, size_u, os.path
                                      .getsize('{}.db.gz'.format(checksum)))
                self.__upload(checksum, meta)

    def __is_valid(self, name):
        today = date.today().strftime('%Y%m%d')
        start, end = name.split('_')
        return start <= today and today <= end

    def __will_valid(self, name):
        today = date.today().strftime('%Y%m%d')
        start, end = name.split('_')
        return today < start

    def __validity_length(self, name1, name2):
        x = dateutil.parser.parse(name1)
        y = dateutil.parser.parse(name2)
        return (y - x).days

    def __sort_key(self, name):
        s, e = name.split('_')
        return s + "{0:03}".format(100 - self.__validity_length(s, e))

    def __clean_overlapping(self, names):
        today = date.today().strftime('%Y%m%d')
        names.sort(key=self.__sort_key)
        print(names)
        if len(names) == 1:
            return names
        return_names = []
        i = 1
        for name in names[1:]:
            this_start, this_end = name.split('_')
            prev_start, prev_end = names[i-1].split('_')
            if not ((this_start < prev_end and this_start <= today)
                    or this_start == prev_start):
                return_names.append(names[i-1])

            i = i + 1
        return_names.append(names[-1])
        return return_names

    def __select_not_had(self, names):
        had = ['_'.join((x['start'], x['end'])) for x in self.__metadata]
        return_names = []
        for name in names:
            if name not in had:
                return_names.append(name)
        return return_names

    def __get_timetable(self, name):
        response = self.__session.get('https://www.ztm.poznan.pl/pl/\
dla-deweloperow/getGTFSFile?file={}.zip'
                                      .format(name), verify='bundle.pem')
        zip_bytes = io.BytesIO(response.content)
        with zipfile.ZipFile(zip_bytes, 'r') as zip_file:
            zip_file.extractall()

    def __compress(self, checksum):
        with open('timetable.db', 'rb') as f_in:
            with gzip.open('{}.db.gz'.format(checksum), 'wb') as f_out:
                shutil.copyfileobj(f_in, f_out)
                os.chmod('{}.db.gz'.format(checksum), 0o644)

        Path('timetable.db').unlink()

    def __archive(self, name, checksum, size_u, size_c):
        metadata = {'size_uncompressed': size_u, 'size_compressed': size_c,
                    'id': checksum}
        start_date, end_date = name.split('_')
        metadata['start'] = start_date
        metadata['end'] = end_date
        self.__metadata.append(metadata)
        return metadata

    def __tidy_up(self):
        names = ['_'.join((row['start'], row['end'])) for row in
                 self.__metadata]
        to_stay = [name for name in names if self.__is_valid(name)
                   or self.__will_valid(name)]
        to_stay = self.__clean_overlapping(to_stay)
        to_remove = [name for name in names if name not in to_stay]
        to_remove = [row['id'] for row in self.__metadata
                     if '_'.join((row['start'], row['end'])) in to_remove]
        new_metadata = []
        for item in self.__metadata:
            if item['id'] not in to_remove:
                new_metadata.append(item)
            else:
                try:
                    Path('{}.db.gz'.format(item['id'])).unlink()
                except FileNotFoundError:
                    pass
                self.__upload_del(item['id'])

        self.__metadata = new_metadata

    def __upload(self, id, meta):
        with open('{}.db.gz'.format(id), 'rb') as f:
            t = f.read()
            sha = hashlib.sha256(t).hexdigest()
        print('uploading {}'.format(id))
        signature = config.key.sign(bytes(sha, 'utf-8'))
        data = msgpack.packb({'meta': meta, 'signature': signature.signature})
        length = len(data)
        data = bytes(str(length), 'utf-8')+b'\n'+data+t
        session = requests.Session()
        response = session.put(config.receiver, data)
        print(response)
        print(response.text)

    def __upload_del(self, id):
        print('uploading del {}'.format(id))
        signature = config.key.sign(bytes(id, 'utf-8'))
        session = requests.Session()
        s = str(base64.b64encode(signature.signature), 'utf-8')
        response = session.delete('{}/{}:{}'.format(config.receiver, id, s))
        print(response)
        print(response.text)


class TimetableConverter:
    __BUF_SIZE = 65536

    def __call__(self):
        return self.__convert()

    def __convert(self):
        connection = sqlite3.connect('timetable.db')
        self.__cursor = connection.cursor()
        self.__create_tables()
        self.__insert_agency()
        self.__insert_stops()
        self.__insert_routes()
        self.__insert_trips()
        self.__insert_stop_times()
        self.__insert_calendar()
        self.__insert_calendar_dates()
        self.__insert_shapes()
        self.__insert_feed_info()

        self.__create_indexes()

        connection.commit()
        checksum = self.__hash_file('timetable.db')
        print(checksum)
        return checksum

    @classmethod
    def __hash_file(cls, file):
        checksum = hashlib.sha256()

        with open(file, 'rb') as f:
            while True:
                data = f.read(cls.__BUF_SIZE)
                if not data:
                    break
                checksum.update(data)

        return checksum.hexdigest()

    def __insert_agency(self):
        with open('agency.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into agency values(?, ?, ?, ?, ?,
                                    ?)''', row)

    def __insert_stops(self):
        with open('stops.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into stops values(?, ?, ?, ?, ?,
                                    ?)''', row)

    def __insert_routes(self):
        with open('routes.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into routes values(?, ?, ?, ?, ?,
                                    ?, ?, ?)''', row)

    def __insert_trips(self):
        with open('trips.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into trips values(?, ?, ?, ?, ?,
                                    ?, ?)''', row)

    def __insert_stop_times(self):
        with open('stop_times.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into stop_times values(?, ?, ?,
                                    ?, ?, ?, ?, ?)''', row)

    def __insert_calendar(self):
        with open('calendar.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into calendar values(?, ?, ?, ?,
                                    ?, ?, ?, ?, ?, ?)''', row)

    def __insert_calendar_dates(self):
        with open('calendar_dates.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into calendar_dates values(?, ?,
                                    ?)''', row)

    def __insert_shapes(self):
        with open('shapes.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into shapes values(?, ?, ?,
                                    ?)''', row)

    def __insert_feed_info(self):
        with open('feed_info.txt', 'r') as csvfile:
            is_header_parsed = False
            reader = csv.reader(csvfile, delimiter=',', quotechar='"')
            for row in reader:
                if not is_header_parsed:
                    is_header_parsed = True
                    continue
                self.__cursor.execute('''insert into feed_info values(?, ?, ?, ?,
                                    ?)''', row)

    def __create_tables(self):
        self.__cursor.execute('''create table agency(agency_id INTEGER PRIMARY KEY,
                   agency_name TEXT,
                   agency_url TEXT,
                   agency_timezone TEXT,
                   agency_phone TEXT,
                   agency_lang TEXT)''')
        self.__cursor.execute('''create table stops(stop_id INTEGER PRIMARY KEY,
                   stop_code TEXT,
                   stop_name TEXT,
                   stop_lat DOUBLE,
                   stop_lon DOUBLE,
                   zone_id TEXT)''')
        self.__cursor.execute('''create table routes(route_id TEXT PRIMARY KEY,
                   agency_id INTEGER,
                   route_short_name TEXT,
                   route_long_name TEXT,
                   route_desc TEXT,
                   route_type INTEGER,
                   route_color TEXT,
                   route_text_color TEXT,
                   FOREIGN KEY(agency_id) REFERENCES agency(agency_id))''')
        self.__cursor.execute('''create table trips(route_id INTEGER,
                   service_id INTEGER,
                   trip_id TEXT PRIMARY KEY,
                   trip_headsign TEXT,
                   direction_id INTEGER,
                   shape_id INTEGER,
                   wheelchair_accessible BOOL,
                   FOREIGN KEY(route_id) REFERENCES routes(route_id),
                   FOREIGN KEY(service_id) REFERENCES calendar(service_id),
                   FOREIGN KEY(shape_id) REFERENCES shapes(shape_id))''')
        self.__cursor.execute('''create table stop_times(trip_id TEXT,
                   arrival_time TEXT,
                   departure_time TEXT,
                   stop_id INTEGER,
                   stop_sequence INTEGER,
                   stop_headsign TEXT,
                   pickup_type INTEGER,
                   drop_off_type INTEGER,
                   FOREIGN KEY(trip_id) REFERENCES trips(trip_id),
                   FOREIGN KEY(stop_id) REFERENCES stops(stop_id))''')
        self.__cursor.execute('''create table calendar(service_id INTEGER PRIMARY KEY,
                   monday TEXT,
                   tuesday TEXT,
                   wednesday TEXT,
                   thursday TEXT,
                   friday TEXT,
                   saturday TEXT,
                   sunday TEXT,
                   start_date TEXT,
                   end_date TEXT)''')
        self.__cursor.execute('''create table calendar_dates(service_id INTEGER,
                   date TEXT,
                   exception_type INTEGER,
                   FOREIGN KEY(service_id) REFERENCES calendar(service_id))''')
        self.__cursor.execute('''create table shapes(shape_id INTEGER,
                   shape_pt_lat DOUBLE,
                   shape_pt_lon DOUBLE,
                   shape_pt_sequence INTEGER)''')
        self.__cursor.execute('''create table feed_info(feed_publisher_name TEXT,
                   feed_publisher_url TEXT,
                   feed_lang TEXT,
                   feed_start_date TEXT,
                   feed_end_date TEXT)''')

    def __create_indexes(self):
        self.__cursor.execute('''create index ix_stop_times__stop
                   on stop_times(stop_id)''')
        self.__cursor.execute('''create index ix_stop_times__trip
                   on stop_times(trip_id)''')
        self.__cursor.execute('''create index ix_shapes__shape
                   on shapes(shape_id)''')


if __name__ == '__main__':
    downloader = TimetableDownloader()
    downloader()
    print('done')
