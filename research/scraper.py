#!/bin/python
"""
js interface: http://ztm.poznan.pl/themes/ztm/dist/js/app.js
nodes: http://ztm.poznan.pl/goeuropa-api/all-nodes
stops in node: http://ztm.poznan.pl/goeuropa-api/node_stops/{node:symbol}
stops: http://ztm.poznan.pl/goeuropa-api/stops-nodes
bike stations: http://ztm.poznan.pl/goeuropa-api/bike-stations

"""
import json
import hashlib
import re
import sqlite3
import sys
import time
import requests
from bs4 import BeautifulSoup

def get_nodes():
    """
    get nodes
    """
    session = requests.session()

    index = session.get('http://ztm.poznan.pl/goeuropa-api/all-nodes')
    return [(stop['symbol'], stop['name']) for stop in json.loads(index.text)]


def get_stops(node):
    """
    get stops
    """
    session = requests.session()

    index = session.get('http://ztm.poznan.pl/goeuropa-api/node_stops/{}'.format(node))
    stops = []
    for stop in json.loads(index.text):
        stop_id = stop['stop']['id']
        number = re.findall("\\d+", stop['stop']['symbol'])[0]
        lat = stop['stop']['lat']
        lon = stop['stop']['lon']
        directions = ', '.join(['{} → {}'.format(transfer['name'], transfer['headsign'])
                                for transfer in stop['transfers']])
        stops.append((stop_id, node, number, lat, lon, directions))
    return stops


def get_lines():
    """
    get lines
    """
    session = requests.session()

    index = session.get('http://ztm.poznan.pl/goeuropa-api/index')
    soup = BeautifulSoup(index.text, 'html.parser')

    lines = {line['data-lineid']: line.text for line in
             soup.findAll(attrs={'class': re.compile(r'.*\blineNo-bt\b.*')})}

    return lines


def get_route(line_id):
    """
    get routes
    """
    session = requests.session()

    index = session.get('http://ztm.poznan.pl/goeuropa-api/line-info/{}'.format(line_id))
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


def get_stop_times(stop_id, line_id, direction_id):
    """
    get timetable
    """
    session = requests.session()

    index = session.post('http://ztm.poznan.pl/goeuropa-api/stop-info/{}/{}'.
                         format(stop_id, line_id), data={'directionId': direction_id})
    soup = BeautifulSoup(index.text, 'html.parser')
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

    return schedules, hashlib.sha512(index.text.encode('utf-8')).hexdigest()


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
    print(time.time())
    with sqlite3.connect('timetable.db') as connection:
        print('creating tables')
        cursor = connection.cursor()
        cursor.execute('create table nodes(symbol TEXT PRIMARY KEY, name TEXT)')
        cursor.execute('create table stops(id TEXT PRIMARY KEY, symbol TEXT \
                        references node(symbol), number TEXT, lat REAL, lon REAL, headsigns TEXT)')
        cursor.execute('create table lines(id TEXT PRIMARY KEY, number TEXT)')
        cursor.execute('create table timetables(id INTEGER PRIMARY KEY, stop_id \
                        TEXT references stop(id), line_id TEXT references line(id), \
                        headsign TEXT, checksum TEXT)')
        cursor.execute('create table departures(id INTEGER PRIMARY KEY, timetable_id INTEGER \
                        references timetable(id), hour INTEGER, minute INTEGER, mode TEXT, \
                        lowFloor INTEGER, modification TEXT)')

        print('getting nodes')
        nodes = get_nodes()
        cursor.executemany('insert into nodes values(?, ?);', nodes)
        nodes_no = len(nodes)
        print('getting stops')
        node_i = 1
        for symbol, _ in nodes:
            print('\rstop {}/{}'.format(node_i, nodes_no), end='')
            sys.stdout.flush()
            cursor.executemany('insert into stops values(?, ?, ?, ?, ?, ?);', get_stops(symbol))
            node_i += 1
        print('')
        lines = get_lines()
        lines_no = len(lines)
        line_i = 1
        tti = 0
        cursor.executemany('insert into lines values(?, ?);', lines.items())
        for line_id, _ in lines.items():
            route = get_route(line_id)
            routes_no = len(route)
            route_i = 1
            for direction, stops in route.items():
                stops_no = len(stops)
                stop_i = 1
                for stop in stops:
                    print('line {}/{} route {}/{} stop {}/{}'.
                          format(line_i, lines_no, route_i, routes_no, stop_i, stops_no), end='')
                    sys.stdout.flush()
                    timetables, checksum = get_stop_times(stop['id'], line_id, direction)
                    cursor.execute('insert into timetables values(?, ?, ?, ?, ?);',
                                   (tti, stop['id'], line_id, stops[-1]['name'], checksum))
                    for mode, times in timetables.items():
                        cursor.executemany('insert into departures values(null, ?, ?, ?, ?, ?, ?);',
                                           [(tti, hour, minute, mode, lowfloor, desc)
                                            for hour, minute, desc, lowfloor in times])
                    stop_i += 1
                    tti += 1
                    print('{}\r'.format(' '*35), end='')
                    sys.stdout.flush()
                route_i += 1
            print('')
            line_i += 1
    print(time.time())


if __name__ == '__main__':
    main()
