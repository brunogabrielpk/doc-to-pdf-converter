(ns doc-converter.converter
  "Handles conversion of .doc and .docx files to PDF format"
  (:require clojure.string)
  (:import [org.jodconverter.core.office OfficeManager]
           [org.jodconverter.local LocalConverter]
           [org.jodconverter.local.office LocalOfficeManager]
           [java.io File]))

;; Global office manager instance
;; This manages the LibreOffice process for document conversion
(defonce office-manager (atom nil))

(defn start-office-manager!
  "Starts the LibreOffice office manager for document conversion.
   This creates a background LibreOffice process that handles conversions."
  []
  (when-not @office-manager
    (let [manager (-> (LocalOfficeManager/builder)
                      (.portNumbers (int-array [2002]))
                      (.build))]
      (.start manager)
      (reset! office-manager manager)
      (println "LibreOffice office manager started"))))

(defn stop-office-manager!
  "Stops the LibreOffice office manager and cleans up resources."
  []
  (when @office-manager
    (.stop @office-manager)
    (reset! office-manager nil)
    (println "LibreOffice office manager stopped")))

(defn convert-to-pdf
  "Converts a document file to PDF format.

   Parameters:
   - input-path: String path to the input file (.doc or .docx)
   - output-path: String path where the PDF should be saved

   Returns:
   - true if conversion successful, false otherwise"
  [input-path output-path]
  (try
    (when-not @office-manager
      (start-office-manager!))

    (let [input-file (File. input-path)
          output-file (File. output-path)]

      (println (format "Converting %s to %s" input-path output-path))

      ;; Perform the actual conversion
      (-> (LocalConverter/make @office-manager)
          (.convert input-file)
          (.to output-file)
          (.execute))

      (println "Conversion completed successfully")
      true)

    (catch Exception e
      (println "Error during conversion:" (.getMessage e))
      (.printStackTrace e)
      false)))

(defn valid-input-file?
  "Checks if the uploaded file has a valid extension (.doc or .docx)"
  [filename]
  (let [filename-lower (clojure.string/lower-case filename)]
    (or (.endsWith filename-lower ".doc")
        (.endsWith filename-lower ".docx"))))
