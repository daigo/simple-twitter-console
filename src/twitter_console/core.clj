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
  (:import (twitter4j TwitterFactory TwitterException Paging))
  (:import (twitter4j.conf ConfigurationBuilder))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class :main true))

;; ==================================================
;; Global variables
;; ==================================================

(def cli-options
  [["-s" "--session SESSION"   "Session name to identify this process"]
   ["-t" "--timeline"          "Timeline mode"]
   ["-i" "--interval INTERVAL" "Interval minutes to reload"
    :default 20
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

;; Holds commandline options
(def opts (atom {}))

(def PAGING_COUNT 200)

;; ==================================================
;; Functions
;; ==================================================

(defn println-err
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
  []
  (io/file "/var/tmp" (str "simple_twitter_client_" (:session @opts))))

(defn check-last-id
  []
  (let [file (file-last-id)]
    (if (.exists file)
      (Long/parseLong (slurp file))
      0)))

(defn save-last-id
  [last-id]
  (spit (file-last-id) last-id))

(defn next-paging
  [paging]
  (doto paging
    (.setPage  (inc (.getPage paging)))))

(defn getHomeTimeline
  [twitter paging]
  (lazy-cat (.getHomeTimeline twitter paging) 
   (getHomeTimeline twitter
                    (next-paging paging))))

(defn getHomeTimeline
  ([twitter]
   (let [paging (Paging. 1 PAGING_COUNT)]
     (.getHomeTimeline twitter paging)))
  ([twitter since-id]
   (let [paging (Paging. 1 PAGING_COUNT since-id)
         col    (.getHomeTimeline twitter paging)]
     (if (empty? col)
       col
       (getHomeTimeline twitter
                        col
                        since-id
                        (dec (.getId (last col)))
                        (.getId (first col))))))
  ([twitter xs since-id max-id last-id]
   (if (<= max-id since-id)
     xs
     (let [paging (Paging. 1 PAGING_COUNT since-id max-id)
           col    (.getHomeTimeline twitter paging)]
       (if (empty? col)
         xs
         (recur twitter
                 (concat xs col)
                 since-id
                 (dec (.getId (last col)))
                 last-id))))))

(defn getRateLimit
  [twitter resource]
  (let [status (get (.getRateLimitStatus twitter) "/statuses/home_timeline")]
    {:limit (.getLimit status)
     :remaining (.getRemaining status)
     :resetTimeInSeconds (.getResetTimeInSeconds status)
     :secondsUntilReset  (.getSecondsUntilReset status)}))

(defn str-name
  [status]
  (let [user (.getUser status)]
    (format "%s @%s"
            (.getName user)
            (.getScreenName user))))

(defn println-message
  [status]
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

(defn domain
  [twitter]
  (let [rate-limit (getRateLimit twitter "/statuses/home_timeline")]
    (when-not (pos? (:remaining rate-limit))
      (println-err (format "Rate limit has run out. Sleep in %s seconds..."
                          (:secondsUntilReset rate-limit)))
      (Thread/sleep (* 1000 (+ 10 (:secondsUntilReset rate-limit))))))
  (try
    (let [last-id (check-last-id)
          xstatus (if (pos? last-id)
                    (getHomeTimeline twitter last-id)
                    (getHomeTimeline twitter))]
      (when-not (empty? xstatus)
        (dorun (map println-message (reverse xstatus)))
        (save-last-id (-> (first xstatus) .getId))))
    (catch Exception ex
      (println-err ex)))
  (Thread/sleep (* 1000 60 (:interval @opts)))
  (recur twitter))

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
    (domain (twitter-instance))))
