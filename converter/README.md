## Key file

In order to upload timetables automatically, private key needs to be provided to `uploader.py`.
`uploader.py` imports `config.py` which needs to be as follows:

```
import nacl.signing

key = nacl.signing.SigningKey(
        b'<here goes hexdigest of private key (64 hexadecimal digits)>',
        encoder=nacl.encoding.HexEncoder)

storage = '<url pointing to where timetables lie with {} placeholder for id>'
receiver = '<url to server script which receives commands to store timetables>'
```

## License

    Converter from gtfs to sqlite database
    Copyright (C) 2018 Adam Pioterek

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

## Libraries

YAML [Spyc](https://github.com/mustangostang/spyc), (c) mustangostang MIT

Sodium [Sodium Compat](https://github.com/paragonie/sodium_compat), (c) paragonie ISC

Msgpack [msgpack.php](https://github.com/rybakit/msgpack.php), (c) rybakit MIT
