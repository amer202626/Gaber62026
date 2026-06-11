# Yemen Services Hub (دليلي 2026) - Comprehensive Technical Documentation & Reference Specification

---

## 📌 1. Executive Application Overview & Architectural Blueprint

**Yemen Services Hub (دليلي 2026)** is an production-grade, highly resilient mobile application designed to bridge the gap between citizens and professional service providers (such as maintenance crews, medical professionals, educational tutors, and tech specialists) across various Yemeni directorates and cities. 

### Core Architectural Goals:
1. **Offline-First Resilience**: Given Yemen's local infrastructure constraints, the application functions fully offline without needing active network signals.
2. **Real-time Live Sync (Bi-directional)**: Changes made to service providers, chats, banners, or configurations are synchronized globally in milliseconds when connected.
3. **Dynamic Arabic-RTL Language Flow**: The layout behaves symmetrically under Jetpack Compose's structured layouts, automatically shifting directions based on RTL constraints.
4. **Secure Backdoor Administration**: Embedded hidden access flows allow system moderators to review and authorize incoming candidate requests, log events, and ban bad actors.

```
       ┌────────────────────────────────────────────────────────┐
       │                Jetpack Compose UI Layer                │
       │  (HomeScreen, Diagnostics, BackdoorSettings, Filters)  │
       └───────────────────────────▲────────────────────────────┘
                                   │
                           Collects StateFlow
                                   │
       ┌───────────────────────────┴────────────────────────────┐
       │                    FirebaseManager                      │
       │  (StateFlow Publishers, Network Checkers, Listeners)   │
       └─────┬────────────────────────────────────────────▲─────┘
             │                                            │
        Reads Local Cache                            Live Sockets
             │                                            │
       ┌─────▼────────────────────────────────────────────┴─────┐
       │             Firebase Firestore SDK Local SQLite        │
       │                   (Unlimited Persistence)              │
       └────────────────────────────────────────────────────────┘
```

---

## 💾 2. Offline-First Synchronization & Persistence Architecture
While Web architectures use **IndexedDB** for client-side document persistence, native Android systems utilize native SQLite databases configured transparently through the Google Firebase Native Android SDK.

### Configuration Specification (Kotlin API):
```kotlin
val settings = FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true) // Enables native database cache (Analogous to Web IndexedDB)
    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Unlimited disk allocation
    .build()
db.firestoreSettings = settings
```

### Advanced Synchronization Mechanisms:
1. **Metachange Snapshots Feed**: Listeners utilize Jetpack Flow streams to register real-time updates. The application identifies whether incoming snapshots origin from the local on-device cache or the live cloud servers using:
   ```kotlin
   isProvidersDataFromCache.value = snapshots.metadata.isFromCache
   ```
2. **Auto-Reconnect Daemon**: Incorporates a hardware internet listener via Android's `ConnectivityManager`. Upon restoring network connection, the system automatically triggers `forceReSubscribe()` to instantly re-establish active sockets and flush pending writes.
3. **Anonymous Credentials Guard**: Authenticates silent anonymous accounts on startup. This satisfies restrictive rules requiring authorization while avoiding annoying login screens on initial launch.

---

## 🗄️ 3. Firebase Firestore Schema Definitions (Fully Documented)

### Collection: `service_providers`
Stores verified, active professionals listed in the public directory.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Document unique identifier (UUID) |
| `fullName` | `String` | Professional name for display |
| `phone` | `String` | Direct telephone call target |
| `whatsapp` | `String` | WhatsApp quick-chat target link |
| `categoryId` | `String` | Direct hierarchy link to service categories |
| `subCategory` | `String` | Specialized sub-trade field |
| `address` | `String` | Precise street directions |
| `area` | `String` | Region/City in Yemen (e.g., Sana'a, Aden) |
| `imageUrl` | `String` | Cloud Storage or fallback placeholder image url |
| `idCardUrl` | `String` | Identification card photo URL for verification checks |
| `gpsLat` | `Double` | Geographical location latitude coordinates |
| `gpsLng` | `Double` | Geographical location longitude coordinates |
| `isVerified` | `Boolean` | Core Verification Identity flag (Approved by moderators) |
| `isPinned` | `Boolean` | Priority pinning to top of directory lists |
| `isRecommended` | `Boolean` | Handpicked recommended provider tag |
| `hasPremiumSubscription`| `Boolean`| High-tier premium subscriber status indicator |
| `loyaltyPoints` | `Int` | Reward system points accrued via positive reviews |
| `ratingSum` | `Float` | Combined rating stars summation |
| `ratingCount` | `Int` | Total reviews count |
| `isBlocked` | `Boolean` | Security flag to halt visibility on complaints |

---

### Collection: `pending_providers`
Stores joining applications submitted by candidates awaiting manual administrative approval.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Unique application tracking ID |
| `fullName` | `String` | Candidate full name |
| `phone` | `String` | Call number |
| `whatsapp` | `String` | Chat number |
| `categoryId` | `String` | Chosen category ID |
| `subCategory` | `String` | Specialization details |
| `address` | `String` | Street details |
| `area` | `String` | City |
| `imageUrl` | `String` | Candidate photo |
| `idCardUrl` | `String` | Identification document photo |
| `gpsLat` | `Double` | Lat coordinate |
| `gpsLng` | `Double` | Lng coordinate |
| `timestamp` | `Long` | Submission epoch mills |

---

### Collection: `categories`
Provides categorization links. Includes a built-in background seeder fallback.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Category key (e.g., `cat_1`, `cat_2`) |
| `nameAr` | `String` | Categorization title in Arabic |
| `nameEn` | `String` | Categorization title in English |
| `iconEmoji` | `String` | High-visibility vector emoji symbol representation |

---

### Collection: `app_config`
Allows instant operational customization over-the-air from the dashboard settings.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `themeType` | `String` | Active skin design ("classic_navy", "modern_teal", "desert_gold") |
| `allowAnonymousReviews` | `Boolean` | Flag controlling safety checks on reviews submissions |
| `adminBackdoorPassword` | `String` | Control code locking settings backdoor dashboard |

---

### Collection: `chats`
Instant real-time room for direct citizen-to-admin help desk queries.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Chat message UUID |
| `senderId` | `String` | User device fingerprint or specific UID |
| `senderName` | `String` | Display name for dialog thread views |
| `receiverId` | `String` | Target identifier |
| `content` | `String` | Secure text payload |
| `timestamp` | `Long` | System epoch mills |

---

## 🎛️ 4. Dynamic Filtering Engine & Verification UI Structure

To provide high-fidelity navigation and directories searching, the view embeds a multi-dimensional filtering query system executed locally inside Kotlin's reactive state machine.

### Multi-Dimensional Query Combinator:
```kotlin
val filteredProviders = remember(
    providers, 
    searchTextInput, 
    selectedFilterCategoryId, 
    selectedFilterCity, 
    selectedFilterMinRating, 
    selectedVerificationFilter
) {
    providers.filter { p ->
        val matchesText = searchTextInput.isEmpty() ||
                p.fullName.contains(searchTextInput, ignoreCase = true) ||
                p.subCategory.contains(searchTextInput, ignoreCase = true)

        val matchesCategory = selectedFilterCategoryId.isEmpty() || p.categoryId == selectedFilterCategoryId
        val matchesCity = selectedFilterCity.isEmpty() || p.area.contains(selectedFilterCity) || p.address.contains(selectedFilterCity)
        val matchesRating = p.averageRating >= selectedFilterMinRating
        
        // Verification Filter: 0 (All), 1 (Verified Only), 2 (Unverified Only)
        val matchesVerification = when (selectedVerificationFilter) {
            1 -> p.isVerified
            2 -> !p.isVerified
            else -> true
        }

        matchesText && matchesCategory && matchesCity && matchesRating && matchesVerification
    }.sortedWith(
        compareByDescending<ServiceProvider> { it.isPinned }
            .thenByDescending { it.isRecommended }
            .thenByDescending { it.hasPremiumSubscription }
    )
}
```

### UI Presentation Rules (M3 Standards):
*   **Offline Cached Pill Indicator**: A persistent high-visibility state indicator at the directory root visually tells users if data matches local disks or servers.
*   **Verification Badges**: Verified professionals are marked with an Emerald Green `✓ مھني موثق` (Verified Professional) badge. Unverified listings show an Amber `⏳ قيد مراجعة الهوية` (In Identity Review Pipeline) badge.
*   **Quick Category Chips**: Horizontal scrolling lists of service categories.
*   **Diagnostic Dashboard Panel**: A quick-toggle troubleshooting environment to inspect connection caches, force socket re-subscriptions, verify collections doc sizes, and fetch full database statistics.

---

## 🔒 5. Firestore Cybersecurity Rulebook

To secure database pathways while preserving open local sync, apply the following production security configuration on your Firebase console settings:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Auth helpers
    function isAuth() {
      return request.auth != null;
    }
    
    // Public Categories & Config visibility 
    match /categories/{catId} {
      allow read: if true;
      allow write: if isAuth(); // Requires administrative token session
    }
    
    match /app_config/settings {
      allow read: if true;
      allow write: if isAuth();
    }
    
    // Professionals directory allow free local synced queries
    match /service_providers/{providerId} {
      allow read: if true;
      allow create, update: if isAuth();
      allow delete: if isAuth();
    }
    
    // Allow users to request joining and write reviews
    match /pending_providers/{pendingId} {
      allow write, create: if true; // Any visitor can register an identity
      allow read, delete: if isAuth(); // Admin only access for review pipelines
    }
    
    match /reviews/{reviewId} {
      allow read: if true;
      allow create: if true; // Open reviews submission if configured
      allow write, delete: if isAuth();
    }
    
    // Chats direct access
    match /chats/{chatMsgId} {
      allow read, create: if true; 
      allow write, delete: if isAuth();
    }
    
    // Safe logging records
    match /activity_logs/{logId} {
      allow create: if true;
      allow read, write, delete: if isAuth();
    }

    match /incident_reports/{reportId} {
      allow create: if true;
      allow read, write, delete: if isAuth();
    }

    match /moderators/{modId} {
      allow read, write: if isAuth();
    }
  }
}
```

---

## 🚀 6. Developer Reference Instructions (The Prompt Specification)

To duplicate, extend, or build this application layout system in any workspace, supply this literal prompt as the base:

```text
Build a Native Android application named "دليلي 2026" (Yemen Services Hub) utilizing Kotlin and Jetpack Compose. 

The app must implement an Offline-First hybrid directory architecture. Turn on SQLite-level offline local cache with unlimited space (CACHE_SIZE_UNLIMITED) on Firebase Firestore settings so the entire database is fully read-write enabled without active network bars. Connect database collections using active Snapshot Listeners mapped onto Kotlin StateFlows to capture modifications live in milliseconds. Set up an anonymous authentication session silently on setup to securely open directories under restrictive security regulations.

Integrate a master directory view featuring quick scrolling tabs for service category codes (Maintenance 🛠️, Doctors 🩺, Tech 📱, etc.) and a sliding promo ads banner cards system. Build a sliding filter panel allowing real-time searching on composite fields: term search, city/area name filtering, minimum rating stars, and Verification Status (differentiating between Emerald verified badges- "✓ مھني موثق" and Amber review tags - "⏳ قيد مراجعة الهوية").

Include a Live Connectivity status badge showing whether the data is fetched directly from local cached SQLite storage disks or live server streams, alongside a debug dashboard ("لوحة فحص المزامنة") displaying collection counts and connection health. Construct a hidden supervisor backdoor administration console locked behind a custom bypass password to authorize pending join requests, manage active banners, log moderator interactions, and ban actors with direct audit tracks. Use clean Material Design 3 guidelines featuring a professional, high-contrast dark Theme (InteractiveYemenTheme) with appropriate edge-to-edge support.
```
