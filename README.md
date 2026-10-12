# NGO Loan Manager (Android) — Premium UI/UX

Offline-first Android app for a single-admin NGO loan ledger. Kotlin, Jetpack Compose, Material 3, Room/SQLite, deterministic flat-interest engine.

## Premium UI upgrade (this build)

- **Design system**: Emerald / mint / pearl palette with restrained 3D depth, soft shadows, and glossy gradient hero surfaces.
- **Typography**: Local Montserrat (Regular, Medium, SemiBold, Bold) via `res/font`. Bengali text falls back to the platform default so mixed BN/EN strings render correctly.
- **Light & dark themes**: `NgoLoanTheme` follows system dark mode; status/navigation bars adapt.
- **Reusable components**: `GlassCard`, `MetricCard`, `HeroCard`, `PremiumPrimaryButton`, `PremiumTextField`, `ListRowCard`, `StatusChip`, `EmptyStateView`, `QuickActionChip`.
- **Screens upgraded**: Login/setup, dashboard, borrower list & detail, loan list & detail (collection + cancellation), reports, settings/password change, forms & dialogs, empty/loading/error states.
- **Motion**: Press scale on cards/buttons, animated visibility for messages and payment history, lightweight content-size animation. Payment save uses an explicit loading flag to avoid double-submit.
- **Financial logic unchanged**: Interest math, installment schedule, oldest-first allocation, advance balance, payment cancellation/replay, audit log, Room schema, and application ID are preserved.

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- Gradle 8.9-compatible Android Gradle Plugin 8.7.3
- Internet once for dependency download; the installed app is offline

## Open and build

1. Extract this project folder.
2. Open `NGO-Loan-Manager` in Android Studio.
3. Let Gradle sync (wrapper JAR is not bundled; Android Studio will use its embedded Gradle or download dependencies).
4. Run on an emulator or device (minSdk 26 / Android 8.0+).

Command-line (if Gradle 8.9+ is installed):

```bash
cd NGO-Loan-Manager
gradle :app:assembleDebug
gradle :app:test
```

## Unit tests

`LoanMathTest` covers annual/full-term interest, zero-rate remainder, and month-end due-date clamping.
`PaymentAllocatorTest` covers oldest-first/partial allocation, cancelling the first of several payments, advance balances, chronological (backdated) ordering and deterministic rebuilds.
`FinancialSummaryTest` covers principal + interest outstanding, cancelled payments/loans, advance handling, fully paid loans and loan-vs-portfolio reconciliation.
`InputValidationTest` covers Taka/rate/term/date validation and safe user-facing messages.
Run from Android Studio or `gradle :app:testDebugUnitTest` (Windows: `gradlew.bat testDebugUnitTest` forwards to an installed Gradle).
Not covered by automated tests: Room DAO/transaction rollback behaviour and Compose UI (no instrumented/Robolectric tests are included).

## Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.9, but `gradle-wrapper.jar` is not bundled; `gradlew`/`gradlew.bat` are fallback launchers that forward to an installed `gradle`. To generate the genuine wrapper once: `gradle wrapper --gradle-version 8.9`.

## Financial representation

Amounts are integer poisha (`100 poisha = ৳1`). Rates are percentage strings. See `FINANCIAL_RULES.md`.

## Known limitations (unchanged from baseline)

Do **not** use for live financial operations until these are completed and verified:

- Encrypted database and encrypted backup/restore
- CSV/PDF report export and printable receipts
- Session timeout lock
- Full dashboard reconciliation for cancelled loans and advance allocations
- End-to-end automated tests for payment allocation, cancellation, and persistence
- Borrower edit / archive UI
- Schema migrations beyond v1

## UI/UX change summary

| Area | Change |
|------|--------|
| Theme | Emerald primary, mint containers, pearl backgrounds; full dark scheme |
| Fonts | Montserrat packaged locally; Bengali via system fallback |
| Dashboard | Hero gradient header, metric grid, quick actions, recent loans |
| Auth | Centered glass card, gradient backdrop, clear error surface |
| Lists | Elevated row cards, avatar initials, status chips |
| Loan detail | Structured summary, installment chips, loading-safe payment button |
| Navigation | Icon + Bengali labels, selected indicator in primary container |
| Components | Shared design tokens for shapes, spacing, money typography |

## Build / test status in this environment

This environment does not include the Android SDK or a full Gradle wrapper JAR. Source was upgraded in place; open in Android Studio to sync, compile, and run unit tests. Do not treat this README as a claim that `assembleDebug` or instrumentation tests passed here.
