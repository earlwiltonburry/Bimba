# Bimba [![Build Status](https://travis-ci.org/apiote/Bimba.svg?branch=master)](https://travis-ci.org/apiote/Bimba)
Bimba (pronounced BEEM-bah [ˈbiːmbʌ], Poznań subdialect for ‘tram’) is the first Free-Software Poznań wandering guide

With Bimba You can check the public transport timetable in Poznań agglomeration (run by ZTM Poznań), and thanks to the Virtual Monitor You can see when exactly a bus or tram will arrive.

<a href="https://f-droid.org/packages/ml.adamsprogs.bimba/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>

Bimba can be found at [Mastodon](https://floss.social/@bimba)

## Mirror

If You’re reading this on Github, You’re seeing a mirror. Changes are pushed to the mirror only when there’s a push to `master`. The original repo is available on [NotABug](https://notabug.org/apiote/Bimba).

Tags (releases) are published in both services but binary builds after version 2.0 are available only on NotABug.

Issues will be tracked in both services. Pull requests on Github will be asked to be sent via e-mail as `diff`s.

## Roadmap

*more important higher*

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
* check the most recent commit. Master contains published snapshots; The most recent commit will be on other branch—most likely `develop`, but there may be a release, feature or hotfix branches. For full description of workflow model head to [Nvie’s model](https://nvie.com/posts/a-successful-git-branching-model/).

### …then You can…

* add a new translation. Just translate `strings.xml` and make a pull request or an issue;
* set up Your own converter instance. For more info head to the [converter readme](converter/README.md)
* help me move my own converter to some PaaS (like Heroku or other I-don’t-know-because-I-cannot-into-cloud)
* think about any other way
* <small> donate. More info [there](http://apiote.tk/donate/)</small>

## Thanks to…

* [tebriz159@github](https://github.com/tebriz159) for new logo ([#4](https://github.com/apiote/Bimba/issues/4))
* [Vistaus@github](https://github.com/Vistaus) for Dutch translation ([#5](https://github.com/apiote/Bimba/pull/5))

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

- Icons [Material](https://material.io/icons), ⓒ Google Apache 2.0
- Feature image [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Poznan._Kaponiera_finally_opened_(44).jpg), ⓒ MOs810 CC BY-SA 4.0
- [Search View](https://github.com/arimorty/floatingsearchview), ⓒ arimorty Apache 2.0
- JSON [gson](https://github.com/google/gson), ⓒ Google Apache 2.0
- HTTP [okhttp](https://github.com/square/okhttp), ⓒ square Apache 2.0
- [SQLite](https://github.com/requery/sqlite-android), ⓒ requery Apache 2.0
