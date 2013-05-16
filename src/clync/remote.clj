(ns clync.remote
  (:import [System.Net WebClient NetworkCredential WebRequest WebRequestMethods+Ftp]
           [System.Security SecureString]))


(defn read-remote
  [remote-key & {:keys [remotes-config]
             :or {remotes-config
                  (str System.Environment/CurrentDirectory "\\.clync-remotes.clj")}}]
  (remote-key (read-string (slurp remotes-config :encoding "UTF-8"))))

(defn get-ftp-tree-file [{:keys [uri username password domain]}]
  (let [;;uri (Uri. (str uri "/.clync-tree.clj"))
        request (doto (WebRequest/Create uri )
                  (.set_Method WebRequestMethods+Ftp/DownloadFile)
                  (.set_Credentials
                   (NetworkCredential. username password domain)))
        file-data (.GetResponse request)]
    file-data))