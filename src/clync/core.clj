(ns clync.core
  (:use [clync.cprint :only [cprint]])
  (:require [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import [System.IO Directory FileStream FileMode IOException]
           [System.Security.Cryptography SHA1CryptoServiceProvider]))

(def root-path (atom nil))
(def ignore-list (atom []))

(defrecord File [hash full-path relative-path short-path])
(defrecord Dir [full-path relative-path short-path files])

(defn Dir* [{:keys [full-path relative-path short-path]} files]
  (Dir. full-path relative-path short-path files))

(defn get-relative-path [root-path full-path]
  (let [matcher (re-matcher
                 (re-pattern (str "(" (str/replace root-path "\\" "\\\\") ")(.*)$" ))
                 full-path)
        rel-path (last (re-find matcher))
        ;; trim leading \\
        rel-path (if (= \\ (first rel-path))
                    (apply str (rest rel-path))
                    rel-path)
        ;; trim trailing \\
        rel-path (if (= \\ (last rel-path))
                   (apply str (butlast rel-path))
                   rel-path)]
    rel-path))

(defn get-short-path [full-path]
  (last (str/split full-path #"\\")))

(defn hash->string [hash]
  (let [str-hash (StringBuilder. (* 2 (. hash Length)))]
    (doseq [b hash]
      (.AppendFormat str-hash "{0:X2}" b))
    (. str-hash ToString)))

(defn sha1-hash [path]
  (try 
    (with-open [fs (FileStream. path FileMode/Open)]
      (-> (.ComputeHash (SHA1CryptoServiceProvider.) fs)
          (hash->string)))
    (catch IOException e
      (println "WARN:" (. e Message)))))

(defn filter-ignores [paths ignore-list]
  (filter 
   (fn [{:keys [relative-path full-path short-path]}]
     (not (some (fn [ignore-path]
                  (= relative-path ignore-path))
                ignore-list)))
   paths))

(defn process-dir [path]
  (let [root-path @root-path
        dirs (Directory/GetDirectories path) 
        files (Directory/GetFiles path)]
    (-> (concat
         (for [d dirs]
           {:full-path d :relative-path (get-relative-path root-path d)
            :short-path (get-short-path d)})
         (for [f files]
           (File. (sha1-hash f) f (get-relative-path root-path f)
                  (get-short-path f))))
        (filter-ignores @ignore-list))))

(defn process-tree [path]
  (let [contents (process-dir path)
        tree (map #(condp = (type %)
                     File [(keyword (:short-path %)) %]
                     [(keyword (:short-path %))
                      (Dir* % (->> (process-tree (:full-path %))
                                   (vec)
                                   (into {})))])
                     contents)]
    (into {} tree)))

(defn build-tree [path]
  (reset! root-path path)
  (process-tree path))

(defn test-tree []
  (reset! ignore-list [".git"
                       "bin"
                       "src\\clojure.console\\obj"
                       "src\\clync\\obj"])
  (build-tree "C:\\dev\\clync"))

(defn test-website []
  (reset! ignore-list [])
  (pp/pprint (build-tree "C:\\dev\\TotalPro\\Dev\\TotalPro_Designer")))


;; TODO change records to have only full-path, use Protocols for
;; methods to retreieve relative-path and short-path from full-path
;; when needed.  this will reduce serializing/deserializng time.

;; 
