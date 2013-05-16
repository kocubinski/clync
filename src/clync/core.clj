(ns clync.core
  (:use [clync.cprint :only [cprint]])
  (:require [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import [System.IO Directory FileStream FileMode IOException]
           [System.Security.Cryptography SHA1CryptoServiceProvider]))

(def root-path (atom nil))
(def ignore-list (atom []))

(defprotocol INode)

(defrecord File [hash full-path root-path]
  INode)

(defrecord Dir [full-path root-path children]
  INode)

(defn relative-path [^INode {:keys [full-path root-path]}]
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

(defn short-path [^INode {:keys [full-path]}]
  (last (str/split full-path #"\\")))

(defn get-keyword [node]
  (keyword (short-path node)))

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

(defn filter-ignores [inodes ignore-list]
  (filter 
   (fn [inode]
     (not (some (fn [ignore-path]
                  (= (relative-path inode) ignore-path))
                ignore-list)))
   inodes))

(defn process-dir [path]
  (let [root-path @root-path
        dirs (Directory/GetDirectories path) 
        files (Directory/GetFiles path)]
    (-> (concat
         (for [d dirs]
           (Dir. d root-path nil))
         (for [f files]
           (File. (sha1-hash f) f root-path)))
        (filter-ignores @ignore-list))))

(defn process-tree [path]
  (let [contents (process-dir path)
        tree (map #(let [{:keys [full-path root-path]} %
                         short-path (short-path %)]
                     (condp = (type %)
                       File [(keyword short-path) %]
                       Dir [(keyword short-path)
                            (Dir. full-path root-path
                                  (->> (process-tree full-path)
                                       (vec)
                                       (into {})))]))
                     contents)]
    (into {} tree)))

(defn build-tree [path]
  (reset! root-path path)
  (process-tree path))

(defn write-tree-state [tree-path & {:keys [config-path]}]
  (let [config-path (or config-path (str tree-path "\\.clync-tree.clj"))]
    (reset! ignore-list [".git"
                         "bin"
                         "src\\clojure.console\\obj"
                         "src\\clync\\obj"])
    (spit config-path (build-tree tree-path))))

(defn read-tree-state [config-path]
  (read-string (slurp config-path)))

(def ^:dynamic *compare-results*)

(defn print-results [compare-results]
  (pp/pprint (filter #(or (not (:in-other? %)) (not (:equal? %)))
                     compare-results)))

(defn compare-file [file dir-other]
  (let [file-key (get-keyword file)
        file-other (-> dir-other :children file-key)]
    {:full-path (:full-path file)
     :in-other? (not (nil? file-other))
     :equal? (= (:hash file) (:hash file-other))}))

(defn compare-dir [^Dir dir-base ^Dir dir-other]
  (doseq [[node-key node] (:children dir-base)]
    (condp = (type node)
      File (set!  *compare-results* (conj *compare-results* (compare-file node dir-other)))
      Dir (compare-dir node (-> dir-other :children node-key)))))

(defn compare-trees [path-base path-other]
  (reset! ignore-list [".git"
                       "bin"
                       "src\\clojure.console\\obj"
                       "src\\clync\\obj"])
  (binding [*compare-results* []]
    (let [tree-base (build-tree path-base)
          tree-other (build-tree path-other)]
      (doseq [[dir-key dir] (filter #(= (type (second %)) Dir) tree-base)]
        (compare-dir dir (dir-key tree-other))))
    (print-results *compare-results*)))

(defn test-tree []
  (reset! ignore-list [".git"
                       "bin"
                       "src\\clojure.console\\obj"
                       "src\\clync\\obj"])
  (pp/pprint 
   (build-tree "C:\\dev\\clync")))

(defn test-website []
  (reset! ignore-list [])
  (pp/pprint (build-tree "C:\\dev\\TotalPro\\Dev\\TotalPro_Designer")))
