(ns brainiac.macros)

(defmacro <? [ch]
  `(brainiac.utils/throw-err (cljs.core.async/<! ~ch)))
