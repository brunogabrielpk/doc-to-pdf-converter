(ns doc-converter.db
  "Database operations for storing conversion history"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; Database connection
(defonce db-spec (atom nil))

(defn init-db!
  "Initializes the SQLite database and creates the conversions table if it doesn't exist"
  []
  (let [db {:dbtype "sqlite" :dbname "conversions.db"}]
    (reset! db-spec db)

    ;; Create conversions table
    (with-open [conn (jdbc/get-connection db)]
      (jdbc/execute! conn
                     ["CREATE TABLE IF NOT EXISTS conversions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        filename TEXT NOT NULL,
                        original_extension TEXT NOT NULL,
                        file_size INTEGER,
                        pdf_path TEXT,
                        converted_at TEXT NOT NULL
                      )"]))

    (println "Database initialized successfully")))

(defn format-timestamp
  "Formats a LocalDateTime to ISO-8601 string"
  [date-time]
  (.format date-time DateTimeFormatter/ISO_LOCAL_DATE_TIME))

(defn cleanup-old-conversions!
  "Keeps only the last 5 conversions in the database and deletes old PDF files"
  []
  (when @db-spec
    (try
      (with-open [conn (jdbc/get-connection @db-spec)]
        ;; Get PDF paths of conversions that will be deleted
        (let [old-conversions (jdbc/execute! conn
                                             ["SELECT pdf_path FROM conversions
                                               WHERE id NOT IN (
                                                 SELECT id FROM conversions
                                                 ORDER BY id DESC
                                                 LIMIT 5
                                               )"])]
          ;; Delete the old PDF files
          (doseq [{:conversions/keys [pdf_path]} old-conversions]
            (when pdf_path
              (try
                (let [file (clojure.java.io/file pdf_path)]
                  (when (.exists file)
                    (.delete file)
                    (println (format "Deleted old PDF: %s" pdf_path))))
                (catch Exception e
                  (println (format "Error deleting PDF file %s: %s" pdf_path (.getMessage e)))))))

          ;; Delete database records
          (jdbc/execute! conn
                         ["DELETE FROM conversions
                           WHERE id NOT IN (
                             SELECT id FROM conversions
                             ORDER BY id DESC
                             LIMIT 5
                           )"])))
      (catch Exception e
        (println "Error cleaning up old conversions:" (.getMessage e))))))

(defn add-conversion!
  "Records a successful conversion in the database.

   Parameters:
   - filename: Original filename
   - extension: File extension (e.g., '.doc', '.jpg')
   - file-size: Size of the file in bytes (optional)
   - pdf-path: Path to the stored PDF file (optional)"
  [filename extension file-size pdf-path]
  (when @db-spec
    (try
      (let [timestamp (format-timestamp (LocalDateTime/now))]
        (sql/insert! @db-spec :conversions
                     {:filename filename
                      :original_extension extension
                      :file_size (or file-size 0)
                      :pdf_path pdf-path
                      :converted_at timestamp}))
      (println (format "Recorded conversion: %s (PDF: %s)" filename pdf-path))

      ;; Keep only the last 5 conversions
      (cleanup-old-conversions!)

      (catch Exception e
        (println "Error recording conversion:" (.getMessage e))
        (.printStackTrace e)))))

(defn get-recent-conversions
  "Retrieves the last 5 conversions from the database.

   Returns a vector of maps with conversion details"
  []
  (when @db-spec
    (try
      (with-open [conn (jdbc/get-connection @db-spec)]
        (vec (jdbc/execute! conn
                            ["SELECT * FROM conversions
                             ORDER BY id DESC
                             LIMIT 5"])))
      (catch Exception e
        (println "Error retrieving conversions:" (.getMessage e))
        []))))

(defn get-conversion-count
  "Returns the total number of conversions recorded"
  []
  (when @db-spec
    (try
      (with-open [conn (jdbc/get-connection @db-spec)]
        (-> (jdbc/execute-one! conn
                               ["SELECT COUNT(*) as count FROM conversions"])
            :count))
      (catch Exception e
        (println "Error getting conversion count:" (.getMessage e))
        0))))

(defn get-conversion-by-id
  "Retrieves a specific conversion by ID.

   Parameters:
   - id: The conversion ID

   Returns a map with conversion details or nil if not found"
  [id]
  (when @db-spec
    (try
      (with-open [conn (jdbc/get-connection @db-spec)]
        (jdbc/execute-one! conn
                           ["SELECT * FROM conversions WHERE id = ?" id]))
      (catch Exception e
        (println "Error retrieving conversion:" (.getMessage e))
        nil))))
