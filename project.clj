(defproject tide "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [korma "0.3.0-RC5"]
				 [org.postgresql/postgresql "9.2-1002-jdbc4"]
				 [clj-yaml "0.4.0"]
				 [log4j "1.2.15" :exclusions [javax.mail/mail
                            javax.jms/jms
                            com.sun.jdmk/jmxtools
                            com.sun.jmx/jmxri]]
                 [clj-jgit "0.6.1"]
                 [clj-ssh "0.5.6"]]
  :main tide.core)
