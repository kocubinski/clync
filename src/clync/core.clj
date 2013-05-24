(ns clync.core
  (:use [clync.cprint :only [cprint]])
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]
            [clync.remote :as remote]
            [clojure.tools.logging :as log])
  (:import
   [System.IO Directory FileStream FileMode IOException DirectoryInfo]
   [System.Security.Cryptography SHA1CryptoServiceProvider]))

;;;;;;;;;;;;;;;;;;;;
;; structures

(def ^:dynamic *ignore-list*)
(def ^:dynamic *root-path*)
(def ^:dynamic *root-path-other*)

(defprotocol INode)

(defrecord File [hash full-path]
  INode)

(defrecord Dir [full-path children]
  INode)

;;;;;;;;;;;;;;;;;;;;
;; tree building

(defn relative-path [^INode {:keys [full-path]}]
  (let [matcher (re-matcher
                 (re-pattern (str "(" *root-path* ")(.*)$" ))
                 full-path)
        rel-path (last (re-find matcher))
        ;; trim leading / 
        rel-path (if (= \/ (first rel-path))
                    (apply str (rest rel-path))
                    rel-path)
        ;; trim trailing/ 
        rel-path (if (= \/ (last rel-path))
                   (apply str (butlast rel-path))
                   rel-path)]
    rel-path))

(defn short-path [^INode {:keys [full-path]}]
  (last (str/split full-path #"/")))

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
  (let [dirs (map #(.Replace % \\ \/) (Directory/GetDirectories path)) 
        files (map #(.Replace % \\ \/) (Directory/GetFiles path))]
    (-> (concat
         (for [d dirs]
           (Dir. d nil))
         (for [f files]
           (File. (sha1-hash f) f)))
        (filter-ignores *ignore-list*))))

(defn process-tree [root-path]
  (let [contents (process-dir root-path)
        tree (map #(let [{:keys [full-path]} %
                         short-path (short-path %)]
                     (condp = (type %)
                       File [short-path %]
                       Dir [short-path
                            (Dir. full-path
                                  (->> (process-tree full-path)
                                       (vec)
                                       (into {})))]))
                     contents)]
    (into {} tree)))

(defn build-tree [path]
  (binding [*root-path* path
            *ignore-list* ["_ReSharper.clync"
                           ".git"
                           "bin"
                           "src/clojure.console/obj"
                           "src/clync/obj"]] 
    {:meta {:root path}
     :tree (process-tree path)}))

(defn write-tree-state [tree-path & {:keys [config-path]}]
  (let [config-path (or config-path (str tree-path "\\.clync-tree.clj"))]
    (spit config-path (pr-str (build-tree tree-path)))))

(defn read-tree-state [config-path]
  (read-string (slurp config-path)))

;;;;;;;;;;;;;;;;;;;;
;; comparing trees

(def ^:dynamic *compare-results*)

(defn print-results [compare-results]
  (comment (pp/pprint (filter #(or (not (:in-other? %)) (not (:equal? %)))
                              compare-results)))
  (let [not-equal (filter #(not (:equal %)) compare-results)
        not-in-other (->> compare-results
                          (filter #(not (:in-other? %)))
                          (map :full-path)
                          (apply str))]
    (cprint (color :red not-in-other))))

(defn compare-file [file dir-other]
  (log/info "compare-file" file "against dir" dir-other)
  (if dir-other
    (let [short-path (short-path file)
          file-other ((:children dir-other) short-path)]
      {:full-path (:full-path file)
       :in-base? true
       :in-other? (not (nil? file-other))
       :equal? (= (:hash file) (:hash file-other))})
    {:full-path (:full-path file)
     :in-base? true
     :in-other? false
     :equal? false}))

(defn compare-dir [^Dir dir-base ^Dir dir-other]
  ;;(log/info "Comparing" (:full-path dir-base) "to" (:full-path dir-other))
  (doseq [[node-key node] (:children dir-base)]
    (condp = (type node)
      File (set!  *compare-results* (conj *compare-results* (compare-file node dir-other)))
      Dir (compare-dir node ((:children dir-other) node-key)))))

(defn compare-trees [tree-base tree-other]
  (log/info "compare-trees")
  (log/debug "tree-base" tree-base)
  (log/debug "tree-other" tree-other)
  (binding [*compare-results* []]
    (doseq [[dir-key dir] (filter #(= (type (second %)) Dir)
                                  (:tree tree-base))]
      (compare-dir dir ((:tree tree-other) dir-key)))
    (print-results *compare-results*)))

(defn compare-paths [path-base path-other]
  (let [tree-base (build-tree path-base)
        tree-other (build-tree path-other)]
    (compare-trees tree-base tree-other)))

;;;;;;;;;;;;;;;;;;;;
;; testing

(defn test-tree []
  (pp/pprint
   (build-tree "C:\\mski\\dev\\clync")))

(defn test-website []
  (pp/pprint (build-tree "C:\\dev\\TotalPro\\Dev\\TotalPro_Designer")))

(defn test-compare []
  (compare-paths "c:/dev/clync" "c:/dev/clync2"))