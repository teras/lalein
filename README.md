## Translations in Java: The proper way

### TL;DR

This library helps Java projects to properly support i18n, by correctly handling plurals and text
reformatting based on rules. The implementation is loosely based on Apple's `Localizable.stringsdict` and
Android's `strings.xml` approach.

An example of a translation file can be found using [JSON](#json-example), or [YAML](#yaml-example),
or [Properties](#properties-example) format. To use in your own programs you need
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
  * [YAML example](#yaml-example)
  * [Properties example](#properties-example)
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

`lalein` comes to fill the gap; a lightweight (only 17K size with Properties backend) Java library, that properly
handles all _Language Plural Rules_. When using `lalein` messages with arbitrary complexity, like

* _No files to copy_
* _Copying 1 file_
* _Copying 1 file out of 2 files_
* _Copying 3 files out of 4 files_

are easy to implement. The configuration is in plain, human-readable, JSON, YAML, or Properties format.

### Translations format

#### First some definitions

* Translation unit: a phrase or a block of text that needs to be translated. Every unit has a _handler_, a textual tag
  that uniquely identifies this unit.
* Translation parameter: parts of text that, when combined, can form a translation unit. It is possible that a
  translation unit has no parameters, or just one parameter if the text to be translated is simple. Every parameter has
  a _handler_, a textual tag that uniquely identifies the parameter per unit.
* Plural form: formulated translation parameters, based on counting a value, and producing the correct plural form.

#### JSON example

Although the translation definitions are data-driven, and can be fully customized to fit your needs, the library by
default provides two backends, under JSON and under YAML. Here there will be an example of how to handle different
scenarios using the JSON backend. The full example file can be found [here](json/src/test/resources/Localizable.json).

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

### Usage in Java

Here is an example, how to use this library in your own code:

```java
// Java
Lalein lalein = JsonLalein.fromResource("/Localizable.en.json");
```

```java
// YAML
Lalein lalein = YamlLalein.fromResource("/Localizable.en.yaml");
```

```java
// Properties
Lalein lalein = PropertiesLalein.fromResource("/Localizable.en.properties");
```

and then, to format a string based on the translation unit handler:

```java
System.out.println(lalein.format("I have baskets with oranges", 0, 1));
System.out.println(lalein.format("I have baskets with oranges", 1, 2));
System.out.println(lalein.format("I have baskets with oranges", 2, 0));
```

Using the helper methods, you get an instance of `Lalein` object. Then, using the `format` method of `Lalein` instance,
we reference a translation unit handler, provide the positional parameters and get the translated text.

For every localization a new localization file is of course required.

### How to add this library to your own projects

To use this library on your own applications, with maven, you need to add either backend:

```xml
<!-- JSON -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>json</artifactId>
    <version>1.1.0</version>
</dependency>
```
or
```xml
<!-- YAML -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>yaml</artifactId>
    <version>1.1.0</version>
</dependency>
```
or
```xml
<!-- Properties -->
<dependency>
    <groupId>com.panayotis.lalein</groupId>
    <artifactId>properties</artifactId>
    <version>1.1.0</version>
</dependency>
```
