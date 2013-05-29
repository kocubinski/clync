(ns clync.init
  (:use clync.core
        [clync.cli :only [cli]]
        [clync.remote :only [get-tree]]))

(defn clean-path [path]
  (.Replace path \\ \/))

(defn action [action {:keys [base-dir] :as args}]
  (condp = action
    :write (do
             (println "Writing tree state...")
             (write-tree-state base-dir args))
    :diff (compare-trees (build-tree base-dir args)
                         (get-tree args))
    (println "Invalid action:" action)))

(defn main [args]
  (let [cli-res
        (cli args
             ["-c" "--clj" :default false :flag true]
             ["-C" "--config"]
             ["-d" "--base-dir" :default System.Environment/CurrentDirectory]
             ["-o" "--other-dir"]
             ["-r" "--remote"]
             ["-i" "--ignore" :default "[]"])
        {:keys [base-dir ignore] :as args} (first cli-res)
        action* (-> cli-res second first)
        ;;args (assoc args :ignore (read-string ignore))
        args (assoc args :base-dir (clean-path base-dir))]
    (println args)
    (action (keyword action*) args)))

(defn test-designer-staging []
  (action :diff {:base-dir "C:/dev/TotalPro/Dev/TotalPro_Designer"
                 :remote :totalpro-designer-staging}))