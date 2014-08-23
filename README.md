# Simple Twitter Console

Simple Twitter command-line client that monitors tweets and shows them to
stdout, which allows you to save your timeline into a file.

## Usage

You need to have a set of your own access keys: consumer token/secret and access
token/secret. Browse http://twitter.com/apps and apply this application as
yours to get those four keys. 

You can pass keys via environment variables.

```
% export SIMPLE_TWITTER_CONSOLE_CONSUMER_KEY="yours"
% export SIMPLE_TWITTER_CONSOLE_CONSUMER_SECRET="yours"
% export SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN="yours"
% export SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN_SECRET="yours"
```

The application has two modes:

* --timeline: monitor your home timeline
* --query: monitor a search keyword

### Bookmark and Session Name

The application saves a kind of bookmark for messages you have already
retrieved in a file located at /var/tmp/simple_twitter_client_<session_name>.
When you run multiple processes, say to monitor several search keywords,
assign unique session names for each process.

```
% java -jar twitter-console-0.1.0-standalone.jar --session timeline --timeline | tee -a timeline.txt
% java -jar twitter-console-0.1.0-standalone.jar --session clojure --query clojure | tee -a clojure.txt
```

## How to Compile

The application is written in Clojure. Leiningen is a standard front-end to
compile a Clojure project.

```
% lein uberjar
```

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
