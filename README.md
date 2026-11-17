# Toll Plazas Between Two Pincodes Backend Service

A production-ready Spring Boot REST API that calculates road routes between Indian postal codes (pincodes), identifies toll plazas located along that route using geographic projection, and returns their details sorted by distance from source.

---

## 1. Architecture & Layered Design

The application follows clean architecture principles, strict separation of concerns, and constructor-based dependency injection:

```text
com.example.tollplaza
│
├── controller
│   └── TollPlazaController.java       # Thin REST controller exposing POST /api/v1/toll-plazas
│
├── service
│   ├── TollPlazaService.java          # Core orchestrator: validates, resolves route, matches tolls
│   ├── GeocodingService.java          # Interface for resolving pincodes to coordinates
│   ├── OpenRouteServiceGeocodingService.java # Geocoding implementation with Caffeine cache
│   ├── RouteService.java              # Interface for road routing & polyline geometry
│   ├── OpenRouteServiceRouteService.java     # Routing implementation with Caffeine cache
│   └── CsvLoaderService.java          # Startup loader for CSV toll dataset using Commons CSV
│
├── client
│   └── OpenRouteServiceClient.java    # HTTP client for OpenRouteService (with graceful fallbacks)
│
├── repository
│   ├── TollPlazaRepository.java       # Interface for toll plaza in-memory data access
│   └── InMemoryTollPlazaRepository.java # Thread-safe in-memory store (CopyOnWriteArrayList)
│
├── model
│   ├── Coordinates.java               # Geographic lat/lon model
│   ├── TollPlaza.java                 # Toll plaza entity model
│   └── RouteDetails.java              # Road route model with geometry polyline
│
├── dto
│   ├── TollPlazaRequest.java          # Request payload with Bean Validation
│   ├── TollPlazaResponse.java         # API response payload
│   ├── RouteResponse.java             # Route summary response DTO
│   ├── TollPlazaDetailsResponse.java  # Toll plaza item response DTO
│   └── ErrorResponse.java             # Unified error response DTO {"error": "..."}
│
├── exception
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice handling 400, 502, 500
│   ├── InvalidPincodeException.java   # Thrown for malformed or unrecognized pincodes
│   ├── SamePincodeException.java      # Thrown when source and destination are identical
│   └── ExternalApiException.java      # Thrown on upstream API failures / timeouts
│
├── config
│   ├── CacheConfig.java               # Spring Cache + Caffeine configuration
│   └── RestClientConfig.java          # RestTemplate timeouts and interceptors
│
└── util
    ├── GeoUtils.java                  # Haversine distance & line segment projection math
    └── PolylineUtils.java             # Polyline decoder utility
```

---

## 2. Technology Stack

- **Java 21**
- **Spring Boot 3.3.5**
- **Maven**
- **Spring Web** & **Spring Validation**
- **Spring Cache** + **Caffeine**
- **Apache Commons CSV 1.11.0**
- **Jackson**
- **JUnit 5**, **Mockito**, and **Spring Test (MockMvc)**

---

## 3. API Specification

### Endpoint: Find Toll Plazas Between Two Pincodes

- **Method:** `POST`
- **Path:** `/api/v1/toll-plazas`
- **Content-Type:** `application/json`

#### Request Body
```json
{
  "sourcePincode": "110001",
  "destinationPincode": "560001"
}
```

#### Success Response (`200 OK`)
```json
{
  "route": {
    "sourcePincode": "110001",
    "destinationPincode": "560001",
    "distanceInKm": 2100.45
  },
  "tollPlazas": [
    {
      "name": "Toll Plaza 1",
      "latitude": 28.7041,
      "longitude": 77.1025,
      "distanceFromSource": 200.12
    },
    {
      "name": "Toll Plaza 2",
      "latitude": 19.076,
      "longitude": 72.8777,
      "distanceFromSource": 1400.67
    }
  ]
}
```

#### No Toll Plazas on Route (`200 OK`)
If a valid route exists but no toll plazas match:
```json
{
  "route": {
    "sourcePincode": "110001",
    "destinationPincode": "560001",
    "distanceInKm": 2100.45
  },
  "tollPlazas": []
}
```

#### Error Response: Invalid Pincode (`400 Bad Request`)
Returned when pincode is missing, not 6 digits, non-numeric, or unrecognized by geocoding:
```json
{
  "error": "Invalid source or destination pincode"
}
```

#### Error Response: Same Source and Destination (`400 Bad Request`)
Returned when source and destination pincodes match:
```json
{
  "error": "Source and destination pincodes cannot be the same"
}
```

#### Error Response: External Service Failure (`502 Bad Gateway`)
Returned when an external provider times out or fails:
```json
{
  "error": "External service error: OpenRouteService connection timed out"
}
```

---

## 4. Key Functional Features

### 1. CSV Loading & In-Memory Storage
- Reads `toll_plaza_india.csv` (headers: `longitude,latitude,toll_name,geo_state`) on application startup.
- Uses **Apache Commons CSV** streaming parser.
- Validates latitude (-90 to 90) and longitude (-180 to 180).
- Skips malformed rows and deduplicates exact matches on startup.
- Loaded into memory in `TollPlazaRepository` — no repeated file I/O per request.

### 2. Geographic Route Matching & Road Distance Calculation
- Uses the **exact road polyline geometry** generated by the routing service.
- Precomputes cumulative road distances at each vertex along the route.
- Applies a bounding-box filter with margin for $O(1)$ fast exclusion of distant plazas.
- For each toll plaza within the bounding box, calculates the exact perpendicular projection onto each segment $(P_i, P_{i+1})$ using spherical local equirectangular projection:
  $$\text{Perpendicular Distance} = \|\vec{w} - t\vec{v}\|$$
  $$\text{Distance from Source} = \text{Cumulative}(P_i) + t \cdot \|\vec{v}\|$$
- Filters candidates within the configurable proximity threshold (`150m` by default).
- Deduplicates opposing-direction booths / duplicate records on dual carriageways.
- Sorts results in **ascending order of road distance from source**.

### 3. Caching with Caffeine
- Caches geocoding results by pincode (`geocoding` cache).
- Caches route geometry by `sourcePincode_destinationPincode` (`routes` cache).
- Caches the final `TollPlazaResponse` (`tollPlazas` cache).
- Configurable TTL (default 60 minutes) and maximum cache size.
- Invalid or error requests are not cached.

---

## 5. Configuration & Running the Application

### Configuration (`application.yml`)
External API settings and thresholds are configurable via `application.yml` or environment variables:

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `app.openrouteservice.api-key` | `ORS_API_KEY` | *(empty)* | OpenRouteService API Key |
| `app.openrouteservice.base-url` | `ORS_BASE_URL` | `https://api.openrouteservice.org` | OpenRouteService Base URL |
| `app.csv.path` | `TOLL_CSV_PATH` | `classpath:toll_plaza_india.csv` | Path to toll plaza CSV |
| `app.toll-matching.proximity-threshold-meters` | - | `150.0` | Maximum distance (meters) to road centerline |
| `app.toll-matching.duplicate-distance-threshold-meters` | - | `500.0` | Duplicate toll plaza distance threshold |
| `app.cache.ttl-minutes` | - | `60` | Caffeine cache time-to-live |
| `app.cache.max-size` | - | `1000` | Caffeine maximum cache entries |

### Running Locally
To set your OpenRouteService API key and start the server:

**Windows PowerShell:**
```powershell
$env:ORS_API_KEY="your-openrouteservice-api-key"
mvn spring-boot:run
```

**Linux / macOS:**
```bash
export ORS_API_KEY="your-openrouteservice-api-key"
mvn spring-boot:run
```

*Note: If no ORS API key is supplied, the service gracefully uses benchmark coordinates for major Indian pincodes and public fallback routing.*

---

## 6. Running Tests

Execute the automated test suite with:

```bash
mvn test
```

The test suite includes 27 comprehensive tests across all layers:
- `GeoUtilsTest`: Haversine calculation, perpendicular projection, segment clamping.
- `CsvLoaderServiceTest`: Parsing valid CSV, skipping malformed records, deduplication on load.
- `TollPlazaServiceTest`: Route matching, ascending distance ordering, proximity threshold cutoff, same pincode and invalid pincode handling.
- `OpenRouteServiceClientTest`: GeoJSON parsing, directions response parsing, external API failure handling.
- `TollPlazaControllerTest`: MockMvc tests for valid 200 responses, 400 validation failures, same pincode error, empty toll results, 502 gateway error.
- `TollPlazaIntegrationTest`: Full Spring Boot context test verifying CSV startup loading and Caffeine cache hit on repeated identical requests.
