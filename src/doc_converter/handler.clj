(ns doc-converter.handler
  "Handles HTTP requests and routing for the document converter application"
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :as response]
            [doc-converter.converter :as converter]
            [doc-converter.db :as db]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json])
  (:import [java.util UUID]
           [java.util.zip ZipOutputStream ZipEntry]
           [java.io ByteArrayOutputStream]))

(defn home-page
  "Serves the main HTML page with the upload form"
  []
  (response/content-type
   (response/response
    (slurp (io/resource "public/index.html")))
   "text/html"))

(defn generate-filename
  "Generates a unique filename using UUID to avoid conflicts"
  [original-filename extension]
  (str (UUID/randomUUID) extension))

(defn create-zip
  "Creates a ZIP file containing multiple PDF files.

   Parameters:
   - pdf-files: Vector of maps with :path (file path) and :name (filename for ZIP entry)

   Returns:
   - Byte array containing the ZIP file"
  [pdf-files]
  (let [baos (ByteArrayOutputStream.)
        zos (ZipOutputStream. baos)]
    (try
      (doseq [{:keys [path name]} pdf-files]
        (let [entry (ZipEntry. name)
              pdf-file (io/file path)]
          (.putNextEntry zos entry)
          (with-open [fis (io/input-stream pdf-file)]
            (io/copy fis zos))
          (.closeEntry zos)))
      (.finish zos)
      (.toByteArray baos)
      (finally
        (.close zos)
        (.close baos)))))

(defn handle-upload
  "Handles the file upload and conversion process.

   Process:
   1. Validates the uploaded files
   2. Saves them temporarily
   3. Converts each to PDF
   4. Returns single PDF or ZIP of multiple PDFs to the user
   5. Cleans up temporary files"
  [request]
  ;; Debug: Print request structure to understand what's available
  (println "\n=== Debug: Request Structure ===")
  (println "Request keys:" (keys request))
  (println "Params:" (:params request))
  (println "Multipart-params:" (:multipart-params request))
  (println "================================\n")

  (let [;; The file parameter might be in :multipart-params or :params
        ;; depending on middleware order. Try both.
        params (or (:multipart-params request) (:params request))
        file-params (get params "file")
        ;; Normalize to a vector - could be a single map or a vector of maps
        file-params-vec (if (map? file-params) [file-params] file-params)]

    (println "File params found:" (count file-params-vec) "file(s)")

    (cond
      ;; No files uploaded
      (or (nil? file-params) (empty? file-params-vec))
      (do
        (println "ERROR: No file parameter found in request")
        (-> (response/response "No files uploaded. Please select at least one file.")
            (response/status 400)))

      ;; Check if any file has invalid type
      (some #(not (converter/valid-input-file? (:filename %))) file-params-vec)
      (-> (response/response "Invalid file type. Please upload .doc, .docx, or image files (.jpg, .jpeg, .png, .gif, .bmp) only")
          (response/status 400))

      ;; Valid files - process them
      :else
      (let [uploads-dir (io/file "uploads")
            ;; Create persistent storage for PDFs
            storage-dir (io/file "pdf-storage")
            _ (when-not (.exists storage-dir) (.mkdirs storage-dir))
            ;; Track all paths for cleanup
            input-paths (atom [])
            pdf-paths (atom [])
            ;; Track PDF info (path and original filename) for ZIP creation
            pdf-info (atom [])
            ;; Track stored PDFs for database
            stored-pdfs (atom [])]

        (try
          ;; Process each file: save and convert to PDF
          (doseq [file-param file-params-vec]
            (let [temp-file (:tempfile file-param)
                  original-filename (:filename file-param)
                  ;; Preserve the original file extension
                  file-extension (re-find #"\.[^.]+$" original-filename)
                  input-filename (generate-filename original-filename (or file-extension ".doc"))
                  output-filename (generate-filename original-filename ".pdf")
                  input-path (.getAbsolutePath (io/file uploads-dir input-filename))
                  output-path (.getAbsolutePath (io/file uploads-dir output-filename))
                  ;; Generate output filename based on original name (case-insensitive)
                  pdf-name (clojure.string/replace original-filename
                                                   #"(?i)\.(doc|docx|jpg|jpeg|png|gif|bmp)$"
                                                   ".pdf")]

              (println (format "Processing file: '%s' -> '%s'" original-filename pdf-name))
              (println (format "Extension match result: %s" (re-find #"(?i)\.(doc|docx|jpg|jpeg|png|gif|bmp)$" original-filename)))
              (println (format "Temp file: %s, exists: %s, size: %d" temp-file (.exists temp-file) (.length temp-file)))

              ;; Check temp file header
              (let [header (with-open [in (io/input-stream temp-file)]
                             (let [buf (byte-array 8)]
                               (.read in buf)
                               buf))]
                (println (format "Temp file header: %s"
                                 (clojure.string/join " " (map #(format "%02X" %) header)))))

              (println (format "Input path: %s" input-path))
              (println (format "Output path: %s" output-path))

              ;; Save uploaded file
              (with-open [in (io/input-stream temp-file)
                          out (io/output-stream (io/file input-path))]
                (io/copy in out))
              (println (format "Saved uploaded file to: %s" input-path))
              (swap! input-paths conj input-path)

              ;; Convert to PDF
              (if (converter/convert-to-pdf input-path output-path)
                (do
                  (swap! pdf-paths conj output-path)
                  (swap! pdf-info conj {:path output-path :name pdf-name})

                  ;; Save PDF to persistent storage
                  (let [stored-filename (str (java.util.UUID/randomUUID) ".pdf")
                        stored-path (.getAbsolutePath (io/file storage-dir stored-filename))]
                    (io/copy (io/file output-path) (io/file stored-path))
                    (swap! stored-pdfs conj {:filename original-filename
                                              :path stored-path
                                              :extension (re-find #"\.[^.]+$" original-filename)
                                              :size (.length temp-file)})))
                (throw (Exception. (format "Failed to convert %s" original-filename))))))

          ;; Record all conversions in database
          (doseq [{:keys [filename path extension size]} @stored-pdfs]
            (db/add-conversion! filename extension size path))

          ;; Return response based on number of files
          (if (= 1 (count @pdf-info))
            ;; Single file - return PDF directly
            (let [{:keys [path name]} (first @pdf-info)
                  pdf-file (io/file path)
                  _ (println (format "PDF file exists: %s, size: %d" (.exists pdf-file) (.length pdf-file)))
                  pdf-bytes (with-open [in (io/input-stream pdf-file)
                                        out (ByteArrayOutputStream.)]
                              (io/copy in out)
                              (.toByteArray out))]
              (println (format "Sending single PDF: %s (%d bytes)" name (count pdf-bytes)))
              (println (format "Content-Type: application/pdf, Filename: %s" name))
              (-> (response/response (java.io.ByteArrayInputStream. pdf-bytes))
                  (response/content-type "application/pdf")
                  (response/header "Content-Disposition"
                                   (format "attachment; filename=\"%s\"" name))))

            ;; Multiple files - return as ZIP
            (let [zip-bytes (create-zip @pdf-info)]
              (println (format "Sending ZIP with %d PDFs (%d bytes)"
                               (count @pdf-info) (count zip-bytes)))
              (-> (response/response (java.io.ByteArrayInputStream. zip-bytes))
                  (response/content-type "application/zip")
                  (response/header "Content-Disposition"
                                   "attachment; filename=\"converted-documents.zip\""))))

          (catch Exception e
            (println "Error processing upload:" (.getMessage e))
            (.printStackTrace e)
            (-> (response/response (str "Error processing files: " (.getMessage e)))
                (response/status 500)))

          (finally
            ;; Clean up all temporary files
            (doseq [path (concat @input-paths @pdf-paths)]
              (try
                (io/delete-file path true)
                (catch Exception e
                  (println "Error deleting file" path ":" (.getMessage e)))))))))))

(defn get-history
  "Returns the last 5 conversions as JSON"
  []
  (let [conversions (db/get-recent-conversions)]
    (-> (response/response (json/generate-string conversions))
        (response/content-type "application/json"))))

(defn download-pdf
  "Downloads a stored PDF by conversion ID"
  [id]
  (if-let [conversion (db/get-conversion-by-id (Integer/parseInt id))]
    (let [pdf-path (:conversions/pdf_path conversion)
          filename (:conversions/filename conversion)]
      (if (and pdf-path (.exists (io/file pdf-path)))
        (let [pdf-file (io/file pdf-path)
              ;; Generate PDF filename from original filename
              pdf-name (str/replace filename #"(?i)\.(doc|docx|jpg|jpeg|png|gif|bmp)$" ".pdf")]
          (println (format "Serving stored PDF: %s (ID: %s)" pdf-name id))
          (-> (response/response pdf-file)
              (response/content-type "application/pdf")
              (response/header "Content-Disposition"
                               (format "attachment; filename=\"%s\"" pdf-name))))
        (-> (response/response "PDF file not found")
            (response/status 404))))
    (-> (response/response "Conversion not found")
        (response/status 404))))

(defroutes app-routes
  "Defines the URL routes for the application"
  (GET "/" [] (home-page))
  (GET "/history" [] (get-history))
  (GET "/download/:id" [id] (download-pdf id))
  (POST "/upload" request (handle-upload request))
  (route/resources "/")  ; Serve static resources
  (route/not-found "Not Found"))

(def app
  "The main application handler with middleware.

   IMPORTANT: Middleware order matters! They are applied from bottom to top.
   - wrap-multipart-params MUST come first (bottom) to process file uploads
   - wrap-defaults adds security headers, session handling, etc.

   This ensures that file uploads are parsed before other middleware processes the request."
  (-> app-routes
      wrap-multipart-params      ; Applied FIRST - parses file uploads
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))
