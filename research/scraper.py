#!/bin/python
"""
js interface: http://www.ztm.poznan.pl/themes/ztm/dist/js/app.js
nodes: http://www.ztm.poznan.pl/goeuropa-api/all-nodes
stops in node: http://www.ztm.poznan.pl/goeuropa-api/node_stops/{node:symbol}
stops: http://www.ztm.poznan.pl/goeuropa-api/stops-nodes
bike stations: http://www.ztm.poznan.pl/goeuropa-api/bike-stations

"""
import json
import hashlib
import os
import re
import sqlite3
import sys
import time
import requests
from bs4 import BeautifulSoup
import secrets


def remove_options(text):
    return re.sub('(<select[^>]*>([^<]*<option[^>]*>[^<]*</option>)+[^<]*</select>)','', text)


def get_validity():
    """
    get timetable validity
    """
    session = requests.session()
    index = session.get('https://www.ztm.poznan.pl/goeuropa-api/index', verify='bundle.pem')
    option = re.search('<option value="[0-9]{8}" selected', index.text).group()
    return option.split('"')[1]


def get_nodes(checksum):
    """
    get nodes
    """
    session = requests.session()

    index = session.get('https://www.ztm.poznan.pl/goeuropa-api/all-nodes', verify='bundle.pem')
    new_checksum = hashlib.sha512(index.text.encode('utf-8')).hexdigest()
    if checksum == new_checksum:
        return None
    return [(stop['symbol'], stop['name']) for stop in json.loads(index.text)], new_checksum


def get_stops(node, checksum):
    """
    get stops
    """
    session = requests.session()

    index = session.get('https://www.ztm.poznan.pl/goeuropa-api/node_stops/{}'.format(node),
                        verify='bundle.pem')
    new_checksum = hashlib.sha512(index.text.encode('utf-8')).hexdigest()
    if checksum == new_checksum:
        return None
    stops = []
    for stop in json.loads(index.text):
        stop_id = stop['stop']['id']
        number = re.findall("\\d+", stop['stop']['symbol'])[0]
        lat = stop['stop']['lat']
        lon = stop['stop']['lon']
        directions = ', '.join(['{} → {}'.format(transfer['name'], transfer['headsign'])
                                for transfer in stop['transfers']])
        stops.append((stop_id, node, number, lat, lon, directions))
    return stops, new_checksum


def get_lines(checksum):
    """
    get lines
    """
    session = requests.session()

    index = session.get('https://www.ztm.poznan.pl/goeuropa-api/index', verify='bundle.pem')
    index = re.sub('route-modal-[0-9a-f]{7}', '', index.text)
    index = remove_options(index)
    new_checksum = hashlib.sha512(index.encode('utf-8')).hexdigest()
    if new_checksum == checksum:
        return None
    soup = BeautifulSoup(index, 'html.parser')

    lines = {line['data-lineid']: line.text for line in
             soup.findAll(attrs={'class': re.compile(r'.*\blineNo-bt\b.*')})}

    return lines, new_checksum


def get_route(line_id):
    """
    get routes
    """
    session = requests.session()

    index = session.get('https://www.ztm.poznan.pl/goeuropa-api/line-info/{}'.format(line_id),
                        verify='bundle.pem')
    soup = BeautifulSoup(index.text, 'html.parser')
    directions = soup.findAll(attrs={'class': re.compile(r'.*\baccordion-item\b.*')})
    routes = {}
    for direction in directions:
        direction_id = direction['data-directionid']
        route = [{'id': stop.find('a')['data-stopid'], 'name': stop['data-name'],
                  'onDemand': re.search('stop-onDemand', str(stop['class'])) != None}
                 for stop in direction.findAll(attrs={'class': re.compile(r'.*\bstop-itm\b.*')})]
        routes[direction_id] = route
    return routes


def get_stop_times(stop_id, line_id, direction_id, checksum):
    """
    get timetable
    """
    session = requests.session()

    index = session.post('https://www.ztm.poznan.pl/goeuropa-api/stop-info/{}/{}'.
                         format(stop_id, line_id), data={'directionId': direction_id},
                         verify='bundle.pem')
    index = re.sub('route-modal-[0-9a-f]{7}', '', index.text)
    index = remove_options(index)
    new_checksum = hashlib.sha512(index.encode('utf-8')).hexdigest()
    if new_checksum == checksum:
        return None
    soup = BeautifulSoup(index, 'html.parser')
    legends = {}
    for row in soup.find(attrs={'class': re.compile(r'.*\blegend-box\b.*')}).findAll('li'):
        row = row.text.split('-')
        row[0] = row[0].rstrip()
        row[1] = row[1].lstrip()
        if row[0] != '_':
            legends[row[0]] = '-'.join(row[1:])
    schedules = {}
    for mode in soup.findAll(attrs={'class': re.compile(r'.*\bmode-tab\b.*')}):
        mode_name = mode['data-mode']
        schedule = {row.find('th').text: [
            {'time': minute.text, 'lowFloor': re.search('n-line', str(minute['class'])) != None}
            for minute in row.findAll('a')]
                    for row in mode.find(attrs={'class': re.compile(r'.*\bscheduler-hours\b.*')}).
                    findAll('tr')}
        schedule_2 = {hour: times for hour, times in schedule.items() if times != []}
        schedule = []
        for hour, deps in schedule_2.items():
            for dep in deps:
                schedule.append((hour, *describe(dep['time'], legends), dep['lowFloor']))
        schedules[mode_name] = schedule

    return schedules, new_checksum


def describe(dep_time, legend):
    """
    describe departure
    """
    desc = []
    while re.match('^\\d+$', dep_time) is None:
        if dep_time[-1] != ',':
            desc.append(legend[dep_time[-1]])
        dep_time = dep_time[:-1]
    return (int(dep_time), '; '.join(desc))


def main():
    """
    main function
    """
    updating = False
    changed = False
    if os.path.exists('timetable.db'):
        updating = True


    with sqlite3.connect('timetable.db') as connection:
        try:
            cursor = connection.cursor()
            if updating:
                cursor.execute("select value from metadata where key = 'validFrom'")
                current_valid_from = cursor.fetchone()[0]
                if get_validity() <= current_valid_from:
                    return 304
            else:
                cursor.execute('create table metadata(key TEXT PRIMARY KEY, value TEXT)')
                cursor.execute('create table checksums(checksum TEXT, for TEXT, id TEXT)')
                cursor.execute('create table nodes(symbol TEXT PRIMARY KEY, name TEXT)')
                cursor.execute('create table stops(id TEXT PRIMARY KEY, symbol TEXT \
                                references node(symbol), number TEXT, lat REAL, lon REAL, \
                                headsigns TEXT)')
                cursor.execute('create table lines(id TEXT PRIMARY KEY, number TEXT)')
                cursor.execute('create table timetables(id TEXT PRIMARY KEY, stop_id TEXT references stop(id), \
                                line_id TEXT references line(id), headsign TEXT)')
                cursor.execute('create table departures(id INTEGER PRIMARY KEY, \
                                timetable_id TEXT references timetable(id), \
                                hour INTEGER, minute INTEGER, mode TEXT, \
                                lowFloor INTEGER, modification TEXT)')

            cursor.execute("delete from metadata where key = 'validFrom'")
            validity = get_validity()
            print(validity)
            cursor.execute("insert into metadata values('validFrom', ?)", (validity,))
            cursor.execute("select checksum from checksums where for = 'nodes'")
            checksum = cursor.fetchone()
            if checksum != None:
                checksum = checksum[0]
            else:
                checksum = ''
            nodes_result = get_nodes(checksum)
            if nodes_result is not None:
                nodes, checksum = nodes_result
                cursor.execute('delete from nodes')
                cursor.execute("delete from checksums where for = 'nodes'")
                cursor.execute("insert into checksums values(?, 'nodes', null)", (checksum,))  # update
                cursor.executemany('insert into nodes values(?, ?)', nodes)
                changed = True
            else:
                cursor.execute('select * from nodes')
                nodes = cursor.fetchall()
                nodes = [(sym, nam) for sym, nam, _ in nodes]
            nodes_no = len(nodes)
            node_i = 1
            for symbol, _ in nodes:
                sys.stdout.flush()
                cursor.execute("select checksum from checksums where for = 'node' and id = ?", (symbol,))
                checksum = cursor.fetchone()
                if checksum != None:
                    checksum = checksum[0]
                else:
                    checksum = ''
                stops_result = get_stops(symbol, checksum)
                if stops_result is not None:
                    stops, checksum = stops_result
                    cursor.execute('delete from stops where symbol = ?', (symbol,))
                    cursor.executemany('insert into stops values(?, ?, ?, ?, ?, ?)', stops)
                    cursor.execute("update checksums set checksum = ? where for = 'node' and id = ?", (checksum, symbol))
                    changed = True
                node_i += 1
            cursor.execute("select checksum from checksums where for = 'lines'")
            checksum = cursor.fetchone()
            if checksum != None:
                checksum = checksum[0]
            else:
                checksum = ''
            lines_result = get_lines(checksum)
            if lines_result is not None:
                lines, checksum = lines_result
                cursor.execute('delete from lines')
                cursor.execute("delete from checksums where for = 'lines'")
                cursor.execute("insert into checksums values(?, 'lines', null)", (checksum,))  # update
                cursor.executemany('insert into lines values(?, ?)', lines.items())
                changed = True
            else:
                cursor.execute('select * from lines')
                lines = cursor.fetchall()

            lines_no = len(lines)
            line_i = 1
            for line_id, _ in lines.items():
                route = get_route(line_id)
                routes_no = len(route)
                route_i = 1
                for direction, stops in route.items():
                    stops_no = len(stops)
                    stop_i = 1
                    for stop in stops:
                        timetable_id = secrets.token_hex(4)
                        sys.stdout.flush()
                        cursor.execute("select checksum from checksums where for = 'timetable' and id = ?", (timetable_id,))
                        checksum = cursor.fetchone()
                        if checksum != None:
                            checksum = checksum[0]
                        else:
                            checksum = ''
                        stop_times = get_stop_times(stop['id'], line_id, direction, checksum)
                        if stop_times is not None:
                            timetables, checksum = stop_times
                            cursor.execute('delete from timetables where line_id = ? and stop_id = ?',
                                           (line_id, stop['id']))
                            cursor.execute('insert into timetables values(?, ?, ?, ?)',
                                           (timetable_id, stop['id'], line_id, stops[-1]['name']))
                            cursor.execute("insert into checksums values(?, 'timetable', ?)", (checksum, timetable_id))
                            changed = True
                            cursor.execute('delete from departures where timetable_id = ?',
                                           (timetable_id,))
                            for mode, times in timetables.items():
                                cursor.executemany('insert into departures values(null, ?, ?, ?, ?, ?, \
                                                    ?)', [(timetable_id, hour, minute, mode, lowfloor, desc)
                                                          for hour, minute, desc, lowfloor in times])
                        stop_i += 1
                        sys.stdout.flush()
                    route_i += 1
                line_i += 1
        except KeyboardInterrupt:
            return 404
    if changed:
        return 0
    return 304


if __name__ == '__main__':
    exit(main())
