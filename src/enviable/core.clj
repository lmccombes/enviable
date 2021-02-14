(ns enviable.core)

(defn var [var-name]
  {::name   var-name
   ::parser identity})

(defn env-var? [x]
  (::name x))

(defn parse-with [var parser]
  (assoc var ::parser parser))

(defn wrap-parser [parser]
  (fn [var]
    (parse-with var parser)))

(defn default-to [var default]
  (assoc var ::default {::default-value default}))

(defn describe [var description]
  (assoc var ::description description))

;; Reading / Parsing

;; TODO rename to single-error

(defn read-result
  ([var input-val]
   [(::name var) input-val])
  ([var input-val parsed-val]
   [(::name var) parsed-val]))

(defn error [read-result]
  {::error [read-result]})

;; TODO rename to single-succes (or something similar)
(defn ok [read-result read-val]
  {::ok [read-result]
   ::value read-val})

;; TODO create function for multiple errors/oks

(defn error? [result]
  (boolean (::error result)))

(def success? (complement error?))

(defn- lookup-var [env {::keys [name]}]
  (get env name))

(defn- parse-var [s {::keys [parser] :as var}]
  (try
    (let [parsed (parser s)]
      (if (nil? parsed)
        (error (read-result var s))
        (ok (read-result var s parsed) parsed)))
    (catch Exception e
      (error (read-result var s)))))

(defn- read-var [env var]
  (if-let [val (lookup-var env var)]
    (parse-var val var)
    (if-let [default (::default var)]
      (let [v (::default-value default)]
        (ok (read-result var nil v) v))
      (error (read-result var nil)))))

(declare -read-env)

(defn- add-to-result [acc-result [k result]]
  (if (and (success? acc-result) (success? result))
    (-> acc-result
        (assoc-in [::value k] (::value result))
        (update ::ok concat (::ok result)))
    {::error (concat (::error acc-result) (::error result))
     ::ok    (concat (::ok acc-result) (::ok result))}))

(defn- read-env-map
  [env var-map]
  (->> var-map
       (map (fn [[k v]]
              [k (-read-env env v)]))
       (reduce add-to-result {})))

(defn -read-env [env x]
  (cond (env-var? x)
        (read-var env x)
        (map? x)
        (read-env-map env x)
        :else
        {::value x}))

(defn read-env
  ([var-map]
    (read-env (System/getenv) var-map))
  ([env var-map]
   (let [res (-read-env env var-map)]
     (if (error? res)
       res
       (::value res)))))




;; Useful Parsers

(defn parse-int [s]
  (Integer/parseInt s))

(def int-var (comp (wrap-parser parse-int) var))