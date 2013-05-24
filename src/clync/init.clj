(ns clync.init
  (:use clync.core
        [clync.cli :only [cli]]
        [clync.remote :only [get-tree]]))

(defn clean-path [path]
  (.Replace path \\ \/))

(defn action [action {:keys [base-dir remote] :as args}]
  (let [base-dir (clean-path base-dir)]
    (condp = action
      :write (do
               (println "Writing tree state...")
               (write-tree-state base-dir))
      :diff (compare-trees (build-tree base-dir)
                           (get-tree args))
      (println "Invalid action:" action))))

(defn main [args]
  (let [cli-res
        (cli args
             ["-c" "--clj" :default false :flag true]
             ["-d" "--base-dir" :default System.Environment/CurrentDirectory]
             ["-o" "--other-dir"]
             ["-r" "--remote"])
        {:keys [base-dir remote] :as args} (first cli-res)
        action* (-> cli-res second first)]
    (action (keyword action*) args)))

(defn test-designer-staging []
  (action :diff {:base-dir "C:/dev/TotalPro/Dev/TotalPro_Designer"
                 :remote :totalpro-designer-staging}))