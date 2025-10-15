# Feature: Download Previous PDFs

## Overview
This feature adds the ability to view and download the last 5 converted PDFs from previous conversions. The PDFs are stored persistently on the server and can be accessed through a history section on the web interface.

## Changes Made

### 1. Database Schema (`src/doc_converter/db.clj`)
- **Added `pdf_path` column** to the `conversions` table to store the file path of each converted PDF
- **Updated `cleanup-old-conversions!`** to delete old PDF files from disk when records are removed
- **Updated `add-conversion!`** to accept and store the PDF file path (4th parameter)
- **Added `get-conversion-by-id`** function to retrieve a specific conversion record by ID

### 2. Backend Handler (`src/doc_converter/handler.clj`)
- **Created persistent storage directory** (`pdf-storage/`) to store converted PDFs
- **Modified upload handler** to:
  - Save a copy of each converted PDF to persistent storage with a UUID filename
  - Record the storage path in the database
- **Added `/download/:id` endpoint** to download a specific PDF by its conversion ID
- **Existing `/history` endpoint** already returns the last 5 conversions (now includes PDF paths)

### 3. Frontend (`resources/public/index.html`)
- **Added "Recent Conversions" section** below the upload form
- **Added CSS styling** for history items with hover effects
- **Added JavaScript functions**:
  - `loadHistory()` - Fetches conversion history from `/history` endpoint
  - `displayHistory()` - Renders the history list with download buttons
  - Auto-refresh history after successful conversion
  - Load history on page load

## API Endpoints

### GET /history
Returns the last 5 conversions as JSON.

**Response Example:**
```json
[
  {
    "conversions/id": 1,
    "conversions/filename": "document.docx",
    "conversions/original_extension": ".docx",
    "conversions/file_size": 12345,
    "conversions/pdf_path": "/path/to/pdf-storage/uuid.pdf",
    "conversions/converted_at": "2025-10-15T10:30:00"
  }
]
```

### GET /download/:id
Downloads the PDF for a specific conversion by ID.

**Parameters:**
- `id` - The conversion ID from the database

**Response:**
- `200 OK` - Returns the PDF file with proper headers
- `404 Not Found` - If conversion ID doesn't exist or PDF file is missing

## Storage

- **Temporary files**: `uploads/` directory (cleaned up after each conversion)
- **Persistent PDFs**: `pdf-storage/` directory (keeps last 5 only)
- **Database**: `conversions.db` (SQLite, stores metadata)

## Automatic Cleanup

The system automatically maintains only the last 5 conversions:
- Old database records are deleted
- Old PDF files are removed from `pdf-storage/`
- Cleanup happens after each new conversion is recorded

## Testing

To test this feature:

1. Start the application
2. Upload and convert some documents
3. Check the "Recent Conversions" section appears below the upload form
4. Click download buttons to retrieve previously converted PDFs
5. Convert more than 5 files and verify old ones are removed

## Notes

- The feature uses UUIDs for stored PDF filenames to avoid conflicts
- Original filenames are preserved in the database for display purposes
- The system handles both single file conversions and batch conversions
- All existing functionality remains unchanged
