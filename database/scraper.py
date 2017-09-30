#!/bin/python
"""
js interface: http://www.ztm.poznan.pl/themes/ztm/dist/js/app.js
nodes: http://www.ztm.poznan.pl/goeuropa-api/all-nodes
stops in node: http://www.ztm.poznan.pl/goeuropa-api/node_stops/{node:symbol}
stops: http://www.ztm.poznan.pl/goeuropa-api/stops-nodes
bike stations: http://www.ztm.poznan.pl/goeuropa-api/bike-stations

alerts: goeuropa-api/alerts/' + lineId;

"""
import json
import os
import re
import sqlite3
import sys
import requests
from bs4 import BeautifulSoup

class TimetableDownloader:
    """
    downloader class
    """
    def __init__(self, verbose):
        self.session = requests.session()
        self.verbose = verbose


    def __get_validity(self):
        """
        get timetable validity
        """
        index = self.__get('https://www.ztm.poznan.pl/goeuropa-api/index')
        option = re.search('<option value="[0-9]{8}" selected', index.text).group()
        return option.split('"')[1]


    def __get_nodes(self):
        """
        get nodes
        """
        index = self.__get('https://www.ztm.poznan.pl/goeuropa-api/all-nodes')
        return [(stop['symbol'], stop['name']) for stop in json.loads(index.text)]


    def __get_stops(self, node):
        """
        get stops
        """
        index = self.__get('https://www.ztm.poznan.pl/goeuropa-api/node_stops/{}'.format(node))
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


    def __get_lines(self):
        """
        get lines
        """
        index = self.__get('https://www.ztm.poznan.pl/goeuropa-api/index')
        soup = BeautifulSoup(index.text, 'html.parser')

        lines = {line['data-lineid']: line.text for line in
                 soup.findAll(attrs={'class': re.compile(r'.*\blineNo-bt\b.*')})}

        return lines


    def __get_route(self, line_id):
        """
        get routes
        """
        index = self.__get('https://www.ztm.poznan.pl/goeuropa-api/line-info/{}'.format(line_id))
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


    def __get_stop_times(self, stop_id, line_id, direction_id):
        """
        get timetable
        """

        """ todo get time to next stop:
            <div class="route-timeline">
            <ul>
            <li…>
            <span class="stop-title">{current node_name} (n/ż)?</span>    --> if not present, return None
            …
            </li>
            <li…>
            …
            <span class="time">{time:INT}'</span>
            </li>
            </ul>
            </div>

        """

        index = self.__post('https://www.ztm.poznan.pl/goeuropa-api/stop-info/{}/{}'.
                                  format(stop_id, line_id), {'directionId': direction_id})
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
                    schedule.append((hour, *self.__describe(dep['time'], legends), dep['lowFloor']))
            schedules[mode_name] = schedule

        return schedules


    @staticmethod
    def __describe(dep_time, legend):
        """
        describe departure
        """
        desc = []
        while re.match('^\\d+$', dep_time) is None:
            try:
                if dep_time[-1] != ',':
                    desc.append(legend[dep_time[-1]])
            except KeyError:
                pass
            dep_time = dep_time[:-1]
        return (int(dep_time), '; '.join(desc))


    def __get(self, url):
        try:
            return self.session.get(url, verify='bundle.pem')
        except:
            self.session = requests.session()
            return self.session.get(url, verify='bundle.pem')


    def __post(self, url, data):
        try:
            return self.session.post(url, data=data, verify='bundle.pem')
        except:
            self.session = requests.session()
            return self.session.post(url, data=data, verify='bundle.pem')


    def download(self):
        """
        main function
        """
        if os.path.exists('timetable.db'):
            connection = sqlite3.connect('timetable.db')
            cursor = connection.cursor()
            cursor.execute("select value from metadata where key = 'validFrom'")
            current_valid_from = cursor.fetchone()[0]
            cursor.close()
            connection.close()
            if self.__get_validity() <= current_valid_from:
                return 304
            else:
                os.remove('timetable.db')

        with sqlite3.connect('timetable.db') as connection:
            try:
                cursor = connection.cursor()
                cursor.execute('create table metadata(key TEXT PRIMARY KEY, value TEXT)')
                cursor.execute('create table nodes(symbol TEXT PRIMARY KEY, name TEXT)')
                cursor.execute('create table stops(id TEXT PRIMARY KEY, symbol TEXT \
                                references node(symbol), number TEXT, lat REAL, lon REAL, \
                                headsigns TEXT)')
                cursor.execute('create table lines(id TEXT PRIMARY KEY, number TEXT)')
                cursor.execute('create table timetables(id TEXT PRIMARY KEY, stop_id TEXT references \
                                stop(id), line_id TEXT references line(id), headsign TEXT, \
                                numberInRoute INTEGER)')
                cursor.execute('create table departures(id INTEGER PRIMARY KEY, \
                                timetable_id TEXT references timetable(id), \
                                hour INTEGER, minute INTEGER, mode TEXT, \
                                lowFloor INTEGER, modification TEXT)')

                validity = self.__get_validity()
                print(validity)
                sys.stdout.flush()
                cursor.execute("insert into metadata values('validFrom', ?)", (validity,))
                nodes = self.__get_nodes()
                cursor.executemany('insert into nodes values(?, ?)', nodes)
                node_i = 1
                for symbol, _ in nodes:
                    if self.verbose:
                        print('node {}'.format(node_i))
                    stops = self.__get_stops(symbol)
                    cursor.executemany('insert into stops values(?, ?, ?, ?, ?, ?)', stops)
                    node_i += 1
                lines = self.__get_lines()
                cursor.executemany('insert into lines values(?, ?)', lines.items())

                timetable_id = 1
                line_i = 1
                for line_id, _ in lines.items():
                    route = self.__get_route(line_id)
                    route_i = 1
                    for direction, stops in route.items():
                        stop_i = 1
                        for stop in stops:
                            if self.verbose:
                                print("stop {} in route {} in line {}".format(stop_i, route_i, line_i))
                            timetables = self.__get_stop_times(stop['id'], line_id, direction)
                            cursor.execute('insert into timetables values(?, ?, ?, ?, ?)',
                                           (timetable_id, stop['id'], line_id, stops[-1]['name'], stop_i))
                            for mode, times in timetables.items():
                                cursor.executemany('insert into departures values(null, ?, ?, ?, ?, ?, \
                                                    ?)', [(timetable_id, hour, minute, mode, lowfloor, desc)
                                                          for hour, minute, desc, lowfloor in times])
                            stop_i += 1
                            timetable_id += 1
                        route_i += 1
                    line_i += 1
            except KeyboardInterrupt:
                return 404
        return 0


if __name__ == '__main__':
    verbose = False
    try:
        if sys.argv[1] == '-v':
            verbose = True
    except IndexError:
        pass
    downloader = TimetableDownloader(verbose)
    exit(downloader.download())
