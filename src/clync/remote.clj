(ns clync.remote
  (:import [System.Net WebClient NetworkCredential WebRequest WebRequestMethods+Ftp]
           [System.Security SecureString]
           [System.IO StreamReader]))

(defn read-remote
  [remote-key & {:keys [remotes-config]
             :or {remotes-config
                  (str System.Environment/CurrentDirectory "\\.clync-config.clj")}}]
  (-> (read-string (slurp remotes-config :encoding "UTF-8")) :remotes remote-key))

(defmulti read-tree-file :protocol)

(defmethod read-tree-file :unc [{:keys [uri]}]
  (let [tree-file (str uri "\\.clync-tree.clj")
        tree (slurp tree-file :enc "UTF-8")]
    (read-string tree)))

(defmethod read-tree-file :ftp  [{:keys [uri username password domain]}]
  (let [uri (Uri. (str uri "/.clync-tree.clj"))
        request (doto (WebRequest/Create uri )
                  ;(.set_Proxy nil)
                  (.set_Method WebRequestMethods+Ftp/DownloadFile)
                  (.set_Method WebRequestMethods+Ftp/ListDirectory)
                  (.set_Credentials
                   (NetworkCredential. username password domain)))]
    (with-open [response (-> request .GetResponse .GetResponseStream)]
      (with-open [reader (StreamReader. response)]
        (println (.ReadToEnd reader))))))

(defn get-tree [{:keys [remote other-dir]}]
  (if remote
    (read-tree-file (read-remote remote))
    ;; create a faux remote entry if using other-dir
    (read-tree-file {:protocol :unc
                     :uri other-dir})))
