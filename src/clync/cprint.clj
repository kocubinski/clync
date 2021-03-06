(ns clync.cprint
  (:use [clojure.clr.pinvoke :only [dllimports]]))

(dllimports
 "Kernel32.dll"
 (GetStdHandle IntPtr [Int32])
 (SetConsoleTextAttribute Boolean [IntPtr UInt32]))

(def colors
  {:blue 1
   :green 2
   :cyan 3
   :red 4
   :pink 5
   :yellow 6
   :light-gray 7
   :dark-gray 8
   :bright-blue 9
   :bright-green 10
   :bright-cyan 11
   :bright-red 12
   :bright-pink 13
   :bright-yellow 14
   :white 15})

(def ^:dynamic *cur-color*)

(defn set-color [color]
  (when-let [color-code (colors color)]
    ;;(set! *cur-color* color)
    (SetConsoleTextAttribute (GetStdHandle (int -11)) (uint color-code))))

(declare cprint*)

(defn cprint-color [sexp]
  (let [prev-color *cur-color*]
    (set-color (second sexp))
    (cprint* (drop 2 sexp))
    (set-color prev-color)))

(defn cprint* [sexp]
  (loop [elems sexp]
    (when-let [elem (first elems)]
      (println "processing" elem)
      (condp = (type elem)
        System.String (print elem "")
        clojure.lang.Symbol ~elem
        clojure.lang.PersistentList (cprint* elem)
        nil)
      (if (= (type elem) clojure.lang.Symbol)
        (condp = elem
          (symbol "color") (cprint-color sexp)
          (print (eval sexp) ""))
        (recur (rest elems))))))

(defn color [color & rest]
  (let [prev-color *cur-color*]
    (set-color color)
    (apply println rest)
    (set-color prev-color)))


;; (foo "bar" baz
;;  (color :blue qux "stuff")
;;  "final" )
;; ->
;; (print foo "bar" baz)
;; (set-color :blue)
;; (print qux "stuff")
;; (set-color :light-gray)
;; (print "final")

(defn parse-literal [sexp emit]
  (println sexp emit)
  (if-let [elem (first sexp)]
    (cond
     ;; string or symbol
     (or (= (type elem) System.String)
         (= (type elem) clojure.lang.Symbol))
     (if-not emit
       (recur (rest sexp) ['print elem])
       (recur (rest sexp) (conj emit (first sexp))))
     ;; persistent list
     (= (type elem) clojure.lang.PersistentList)
     (color ))))

(defmacro cprint [& body]
  (parse-literal body nil))

'((print foo "foo")
  (set-color :blue)
  (print "blue stuff")
  (set-color :light-gray))

