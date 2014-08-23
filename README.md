# Simple Twitter Console

Simple Twitter command-line client that monitors tweets and shows them to
stdout, which allows you to save your timeline into a file.

## Usage

% java -jar twitter-console-0.1.0-standalone.jar --session timeline | tee -a timeline.txt

## How to Compile

The application is written in Clojure. Leiningen is a standard front-end to
compile a Clojure project.

% lein uberjar

## License

Copyright (C) 2014 Daigo Moriwaki <daigo at debian dot com>

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
