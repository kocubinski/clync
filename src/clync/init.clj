(assembly-load "ClojureClrEx")

(ns clync.init
  (:require [clync.core :as core])
  (:use [clync.cli :only [cli]]))

(defn main [args]
  (let [cli-res
        (cli args
             ["-c" "--clj" :default false :flag true]
             ["-d" "--base-dir" :default System.Environment/CurrentDirectory])
        {:keys [base-dir]} (first cli-res)
        action (-> cli-res second first)]
    (println cli-res)
    (condp = action
      "write" (do
                (println "Writing tree state...")
                (core/write-tree-state base-dir))
      (println "Invalid action:" action))))