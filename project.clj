(defproject twitter_console "0.1.0"
  :description "Simple Twitter command-line client that monitors tweets and shows them to
stdout, which allows you to save your timeline into a file."
  :url "https://github.com/daigo/simple-twitter-console"
  :license {:name "GPL v3 or later"
            :url "http://www.gnu.org/copyleft/gpl.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.twitter4j/twitter4j-core "4.0.2"]
                 [org.twitter4j/twitter4j-stream "4.0.2"]]
  :aot [twitter_console.core]
  :main twitter_console.core)
