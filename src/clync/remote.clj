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
  (time
   (let [tree-file (str uri "\\.clync-tree.clj")
         tree (slurp tree-file :enc "UTF-8")])))

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

(defn get-tree [remote-key]
  (read-tree-file (read-remote remote-key)))

