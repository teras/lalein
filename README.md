## Internationalization - i18n - in Java: The proper way

### TL;DR

This library helps Java projects to properly support i18n, by correctly handling plurals and text
reformatting based on rules. Plural categories follow the CLDR standard (`zero`, `one`, `two`, `few`,
`many`, `other`), shared with mainstream mobile and web localization tooling.

Translation files are supported in [JSON](#json-example), [YAML](#yaml-example),
[Properties](#properties-example), Apple's [String Catalog (.xcstrings)](#xcstrings-example),
Mozilla's [Fluent (.ftl)](#fluent-example), or a programmatic [Java Map](#map-example). To use in your own programs you need
to [import the library](#how-to-add-this-library-to-your-own-projects) and [use it in your code](#usage-in-java).

### TOC

* [TL;DR](#tldr)
* [TOC](#toc)
* [The problem](#the-problem)
* [This solution](#this-solution)
* [Translations format](#translations-format)
  * [First some definitions](#first-some-definitions)
  * [JSON example](#json-example)
    * [Simple definitions](#simple-definitions)
    * [Simple with plurals definition](#simple-with-plurals-definition)
    * [Complex with plurals definition](#complex-with-plurals-definition)
    * [Explicit argument index](#explicit-argument-index)
    * [Explicit master format](#explicit-master-format)
  * [YAML example](#yaml-example)
  * [Properties example](#properties-example)
  * [XCStrings example](#xcstrings-example)
  * [Fluent example](#fluent-example)
  * [Map example](#map-example)
* [Usage in Java](#usage-in-java)
* [How to add this library to your own projects](#how-to-add-this-library-to-your-own-projects)

### The problem

If you want to have internationalization (or i18n in short) in Java the right way, you are out of luck. The best thing
you can do, is to define some static strings inside some localized property files. This might work well, if you want to
translate some simple messages.

If you want a more sophisticated approach, you are on your own. Messages like "_Copying 1 file(s) out of 3 file(s)_" are
impossible to be properly generated, with aesthetically (or even grammatically) correct results.

For simple cases like "_Copying 1 file_" or "_Copying 2 files_", using code like this

```java
String message = count == 1 ? "Copying 1 file" : "Copying " + count + " files";
```

sounds like a good idea - until for example French needs to be supported, where zero is considered singular instead of
plural.

### This solution

`lalein` comes to fill the gap; a lightweight (only 14K core plus 5K for the Properties backend) Java library, that properly
handles all _Language Plural Rules_. When using `lalein` messages with arbitrary complexity, like

* _No files to copy_
* _Copying 1 file_
* _Copying 1 file out of 2 files_
* _Copying 3 files out of 4 files_

are easy to implement. Translations can be authored in any of the supported formats — plain JSON, YAML, Java
Properties, String Catalogs, Fluent, or a programmatic Java `Map` — and every backend round-trips
into every other, so the same source can drive different downstream consumers.

### Translations format

#### First some definitions

* Translation unit: a phrase or a block of text that needs to be translated. Every unit has a _handler_, a textual tag
  that uniquely identifies this unit.
* Translation parameter: parts of text that, when combined, can form a translation unit. It is possible that a
  translation unit has no parameters, or just one parameter if the text to be translated is simple. Every parameter has
  a _handler_, a textual tag that uniquely identifies the parameter per unit.
* Plural form: formulated translation parameters, based on counting a value, and producing the correct plural form.

#### JSON example

Although the translation definitions are data-driven, and can be fully customized to fit your needs, the library
ships with several backends: JSON, YAML, Properties, String Catalog (.xcstrings), Fluent (.ftl)
and a plain `Map<String, Object>` for programmatic use. The next sections walk through the JSON form (which is the
most general); the other backends accept the same translation structure with their own syntactic conventions. The
full JSON example file lives [here](json/src/test/resources/Localizable.json).

##### Simple definitions

An example of a simple translation units follows:

```json
{
  "peaches": "I have peaches."
}
```

Tag `peaches` is the translation unit handle, with no translation parameters, only the simple translated text.
This format is equivalent of the common properties file in Java.

##### Simple with plurals definition

```json
{
  "apples": {
    "z": "I don't have apples.",
    "o": "I have one apple.",
    "t": "I have two apples.",
    "r": "I have %d apples."
  }
}
```

This unit requires a numerical value to be passed as a parameter, when the unit is used in code. Note that in order to
use the numeric value inside the translation, formatting with `%d` or `%1$d` (or similar) should be used.

In this case, the translation unit has only one translation parameter, which is defined by the following plural forms:

* `z` : The text to display when the value is equal to zero
* `o` : The text to display when the value is equal to one
* `t` : The text to display when the value is equal to two
* `f` : The text to display when the value resolves to _few_ according to current locale.
* `m` : The text to display when the value resolves to _many_ according to current locale.
* `r` : The text to display in all other cases.

Note that `r` is used as a fallback, when the value does not fall into any of the other forms, or the form is
missing. For example, when only the `r` is defined, the system will handle this unit as a "simple, properties like"
unit.

##### Complex with plurals definition

```json
{
  "baskets_with_oranges": {
    "baskets": {
      "z": "I don't have a basket %{oranges_zero_basket}",
      "o": "I have a basket %{oranges}",
      "r": "I have %1$d baskets %{oranges}"
    },
    "oranges": {
      "z": "without oranges",
      "o": "with one orange",
      "r": "with %2$d oranges"
    },
    "^oranges_zero_basket": {
      "z": "or an orange",
      "o": "but I have an orange",
      "r": "but I have %2$d oranges"
    }
  }
}
```

In this example, the translation unit consists of 3 plural forms: `baskets`, `oranges`, and `oranges_zero_basket`.

Translation always starts from the first plural format, which is bound with the first numeric parameter as well. When
references of the form "`%{...}`" appear, then the system uses this tag to search for parameters with this handler. This
procedure is recursive, until no more references are found.

Every parameter takes argument based on its position, starting from 1. In this example, `baskets` takes its value from
the first parameter (thus displaying this value we have to reference it as `%1$d`) and `oranges` from the second
parameter (thus, to use it for displaying again we should reference its value as `%2$d`).

Note the last parameter, which is prefixed with a `^` sign. This marks that the current parameter still uses the same
indexed parameter as the previous parameter. This is useful if we want to have more than one parameter, to reference the
same positional parameter.

##### Explicit argument index

As an alternative to relying on key order and the `^` prefix, any parameter map may declare its argument index
explicitly with a reserved `i` key, mirroring the convention already used by the Properties backend. When `i` is
present it overrides the positional/caret inference; when absent the previous behaviour is preserved unchanged.

```json
{
  "baskets_with_oranges": {
    "oranges": {
      "i": 2,
      "z": "without oranges",
      "o": "with one orange",
      "r": "with %2$d oranges"
    },
    "baskets": {
      "i": 1,
      "z": "I don't have a basket %{oranges_zero_basket}.",
      "o": "I have a basket %{oranges}.",
      "r": "I have %1$d baskets %{oranges}."
    },
    "oranges_zero_basket": {
      "i": 2,
      "z": "or an orange",
      "o": "but I have an orange",
      "r": "but I have %2$d oranges"
    }
  }
}
```

This makes the configuration order-independent — useful when the file is generated by a tool that does not preserve
key order, or when sharing an argument with a non-adjacent parameter would otherwise require multiple `^` prefixes.
Mixing explicit and implicit indices in the same translation is allowed; existing files without `i` keep working
without changes.

##### Explicit master format

By default the root format string of a multi-parameter translation is inferred from the first key, producing
`%{firstKey}`. Every other parameter has to be reached transitively through `%{...}` references inside the plural
forms of that first parameter — i.e. the first parameter must "wrap" the rest.

When the rest of the translation does not naturally have a single wrapping parameter, a reserved `format` key may be
placed alongside the parameters to declare the root template explicitly. When `format` is present it overrides the
"first key" inference; when absent the previous behaviour is preserved unchanged.

Take a message that conveys two independent counts, like _"3 hours and 12 minutes ago"_. Without an explicit master
format, one of the two values would have to nest the other inside every one of its plural forms — duplicating the
embedding logic across each branch. With `format` the sentence is written once at the top and each parameter stays
self-contained:

```json
{
  "elapsed_time": {
    "format": "%{hours} and %{minutes} ago",
    "hours": {
      "i": 1,
      "z": "less than an hour",
      "o": "1 hour",
      "r": "%1$d hours"
    },
    "minutes": {
      "i": 2,
      "z": "0 minutes",
      "o": "1 minute",
      "r": "%2$d minutes"
    }
  }
}
```

```java
lalein.format("elapsed_time", 0,  5);   // "less than an hour and 5 minutes ago"
lalein.format("elapsed_time", 1,  1);   // "1 hour and 1 minute ago"
lalein.format("elapsed_time", 3, 30);   // "3 hours and 30 minutes ago"
```

This also enables:

* a parameter appearing in any position, even reversed against its argument index (e.g. `"%{minutes} into the %{hours}-hour block"`)
* the same parameter referenced multiple times — useful for confirmation strings like
  `"Delete %{count}? %{count} will be lost."`
* different word orders per locale by changing only `format`, keeping the parameter definitions intact across
  translations

The writer emits `format` only when the translation's actual format differs from the default `%{firstKey}`, so
round-tripping files that don't use `format` stays compact and identical.

#### YAML example

A YAML version of this example can be found [here](yaml/src/test/resources/Localizable.yaml). Same rules and definitions
as with the JSON format apply. Only the syntax of the configuration file follows YAML standards.

#### Properties example

Properties do not have internal structure, they are `key/value` based so special handling is needed when using this
backend format. On the plus side, no external libraries are required and the size of `lalein` is at minimum. If there
are no size concerns, it is advised to use a more user-readable format instead.

An example of the same dataset as Properties, is [here](properties/src/test/resources/Localizable.properties):

```properties
peaches=I have peaches.
apples=%{main}
apples.main.i=1
apples.main.z=I don't have apples.
apples.main.o=I have an apple.
apples.main.t=I have two apples.
apples.main.r=I have %d apples.
baskets_with_oranges=%{baskets}
baskets_with_oranges.baskets.i=1
baskets_with_oranges.baskets.z=I don't have a basket %{oranges_zero_basket}.
baskets_with_oranges.baskets.o=I have a basket %{oranges}.
baskets_with_oranges.baskets.r=I have %1$d baskets %{oranges}.
baskets_with_oranges.oranges.i=2
baskets_with_oranges.oranges.z=without oranges
baskets_with_oranges.oranges.o=with one orange
baskets_with_oranges.oranges.r=with %2$d oranges
baskets_with_oranges.oranges_zero_basket.i=2
baskets_with_oranges.oranges_zero_basket.z=or an orange
baskets_with_oranges.oranges_zero_basket.o=but I have an orange
baskets_with_oranges.oranges_zero_basket.r=but I have %2$d oranges
```

The main differences with the other, structured, formats are:

* All entries that do not have a dot in their key name, are considered as translation units.
* Translation parameter handlers should be explicitly named, even if there is just one parameter.
* The plural forms should be described, one by one, in the format `UNIT.PARAMETER.FORM`, where
    * `UNIT` is the name of the translation unit
    * `PARAMETER` is the name of the translation parameter
    * `FORM` is the plural form single letter name
* The index of the parameter should be explicitly defined in a key named `UNIT.PARAMETER.i`
* The parameter list is gathered automatically through recursion from the various format Strings.

#### XCStrings example

Apple's String Catalog (`.xcstrings`) — the JSON-based format introduced with Xcode 15 — is supported as a first-class
backend. Catalogs may carry multiple locales in a single file; the loader accepts a language parameter or falls back
to the catalog's `sourceLanguage`. Plural variants follow the standard CLDR categories (`zero`, `one`, `two`, `few`,
`many`, `other`), and multi-parameter messages are represented through `substitutions` with explicit `argNum` and
`formatSpecifier`. The full example file lives [here](xcstrings/src/test/resources/Localizable.xcstrings).

```json
{
  "sourceLanguage": "en",
  "version": "1.0",
  "strings": {
    "apples": {
      "localizations": {
        "en": {
          "variations": {
            "plural": {
              "one":   { "stringUnit": { "state": "translated", "value": "I have an apple." } },
              "other": { "stringUnit": { "state": "translated", "value": "I have %lld apples." } }
            }
          }
        }
      }
    }
  }
}
```

Apple-specific format specifiers (`%lld`, `%@`, `%arg`) are translated to their Java equivalents transparently.

#### Fluent example

Mozilla's [Fluent](https://projectfluent.org/) (`.ftl`) is also supported, written with a small hand-rolled parser
that needs no external dependencies. Nested select expressions map directly onto Lalein's multi-parameter plurals,
making it the most expressive backend in the set. The full example file lives [here](fluent/src/test/resources/Localizable.ftl).

```fluent
peaches = I have peaches.

apples = { $count ->
    [zero]  I don't have apples.
    [one]   I have an apple.
    [two]   I have two apples.
   *[other] I have { $count } apples.
}
```

Each Fluent variable becomes a Lalein `Parameter`; argument indices are assigned by order of first appearance.

#### Map example

For programmatic, in-memory usage, a `Map<String, Object>` mirroring the JSON structure can be passed directly:

```java
Map<String, Object> apples = new LinkedHashMap<>();
apples.put("o", "I have an apple.");
apples.put("r", "I have %d apples.");
Map<String, Object> data = new LinkedHashMap<>();
data.put("apples", apples);
Lalein lalein = MapLalein.fromMap(data);
```

This is the minimal-dependency backend (only `lalein-core` is required) and is useful for tests, code generation,
or building translation data dynamically without round-tripping through a text format.

### Usage in Java

Here is an example, how to use this library in your own code:

```java
// JSON
Lalein lalein = JsonLalein.fromResource("/Localizable.en.json");

// YAML
Lalein lalein = YamlLalein.fromResource("/Localizable.en.yaml");

// Properties
Lalein lalein = PropertiesLalein.fromResource("/Localizable.en.properties");

// XCStrings — second argument is the target language
Lalein lalein = XcStringsLalein.fromResource("/Localizable.xcstrings", "en");

// Fluent
Lalein lalein = FluentLalein.fromResource("/Localizable.ftl");

// Map (programmatic)
Lalein lalein = MapLalein.fromMap(someMap);
```

Translations loaded from any backend can be re-emitted to any other — handy for migration or for keeping a single
authoring format synchronised with platform-specific outputs:

```java
Lalein source = YamlLalein.fromResource("/Localizable.yaml");
JsonObject xcstrings = XcStringsLalein.toJson(source, "en");
String fluentText   = FluentLalein.toString(source);
Properties props    = PropertiesLalein.toProperties(source);
```

Then, to format a string based on the translation unit handler:

```java
System.out.println(lalein.format("I have baskets with oranges", 0, 1));
System.out.println(lalein.format("I have baskets with oranges", 1, 2));
System.out.println(lalein.format("I have baskets with oranges", 2, 0));
```

Using the helper methods, you get an instance of `Lalein` object. Then, using the `format` method of `Lalein` instance,
we reference a translation unit handler, provide the positional parameters and get the translated text.

For every localization a new localization file is of course required.

### How to add this library to your own projects

With Maven, add the backend dependency that matches your translation format. Each backend transitively pulls in
`lalein-core`, so you do not need to declare it separately.

```xml
<!-- JSON -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>json</artifactId>
    <version>1.1.2</version>
</dependency>
```
```xml
<!-- YAML -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>yaml</artifactId>
    <version>1.1.2</version>
</dependency>
```
```xml
<!-- Properties (no extra runtime dependencies — smallest footprint) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>properties</artifactId>
    <version>1.1.2</version>
</dependency>
```
```xml
<!-- XCStrings (String Catalog) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>xcstrings</artifactId>
    <version>1.1.2</version>
</dependency>
```
```xml
<!-- Fluent — Mozilla .ftl (no extra runtime dependencies) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>fluent</artifactId>
    <version>1.1.2</version>
</dependency>
```
```xml
<!-- Map — programmatic, no I/O -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>map</artifactId>
    <version>1.1.2</version>
</dependency>
```

Backends can be combined freely in the same project; for example, a build tool might depend on `xcstrings` and
`yaml` simultaneously to convert between the two.
