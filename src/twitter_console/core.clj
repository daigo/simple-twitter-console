;;; Copyright (C) 2014 Daigo Moriwaki <daigo at debian dot com>
;;;
;;; This program is free software; you can redistribute it and/or modify
;;; it under the terms of the GNU General Public License as published by
;;; the Free Software Foundation; either version 3 of the License, or
;;; (at your option) any later version.
;;; 
;;; This program is distributed in the hope that it will be useful,
;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;; GNU General Public License for more details.
;;; 
;;; You should have received a copy of the GNU General Public License
;;; along with this program; if not, write to the Free Software
;;; Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

(ns twitter_console.core
  (:import (twitter4j TwitterFactory TwitterException Paging Query))
  (:import (twitter4j.conf ConfigurationBuilder))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class :main true))

;; ==================================================
;; Global variables
;; ==================================================

(def cli-options
  [["-h" "--help"]
   ["-i" "--interval INTERVAL" "Interval minutes to reload"
    :default 20
    :parse-fn #(Integer/parseInt %)]
   ["-q" "--query QUERY"       "Query mode"]
   ["-r" "--rate-limit"        "Show all the current rate limit information"]
   ["-s" "--session SESSION"   "Session name to identify this process"]
   ["-t" "--timeline"          "Timeline mode"] ])

;; Holds commandline options
(def opts (atom {}))

;; How many statues to get in a batch. Max 200.
(def PAGING_COUNT 200)

;; ==================================================
;; Functions
;; ==================================================

(defn println-err
  "Print str to stderr."
  [str]
  (binding [*out* *err*]
    (println str)))

(defn twitter-instance
  "Return an instance of Twitter class by using keys read from environment varibles:
    * SIMPLE_TWITTER_CONSOLE_CONSUMER_KEY
    * SIMPLE_TWITTER_CONSOLE_CONSUMER_SECRET
    * SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN
    * SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN_SECRET"
  []
  (let [env (System/getenv)
        [consumer-key
         consumer-secret
         access-token
         token-secret] (map #(get env %) ["SIMPLE_TWITTER_CONSOLE_CONSUMER_KEY"
                                          "SIMPLE_TWITTER_CONSOLE_CONSUMER_SECRET"
                                          "SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN"
                                          "SIMPLE_TWITTER_CONSOLE_ACCESS_TOKEN_SECRET"])]
    (when (some nil? [consumer-key consumer-secret access-token token-secret])
      (throw (IllegalStateException. "Twitter configuration not found")))
    (let [conf (doto (ConfigurationBuilder.)
                 (.setDebugEnabled           true)
                 (.setOAuthConsumerKey       consumer-key)
                 (.setOAuthConsumerSecret    consumer-secret)
                 (.setOAuthAccessToken       access-token)
                 (.setOAuthAccessTokenSecret token-secret))]
     (.getInstance (TwitterFactory. (.build conf))))))

(defn file-last-id
  "Returns a File instance to hold the last id."
  []
  (let [home (get (System/getenv) "HOME")
        dir  (io/file home ".simple_twitter_console")]
    (if-not (.exists dir)
      (.mkdir dir))
    (io/file dir (:session @opts))))

(defn check-last-id
  "Loads and returns the last id. If a file does not exist, returns 0."
  []
  (let [file ^java.io.File (file-last-id)]
    (if (.exists file)
      (Long/parseLong (slurp file))
      0)))

(defn save-last-id
  "Saves the last id to a specific file."
  [last-id]
  (spit (file-last-id) last-id))

(defn clear-last-id
  "Delete the last id file"
  []
  (let [file ^java.io.File (file-last-id)]
    (if (.exists file)
      (.delete file))))

(defn max-id
  [xs]
  (.getId ^twitter4j.Status (first xs)))

(defn next-max-id
  [xs]
  (dec (.getId ^twitter4j.Status (last xs))))

(defn getHomeTimeline
  "Returns a sequence of statuses of home timeline."
  ([^twitter4j.Twitter twitter]
   (let [paging (Paging. 1 PAGING_COUNT)]
     (.getHomeTimeline twitter paging)))
  ([^twitter4j.Twitter twitter since-id]
   (let [paging (Paging. 1 PAGING_COUNT since-id)
         col    (.getHomeTimeline twitter paging)]
     (if (empty? col)
       col
       (getHomeTimeline twitter
                        col
                        since-id
                        (next-max-id col)
                        (max-id col)))))
  ([^twitter4j.Twitter twitter xs since-id max-id last-id]
   (if (<= max-id since-id)
     xs
     (let [paging (Paging. 1 PAGING_COUNT since-id max-id)
           col    (.getHomeTimeline twitter paging)]
       (if (empty? col)
         xs
         (recur twitter
                 (concat xs col)
                 since-id
                 (next-max-id col)
                 last-id))))))

(defn getQuery
  ""
  ([^twitter4j.Twitter twitter search-str]
   (let [query (doto (Query. search-str)
                 (.setCount PAGING_COUNT))]
     (-> (.search twitter query)
       .getTweets)))
  ([^twitter4j.Twitter twitter search-str since-id]
   (let [query  (doto (Query. search-str)
                  (.setCount PAGING_COUNT)
                  (.setSinceId since-id))
         tweets (-> (.search twitter query) .getTweets)]
     (if (empty? tweets)
       []
       (getQuery twitter
                 search-str
                 tweets
                 since-id
                 (next-max-id tweets)
                 (max-id tweets)))))
  ([^twitter4j.Twitter twitter search-str xs since-id max-id last-id]
   (println-err (format "Reading %d   %d..%d" last-id since-id max-id))
   (if (<= max-id since-id)
     xs
     (let [query  (doto (Query. search-str)
                    (.setCount PAGING_COUNT)
                    (.setSinceId since-id)
                    (.setMaxId max-id))
           tweets (-> (.search twitter query) .getTweets)]
       (if (empty? tweets)
         xs
         (recur twitter
                search-str
                (concat xs tweets)
                since-id
                (next-max-id tweets)
                last-id))))))

(defn show-all-rate-limit
  "Dumps all the current rate limit information."
  [^twitter4j.Twitter twitter]
  (let [m (.getRateLimitStatus twitter)]
    (doseq [k (sort (keys m))]
      (println k ", " (get m k)))))

(defn getRateLimit
  "Returns a map denoting rate limit information."
  [^twitter4j.Twitter twitter resource]
  (let [status ^twitter4j.RateLimitStatus (get (.getRateLimitStatus twitter)
                                               "/statuses/home_timeline")]
    {:limit (.getLimit status)
     :remaining (.getRemaining status)
     :resetTimeInSeconds (.getResetTimeInSeconds status)
     :secondsUntilReset  (.getSecondsUntilReset status)}))

(defn str-name
  "Returns a string representing a user name for a Status"
  [^twitter4j.Status status]
  (let [user ^twitter4j.User (.getUser status)]
    (format "%s @%s"
            (.getName user)
            (.getScreenName user))))

(defn println-message
  "Returns a formatted string representing a tweet message."
  [^twitter4j.Status status]
  (if (.isRetweet status)
    (println (format ">>> (x%d retweeted by %s) %s [%s]"
                     (.getRetweetCount status)
                     (str-name status)
                     (str-name (.getRetweetedStatus status))
                     (-> (.getCreatedAt status) .toString)))
    (println (format ">>> %s [%s]"
                     (str-name status)
                     (-> (.getCreatedAt status) .toString))))
  (println (.getText status)))

(defn check-and-wait-rate-limit
  [^twitter4j.Twitter twitter resource]
  (let [rate-limit (getRateLimit twitter resource)]
    (when-not (pos? (:remaining rate-limit))
      (println-err (format "Rate limit has run out. Sleep in %s seconds..."
                          (:secondsUntilReset rate-limit)))
      (Thread/sleep (* 1000 (+ 10 (:secondsUntilReset rate-limit)))))))

(defn do-timeline
  [^twitter4j.Twitter twitter]
  (check-and-wait-rate-limit twitter "/statuses/home_timeline")
  (try
    (let [last-id (check-last-id)
          xstatus (if (pos? last-id)
                    (getHomeTimeline twitter last-id)
                    (getHomeTimeline twitter))]
      (when-not (empty? xstatus)
        (dorun (map println-message (reverse xstatus)))
        (save-last-id (.getId ^twitter4j.Status (first xstatus)))))
    (catch TwitterException ex
      (println-err ex)
      (when (= 429 (.getErrorCode ex))
        (println "### Rate limt has been exhausted, probably due to too much tweets to be retreived.")
        (println "### The last id has been cleared. There might be a gap in tweets you receive.")
        (clear-last-id)))
    (catch Exception ex
      (println-err ex)))
  (Thread/sleep (* 1000 60 (:interval @opts)))
  (recur twitter))

(defn do-query
  [^twitter4j.Twitter twitter search-str]
  (check-and-wait-rate-limit twitter "/search/tweets")
  (try
    (let [last-id (check-last-id)
          xstatus (if (pos? last-id)
                    (getQuery twitter search-str last-id)
                    (getQuery twitter search-str))]
      (when-not (empty? xstatus)
        (dorun (map println-message (reverse xstatus)))
        (save-last-id (.getId ^twitter4j.Status (first xstatus)))))
    (catch TwitterException ex
      (println-err ex)
      (when (= 429 (.getErrorCode ex))
        (println "### Rate limt has been exhausted, probably due to too much tweets to be retrived.")
        (println "### The last id has been cleared. There might be a gap in tweets you receive.")
        (clear-last-id)))
    (catch Exception ex
      (println-err ex)))
  (Thread/sleep (* 1000 60 (:interval @opts)))
  (recur twitter search-str))

(defn usage
  [options-summary]
  (->> ["USAGE: java -jar twitter-console.jar [options]"
        ""
        "Options:"
        options-summary]
    (str/join \newline)))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit
  [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (reset! opts options)
    (cond
      (:help @opts)             (exit 0 (usage summary))
      (empty? (:session @opts)) (exit 1 (str "ERROR: --session is required.\n\n"
                                             (usage summary)))
      errors                    (exit 1 (error-msg errors)))  
    (cond
      (:timeline @opts)   (do-timeline (twitter-instance))
      (:query @opts)      (do-query (twitter-instance) (:query @opts))
      (:rate-limit @opts) (show-all-rate-limit (twitter-instance))
      :else               (exit 1 (usage summary)))))
