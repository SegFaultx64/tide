(ns tide.core
  (:require [clojure.java.io :as io])
  (:require [clojure.java.jdbc :as sql])
  (:require [clj-yaml.core :as yaml]) 
  (:use [clj-jgit.porcelain])
  (:use korma.db korma.core)
  (:use [clojure.java.shell :as sh])
  (:use [clojure.java.io :only (as-file copy)])
  (:use [clojure.string :only (split)])
  (:gen-class :main true))

(import '(java.io BufferedReader InputStreamReader)) 

(defn cmd [cmd cwd] (.. Runtime getRuntime (exec 
	^"[Ljava.lang.String;" (into-array cmd)
	nil
	(as-file cwd)
	))) 

(defn cmdout [o] 
  (let [r (BufferedReader. 
             (InputStreamReader. 
               (.getInputStream o)))] 
    (dorun (map println (line-seq r))))) 

(defn cmderr [o] 
  (let [r (BufferedReader. 
             (InputStreamReader. 
               (.getErrorStream o)))] 
    (dorun (map println (line-seq r))))) 

(defn strip [coll chars]
  (apply str (remove #((set chars) %) coll)))

(defn tidify [name config]
	(cmdout (cmd ["git" "remote" "add" "tide" (str "git@" (:server config) ":" name)] (str "./" name "/www")))
	(cmderr (cmd ["git" "tide" "dokku" "master"] (str "./" name "/www"))))

(defn gitInitialize [name]
	(cmdout (cmd ["git" "init"] (str "./" name "/www")))
	(cmdout (cmd ["git" "add" "."] (str "./" name "/www")))
	(cmdout (cmd ["git" "commit" "-am" "Initial Commit - courtesy of Tide"] (str "./" name "/www"))))

(defn clone [name]
	(let [l4v (str (System/getProperty "user.home") "/.tidefiles/l4v")]
		(println "Fetching / updating basebox")
		(cmdout (cmd ["git" "clone" "https://github.com/bryannielsen/Laravel4-Vagrant.git" l4v "/.tidefiles/l4v"] (str "./")))
		(cmdout (cmd ["git" "pull" "origin" "master"] l4v))
		; (if (not (.exists (as-file l4v)))
		; 	((println "Getting basebox")
		; 		(cmdout (cmd ["git" "clone" "https://github.com/bryannielsen/Laravel4-Vagrant.git" l4v "/.tidefiles/l4v"] (str "./"))))
		; 	((println "Updating basebox")
		; 		(cmd ["git" "pull" "origin" "master"] l4v) 0))
		(println "Cloning base box")
		(cmdout (cmd ["git" "clone" "--no-hardlinks" l4v (str name)] (str "./")))))

(defn newProject [name config]
	(clone name)
	(println "Bringing up base box")
	(cmdout (cmd ["vagrant" "up"] (str "./" name)))
	(gitInitialize name)
	(tidify name config))

(defn getDb [user password host port]
	(def db
	  {:classname "org.postgresql.Driver"
	   :subprotocol "postgresql"
	   :subname (str "//" host ":" port "/postgres")
	   :user user
	   :password password
	   })
	db)

(defn getPgDb [user password host port]
	(defdb pgSql (postgres {
			:db "postgres"
			:user user
			:password password
			;; optional keys
			:host host
			:port port
			:delimiters ""})))

(defn create-database [name]
  (sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "create database " name))
      (seq (.executeBatch s)))))

(defn delete-database [name db]
  (sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "drop database " name))
      (seq (.executeBatch s)))))

(defn delete-user [name db]
  (sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "drop user " name))
      (seq (.executeBatch s)))))

(defn create-user [name password]
  (sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "create user " name " with password '" password "' "))
      (seq (.executeBatch s)))))

(defn grant-user [name database]
  (sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "grant all privileges on database " database " to " name))
      (seq (.executeBatch s)))))

(defn do-all [name uname password db]
	(sql/with-connection db
    (with-open [s (.createStatement (sql/connection))]
      (.addBatch s (str "create database " name))
      (.addBatch s (str "create user " uname " with password '" password "' "))
      (.addBatch s (str "grant all privileges on database " name " to " uname))
      (seq (.executeBatch s)))))

(defn ask [question] 
	(print (str question " "))
	(flush)
	(read-line))

(defn getDbInfo [db] 
	[(ask "Database Name?") (ask "Database User?") (ask "User Password?") db])

(defn setupDb [properties]
	(apply do-all properties))

(defn doDbSetup [db]
	(setupDb (getDbInfo db))
	(println "DB Setup!"))

(defn doDbDelete [db]
	(delete-database (ask "Database Name?") db)
	(println "DB Deleted!"))

(defn doUserDelete [db]
	(delete-user (ask "User Name?") db)
	(println "User Deleted!"))

(defn doDbList [db]
	(defentity databases
		(pk :id)
		(table :pg_database)
		(database db)
		(entity-fields :datname))
	(println "Databases:")
	(doseq [name (select databases
	 		(fields [:datname :name]))]
			(println (:name name))))

(defn doDone [options]
	(println "Bye!")
	(System/exit 0))

(defn clearScreen []
	(println "\033[2J")
	(println "\u001b[H"))

(defn banner []
	(println "Welcome to")
	(println "

  _______     __        ____              __
 /_  __(_)___/ /__     / __ \\____  ____  / /
  / / / / __  / _ \\   / /_/ / __ \\/ __ \\/ / 
 / / / / /_/ /  __/  / ____/ /_/ / /_/ / /  
/_/ /_/\\____/\\___/  /_/    \\____/\\____/_/   
		
		"))

(defn setupConfig [config]
	[(getDb (:user config) (:password config) (:server config) (:port config))
	(getPgDb (:user config) (:password config) (:server config) (:port config))
	])

(defn loadConfig [home]
	(if (.exists (as-file (str home "/.tide")))
		[(setupConfig
		(yaml/parse-string
			(slurp (str home "/.tide"))))
		(yaml/parse-string
			(slurp (str home "/.tide")))]
		((println "You need to define a ~/.tide")
			(System/exit 0))))

(defn project? []
	(println "create")
	(println "tidify")
	(println "gitinit"))

(defn db? []
	(println "list")
	(println "setup")
	(println "delete")
	(println "delete user"))

(defn doProject [options config]
	(case options
		["clone"] (clone (ask "Project Name?"))
		["create"] (newProject (ask "Project Name?") config)
		["tidify"] (tidify (ask "Project Name?") config)
		["gitinit"] (gitInitialize (ask "Project Name?"))
		(project?)))

(defn doDb [options db pgdb]
	(case options
		["list"] (doDbList pgdb)
		["setup"] (doDbSetup db)
		["delete"] (doDbDelete db)
		["delete" "user"] (doUserDelete db)
		(db?)))

(defn all? []
	(println "project")
	(println "\t\tcreate")
	(println "\t\ttidify")
	(println "\t\tgitinit")
	(println "")
	(println "db")
	(println "\t\tlist")
	(println "\t\tsetup")
	(println "\t\tdelete")
	(println "\t\tdelete user")
	(println "")
	(println "done"))

(defn coreLoop [dbs config]
	(let [db (nth dbs 0)
		pgdb (nth dbs 1)]
	(println "")
	(let [input (split (ask "What do you want to do?") #"\s+")]
	(case (first input)
		"project" (doProject (rest input) config)
		"db" (doDb (rest input) db pgdb)
		"done" (doDone (rest input))
		"exit" (doDone (rest input))
		(all?))
	(coreLoop dbs config))))

(defn -main []
	(clearScreen)
	(banner)
	(apply coreLoop (loadConfig (System/getProperty "user.home"))))
	
