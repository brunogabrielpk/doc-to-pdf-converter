(ns doc-converter.converter
  "Handles conversion of .doc, .docx, and image files to PDF format"
  (:require [clojure.string :as str])
  (:import [org.jodconverter.core.office OfficeManager]
           [org.jodconverter.local LocalConverter]
           [org.jodconverter.local.office LocalOfficeManager]
           [java.io File]
           [org.apache.pdfbox.pdmodel PDDocument PDPage]
           [org.apache.pdfbox.pdmodel.common PDRectangle]
           [org.apache.pdfbox.pdmodel PDPageContentStream]
           [org.apache.pdfbox.pdmodel.graphics.image PDImageXObject]
           [javax.imageio ImageIO]))

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

(defn image-file?
  "Checks if the file is an image based on extension"
  [filename]
  (let [filename-lower (str/lower-case filename)]
    (or (.endsWith filename-lower ".jpg")
        (.endsWith filename-lower ".jpeg")
        (.endsWith filename-lower ".png")
        (.endsWith filename-lower ".gif")
        (.endsWith filename-lower ".bmp"))))

(defn document-file?
  "Checks if the file is a document (.doc or .docx)"
  [filename]
  (let [filename-lower (str/lower-case filename)]
    (or (.endsWith filename-lower ".doc")
        (.endsWith filename-lower ".docx"))))

(defn valid-input-file?
  "Checks if the uploaded file has a valid extension (.doc, .docx, or image formats)"
  [filename]
  (or (document-file? filename)
      (image-file? filename)))

(defn convert-image-to-pdf
  "Converts an image file to PDF format using PDFBox.

   Parameters:
   - input-path: String path to the input image file
   - output-path: String path where the PDF should be saved

   Returns:
   - true if conversion successful, false otherwise"
  [input-path output-path]
  (try
    (let [image-file (File. input-path)]
      (println (format "Reading image file: %s, exists: %s, size: %d, canRead: %s"
                       input-path (.exists image-file) (.length image-file) (.canRead image-file)))

      ;; Log available image readers for debugging
      (println "Available ImageIO readers:" (seq (javax.imageio.ImageIO/getReaderFormatNames)))

      ;; Read first few bytes to verify file integrity
      (let [header-bytes (with-open [fis (java.io.FileInputStream. image-file)]
                           (let [buf (byte-array 8)]
                             (.read fis buf)
                             buf))]
        (println (format "File header (hex): %s"
                         (clojure.string/join " " (map #(format "%02X" %) header-bytes)))))

      ;; Try to read the image
      (let [image (ImageIO/read image-file)]
        (println (format "ImageIO/read result: %s" (if image "SUCCESS" "NULL")))
        (when-not image
          (throw (Exception. (format "Failed to read image file: %s (exists: %s, size: %d, canRead: %s)"
                                     input-path (.exists image-file) (.length image-file) (.canRead image-file)))))

        (with-open [document (PDDocument.)]
          (let [img-width (.getWidth image)
                img-height (.getHeight image)
                ;; Create a page with the same aspect ratio as the image
                page-rect (PDRectangle. (float img-width) (float img-height))
                page (PDPage. page-rect)]

            (.addPage document page)
            (println (format "Created PDF page: %dx%d" img-width img-height))

            ;; Create image object from file
            (let [pdImage (PDImageXObject/createFromFile input-path document)]
              (println (format "Created PDImage: %dx%d" (.getWidth pdImage) (.getHeight pdImage)))

              ;; Draw the image on the page
              (with-open [content-stream (PDPageContentStream. document page)]
                ;; drawImage takes 3 args: image, x, y
                ;; The image will be drawn at original size
                (.drawImage content-stream pdImage (float 0) (float 0))
                (println "Drew image on PDF page"))

              ;; Save the PDF
              (.save document output-path)
              (println (format "Saved PDF to: %s" output-path)))))

        ;; Verify the PDF was created
        (let [pdf-file (File. output-path)]
          (if (.exists pdf-file)
            (println (format "Image conversion completed successfully. PDF size: %d bytes" (.length pdf-file)))
            (throw (Exception. "PDF file was not created"))))
        true))

    (catch Exception e
      (println "Error during image conversion:" (.getMessage e))
      (.printStackTrace e)
      false)))

(defn convert-to-pdf
  "Converts a document or image file to PDF format.

   Parameters:
   - input-path: String path to the input file (.doc, .docx, or image)
   - output-path: String path where the PDF should be saved

   Returns:
   - true if conversion successful, false otherwise"
  [input-path output-path]
  (let [input-file (File. input-path)
        filename (.getName input-file)]

    (println (format "convert-to-pdf called: filename=%s, is-image=%s, is-doc=%s"
                     filename (image-file? filename) (document-file? filename)))

    (cond
      ;; Handle image files
      (image-file? filename)
      (do
        (println "Routing to image converter")
        (convert-image-to-pdf input-path output-path))

      ;; Handle document files
      (document-file? filename)
      (try
        (when-not @office-manager
          (start-office-manager!))

        (let [output-file (File. output-path)]
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
          false))

      ;; Unknown file type
      :else
      (do
        (println "Unsupported file type:" filename)
        false))))
