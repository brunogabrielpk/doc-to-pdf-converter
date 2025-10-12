# Document to PDF Converter

A web-based application that converts Microsoft Word documents (.doc, .docx) to PDF format. Built with Clojure and LibreOffice, featuring a modern web interface and Docker containerization.

## Features

- **Multiple File Support**: Upload and convert multiple documents at once
- **Batch Conversion**: Single files are returned as PDF, multiple files are packaged as ZIP
- **Modern Web Interface**: Drag-and-drop or click to upload files
- **Docker Ready**: Fully containerized application for easy deployment
- **File Format Support**: Converts both .doc (Word 97-2003) and .docx (Word 2007+) formats

## Quick Start with Docker

### Pull from Docker Hub

```bash
docker pull pokkew/doc-to-pdf-converter:latest
docker run -p 3000:3000 pokkew/doc-to-pdf-converter:latest
```

Then open your browser to `http://localhost:3000`

### Build from Source

```bash
git clone https://github.com/pokkew/doc-to-pdf-converter.git
cd doc-to-pdf-converter
docker build -t doc-to-pdf-converter .
docker run -p 3000:3000 doc-to-pdf-converter
```

## Usage

1. Open the web interface at `http://localhost:3000`
2. Click the upload area or drag and drop your .doc or .docx files
3. Click "Convert to PDF"
4. Your converted files will automatically download:
   - **Single file**: Downloads as a PDF with the original filename
   - **Multiple files**: Downloads as `converted-documents.zip` containing all PDFs

## Technology Stack

- **Backend**: Clojure with Ring and Compojure
- **Document Conversion**: LibreOffice (via JODConverter)
- **Frontend**: Vanilla JavaScript with modern CSS
- **Containerization**: Docker with Alpine Linux

## Architecture

```
├── src/
│   └── doc_converter/
│       ├── core.clj          # Application entry point
│       ├── handler.clj        # HTTP request handlers and routing
│       └── converter.clj      # Document conversion logic
├── resources/
│   └── public/
│       └── index.html         # Web interface
├── deps.edn                   # Clojure dependencies
└── Dockerfile                 # Docker container configuration
```

## Development

### Prerequisites

- Java 11 or higher
- Clojure CLI tools
- LibreOffice (for local development)

### Running Locally

```bash
# Install dependencies
clojure -P

# Create uploads directory
mkdir -p uploads

# Run the application
clojure -M -m doc-converter.core
```

The application will start on `http://localhost:3000`

### Building the Docker Image

```bash
docker build -t doc-to-pdf-converter .
```

### Environment Variables

- `PORT`: Server port (default: 3000)

## API

### POST /upload

Upload one or more documents for conversion.

**Request**:
- Content-Type: `multipart/form-data`
- Body: One or more files with parameter name `file`

**Response**:
- Single file: `application/pdf` with the converted PDF
- Multiple files: `application/zip` containing all converted PDFs

**Example with curl**:

```bash
# Single file
curl -F "file=@document.docx" http://localhost:3000/upload -o output.pdf

# Multiple files
curl -F "file=@doc1.docx" -F "file=@doc2.doc" http://localhost:3000/upload -o output.zip
```

## Docker Hub

The official Docker image is available at:
```
docker pull pokkew/doc-to-pdf-converter:latest
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is open source and available under the MIT License.

## Author

Created by pokkew

## Acknowledgments

- LibreOffice for document conversion capabilities
- JODConverter for Java-LibreOffice integration
- The Clojure community for excellent libraries and tools
