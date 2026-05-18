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
    * [Which argument drives the plural](#which-argument-drives-the-plural)
    * [Select-mode parameters (gender, formality, …)](#select-mode-parameters-gender-formality-)
    * [Complex with multiple parameters](#complex-with-multiple-parameters)
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

`lalein` comes to fill the gap; a lightweight (only 15K core plus 5K for the Properties backend) Java library, that properly
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

##### Which argument drives the plural

When a translation has a single plural parameter, the caller passes arguments positionally — exactly as with
`String.format`. Lalein figures out *which* of those arguments is the numeric counter that selects between
`o`/`r`/etc. automatically, by looking at the translation in this order:

1. **Unique positional ref inside the plural forms.** If the plural forms reference a single positional numeric
   placeholder (e.g. `%2$d`), that index is the counter.
2. **First numeric placeholder in the handler.** If step 1 finds nothing useful (or finds conflicting references),
   Lalein scans the handler itself for the first numeric printf placeholder.
3. **Fallback to `1`.** When neither step yields an answer (e.g. for opaque keys like `"apples"` without any
   placeholder), the first argument is used.

The next four examples cover the common cases. In every case the caller code is plain positional varargs — there
is nothing extra to remember at the call site.

###### Case A — the counter is the first numeric argument

The typical case. Just write the translation; Lalein picks argument 1.

```json
{
  "I have %d apples": {
    "o": "I have one apple.",
    "r": "I have %d apples."
  }
}
```

```java
lalein.format("I have %d apples", 5);  // "I have 5 apples."
```

###### Case B — the counter is not the first argument

A mixed-type message where the count is the *second* argument. No `i` key, no reordering:

```json
{
  "Cash payment of %s saved (%d month(s) allocated)": {
    "o": "Cash payment of %1$s saved (1 month allocated).",
    "r": "Cash payment of %1$s saved (%2$d months allocated)."
  }
}
```

```java
lalein.format("Cash payment of %s saved (%d month(s) allocated)", "12.34", 1);
// "Cash payment of 12.34 saved (1 month allocated)."
```

Step 1 picks up `%2$d` in the forms; step 2 confirms that the first *numeric* placeholder in the handler is at
position 2. Either way, the counter is argument 2.

###### Case C — multiple `%d` in the handler, the counter is the *non-first* one

Step 1 is decisive here: the forms reference only `%2$d`, so it is unambiguous.

```json
{
  "text %d %d": {
    "o": "single %2$d",
    "r": "many %2$d"
  }
}
```

```java
lalein.format("text %d %d", 99, 1);   // "single 1"
lalein.format("text %d %d", 99, 7);   // "many 7"
```

###### Case D — genuinely ambiguous: use the explicit `i` key

When the handler has two or more numeric placeholders *and* the forms reference more than one of them, Lalein
cannot guess which is the counter. Spell it out with `i`:

```json
{
  "Selected %d of %d items": {
    "i": 2,
    "o": "Selected %1$d of %2$d item",
    "r": "Selected %1$d of %2$d items"
  }
}
```

```java
lalein.format("Selected %d of %d items", 0, 1);  // "Selected 0 of 1 item"
lalein.format("Selected %d of %d items", 3, 5);  // "Selected 3 of 5 items"
```

`i` always wins over auto-derive — useful when the data shape is unavoidably ambiguous, or when you want to be
explicit for clarity.

##### Select-mode parameters (gender, formality, …)

The plural keys `z/o/t/f/m/r` are reserved for the CLDR plural categories. **Any other key** the translator writes
inside a parameter becomes a *custom selector* — chosen by passing a `String` (or `Enum`) at the call site instead
of a `Number`. The `r` key keeps its role as the universal fallback when no custom key matches.

```json
{
  "post_liked": {
    "female": "She liked your post",
    "male":   "He liked your post",
    "r":      "They liked your post"
  }
}
```

```java
lalein.format("post_liked", "female");   // "She liked your post"
lalein.format("post_liked", "male");     // "He liked your post"
lalein.format("post_liked", "other");    // "They liked your post"
lalein.format("post_liked", null);       // "They liked your post"  (null falls to r)
```

There is no built-in vocabulary — the translator decides which keys make sense for each message. The same data
model handles gender, formality (`formal`/`casual`), variants, term selection, or anything else that branches on
a discrete value.

A parameter mixing CLDR keys and custom keys is allowed and behaves as a select-mode parameter for `String` args
and as a plural parameter for `Number` args.

##### Complex with multiple parameters

When a single phrase combines several plural- or select-driven values, switch to the *complex form*: each
parameter is a named sub-map, the master template references them with `%{name}`, and references can be nested.
The example below combines a `String` selector for the subject pronoun with a `Number`-driven plural count —
written cell-by-cell so each natural-language form stays intact across locales.

```json
{
  "user_apples": {
    "format": "%{verb}",
    "verb": {
      "female": "%{female_count}",
      "male":   "%{male_count}",
      "r":      "%{other_count}"
    },
    "female_count": {
      "i": 2,
      "z": "She doesn't have apples",
      "o": "She has 1 apple",
      "r": "She has %2$d apples"
    },
    "^male_count": {
      "z": "He doesn't have apples",
      "o": "He has 1 apple",
      "r": "He has %2$d apples"
    },
    "^other_count": {
      "z": "They don't have apples",
      "o": "They have 1 apple",
      "r": "They have %2$d apples"
    }
  }
}
```

```java
lalein.format("user_apples", "female", 0);  // "She doesn't have apples"
lalein.format("user_apples", "female", 5);  // "She has 5 apples"
lalein.format("user_apples", "male",   1);  // "He has 1 apple"
lalein.format("user_apples", "other",  0);  // "They don't have apples"
```

What is going on:

* The `format` key is the *master template*. When present it overrides the default of `%{firstKey}` and is
  the entry point of the resolution.
* Each `%{name}` is replaced by resolving the named parameter against the right argument. The result may itself
  contain further `%{...}` references — the resolver runs recursively until no more remain.
* `verb` is a *select-mode* parameter (custom keys `female`/`male`, plus `r` as fallback). It maps the first
  argument (a `String`) to another `%{name}` reference, picking the matching count parameter.
* `female_count`, `male_count`, `other_count` are *plural-mode* parameters (only CLDR keys). They read the
  second argument (a `Number`) and pick `z` / `o` / `r` based on its value.
* `i: 2` declares the argument index explicitly. `^male_count` and `^other_count` use the caret prefix to inherit
  the previous parameter's index (`2`) without repeating it. Both forms are equivalent — choose whichever reads
  better.
* Every cell of the (gender × count) cross-product is written verbatim. There is no factorization or
  combining of fragments — that is intentional: word order, verb agreement, and negation rules differ wildly
  across languages, so each locale rewrites the full phrase per branch. This mirrors how ICU MessageFormat's
  nested `{gender, select, … {{count, plural, …}}}` works.

If you only need to declare argument positions without nested resolution, the `i` key alone is enough:

```json
{
  "baskets": {
    "i": 2,
    "z": "without baskets",
    "o": "with one basket",
    "r": "with %2$d baskets"
  }
}
```

`i` overrides the default positional/caret inference, makes configurations order-independent, and is useful
when the file is generated by tools that don't preserve key order.

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
    <version>1.2</version>
</dependency>
```
```xml
<!-- YAML -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>yaml</artifactId>
    <version>1.2</version>
</dependency>
```
```xml
<!-- Properties (no extra runtime dependencies — smallest footprint) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>properties</artifactId>
    <version>1.2</version>
</dependency>
```
```xml
<!-- XCStrings (String Catalog) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>xcstrings</artifactId>
    <version>1.2</version>
</dependency>
```
```xml
<!-- Fluent — Mozilla .ftl (no extra runtime dependencies) -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>fluent</artifactId>
    <version>1.2</version>
</dependency>
```
```xml
<!-- Map — programmatic, no I/O -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>map</artifactId>
    <version>1.2</version>
</dependency>
```

Backends can be combined freely in the same project; for example, a build tool might depend on `xcstrings` and
`yaml` simultaneously to convert between the two.
