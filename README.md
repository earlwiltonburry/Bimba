# Bimba [![Build Status](https://travis-ci.org/apiote/Bimba.svg?branch=master)](https://travis-ci.org/apiote/Bimba)
Bimba (pronounced BEEM-bah [ˈbiːmbʌ], Poznań subdialect for ‘tram’) is the first Free-Software Poznań wandering guide

With Bimba You can check the public transport timetable in Poznań agglomeration (run by ZTM Poznań), and thanks to the Virtual Monitor You can see when exactly a bus or tram will arrive.

<a href="https://f-droid.org/packages/ml.adamsprogs.bimba/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>

## Roadmap

### App

*more important higher*

*(tick – released, M – on master, T – testing)*

* [x] incremental timetable generator
* [x] favourite stops
    * [x] offline timetable
    * [x] Virtual Monitor
    * [x] peek all departures in a favourite
* [x] less non-intuitive timetable refresh gesture
* [ ] nearest stop(s) by GPS
* [ ] ‘through mid-stop’ on lines with only 1 direction
* [ ] city bike stations on map
* [ ] searching by line number
* [ ] stops on map
* [ ] refreshing on wakeup
* [x] VM times immediately if online, not through offline timetable
* [x] detecting holiday
* [ ] ticket machines on map
* [ ] ever-present searchbar
* [ ] other things on map


## If You want to help…

### …be sure to…

* check the issues, both closed and open;
* check the most recent commit. Master contains published snapshots; most recent commit will be on other branch (there’s a branch with upcomming version and branches with betas that are periodically merged into it).

### …then You can…

* add a new translation. Just translate `strings.xml` and make a pull request or an issue;
* set up Your own converter instance. For more info head to the [converter readme](converter/README.md)
* help me move my own converter to some PaaS (like Heroku or other I-don’t-know-because-I-cannot-into-cloud)
* think about any other way
* <small> donate. More info [there](http://adamsprogs.tk/w/donate/)</small>


## Thanks to…

* @tebriz159 for new logo (#4)
* @Vistaus for Dutch translation (#5)

---

    Bimba
    Copyright (C) 2017–2018  Adam Pioterek

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

- Icons [Material](https://material.io/icons), (c) Google Apache 2.0
- Feature image [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Poznan._Kaponiera_finally_opened_(44).jpg), (c) MOs810 CC BY-SA 4.0
- [Search View](https://github.com/arimorty/floatingsearchview), (c) arimorty Apache 2.0
- JSON [gson](https://github.com/google/gson), (c) Google Apache 2.0
- HTTP [okhttp](https://github.com/square/okhttp), (c) square Apache 2.0
- [SQLite](https://github.com/requery/sqlite-android), (c) requery Apache 2.0
