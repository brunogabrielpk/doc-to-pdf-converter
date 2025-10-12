# Clojure Learning Tutorial: Document to PDF Converter

## Overview

This tutorial explains how the document-to-PDF converter works, helping you understand Clojure concepts through a practical application.

## Project Structure Explained

```
doc-to-pdf-converter/
├── deps.edn                    # Dependency management
├── Dockerfile                  # Container configuration
├── src/doc_converter/          # Source code
│   ├── core.clj               # Application entry point
│   ├── handler.clj            # Web request handling
│   └── converter.clj          # Conversion logic
├── resources/public/           # Static web files
│   └── index.html             # User interface
└── uploads/                    # Temporary file storage
```

---

## Step-by-Step Guide to Running the Application

### Method 1: Using Docker (Recommended for Learning)

#### Step 1: Build the Docker Image

```bash
docker build -t doc-to-pdf-converter .
```

**What happens:**
- Docker reads the Dockerfile
- Downloads the Clojure base image
- Installs LibreOffice
- Downloads all Clojure dependencies
- Copies your application code into the container
- Creates a runnable image

**Expected output:** You'll see layers being built, ending with "Successfully tagged doc-to-pdf-converter:latest"

#### Step 2: Run the Container

```bash
docker run -p 3000:3000 doc-to-pdf-converter
```

**What happens:**
- Creates a new container from the image
- Maps port 3000 from container to your host machine
- Starts the Clojure application
- Initializes LibreOffice for document conversion

**Expected output:**
```
LibreOffice office manager started
Starting server on port 3000...
Visit http://localhost:3000 to use the application
```

#### Step 3: Test the Application

1. Open your web browser
2. Navigate to `http://localhost:3000`
3. Upload a .doc or .docx file
4. Click "Convert to PDF"
5. The PDF will download automatically

#### Step 4: Stop the Container

Press `Ctrl+C` in the terminal where the container is running.

---

## Understanding the Clojure Code

### 1. deps.edn - Dependency Management

```clojure
{:paths ["src" "resources"]
 :deps {org.clojure/clojure {:mvn/version "1.11.1"}
        ring/ring-core {:mvn/version "1.10.0"}
        ...}}
```

**Key concepts:**
- `:paths` - where Clojure looks for code
- `:deps` - external libraries (from Maven repository)
- Uses EDN (Extensible Data Notation) - Clojure's data format

---

### 2. core.clj - Application Entry Point

```clojure
(ns doc-converter.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [doc-converter.handler :refer [app]]
            [doc-converter.converter :as converter])
  (:gen-class))
```

**Clojure concepts demonstrated:**

#### Namespaces
- `ns` declares a namespace (like a package/module)
- Prevents naming conflicts
- Organizes code

#### Requiring dependencies
- `:require` imports other namespaces
- `:refer` imports specific functions
- `:as` creates an alias

#### The -main function
```clojure
(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (converter/start-office-manager!)
    (run-jetty app {:port port :join? true})))
```

**Breakdown:**
- `defn` defines a function
- `[& args]` accepts variable arguments
- `let` creates local bindings (like variables)
- `or` returns first truthy value
- Functions ending with `!` modify state (convention)
- Map literals use `{:key value}` syntax

---

### 3. converter.clj - Document Conversion

```clojure
(defonce office-manager (atom nil))
```

**Atom explained:**
- Atoms hold mutable state in Clojure
- Changed with `reset!` or `swap!`
- Thread-safe by default
- `defonce` ensures it's only created once (survives REPL reloads)

```clojure
(defn start-office-manager! []
  (when-not @office-manager
    (let [manager (-> (LocalOfficeManager/builder)
                      (.portNumbers (int-array [2002]))
                      (.build))]
      (.start manager)
      (reset! office-manager manager))))
```

**Key concepts:**
- `@` dereferences an atom (gets its value)
- `when-not` is like `if not`
- `->` (thread-first macro) chains method calls
- `.method` calls Java methods (Java interop)
- `reset!` sets a new value in an atom

```clojure
(defn valid-input-file? [filename]
  (let [filename-lower (clojure.string/lower-case filename)]
    (or (.endsWith filename-lower ".doc")
        (.endsWith filename-lower ".docx"))))
```

**Conventions:**
- Functions ending with `?` return boolean (predicates)
- Last expression is automatically returned (no `return` keyword)

---

### 4. handler.clj - Web Request Handling

```clojure
(defroutes app-routes
  (GET "/" [] (home-page))
  (POST "/upload" request (handle-upload request))
  (route/resources "/")
  (route/not-found "Not Found"))
```

**Routing explained:**
- `defroutes` creates route definitions
- `GET`, `POST` define HTTP method handlers
- Requests are just Clojure maps

```clojure
(defn handle-upload [request]
  (let [params (:params request)
        file-param (get params "file")]
    (cond
      (nil? file-param) 
      (-> (response/response "No file uploaded")
          (response/status 400))
      
      (not (converter/valid-input-file? (:filename file-param)))
      (-> (response/response "Invalid file type")
          (response/status 400))
      
      :else
      (process-conversion file-param))))
```

**Key concepts:**
- `let` destructures and binds values
- `:keyword` accesses map values
- `cond` is like switch/case (evaluates pairs of test/expression)
- `:else` is the default case
- `->` threads the response through transformations

```clojure
(def app
  (-> app-routes
      (wrap-defaults site-defaults)
      wrap-multipart-params))
```

**Middleware:**
- Functions that wrap handlers
- Add functionality like session handling, security headers
- Applied in reverse order (bottom-up)

---

## Testing the Application Step-by-Step

### Create a Test Document

1. Create a simple .docx file with Microsoft Word or LibreOffice
2. Add some text: "Hello from Clojure!"
3. Save as `test.docx`

### Test via Web Interface

1. Start the application (see Docker instructions above)
2. Open browser to `http://localhost:3000`
3. Drag and drop `test.docx` or click to upload
4. Click "Convert to PDF"
5. Verify the PDF downloads and opens correctly

### Test via Command Line (curl)

```bash
curl -X POST -F "file=@test.docx" http://localhost:3000/upload --output result.pdf
```

This sends a POST request with your file and saves the PDF.

### Viewing Logs

The Docker container outputs logs showing:
- When LibreOffice starts
- When files are uploaded
- Conversion progress
- Any errors

---

## Common Issues and Solutions

### Issue: "Conversion failed. Please ensure LibreOffice is installed"

**Cause:** LibreOffice not properly installed in the container

**Solution:** Rebuild the Docker image:
```bash
docker build --no-cache -t doc-to-pdf-converter .
```

### Issue: Port 3000 already in use

**Solution:** Use a different port:
```bash
docker run -p 8080:3000 doc-to-pdf-converter
```
Then access at `http://localhost:8080`

### Issue: File not converting

**Possible causes:**
- File is corrupted
- File is password-protected
- File uses unsupported features

**Debug:** Check container logs:
```bash
docker logs <container-id>
```

---

## Learning Exercises

### Exercise 1: Add Support for .odt Files

1. Modify `valid-input-file?` in `converter.clj`
2. Add `.endsWith filename-lower ".odt"`
3. Update the HTML to accept `.odt` files

### Exercise 2: Add a File Size Limit

1. In `handle-upload` function
2. Check `(.length (:tempfile file-param))`
3. Return error if > 10MB (10485760 bytes)

### Exercise 3: Keep Conversion History

1. Create an atom to store conversion history
2. Add timestamp and filename on each conversion
3. Create a new route `GET /history` to display it

### Exercise 4: Add Environment Configuration

1. Read max file size from environment variable
2. Use `(System/getenv "MAX_FILE_SIZE")`
3. Update Dockerfile with `ENV MAX_FILE_SIZE=10485760`

---

## Next Steps in Your Clojure Journey

1. **Learn the REPL:** Interactive development is Clojure's superpower
2. **Explore Ring middleware:** Session handling, authentication, CORS
3. **Database integration:** Add `next.jdbc` for SQL databases
4. **Testing:** Learn `clojure.test` for unit testing
5. **Build tools:** Explore Leiningen or tools.build
6. **ClojureScript:** Use Clojure in the browser

---

## Resources

- [Clojure Documentation](https://clojure.org/guides/getting_started)
- [Ring Documentation](https://github.com/ring-clojure/ring)
- [Compojure Documentation](https://github.com/weavejester/compojure)
- [Clojure Style Guide](https://guide.clojure.style/)

## Questions to Explore

1. Why does Clojure prefer immutable data structures?
2. How do atoms differ from refs and agents?
3. What are the benefits of the thread-first `->` macro?
4. How does Clojure's approach to state differ from OOP?
5. What makes Lisps like Clojure good for domain-specific languages?

Happy Clojure learning! 🎉
