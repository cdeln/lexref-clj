(defproject lexref "0.2.0"
  :description "Lexical references in Clojure"
  :url "https://github.com/cdeln/lexref-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :profiles {:dev {:dependencies [[clj-python/libpython-clj "2.024"]]
                   :jvm-opts ["--add-modules" "jdk.incubator.foreign,jdk.incubator.vector"
                              "--enable-native-access=ALL-UNNAMED"
                              "-Djdk.attach.allowAttachSelf"]
                   :main ^:skip-aot lexref.dev}}
  :repl-options {:init-ns lexref.dev}
  :uberjar-exclusions [#"lexref.dev"])
