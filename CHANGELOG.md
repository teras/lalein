# Changelog

All notable changes between published releases of `com.panayotis.lalein` on
Maven Central.

## [1.2] — 2026-05-16

### Added

- **XCStrings backend** (`com.panayotis.lalein:xcstrings`) — Apple's String
  Catalog (.xcstrings) format introduced with Xcode 15. JSON-based; depends
  only on `minimal-json`.
- **Fluent backend** (`com.panayotis.lalein:fluent`) — Mozilla Fluent (.ftl)
  with native support for nested select expressions, written against the JDK
  alone (no external runtime dependencies).
- **Reverse-direction methods** for every backend (`toJson`, `toYaml`,
  `toProperties`, `toMap`, `toString`). Lalein now round-trips through every
  supported format and acts as a universal interchange between them.
- **Optional `i` key** in JSON / YAML / Map / XCStrings parameter blocks —
  declares the argument index explicitly, removing the dependency on key order
  and the `^` prefix. When absent the previous positional inference still
  applies, so existing files keep working unchanged.
- **Optional `format` key** in JSON / YAML / Map parameter blocks — declares
  the master template explicitly. Enables sentences with multiple top-level
  parameters (e.g. _"3 hours and 12 minutes ago"_), parameters appearing in any
  position regardless of their argument index, the same parameter referenced
  multiple times, and per-locale word-order changes. When absent the first-key
  inference still applies.
- **Auto-derive `argumentIndex` in the short form.** Lalein now infers which
  argument drives the plural without requiring an explicit `i` key in the
  common cases. Two-step lookup: (1) scan the plural forms for a unique
  positional numeric ref (e.g. `%2$d`); (2) if step 1 is empty or ambiguous,
  scan the handler for the first numeric placeholder. Fallback `1` preserves
  the previous default for keys with no numeric placeholders (e.g. `"apples"`).
  Explicit `i` still overrides when the data is genuinely ambiguous (handler
  with ≥2 numeric placeholders, forms referencing more than one of them).
- **Richer `LaleinException` messages.** Errors thrown from `Parameter.resolve`
  now name the offending handler and parameter, e.g.
  `A numeric argument at position #2 is required but got java.lang.String
  (parameter 'base' of 'Cash payment of %s saved (%d allocated)')`.
- **Select-mode parameters.** A parameter may now declare arbitrary non-CLDR
  keys (e.g. `female`, `male`, `formal`) alongside the standard plural tags.
  When called with a `String`/`Enum` argument the matching key is selected;
  unknown values fall through to `r`. This brings ICU `select`-equivalent
  expressiveness — gender, formality, term variants — without leaving the
  positional-varargs call model. Number arguments continue to use the existing
  CLDR plural logic unchanged. The `format` key remains reserved and is now
  rejected with a clear message if used as a form name inside a parameter.

### Changed

- **Core library size reduced ~10%** (15,681 → 14,632 bytes) via:
  - `DataConverter` rewritten to operate on `Map<String, Object>` instead of
    accepting per-backend functional adapters. Removes ~3 KB of generic bridge
    methods and synthetic lambdas.
  - `PluralType` no longer uses the `Stream` API for its tag-validation set.
  - `PluralResolvers` no longer keeps an internal `HashMap` of lambdas; direct
    conditional dispatch replaces it.
  - `Lalein.hashCode` no longer goes through `Objects.hash`.
- **All data-format backends shrunk** as a side effect of the `DataConverter`
  refactor: `json` -16%, `yaml` -18%, `map` -29%, `properties` -8%.
- README extensively reorganised: documents every backend (with a dedicated
  example each), explains the `i` and `format` opt-in keys, lists Maven
  coordinates for every artifact, and shows cross-format conversion examples.

### Fixed

- Resource loaders no longer NPE when a requested resource is missing —
  callers now receive a `LaleinException` with the offending path.
- Grammar in the "numeric argument required" exception (`"An numeric"` →
  `"A numeric"`).

### Internal

- Test suite expanded from **5 → 289 tests** across 8 modules. Coverage now
  includes:
  - Cross-language plural rules (English, French, Hindi, Punjabi, Portuguese,
    plus error paths for unsupported languages).
  - Every `Number` subtype (`int`, `long`, `short`, `byte`, `float`, `double`,
    `BigDecimal`, `BigInteger`, `AtomicInteger`, `AtomicLong`).
  - Boundary tolerances around the natural ZERO / ONE / TWO bands
    (1.999999, 2.000001, etc.).
  - Full 4×4 (16) and 4×4×4 (64) value matrices for two- and three-argument
    templates.
  - Per-backend round-trip and Apple format-specifier conversion edge cases.
- Build now targets Java 1.8 source/target, compiled with Kotlin 2.2.21 (up
  from 1.7.10) so it works under recent JDKs.
- Removed internal `TriFunction` interface (was package-private, never public
  API).

---

## [1.1.0] — 2023-01-28

### Added

- **`core` artifact** split out as a standalone module containing `Lalein`,
  `Translation`, `Parameter`, `PluralType`, `PluralResolver`,
  `PluralResolvers`, and `LaleinException`. Backends now depend on `core`
  instead of bundling the runtime themselves.
- **`info` artifact** — read-only introspection layer exposing the structure
  of a `Lalein` instance via `LaleinInfo`, `TranslationInfo`, and
  `ParameterInfo`.
- **`properties` artifact** — Java `.properties` backend with explicit
  `UNIT.PARAMETER.i` argument indices.
- **`map` artifact** — programmatic `Map<String, Object>` backend, useful for
  tests, code generation, and dynamic translation data.
- **`editor` artifact** — Kotlin/Swing GUI editor for translations,
  publishable alongside the library.

### Changed

- **Maven coordinates renamed**: `lalein-json` → `json`, `lalein-yaml` →
  `yaml` (drop the `lalein-` prefix). All artifacts now share the convention
  `com.panayotis.lalein:<format>`.
- `LaleinProvider` renamed to `LaleinLoader`; added the
  `fromResource(String)` convenience method to every backend.
- Package names normalised across all modules.

### Notes

This release is a **breaking change in Maven coordinates** but largely
source-compatible at the API level. Consumers of `lalein-json` /
`lalein-yaml` must update their `artifactId`s when upgrading.

---

## [1.0.0] — 2023-01-21

Initial public release on Maven Central. Two artifacts published:

- `com.panayotis.lalein:lalein-json:1.0.0`
- `com.panayotis.lalein:lalein-yaml:1.0.0`

Provided the foundational `Lalein` runtime plus JSON and YAML backends with
support for CLDR plural categories (`zero`, `one`, `two`, `few`, `many`,
`other`), nested parameter references via `%{name}`, and the `^` prefix
convention for sharing argument positions across parameters.
